package peergos.payment;

import peergos.payment.util.*;

import java.time.*;
import java.util.*;

public class RamPaymentStore implements PaymentStore {

    private final Map<String, UserState> userStates;

    public RamPaymentStore() {
        this.userStates = new HashMap<>();
    }

    @Override
    public synchronized long userCount() {
        return userStates.size();
    }

    @Override
    public synchronized boolean hasUser(String username) {
        return userStates.containsKey(username);
    }

    @Override
    public synchronized List<String> getAllUsernames() {
        return new ArrayList<>(userStates.keySet());
    }

    public synchronized UserState ensureUser(String username, Natural freeSpace, LocalDateTime now) {
        userStates.putIfAbsent(username, new UserState(freeSpace, Natural.ZERO, Natural.ZERO, Natural.ZERO,
                Natural.ZERO, now, "gbp", null, new ArrayList<>()));
        return userStates.get(username);
    }

    @Override
    public void setCustomer(String username, CustomerResult customer) {
        userStates.get(username).setCustomer(customer);
    }

    @Override
    public synchronized CustomerResult getCustomer(String username) {
        return userStates.get(username).getCustomer();
    }

    @Override
    public void setCurrentBalance(String username, Natural balance) {
        userStates.get(username).setCurrentBalance(balance);
    }

    @Override
    public Natural getCurrentBalance(String username) {
        return userStates.get(username).getCurrentBalance();
    }

    @Override
    public void setCurrentQuota(String username, Natural quota) {
        userStates.get(username).setQuota(quota);
    }

    @Override
    public void setFreeQuota(String username, Natural quota) {
        userStates.get(username).setFreeQuota(quota);
    }

    @Override
    public Natural getFreeQuota(String username) {
        return userStates.get(username).getFreeQuota();
    }

    @Override
    public void addPayment(String username, PaymentResult payment) {
        userStates.get(username).addPayment(payment);
    }

    @Override
    public void setQuotaExpiry(String username, LocalDateTime expiry) {
        userStates.get(username).setQuotaExpiry(expiry);
    }

    @Override
    public LocalDateTime getQuotaExpiry(String username) {
        return userStates.get(username).getQuotaExpiry();
    }

    @Override
    public synchronized Natural getDesiredQuota(String username) {
        return userStates.get(username).getDesiredQuota();
    }

    @Override
    public synchronized void setDesiredQuota(String username, Natural quota, LocalDateTime now) {
        userStates.get(username).setDesiredQuota(quota);
    }

    @Override
    public synchronized Natural getCurrentQuota(String username) {
        return userStates.get(username).getQuota();
    }
}
