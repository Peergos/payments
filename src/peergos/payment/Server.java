package peergos.payment;

import com.sun.net.httpserver.*;
import org.sqlite.*;
import peergos.payment.http.*;
import peergos.payment.http.FileHandler;
import peergos.payment.util.*;
import peergos.payment.util.Args;
import peergos.server.*;
import peergos.server.storage.admin.*;
import peergos.shared.corenode.*;
import peergos.shared.storage.*;
import peergos.shared.user.*;
import peergos.shared.util.Triple;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.sql.*;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;
import java.util.stream.*;

public class Server {
    private static final Logger LOG = Logger.getLogger("NULL_FORMAT");
    public static final String UI_URL = "/";

    public static final int HANDLER_THREADS = 50;
    public static final int CONNECTION_BACKLOG = 100;

    private final PaymentState state;
    private final ContentAddressedStorage dht;
    private final CoreNode core;

    public Server(PaymentState state, ContentAddressedStorage dht, CoreNode core) {
        this.state = state;
        this.dht = dht;
        this.core = core;
    }

    public void initAndStart(String publicUrl,
                             InetSocketAddress publicApi,
                             InetSocketAddress privateApi,
                             Optional<Path> webroot,
                             String peergosUrl,
                             boolean useWebCache) throws IOException {
        LOG.info("Starting Payment server private api at: " + privateApi);
        HttpServer privateServer = HttpServer.create(privateApi, CONNECTION_BACKLOG);
        privateServer.createContext("/" + HttpQuotaAdmin.QUOTA_URL, new QuotaHandler(state, dht, core, publicUrl));
        privateServer.setExecutor(Executors.newFixedThreadPool(HANDLER_THREADS));
        privateServer.start();

        LOG.info("Starting Payment server public api at: " + publicApi);
        HttpServer publicServer = HttpServer.create(publicApi, CONNECTION_BACKLOG);

        //define web-root static-handler
        if (webroot.isPresent())
            LOG.info("Using webroot from local file system: " + webroot);
        else
            LOG.info("Using webroot from jar");
        StaticHandler handler = webroot.map(p -> (StaticHandler) new FileHandler(p, true, peergosUrl))
                .orElseGet(() -> new JarHandler(true, Paths.get("webroot"), peergosUrl));

        if (useWebCache) {
            LOG.info("Caching web-resources");
            handler = handler.withCache();
        }

        publicServer.createContext(UI_URL, handler);

        publicServer.setExecutor(Executors.newFixedThreadPool(HANDLER_THREADS));
        publicServer.start();
    }

    private static InetSocketAddress parseAddress(String addr) {
        if (addr.startsWith("http://"))
            addr = addr.substring(7);
        if (addr.startsWith("https://"))
            addr = addr.substring(8);
        if (addr.contains("/"))
            addr = addr.substring(0, addr.indexOf("/"));
        int split = addr.indexOf(":");
        return new InetSocketAddress(addr.substring(0, split), Integer.parseInt(addr.substring(split + 1)));
    }

    private static void startPeriodicPaymentProcessor(PaymentState state, LocalTime atTime) {

        Runnable periodicPaymentTask = () -> {
            while (true) {
                try {
                    LOG.info("Starting Periodic payment run. User count: " + state.userCount());
                    Triple<Integer, Integer, Integer> stats = state.processAll(LocalDateTime.now());
                    LOG.info("Completed Periodic payment run. " + " success count: " + stats.left +
                            " failure count: " + stats.middle + " exception count: " + stats.right);
                } catch (Throwable t) {
                    LOG.log(Level.SEVERE, "Unexpected Exception occurred", t);
                }
                try {
                    LocalDateTime now = LocalDateTime.now();
                    Duration nextInvocation = Duration.between(now, LocalDateTime.of(now.toLocalDate().plusDays(1), atTime));
                    Thread.sleep(nextInvocation.toMillis());
                } catch (InterruptedException ie) { }
            }
        };
        Duration duration = Duration.between(LocalTime.now(), atTime);
        if (duration.isNegative()) {
            duration = Duration.between(LocalDateTime.now(), LocalDateTime.of(LocalDate.now().plusDays(1), atTime));
        }
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException ie) { }

        Thread process = new Thread(periodicPaymentTask);
        process.start();
    }

    public static void main(String[] args) throws Exception {
        Main.initCrypto();
        Args a = Args.parse(args);

        String stripe_secret_key = a.getArg("stripe-secret");
        String stripe_public_key = a.getArg("stripe-public");
        Bank bank = new StripeProcessor(stripe_secret_key);
        Natural minPayment = new Natural(a.getLong("min-payment", 500));
        Natural defaultFreeQuota = new Natural(a.getLong("free-quota", 100 * 1024*1024L));
        int maxUsers = a.getInt("max-users");
        Set<Long> allowedQuotas = Arrays.stream(a.getArg("allowed-quotas", "0,10,100").split(","))
                .map(Long::parseLong)
                .map(g -> g * Builder.GIGABYTE)
                .collect(Collectors.toSet());

        Connection sqlConn = Builder.buildSql(a.getArg("payment-store-sql-file", "payments-store.sql"));
        PaymentStore store = new SqlPaymentStore(sqlConn);
        Pricer pricer = Builder.buildPricer(a);
        PaymentState state = new PaymentState(store, pricer, minPayment, bank, defaultFreeQuota, maxUsers, allowedQuotas);

        JavaPoster poster = new JavaPoster(new URL("http://" + a.getArg("peergos-address")));
        ContentAddressedStorage.HTTP dht = new ContentAddressedStorage.HTTP(poster, true);
        HTTPCoreNode core = new HTTPCoreNode(poster);
        Server daemon = new Server(state, dht, core);

        String publicUrl = a.getArg("public-api-address", "http://localhost:7000") + "/addcard.html?stripe_public=" + stripe_public_key;
        InetSocketAddress publicListener = parseAddress(a.getArg("public-listener-address", publicUrl));
        InetSocketAddress privateApi = parseAddress(a.getArg("private-api-address", "http://localhost:6000"));
        Optional<Path> webroot = a.hasArg("webroot") ?
                Optional.of(Paths.get(a.getArg("webroot"))) :
                Optional.empty();
        boolean useWebAssetCache = a.getBoolean("webcache", true);
        String publicPeergosUrl = a.getArg("public-peergos-url", "http://localhost:8000");

        daemon.initAndStart(publicUrl, publicListener, privateApi, webroot, publicPeergosUrl, useWebAssetCache);

        String dailyPaymentScheduledTime = a.getArg("daily-payment-scheduled-time", "14:00");
        startPeriodicPaymentProcessor(state, DateUtil.toTime(dailyPaymentScheduledTime));
    }
}