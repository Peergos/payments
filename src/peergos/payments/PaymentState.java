package peergos.payments;

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
                    }
                }
            }
        }

        public synchronized void setMinPayment(long cents) {
            this.minPaymentCents = new Natural(cents);
        }

        public synchronized void setDesiredQuota(long bytes) {
            this.desiredQuotaBytes = new Natural(bytes);
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
    private final Bank bank;

    public PaymentState(Map<String, UserState> userStates, Natural bytesPerCent, Bank bank) {
        this.userStates = userStates;
        this.bytesPerCent = bytesPerCent;
        this.bank = bank;
    }

    public Natural convertCentsToBytes(Natural cents) {
        return cents.times(bytesPerCent);
    }

    public Natural convertBytesToCents(Natural bytes) {
        return bytes.divide(bytesPerCent);
    }

    public synchronized void addUser(String username, long desiredQuota) {
        userStates.putIfAbsent(username, new UserState(Natural.ZERO, Natural.ZERO, Natural.ZERO, Natural.ZERO,
                new Natural(desiredQuota), LocalDateTime.MIN, "gbp", null, new HashMap<>()));
    }

    public synchronized void addCard(String username, CardToken card, LocalDateTime now) {
        UserState userState = userStates.get(username);
        if (userState == null)
            throw new IllegalStateException("User not present when adding card: " + username);
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
