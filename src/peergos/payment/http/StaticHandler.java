package peergos.payment.http;

import com.sun.net.httpserver.*;

import java.io.*;
import java.nio.file.Path;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;

public abstract class StaticHandler implements HttpHandler
{
    private final boolean isGzip;
    private final String peergosUrl;

    public StaticHandler(boolean isGzip, String peerogsUrl) {
        this.isGzip = isGzip;
        this.peergosUrl = peerogsUrl;
    }

    public abstract Asset getAsset(String resourcePath) throws IOException;

    public static class Asset {
        public final byte[] data;
        public final String hash;

        public Asset(byte[] data) {
            this.data = data;
            byte[] digest = sha256(data);
            this.hash = bytesToHex(Arrays.copyOfRange(digest, 0, 4));
        }
    }

    private static String bytesToHex(byte[] data)
    {
        StringBuilder s = new StringBuilder();
        for (byte b : data)
            s.append(String.format("%02x", b & 0xFF));
        return s.toString();
    }

    private static byte[] sha256(byte[] input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(input);
            return md.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    protected boolean isGzip() {
        return isGzip;
    }

    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        String path = httpExchange.getRequestURI().getPath();
        try {
            path = path.substring(1);
            path = path.replaceAll("//", "/");
            if (path.length() == 0)
                path = "index.html";

            Asset res = getAsset(path);

            if (isGzip)
                httpExchange.getResponseHeaders().set("Content-Encoding", "gzip");
            if (path.endsWith(".js"))
                httpExchange.getResponseHeaders().set("Content-Type", "text/javascript");
            else if (path.endsWith(".html"))
                httpExchange.getResponseHeaders().set("Content-Type", "text/html");
            else if (path.endsWith(".css"))
                httpExchange.getResponseHeaders().set("Content-Type", "text/css");
            else if (path.endsWith(".json"))
                httpExchange.getResponseHeaders().set("Content-Type", "application/json");
            else if (path.endsWith(".png"))
                httpExchange.getResponseHeaders().set("Content-Type", "image/png");
	    else if (path.endsWith(".woff"))
                httpExchange.getResponseHeaders().set("Content-Type", "application/font-woff");
	    
            if (httpExchange.getRequestMethod().equals("HEAD")) {
                httpExchange.getResponseHeaders().set("Content-Length", "" + res.data.length);
                httpExchange.sendResponseHeaders(200, -1);
                return;
            }
            if (res.data.length > 100 * 1024) {
                httpExchange.getResponseHeaders().set("Cache-Control", "public, max-age=3600");
                httpExchange.getResponseHeaders().set("ETag", res.hash);
            }

            // Only allow assets to be loaded from the original host
//            httpExchange.getResponseHeaders().set("content-security-policy", "default-src https: 'self'");
            // Don't anyone to load Peergos site in an iframe
            httpExchange.getResponseHeaders().set("x-frame-options", "sameorigin");
            // Let the peergos server iframe the payment page
            httpExchange.getResponseHeaders().set("Content-Security-Policy", "frame-ancestors " + peergosUrl + ";");
            // Enable cross site scripting protection
            httpExchange.getResponseHeaders().set("x-xss-protection", "1; mode=block");
            // Don't let browser sniff mime types
            httpExchange.getResponseHeaders().set("x-content-type-options", "nosniff");
            // Don't send Peergos referrer to anyone
            httpExchange.getResponseHeaders().set("referrer-policy", "no-referrer");

            httpExchange.sendResponseHeaders(200, res.data.length);
            httpExchange.getResponseBody().write(res.data);
            httpExchange.getResponseBody().close();
        } catch (NullPointerException t) {
            System.err.println("Error retrieving: " + path);
        } catch (Throwable t) {
            System.err.println("Error retrieving: " + path);
            httpExchange.sendResponseHeaders(404, 0);
            httpExchange.getResponseBody().close();
        }
    }

    public static void checkPath(Path root, Path resourcePath) {
        try {
            String rootCanonicalPath = root.toFile().getCanonicalPath();
            String resourceCanonicalPath = resourcePath.toFile().getCanonicalPath();
            if (! resourceCanonicalPath.startsWith(rootCanonicalPath)) {
                throw new IllegalStateException("Invalid resourcePath: " + resourcePath);
            }
        } catch(IOException ioe) {
            throw new IllegalStateException("Invalid resourcePath: " + resourcePath);
        }
    }

    protected static byte[] readResource(InputStream in, boolean gzip) throws IOException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        OutputStream gout = gzip ? new GZIPOutputStream(bout) : new DataOutputStream(bout);
        byte[] tmp = new byte[4096];
        int r;
        while ((r=in.read(tmp)) >= 0)
            gout.write(tmp, 0, r);
        gout.flush();
        gout.close();
        in.close();
        return bout.toByteArray();
    }

    public StaticHandler withCache() {
        Map<String, Asset> cache = new ConcurrentHashMap<>();
        StaticHandler that = this;

        return new StaticHandler(isGzip, peergosUrl) {
            @Override
            public Asset getAsset(String resourcePath) throws IOException {
                if (! cache.containsKey(resourcePath))
                    cache.put(resourcePath, that.getAsset(resourcePath));
                return cache.get(resourcePath);
            }
        };
    }
}
