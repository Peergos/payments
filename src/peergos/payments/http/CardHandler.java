package peergos.payments.http;

import com.sun.net.httpserver.*;

import java.io.*;
import java.util.*;
import java.util.logging.*;

public class CardHandler implements HttpHandler {

    private static final Logger LOG = Logger.getLogger("NULL_FORMAT");
    private static final int MAX_PAYLOAD_SIZE = 4096;

    public CardHandler() {}

    @Override
    public void handle(HttpExchange exchange) {
        try {
            byte[] bodyBytes = readFully(exchange.getRequestBody(), MAX_PAYLOAD_SIZE);
            String body = new String(bodyBytes);

            String[] parts = body.split("&");
            Map<String, String> params = new HashMap<>();
            for (String part : parts) {
                String[] mapping = part.split("=");
                String value = mapping.length > 1 ? mapping[1] : "";
                params.put(mapping[0], value);
            }
            System.out.println(params);

            // TODO store in database

            byte[] resp = "<html><body><h1>Card accepted</h1></body></html>".getBytes();
            exchange.sendResponseHeaders(200, resp.length);
            exchange.getResponseBody().write(resp);
            exchange.close();
        } catch (Exception e) {
            LOG.log(Level.SEVERE, e, e::getMessage);
            throw new RuntimeException(e);
        }
    }

    public static byte[] readFully(InputStream in, int maxSize) throws IOException {
        ByteArrayOutputStream bout =  new ByteArrayOutputStream();
        byte[] b =  new  byte[0x1000];
        int nRead;
        while ((nRead = in.read(b, 0, b.length)) != -1 ) {
            bout.write(b, 0, nRead);
            if (bout.size() > maxSize)
                throw new IllegalStateException("Too much data to read!");
        }
        in.close();
        return bout.toByteArray();
    }


}
