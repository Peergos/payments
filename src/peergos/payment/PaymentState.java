package peergos.payment;

import peergos.payment.util.*;

import java.time.*;
import java.util.*;

public class PaymentState implements Converter {

    public static class UserState {
        private Natural freeBytes;
        private Natural minPaymentCents;
        private Natural currentBalanceCents;
        private Natural currentQuotaBytes;
        private Natural desiredQuotaBytes;
        private LocalDateTime expiry;
        private String currency;
        private CustomerResult customer;
        private final List<PaymentResult> payments;

        public UserState(Natural freeBytes,
                         Natural minPaymentCents,
                         Natural currentBalanceCents,
                         Natural currentQuotaBytes,
                         Natural desiredQuotaBytes,
                         LocalDateTime expiry,
                         String currency,
                         CustomerResult customer,
                         List<PaymentResult> payments) {
            this.freeBytes = freeBytes;
            this.minPaymentCents = minPaymentCents;
            this.currentBalanceCents = currentBalanceCents;
            this.currentQuotaBytes = currentQuotaBytes;
            this.desiredQuotaBytes = desiredQuotaBytes;
            this.expiry = expiry;
            this.currency = currency;
            this.customer = customer;
            this.payments = payments;
        }

        /**
         *  Take any payments and expire any old quota
         */
        public synchronized void update(LocalDateTime now, Converter converter, Bank bank) {
            System.out.printf("********** Update payment state: current=");
            System.out.println(this);
            if (now.isAfter(expiry.minusSeconds(1)))
                currentQuotaBytes = Natural.ZERO;
            if (currentQuotaBytes.val < desiredQuotaBytes.val) {
                Natural toPay = converter.convertBytesToCents(desiredQuotaBytes.minus(currentQuotaBytes));
                // use any existing balance first
                if (currentBalanceCents.val > 0) {
                    if (currentBalanceCents.val >= toPay.val) {
                        currentBalanceCents = currentBalanceCents.minus(toPay);
                        currentQuotaBytes = desiredQuotaBytes;
                    } else {
                        currentQuotaBytes = currentQuotaBytes.plus(converter.convertCentsToBytes(currentBalanceCents));
                        currentBalanceCents = Natural.ZERO;
                    }
                }
                if (currentQuotaBytes.val < desiredQuotaBytes.val) {
                    // take a payment
                    Natural remaining = converter.convertBytesToCents(desiredQuotaBytes.minus(currentQuotaBytes));
                    Natural toCharge = minPaymentCents.max(remaining);
                    try {
                        PaymentResult paymentResult = bank.takePayment(customer, toCharge, currency, now);
                        payments.add(paymentResult);
                        if (paymentResult.isSuccessful()) {
                            currentBalanceCents = toCharge.minus(remaining);
                            currentQuotaBytes = desiredQuotaBytes;
                            expiry = now.plusMonths(1);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            System.out.println("final state: " + this);
        }

        public synchronized void setMinPayment(Natural cents) {
            this.minPaymentCents = cents;
        }

        public synchronized void setDesiredQuota(Natural bytes) {
            this.desiredQuotaBytes = bytes;
        }

        public synchronized IntentResult generateIntent(Bank bank) {
            if (customer == null)
                customer = bank.createCustomer();
            return bank.setupIntent(customer);
        }

        public synchronized long currentQuota() {
            return freeBytes.val + currentQuotaBytes.val;
        }

        @Override
        public String toString() {
            return "UserState{" +
                    "\n\t freeMiB=" + freeBytes.val/1024/1024 +
                    ",\n\t minPaymentCents=" + minPaymentCents +
                    ",\n\t currentBalanceCents=" + currentBalanceCents +
                    ",\n\t currentQuotaMiB=" + currentQuotaBytes.val/1024/1024 +
                    ",\n\t desiredQuotaMiB=" + desiredQuotaBytes.val/1024/1024 +
                    ",\n\t expiry=" + expiry +
                    ",\n\t currency='" + currency + '\'' +
                    ",\n\t payments=" + payments +
                    "\n}";
        }
    }

    private final Map<String, UserState> userStates;
    private final Natural bytesPerCent;
    private final Natural minQuota;
    private final Bank bank;
    private final Natural defaultFreeQuota;
    private final int maxUsers;
    private final Set<Long> allowedQuotas;

    public PaymentState(Map<String, UserState> userStates,
                        Natural bytesPerCent,
                        Natural minQuota,
                        Bank bank,
                        Natural defaultFreeQuota,
                        int maxUsers,
                        Set<Long> allowedQuotas) {
        this.userStates = userStates;
        this.bytesPerCent = bytesPerCent;
        this.minQuota = minQuota;
        this.bank = bank;
        this.defaultFreeQuota = defaultFreeQuota;
        this.maxUsers = maxUsers;
        this.allowedQuotas = allowedQuotas;
    }

    public boolean acceptingSignups() {
        return userCount() < maxUsers;
    }

    public List<String> getAllUsernames() {
        return new ArrayList<>(userStates.keySet());
    }

    public long userCount() {
        return userStates.size();
    }

    public boolean hasUser(String username) {
        return userStates.containsKey(username);
    }

    public Natural convertCentsToBytes(Natural cents) {
        return cents.times(bytesPerCent);
    }

    public Natural convertBytesToCents(Natural bytes) {
        return bytes.divide(bytesPerCent);
    }

    public String generateClientSecret(String username) {
        if (! userStates.containsKey(username))
            throw new IllegalStateException("Unknown user: " + username);
        UserState userState = userStates.get(username);
        return userState.generateIntent(bank).clientSecret;
    }

    public synchronized UserState ensureUser(String username, LocalDateTime now) {
        userStates.putIfAbsent(username, new UserState(defaultFreeQuota, Natural.ZERO, Natural.ZERO, Natural.ZERO,
                Natural.ZERO, now, "gbp", null, new ArrayList<>()));
        return userStates.get(username);
    }

    public synchronized UserState ensureUser(String username, Natural freeSpace, LocalDateTime now) {
        userStates.putIfAbsent(username, new UserState(freeSpace, Natural.ZERO, Natural.ZERO, Natural.ZERO,
                Natural.ZERO, now, "gbp", null, new ArrayList<>()));
        return userStates.get(username);
    }

    public synchronized void setDesiredQuota(String username, Natural quota, LocalDateTime now) {
        if (! allowedQuotas.contains(quota.val))
            throw new IllegalStateException("Invalid quota requested: " + quota.val);
        UserState userState = ensureUser(username, now);
        userState.setDesiredQuota(quota.max(minQuota));
        userState.update(now, this, bank);
    }

    public synchronized void processAll(LocalDateTime now) {
        for (UserState userState : userStates.values()) {
            userState.update(now, this, bank);
        }
    }

    public synchronized long getCurrentQuota(String username) {
        UserState userState = userStates.get(username);
        if (userState == null) {
            if (acceptingSignups()) {
                ensureUser(username, defaultFreeQuota, LocalDateTime.now());
                return defaultFreeQuota.val;
            }
            throw new IllegalStateException("Unknown user " + username);
        }
        return userState.currentQuota();
    }
}
