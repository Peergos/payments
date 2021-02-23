package peergos.payment;

import peergos.payment.util.*;

import java.io.*;
import java.net.*;
import java.time.*;
import java.util.*;
import java.util.logging.*;
import java.util.stream.*;

public class StripeProcessor implements Bank {
    private static final Logger LOG = Logger.getLogger("NULL_FORMAT");

    private final String stripeSecretToken;

    public StripeProcessor(String stripeSecretToken) {
        this.stripeSecretToken = stripeSecretToken;
    }

    @Override
    public IntentResult setupIntent(CustomerResult cus) {
        Map<String, Object> res = (Map) JSONParser.parse(setupIntent(stripeSecretToken, cus));
        // TODO parse more fields from res
        String id = (String) res.get("id");
        String clientSecret = (String) res.get("client_secret");
        return new IntentResult(id, clientSecret, LocalDateTime.now());
    }

    @Override
    public CustomerResult createCustomer(String username) {
        String rawJson = createCustomer(stripeSecretToken, username);
        Map<String, Object> res = (Map) JSONParser.parse(rawJson);
        // TODO parse more fields from res
        String id = (String) res.get("id");
        return new CustomerResult(id);
    }

    @Override
    public PaymentResult takePayment(CustomerResult cus,
                                     Natural cents,
                                     String currency,
                                     LocalDateTime now,
                                     Natural forQuota) {
        List<PaymentMethod> paymentMethods = listPaymentMethods(cus, stripeSecretToken);
        if (paymentMethods.isEmpty())
            throw new IllegalStateException("No card registered for user!");
        Collections.sort(paymentMethods, (a, b) -> (int) (b.created - a.created));
        // use the payment method most recently created
        PaymentMethod card = paymentMethods.get(0);
        try {
            // update customer email
            updateCustomer(stripeSecretToken, cus.id, card.email);

            // take payment
            Map<String, Object> res = (Map) JSONParser.parse(takePayment(cents, currency, cus, card, stripeSecretToken, forQuota));
            boolean success = "succeeded".equals(res.get("status"));
            Optional<String> errMessage = Optional.ofNullable(res.get("failure_message")).map(x -> (String) x);
            // TODO parse more fields from res
            return new PaymentResult(cents, currency, now, errMessage);
        } catch (Exception e) {
            e.printStackTrace();
            return new PaymentResult(cents, currency, now, Optional.of(e.getMessage()));
        }
    }

    public static String createCustomer(String stripeSecretKey, String username) {
        try {
            Map<String, String> params = new HashMap<>();
            params.put("metadata[username]", username);
            String res = post("https://api.stripe.com/v1/customers", stripeSecretKey, params);
            System.out.println("Created customer: " + res);
            return res;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public static String updateCustomer(String stripeSecretKey, String customerId, String email) {
        try {
            Map<String, String> params = new HashMap<>();
            params.put("email", email);
            String res = post("https://api.stripe.com/v1/customers/" + customerId, stripeSecretKey, params);
            System.out.println("Created customer: " + res);
            return res;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public static String setupIntent(String stripeSecretKey, CustomerResult cus) {
        try {
            Map<String, String> params = new HashMap<>();
            params.put("customer", cus.id);
            String res = post("https://api.stripe.com/v1/setup_intents", stripeSecretKey, params);
            System.out.println("Created intent: " + res);
            return res;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public static List<PaymentMethod> listPaymentMethods(CustomerResult cus, String stripeSecretKey) {
        try {
            Map<String, String> params = new HashMap<>();
            params.put("customer", cus.id);
            params.put("type", "card");
            String res = get("https://api.stripe.com/v1/payment_methods", stripeSecretKey, params);
            System.out.println("Retrieved payment methods: " + res);
            Map<String, Object> json = (Map) JSONParser.parse(res);
            List<Object> methods = (List)json.get("data");
            return methods.stream()
                    .map(j -> new PaymentMethod(
                            (String)((Map)j).get("id"),
                            (String)((Map)((Map)j).get("billing_details")).get("email"),
                            (Integer)((Map)j).get("created")))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public static String takePayment(Natural cents,
                                     String currency,
                                     CustomerResult cus,
                                     PaymentMethod method,
                                     String stripeSecretKey,
                                     Natural forQuota) throws IOException {
        Map<String, String> params = new HashMap<>();
        params.put("amount", Long.toString(cents.val));
        params.put("currency", currency);
        params.put("customer", cus.id);
        params.put("payment_method", method.id);
        params.put("off_session", "true");
        params.put("confirm", "true");
        params.put("metadata[desired_quota]", Long.toString(forQuota.val));
        String res = post("https://api.stripe.com/v1/payment_intents", stripeSecretKey, params);
        System.out.println("Took payment: " + res);
        return res;
    }

    public static String post(String url, String stripeSecretKey, Map<String, String> parameters) throws IOException {
        String body = parameters.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .reduce((a, b) -> a + "&" + b)
                .orElse("");
        return new String(post(url, stripeSecretKey, body));
    }

    public static byte[] post(String url, String stripeSecretKey, String payload) throws IOException {
        HttpURLConnection conn = null;
        try
        {
            conn = (HttpURLConnection) new URL(url).openConnection();
            String auth = Base64.getEncoder().encodeToString((stripeSecretKey + ":").getBytes());
            conn.setRequestProperty("Authorization", "Basic " + auth);
            conn.setDoInput(true);
            conn.setDoOutput(true);
            DataOutputStream dout = new DataOutputStream(conn.getOutputStream());

            dout.write(payload.getBytes());
            dout.flush();

            DataInputStream din = new DataInputStream(conn.getInputStream());
            return IOUtil.readFully(din, 10*1024);
        } catch (IOException e){
            DataInputStream din = new DataInputStream(conn.getErrorStream());
            String body = new String(IOUtil.readFully(din, 20*1024));
            if (conn != null)
                conn.disconnect();
            Map resp = (Map) JSONParser.parse(body);
            Map err = (Map) resp.get("error");
            String code = (String) err.get("code");
            String message = (String) err.get("message");
            LOG.log(Level.SEVERE, code + ": " + message);
            throw new IllegalStateException(message, e);
        } finally {
            if (conn != null)
                conn.disconnect();
        }
    }

    public static String get(String url, String stripeSecretKey, Map<String, String> parameters) throws IOException {
        HttpURLConnection conn = null;
        try
        {
            String query = parameters.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .reduce((a, b) -> a + "&" + b)
                .orElse("");
            conn = (HttpURLConnection) new URL(url + "?" + query).openConnection();
            String auth = Base64.getEncoder().encodeToString((stripeSecretKey + ":").getBytes());
            conn.setRequestProperty("Authorization", "Basic " + auth);
            conn.setDoInput(true);

            DataInputStream din = new DataInputStream(conn.getInputStream());
            return new String(IOUtil.readFully(din, 10*1024));
        } catch (IOException e){
            DataInputStream din = new DataInputStream(conn.getErrorStream());
            String body = new String(IOUtil.readFully(din, 4096));
            if (conn != null)
                conn.disconnect();
            Map resp = (Map) JSONParser.parse(body);
            Map err = (Map) resp.get("error");
            String code = (String) err.get("code");
            String message = (String) err.get("message");
            LOG.log(Level.SEVERE, code + ": " + message);
            throw new IllegalStateException(message, e);
        } finally {
            if (conn != null)
                conn.disconnect();
        }
    }
}
