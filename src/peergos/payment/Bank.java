package peergos.payment;

import peergos.payment.util.*;

import java.time.*;

public interface Bank {

    CustomerResult createCustomer(String username);

    IntentResult setupIntent(CustomerResult cus);

    PaymentResult takePayment(CustomerResult cus, Natural cents, String currency, LocalDateTime now, Natural forQuota);
}
