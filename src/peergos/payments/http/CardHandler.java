package peergos.payments.http;

import com.sun.net.httpserver.*;
import peergos.payments.*;

import java.time.*;
import java.util.*;
import java.util.logging.*;

public class CardHandler implements HttpHandler {

    private static final Logger LOG = Logger.getLogger("NULL_FORMAT");
    private static final int MAX_PAYLOAD_SIZE = 4096;

    private final PaymentState state;

    public CardHandler(PaymentState state) {
        this.state = state;
    }

    @Override
    public void handle(HttpExchange exchange) {
        try {
            byte[] bodyBytes = IOUtil.readFully(exchange.getRequestBody(), MAX_PAYLOAD_SIZE);
            String body = new String(bodyBytes);

            String[] parts = body.split("&");
            Map<String, String> params = new HashMap<>();
            for (String part : parts) {
                String[] mapping = part.split("=");
                String value = mapping.length > 1 ? mapping[1] : "";
                params.put(mapping[0], value);
            }
            System.out.println(params);

            state.addCard(params.get("username"), new CardToken(params.get("stripe_token")), LocalDateTime.now());
            // TODO remove this test payment


            byte[] resp = "<html><body><h1>Card accepted</h1></body></html>".getBytes();
            exchange.sendResponseHeaders(200, resp.length);
            exchange.getResponseBody().write(resp);
            exchange.close();
        } catch (Exception e) {
            LOG.log(Level.SEVERE, e, e::getMessage);
            throw new RuntimeException(e);
        }
    }
}
