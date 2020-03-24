package peergos.payment;

import peergos.payment.util.*;

import java.time.*;
import java.util.*;

public interface PaymentStore {

    long userCount();

    boolean hasUser(String username);

    List<String> getAllUsernames();

    UserState ensureUser(String username, Natural freeSpace, LocalDateTime now);

    void setCustomer(String username, CustomerResult customer);
    CustomerResult getCustomer(String username);

    void setDesiredQuota(String username, Natural quota, LocalDateTime now);
    Natural getDesiredQuota(String username);

    void setCurrentBalance(String username, Natural balance);
    Natural getCurrentBalance(String username);

    void setCurrentQuota(String username, Natural quota);
    Natural getCurrentQuota(String username);

    void setFreeQuota(String username, Natural quota);
    Natural getFreeQuota(String username);

    void addPayment(String username, PaymentResult payment);

    void setQuotaExpiry(String username, LocalDateTime expiry);
    LocalDateTime getQuotaExpiry(String username);
}
