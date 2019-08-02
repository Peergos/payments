package peergos.payment;

import peergos.payment.util.*;

import java.time.*;

public interface Bank {

    PaymentResult takePayment(CardToken cardToken, Natural cents, String currency, LocalDateTime now);
}
