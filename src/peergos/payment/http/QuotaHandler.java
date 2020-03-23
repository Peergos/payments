package peergos.payment.http;

import com.sun.net.httpserver.*;
import peergos.payment.*;
import peergos.payment.util.*;
import peergos.server.storage.admin.*;
import peergos.server.util.*;
import peergos.shared.cbor.*;
import peergos.shared.corenode.*;
import peergos.shared.crypto.hash.*;
import peergos.shared.storage.*;
import peergos.shared.util.*;

import java.io.*;
import java.time.*;
import java.util.*;
import java.util.function.*;
import java.util.logging.*;
import java.util.stream.*;

public class QuotaHandler  implements HttpHandler {
    private static final Logger LOG = Logging.LOG();

    private final PaymentState state;
    private final ContentAddressedStorage dht;
    private final CoreNode core;
    private final String ourUrl;

    public QuotaHandler(PaymentState state, ContentAddressedStorage dht, CoreNode core, String ourUrl) {
        this.state = state;
        this.dht = dht;
        this.core = core;
        this.ourUrl = ourUrl;
    }

    @Override
    public void handle(HttpExchange exchange) {
        long t1 = System.currentTimeMillis();
        String path = exchange.getRequestURI().getPath();
        if (path.startsWith("/"))
            path = path.substring(1);
        String[] subComponents = path.substring(HttpQuotaAdmin.QUOTA_URL.length()).split("/");
        String method = subComponents[0];

        Map<String, List<String>> params = HttpUtil.parseQuery(exchange.getRequestURI().getQuery());
        Function<String, String> last = key -> params.get(key).get(params.get(key).size() - 1);


        Cborable result;
        try {
            switch (method) {
                case "signups":
                    result = new CborObject.CborBoolean(state.acceptingSignups());
                    break;
                case "usernames":
                    List<String> usernames = state.getAllUsernames();
                    result = new CborObject.CborList(usernames.stream()
                            .map(CborObject.CborString::new)
                            .collect(Collectors.toList()));
                    break;
                case "allowed": {
                    String username = last.apply("username");
                    result = new CborObject.CborBoolean(state.hasUser(username) || state.acceptingSignups());
                    break;
                }
                case "quota-by-name": {
                    String username = last.apply("username");
                    long quota = state.getCurrentQuota(username);
                    result = new CborObject.CborLong(quota);
                    break;
                }
                case "payment-properties": {
                    PublicKeyHash owner = PublicKeyHash.fromString(params.get("owner").get(0));
                    byte[] signedTime = ArrayOps.hexToBytes(last.apply("auth"));
                    TimeLimited.isAllowedTime(signedTime, 120, dht, owner);
                    String username = core.getUsername(owner).join();
                    String clientSecret = state.getClientSecret(username);
                    result = new PaymentProperties(ourUrl, clientSecret).toCbor();
                    break;
                }
                case "quota": {
                    PublicKeyHash owner = PublicKeyHash.fromString(params.get("owner").get(0));
                    byte[] signedTime = ArrayOps.hexToBytes(last.apply("auth"));
                    String username = core.getUsername(owner).join();
                    TimeLimited.isAllowedTime(signedTime, 120, dht, owner);
                    long quota = state.getCurrentQuota(username);
                    result = new CborObject.CborLong(quota);
                    break;
                }
                case "request": {
                    PublicKeyHash owner = PublicKeyHash.fromString(params.get("owner").get(0));
                    byte[] signedReq = ArrayOps.hexToBytes(last.apply("req"));
                    SpaceUsage.SpaceRequest req = QuotaAdmin.parseQuotaRequest(owner, signedReq, dht);
                    System.out.println("setting desired quota to " + req.getSizeInBytes());
                    state.setDesiredQuota(req.username, new Natural(req.getSizeInBytes()), LocalDateTime.now());
                    result = new CborObject.CborBoolean(true);
                    break;
                }
                default:
                    throw new IOException("Unknown method in StorageHandler!");
            }

            byte[] b = result.serialize();
            exchange.sendResponseHeaders(200, b.length);
            exchange.getResponseBody().write(b);
        } catch (Exception e) {
            HttpUtil.replyError(exchange, e);
        } finally {
            exchange.close();
            long t2 = System.currentTimeMillis();
            LOG.info("Quota admin handled " + method + " request in: " + (t2 - t1) + " mS");
        }
    }
}
