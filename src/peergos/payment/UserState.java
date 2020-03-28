package peergos.payment;

import peergos.payment.util.*;

import java.time.*;
import java.util.*;

public class UserState {

    private Natural freeBytes;
    private Natural currentBalanceCents;
    private Natural currentQuotaBytes;
    private Natural desiredQuotaBytes;
    private LocalDateTime expiry;
    private CustomerResult customer;

    public UserState(Natural freeBytes,
                     Natural currentBalanceCents,
                     Natural currentQuotaBytes,
                     Natural desiredQuotaBytes,
                     LocalDateTime expiry,
                     CustomerResult customer) {
        this.freeBytes = freeBytes;
        this.currentBalanceCents = currentBalanceCents;
        this.currentQuotaBytes = currentQuotaBytes;
        this.desiredQuotaBytes = desiredQuotaBytes;
        this.expiry = expiry;
        this.customer = customer;
    }

    public void setCustomer(CustomerResult customer) {
        this.customer = customer;
    }

    public CustomerResult getCustomer() {
        return customer;
    }

    public synchronized void setDesiredQuota(Natural bytes) {
        this.desiredQuotaBytes = bytes;
    }

    public synchronized Natural getDesiredQuota() {
        return desiredQuotaBytes;
    }

    public synchronized void setCurrentBalance(Natural balance) {
        this.currentBalanceCents = balance;
    }

    public synchronized Natural getCurrentBalance() {
        return currentBalanceCents;
    }

    public synchronized void setQuota(Natural quota) {
        this.currentQuotaBytes = quota;
    }

    public synchronized Natural getQuota() {
        return currentQuotaBytes;
    }

    public synchronized void setFreeQuota(Natural freeQuota) {
        this.freeBytes = freeQuota;
    }

    public synchronized Natural getFreeQuota() {
        return freeBytes;
    }

    public synchronized void setQuotaExpiry(LocalDateTime expiry) {
        this.expiry = expiry;
    }

    public LocalDateTime getQuotaExpiry() {
        return expiry;
    }

    @Override
    public String toString() {
        return "UserState{" +
                "\n\t freeMiB=" + freeBytes.val/1024/1024 +
                ",\n\t currentBalanceCents=" + currentBalanceCents +
                ",\n\t currentQuotaMiB=" + currentQuotaBytes.val/1024/1024 +
                ",\n\t desiredQuotaMiB=" + desiredQuotaBytes.val/1024/1024 +
                ",\n\t expiry=" + expiry +
                "\n}";
    }
}
