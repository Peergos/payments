package peergos.payment;

import peergos.payment.util.*;

import java.time.*;
import java.util.*;

public class PaymentResult {
    public final Natural amount;
    public final String currency;
    public final LocalDateTime time;
    public final Optional<String> failureError;

    public PaymentResult(Natural amount, String currency, LocalDateTime time, Optional<String> failureError) {
        this.amount = amount;
        this.currency = currency;
        this.time = time;
        this.failureError = failureError;
    }

    public boolean isSuccessful() {
        return ! failureError.isPresent();
    }

    @Override
    public String toString() {
        Natural hundred = Natural.of(100);
        return amount.divide(hundred) + "." + String.format("%02d", amount.mod(hundred).val)
                + " " + currency
                + failureError.map(e -> " " + e).orElse("");
    }
}
