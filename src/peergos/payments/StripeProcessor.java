package peergos.payments;

import java.time.*;
import java.util.*;

public class StripeProcessor implements Bank {

    private final String stripeSecretToken;

    public StripeProcessor(String stripeSecretToken) {
        this.stripeSecretToken = stripeSecretToken;
    }

    @Override
    public PaymentResult takePayment(CardToken cardToken, Natural cents, String currency, LocalDateTime now) {
        String res = Payment.takePayment(cardToken.token, stripeSecretToken);
        // TODO parse res
        return new PaymentResult(cents, currency, now, Optional.empty());
    }
}
