package peergos.payments;

import peergos.payments.util.*;

import java.time.*;

public interface Bank {

    PaymentResult takePayment(CardToken cardToken, Natural cents, String currency, LocalDateTime now);
}
