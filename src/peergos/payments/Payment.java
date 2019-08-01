package peergos.payments;

import sun.misc.*;

import java.io.*;
import java.net.*;

public class Payment {

    public static void takePayment(String stripeToken, String stripeSecretKey) {
        try {
            String payload = "amount=500&currency=gbp&source=" + stripeToken;
            byte[] result = post("https://api.stripe.com/v1/charges", stripeSecretKey, payload);
            System.out.println("Took payment: " + new String(result));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static byte[] post(String url, String stripeSecretKey, String payload) throws IOException  {
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
