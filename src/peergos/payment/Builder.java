package peergos.payment;

import org.sqlite.SQLiteDataSource;
import peergos.payment.util.Args;
import peergos.payment.util.Natural;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Builder {

    protected static final long GIGABYTE = 1024*1024*1024L;

    protected static Pricer buildPricer(Args a) {
        boolean fixedPrices = a.hasArg("quota-prices");
        if (! fixedPrices)
            return new LinearPricer(new Natural(1024 * 1024 * 1024L / 50));

        List<Natural> allowedQuotas = Arrays.stream(a.getArg("allowed-quotas", "0,10,100").split(","))
                .map(Long::parseLong)
                .map(g -> g * GIGABYTE)
                .map(Natural::new)
                .collect(Collectors.toList());

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
