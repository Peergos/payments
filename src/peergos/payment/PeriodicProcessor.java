package peergos.payment;

import peergos.payment.util.Args;
import peergos.payment.util.Natural;
import peergos.server.Main;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class PeriodicProcessor {

    private static final Logger LOG = Logger.getLogger("NULL_FORMAT");

    private PeriodicProcessor() {

    }

    private void process(PaymentState state) {
        LocalDateTime processTime = LocalDateTime.now();
        LOG.info("Running PeriodicProcessor for LocalDateTime: " + processTime);
        state.processAll(processTime);
        LOG.info("Finished PeriodicProcessor");
    }

    public static void main(String[] args) throws Exception {
        Main.initCrypto();
        Args a = Args.parse(args);

        String stripe_secret_key = a.getArg("stripe-secret");
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

        PeriodicProcessor processor = new PeriodicProcessor();
        processor.process(state);
    }
}
