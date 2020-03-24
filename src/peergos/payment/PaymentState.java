package peergos.payment;

import peergos.payment.util.*;
import peergos.shared.storage.*;

import java.time.*;
import java.util.*;

public class PaymentState implements Converter {

    private final PaymentStore userStates;
    private final Natural bytesPerCent;
    private final Natural minQuota;
    private final Natural minPaymentCents;
    private final Bank bank;
    private final Natural defaultFreeQuota;
    private final int maxUsers;
    private final Set<Long> allowedQuotas;

    public PaymentState(PaymentStore userStates,
                        Natural bytesPerCent,
                        Natural minQuota,
                        Natural minPaymentCents,
                        Bank bank,
                        Natural defaultFreeQuota,
                        int maxUsers,
                        Set<Long> allowedQuotas) {
        this.userStates = userStates;
        this.bytesPerCent = bytesPerCent;
        this.minQuota = minQuota;
        this.minPaymentCents = minPaymentCents;
        this.bank = bank;
        this.defaultFreeQuota = defaultFreeQuota;
        this.maxUsers = maxUsers;
        this.allowedQuotas = allowedQuotas;
    }

    public boolean acceptingSignups() {
        return userCount() < maxUsers;
    }

    public List<String> getAllUsernames() {
        return userStates.getAllUsernames();
    }

    public long userCount() {
        return userStates.userCount();
    }

    public boolean hasUser(String username) {
        return userStates.hasUser(username);
    }

    public Natural convertCentsToBytes(Natural cents) {
        return cents.times(bytesPerCent);
    }

    public Natural convertBytesToCents(Natural bytes) {
        return bytes.divide(bytesPerCent);
    }

    public PaymentProperties getPaymentProperties(String username, boolean newClientSecret, String ourUrl) {
        String clientSecret = newClientSecret ? generateClientSecret(username) : "";
        long desiredQuota = userStates.getDesiredQuota(username).val;
        return new PaymentProperties(ourUrl, clientSecret, desiredQuota);
    }

    public String generateClientSecret(String username) {
        if (! userStates.hasUser(username))
            throw new IllegalStateException("Unknown user: " + username);
        CustomerResult customer = userStates.getCustomer(username);
        if (customer == null) {
            customer = bank.createCustomer();
            userStates.setCustomer(username, customer);
        }
        return bank.setupIntent(customer).clientSecret;
    }

    public synchronized UserState ensureUser(String username, LocalDateTime now) {
        return ensureUser(username, defaultFreeQuota, now);
    }

    public synchronized UserState ensureUser(String username, Natural freeSpace, LocalDateTime now) {
        return userStates.ensureUser(username, freeSpace, now);
    }

    /**
     *  Take any payments and expire any old quota
     */
    private void processUser(String username, LocalDateTime now) {
        Natural desiredQuotaBytes = userStates.getDesiredQuota(username);
        if (now.isAfter(userStates.getQuotaExpiry(username).minusSeconds(1)))
            userStates.setCurrentQuota(username, Natural.ZERO);

        Natural currentQuotaBytes = userStates.getCurrentQuota(username);
        if (currentQuotaBytes.val < desiredQuotaBytes.val) {
            Natural toPay = convertBytesToCents(desiredQuotaBytes.minus(currentQuotaBytes));
            // use any existing balance first
            Natural currentBalanceCents = userStates.getCurrentBalance(username);
            if (currentBalanceCents.val > 0) {
                if (currentBalanceCents.val >= toPay.val) {
                    userStates.setCurrentBalance(username, currentBalanceCents.minus(toPay));
                    currentQuotaBytes = desiredQuotaBytes;
                } else {
                    currentQuotaBytes = currentQuotaBytes.plus(convertCentsToBytes(currentBalanceCents));
                    userStates.setCurrentBalance(username, Natural.ZERO);
                }
            }
            if (currentQuotaBytes.val < desiredQuotaBytes.val) {
                // take a payment
                Natural remaining = convertBytesToCents(desiredQuotaBytes.minus(currentQuotaBytes));
                Natural toCharge = minPaymentCents.max(remaining);
                try {
                    CustomerResult customer = userStates.getCustomer(username);
                    PaymentResult paymentResult = bank.takePayment(customer, toCharge, "gbp", now);
                    userStates.addPayment(username, paymentResult);
                    if (paymentResult.isSuccessful()) {
                        userStates.setCurrentBalance(username, toCharge.minus(remaining));
                        userStates.setCurrentQuota(username, desiredQuotaBytes);
                        userStates.setQuotaExpiry(username, now.plusMonths(1));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public synchronized void setDesiredQuota(String username, Natural quota, LocalDateTime now) {
        if (! allowedQuotas.contains(quota.val))
            throw new IllegalStateException("Invalid quota requested: " + quota.val);
        userStates.ensureUser(username, defaultFreeQuota, now);
        userStates.setDesiredQuota(username, quota.max(minQuota), now);
        processUser(username, now);
    }

    public synchronized void processAll(LocalDateTime now) {
        for (String username : getAllUsernames()) {
            processUser(username, now);
        }
    }

    public synchronized long getCurrentQuota(String username) {
        if (! userStates.hasUser(username)) {
            if (acceptingSignups()) {
                ensureUser(username, defaultFreeQuota, LocalDateTime.now());
                return defaultFreeQuota.val;
            }
            throw new IllegalStateException("Unknown user " + username);
        }
        return userStates.getCurrentQuota(username).val + userStates.getFreeQuota(username).val;
    }
}
