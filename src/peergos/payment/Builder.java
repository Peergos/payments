package peergos.payment;

import com.zaxxer.hikari.*;
import org.sqlite.SQLiteDataSource;
import peergos.payment.util.Args;
import peergos.payment.util.Natural;
import peergos.server.util.*;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.function.*;
import java.util.stream.Collectors;

public class Builder {

    protected static final long GIGABYTE = 1024*1024*1024L;
    protected static final long MEGABYTE = 1024*1024L;

    public static Supplier<Connection> buildEphemeralSqlite() {
        try {
            Connection memory = Sqlite.build(":memory:");
            // We need a connection that ignores close
            Connection instance = new Sqlite.UncloseableConnection(memory);
            return () -> instance;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static Supplier<Connection> getDBConnector(Args a, String dbName) {
        boolean usePostgres = a.getBoolean("use-postgres", false);
        HikariConfig config;
        if (usePostgres) {
            String postgresHost = a.getArg("postgres.host");
            int postgresPort = a.getInt("postgres.port", 5432);
            String databaseName = a.getArg("postgres.database", "peergos");
            String postgresUsername = a.getArg("postgres.username");
            String postgresPassword = a.getArg("postgres.password");

            Properties props = new Properties();
            props.setProperty("dataSourceClassName", "org.postgresql.ds.PGSimpleDataSource");
            props.setProperty("dataSource.serverName", postgresHost);
            props.setProperty("dataSource.portNumber", "" + postgresPort);
            props.setProperty("dataSource.user", postgresUsername);
            props.setProperty("dataSource.password", postgresPassword);
            props.setProperty("dataSource.databaseName", databaseName);
            config = new HikariConfig(props);
            HikariDataSource ds = new HikariDataSource(config);

            return () -> {
                try {
                    return ds.getConnection();
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            };
        } else {
            String sqlFilePath = a.getArg(dbName);
            if (":memory:".equals(sqlFilePath))
                return buildEphemeralSqlite();
            try {
                Connection memory = Sqlite.build(sqlFilePath);
                // We need a connection that ignores close
                Connection instance = new Sqlite.UncloseableConnection(memory);
                return () -> instance;
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static long parseQuota(String in) {
        in = in.trim();
        if (in.endsWith("g"))
            return Long.parseLong(in.substring(0, in.length() - 1)) * Builder.GIGABYTE;
        if (in.endsWith("m"))
            return Long.parseLong(in.substring(0, in.length() - 1)) * Builder.MEGABYTE;
        return Long.parseLong(in);
    }

    public static List<Natural> parseQuotas(Args a) {
        return Arrays.stream(a.getArg("allowed-quotas", "0,1m,50g").split(","))
                .map(Builder::parseQuota)
                .map(Natural::of)
                .collect(Collectors.toList());
    }

    protected static Pricer buildPricer(Args a) {
        boolean fixedPrices = a.hasArg("quota-prices");
        if (!fixedPrices && a.hasArg("allowed-quotas"))
            throw new IllegalStateException("Allowed quotas are listed, but not prices! (quota-prices arg)");
        if (! fixedPrices)
            return new LinearPricer(new Natural(1024 * 1024 * 1024L / 50));

        List<Natural> allowedQuotas = parseQuotas(a);

        List<Natural> prices = Arrays.stream(a.getArg("quota-prices", "0,500,5000").split(","))
                .map(Long::parseLong)
                .map(Natural::new)
                .collect(Collectors.toList());

        if (prices.size() != allowedQuotas.size())
            throw new IllegalStateException("Number of prices != number of quotas!");
        Map<Natural, Natural> bytesToPrice = new HashMap<>();
        for (int i=0; i < prices.size(); i++)
            bytesToPrice.put(allowedQuotas.get(i), prices.get(i));
        return new FixedPricer(bytesToPrice);
    }

    public static Connection buildSql(String dbPath) {
        String url = "jdbc:sqlite:" + dbPath;
        SQLiteDataSource dc = new SQLiteDataSource();
        dc.setUrl(url);
        try {
            Connection conn = dc.getConnection();
            conn.setAutoCommit(true);
            return conn;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
