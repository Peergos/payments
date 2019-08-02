package peergos.payment;

import peergos.payment.util.*;

import java.time.*;
import java.util.*;

public class PaymentState implements Converter {

    static class UserState {
        private Natural freeBytes;
        private Natural minPaymentCents;
        private Natural currentBalanceCents;
        private Natural currentQuotaBytes;
        private Natural desiredQuotaBytes;
        private LocalDateTime expiry;
        private String currency;
        private CardToken currentCard;
        private final Map<CardToken, List<PaymentResult>> payments;

        public UserState(Natural freeBytes,
                         Natural minPaymentCents,
                         Natural currentBalanceCents,
                         Natural currentQuotaBytes,
                         Natural desiredQuotaBytes,
                         LocalDateTime expiry,
                         String currency,
                         CardToken currentCard,
                         Map<CardToken, List<PaymentResult>> payments) {
            this.freeBytes = freeBytes;
            this.minPaymentCents = minPaymentCents;
            this.currentBalanceCents = currentBalanceCents;
            this.currentQuotaBytes = currentQuotaBytes;
            this.desiredQuotaBytes = desiredQuotaBytes;
            this.expiry = expiry;
            this.currency = currency;
            this.currentCard = currentCard;
            this.payments = payments;
        }

        /**
         *  Take any payments and expire any old quota
         */
        public synchronized void update(LocalDateTime now, Converter converter, Bank bank) {
            if (now.isAfter(expiry))
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
                    Natural remaining = converter.convertBytesToCents(desiredQuotaBytes.minus(currentBalanceCents));
                    Natural toCharge = minPaymentCents.max(remaining);
                    PaymentResult paymentResult = bank.takePayment(currentCard, toCharge, currency, now);
                    payments.putIfAbsent(currentCard, new ArrayList<>());
                    payments.get(currentCard).add(paymentResult);
                    if (paymentResult.isSuccessful()) {
                        currentBalanceCents = toCharge.minus(remaining);
                        currentQuotaBytes = desiredQuotaBytes;
                        expiry = now.plusMonths(1);
                    }
                }
            }
        }

        public synchronized void setMinPayment(Natural cents) {
            this.minPaymentCents = cents;
        }

        public synchronized void setDesiredQuota(Natural bytes) {
            this.desiredQuotaBytes = bytes;
        }

        public synchronized void addCard(CardToken card) {
            currentCard = card;
            payments.putIfAbsent(card, new ArrayList<>());
        }

        public synchronized long currentQuota() {
            return freeBytes.val + currentQuotaBytes.val;
        }
    }

    private final Map<String, UserState> userStates;
    private final Natural bytesPerCent;
    private final Natural minQuota;
    private final Bank bank;

    public PaymentState(Map<String, UserState> userStates,
                        Natural bytesPerCent,
                        Natural minQuota,
                        Bank bank) {
        this.userStates = userStates;
        this.bytesPerCent = bytesPerCent;
        this.minQuota = minQuota;
        this.bank = bank;
    }

    public Natural convertCentsToBytes(Natural cents) {
        return cents.times(bytesPerCent);
    }

    public Natural convertBytesToCents(Natural bytes) {
        return bytes.divide(bytesPerCent);
    }

    public synchronized UserState ensureUser(String username) {
        userStates.putIfAbsent(username, new UserState(Natural.ZERO, Natural.ZERO, Natural.ZERO, Natural.ZERO,
                Natural.ZERO, LocalDateTime.MIN, "gbp", null, new HashMap<>()));
        return userStates.get(username);
    }

    public synchronized void setDesiredQuota(String username, Natural quota) {
        UserState userState = ensureUser(username);
        userState.setDesiredQuota(quota.max(minQuota));
    }

    public synchronized void addCard(String username, CardToken card, LocalDateTime now) {
        UserState userState = userStates.get(username);
        if (userState == null) {
            // we assume the call is already authorized by the storage node
            userState = ensureUser(username);
            userState.setDesiredQuota(minQuota);
        }
        userState.addCard(card);
        userState.update(now, this, bank);
    }

    public synchronized void processAll(LocalDateTime now) {
        for (UserState userState : userStates.values()) {
            userState.update(now, this, bank);
        }
    }

    public synchronized long getCurrentQuota(String username) {
        UserState userState = userStates.get(username);
        if (userState == null)
            throw new IllegalStateException("Unknown user " + username);
        return userState.currentQuota();
    }
}
