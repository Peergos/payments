package peergos.payment;

import peergos.payment.util.*;

import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.function.*;

public class LoadPaymentStore {

    public static void main(String[] args) throws IOException {
        Args a = Args.parse(args);
        if (a.hasArg("file")) {
            Supplier<Connection> database = Builder.getDBConnector(a, "payment-store-sql-file");
            PaymentStore store = new SqlPaymentStore(database, a.getBoolean("use-postgres", false));
            List<String> lines = Files.readAllLines(Paths.get(a.getArg("file")));
            for (String line : lines) {
                String[] parts = line.trim().split(" ");
                store.setFreeQuota(parts[0], Natural.of(Long.parseLong(parts[1])));
            }
        } else if (a.hasArg("user")) {
            Supplier<Connection> database = Builder.getDBConnector(a, "payment-store-sql-file");
            PaymentStore store = new SqlPaymentStore(database, a.getBoolean("use-postgres", false));
            store.setFreeQuota(a.getArg("user"), Natural.of(a.getLong("free-quota")));
        } else {
            System.out.println("Usage: \n" +
                    "java peergos.payment.LoadPaymentStore -file $file");
            System.out.println("   or");
            System.out.println("java peergos.payment.LoadPaymentStore -user $username -free-quota $freequota");
        }
    }
}
