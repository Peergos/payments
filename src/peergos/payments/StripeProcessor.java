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
        Map<String, Object> res = (Map) JSONParser.parse(Payment.takePayment(cardToken.token, stripeSecretToken));
        boolean success = "succeeded".equals(res.get("status"));
        Optional<String> errMessage = Optional.ofNullable(res.get("failure_message")).map(x -> (String) x);
        // TODO parse more fields from res
        return new PaymentResult(cents, currency, now, errMessage);
    }
}
