package peergos.payments;

import sun.misc.*;

import java.io.*;
import java.net.*;
import java.time.*;
import java.util.*;

public class StripeProcessor implements Bank {

    private final String stripeSecretToken;

    public StripeProcessor(String stripeSecretToken) {
        this.stripeSecretToken = stripeSecretToken;
    }

    @Override
    public PaymentResult takePayment(CardToken cardToken, Natural cents, String currency, LocalDateTime now) {
        Map<String, Object> res = (Map) JSONParser.parse(takePayment(cents, currency, cardToken.token, stripeSecretToken));
        boolean success = "succeeded".equals(res.get("status"));
        Optional<String> errMessage = Optional.ofNullable(res.get("failure_message")).map(x -> (String) x);
        // TODO parse more fields from res
        return new PaymentResult(cents, currency, now, errMessage);
    }

    public static String takePayment(Natural cents, String currency, String stripeToken, String stripeSecretKey) {
        try {
            String payload = "amount=" + cents.val + "&currency=" + currency + "&source=" + stripeToken;
            byte[] result = post("https://api.stripe.com/v1/charges", stripeSecretKey, payload);
            String res = new String(result);
            System.out.println("Took payment: " + res);
            return res;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public static byte[] post(String url, String stripeSecretKey, String payload) throws IOException {
        HttpURLConnection conn = null;
        try
        {
            conn = (HttpURLConnection) new URL(url).openConnection();
            String auth = new BASE64Encoder().encode((stripeSecretKey + ":").getBytes());
            conn.setRequestProperty("Authorization", "Basic " + auth);
            conn.setDoInput(true);
            conn.setDoOutput(true);
            DataOutputStream dout = new DataOutputStream(conn.getOutputStream());

            dout.write(payload.getBytes());
            dout.flush();

            DataInputStream din = new DataInputStream(conn.getInputStream());
            return IOUtil.readFully(din, 4096);
        } finally {
            if (conn != null)
                conn.disconnect();
        }
    }
}
