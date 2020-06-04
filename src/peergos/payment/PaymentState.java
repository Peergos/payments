package peergos.payment;

import peergos.payment.util.*;
import peergos.shared.storage.*;

import java.time.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PaymentState {
    private static final Logger LOG = Logger.getLogger("NULL_FORMAT");

    private final PaymentStore userStates;
    private final Pricer pricer;
    private final Natural minPaymentCents;
    private final Bank bank;
    private final Natural defaultFreeQuota;
    private final int maxUsers;
    private final Set<Long> allowedQuotas;

    public PaymentState(PaymentStore userStates,
                        Pricer pricer,
                        Natural minPaymentCents,
                        Bank bank,
                        Natural defaultFreeQuota,
                        int maxUsers,
                        Set<Long> allowedQuotas) {
        this.userStates = userStates;
        this.pricer = pricer;
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

    public PaymentProperties getPaymentProperties(String username, boolean newClientSecret, String ourUrl) {
        Optional<String> clientSecret = newClientSecret ? Optional.of(generateClientSecret(username)) : Optional.empty();
        long freeQuota = userStates.getFreeQuota(username).val;
        long desiredQuota = userStates.getDesiredQuota(username).val;
        Optional<String> error = userStates.getError(username);
        return error.map(err -> PaymentProperties.errored(ourUrl, err, clientSecret, freeQuota, desiredQuota))
                .orElseGet(() -> new PaymentProperties(ourUrl, clientSecret, freeQuota, desiredQuota));
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

    public synchronized void ensureUser(String username, LocalDateTime now) {
        ensureUser(username, defaultFreeQuota, now);
    }

    public synchronized void ensureUser(String username, Natural freeSpace, LocalDateTime now) {
        userStates.ensureUser(username, freeSpace, now);
    }

    /**
     *  Take any payments and expire any old quota
     */
    private synchronized boolean processUser(String username, LocalDateTime now) {
        boolean processed = true;
        Natural desiredQuotaBytes = userStates.getDesiredQuota(username);
        if (now.isAfter(userStates.getQuotaExpiry(username).minusSeconds(1)))
            userStates.setCurrentQuota(username, Natural.ZERO);

        Natural currentQuotaBytes = userStates.getCurrentQuota(username);
        if (currentQuotaBytes.val < desiredQuotaBytes.val) {
            Natural toPay = pricer.convertBytesToCents(desiredQuotaBytes.minus(currentQuotaBytes));
            // use any existing balance first
            Natural currentBalanceCents = userStates.getCurrentBalance(username);
            if (currentBalanceCents.val > 0) {
                if (currentBalanceCents.val >= toPay.val) {
                    userStates.setCurrentBalance(username, currentBalanceCents.minus(toPay));
                    userStates.setCurrentQuota(username, desiredQuotaBytes);
                    userStates.setQuotaExpiry(username, now.plusMonths(1));
                    return true;
                }
            }
            if (currentQuotaBytes.val < desiredQuotaBytes.val) {
                // take a payment
                Natural remaining = pricer.priceDifferenceInCents(currentQuotaBytes, desiredQuotaBytes);
                Natural toCharge = minPaymentCents.max(remaining);
                try {
                    CustomerResult customer = userStates.getCustomer(username);
                    PaymentResult paymentResult = bank.takePayment(customer, toCharge, "gbp", now);
                    if (paymentResult.isSuccessful()) {
                        userStates.setCurrentBalance(username, toCharge.minus(remaining));
                        userStates.setCurrentQuota(username, desiredQuotaBytes);
                        userStates.setQuotaExpiry(username, now.plusMonths(1));
                    } else {
                        userStates.setError(username, paymentResult.failureError.get());
                        processed = false;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    processed = false;
                }
            }
        }
        return processed;
    }

    public synchronized void setDesiredQuota(String username, Natural quota, LocalDateTime now) {
        if (! allowedQuotas.contains(quota.val))
            throw new IllegalStateException("Invalid quota requested: " + quota.val);
        userStates.ensureUser(username, defaultFreeQuota, now);
        userStates.setDesiredQuota(username, quota, now);
        processUser(username, now);
    }

    public synchronized Pair<Integer, Integer> processAll(LocalDateTime now) {
        int successCount = 0;
        int failureCount = 0;
        for (String username : getAllUsernames()) {
            try {
                if (processUser(username, now == null ? LocalDateTime.now() : now)) {
                    successCount++;
                } else {
                    failureCount++;
                }
            } catch (Throwable err) {
                LOG.log(Level.SEVERE,"Unable to process user:" + username, err);
                failureCount++;
            }
        }
        return new Pair<>(successCount, failureCount);
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
