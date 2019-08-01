package peergos.payments.http;

import com.sun.net.httpserver.*;

import java.io.*;

public class CardHandler implements HttpHandler {

    private static final int MAX_PAYLOAD_SIZE = 4096;

    public CardHandler() {}

    @Override
    public void handle(HttpExchange exchange) throws IOException {

        byte[] bodyBytes = readFully(exchange.getRequestBody(), MAX_PAYLOAD_SIZE);
        String body = new String(bodyBytes);
        System.out.println(body);
        byte[] resp = "<html><body><h1>Card accepted</h1></body></html>".getBytes();
        exchange.sendResponseHeaders(200, resp.length);
        exchange.getResponseBody().write(resp);
        exchange.close();
    }

    public static byte[] readFully(InputStream in, int maxSize) throws IOException {
        ByteArrayOutputStream bout =  new ByteArrayOutputStream();
        byte[] b =  new  byte[0x1000];
        int nRead;
        while ((nRead = in.read(b, 0, b.length)) != -1 )
            bout.write(b, 0, nRead);
        in.close();
        return bout.toByteArray();
    }


}
