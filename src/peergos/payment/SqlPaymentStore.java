package peergos.payment;

import peergos.payment.util.*;
import peergos.server.util.Logging;

import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.function.*;
import java.util.logging.*;

public class SqlPaymentStore implements PaymentStore {

    private static final Logger LOG = Logging.LOG();
    private Supplier<Connection> conn;
    private final boolean isPostgres;

    public SqlPaymentStore(Supplier<Connection> conn, boolean isPostgres) {
        this.conn = conn;
        this.isPostgres = isPostgres;
        init();
    }

    private String sqlInteger() {
        return isPostgres ? "BIGINT" : "INTEGER";
    }

    private String createTableStatement() {
        return "CREATE TABLE IF NOT EXISTS users " +
                "(name VARCHAR(32) PRIMARY KEY NOT NULL, " +
                "customerid text, " +
                "free "+sqlInteger()+" NOT NULL CHECK (free >= 0), " +
                "desired "+sqlInteger()+" NOT NULL CHECK (desired >= 0), " +
                "quota "+sqlInteger()+" NOT NULL CHECK (quota >= 0), " +
                "expiry " + (isPostgres ? "TIMESTAMP" : "DATETIME") + " NOT NULL, " +
                "error TEXT, " +
                "balance INTEGER NOT NULL CHECK (balance >= 0));";
    }

    private synchronized void init() {
        try {
            createTable(createTableStatement(), conn.get());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Connection getConnection() {
        Connection connection = conn.get();
        try {
            connection.setAutoCommit(true);
            connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
            return connection;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    void createTable(String sqlTableCreate, Connection conn) throws SQLException {
        Statement createStmt = conn.createStatement();
        createStmt.executeUpdate(sqlTableCreate);
        createStmt.close();
    }

    @Override
    public long userCount() {
        try (Connection conn = getConnection();
             PreparedStatement count = conn.prepareStatement("SELECT COUNT(*) FROM users;")) {
            ResultSet resultSet = count.executeQuery();
            if (! resultSet.next())
                return 0;
            return resultSet.getLong(1);
        } catch (SQLException sqe) {
            LOG.log(Level.WARNING, sqe.getMessage(), sqe);
            throw new RuntimeException(sqe);
        }
    }

    @Override
    public boolean hasUser(String username) {
        try (Connection conn = getConnection();
             PreparedStatement count = conn.prepareStatement("SELECT COUNT(*) FROM users where name = ?;")) {
            count.setString(1, username);
            ResultSet resultSet = count.executeQuery();
            if (! resultSet.next())
                return false;
            return resultSet.getLong(1) == 1;
        } catch (SQLException sqe) {
            LOG.log(Level.WARNING, sqe.getMessage(), sqe);
            throw new RuntimeException(sqe);
        }
    }

    @Override
    public List<String> getAllUsernames() {
        try (Connection conn = getConnection();
             PreparedStatement select = conn.prepareStatement("SELECT name FROM users;")) {
            ResultSet rs = select.executeQuery();
            List<String> results = new ArrayList<>();
            while (rs.next()) {
                String username = rs.getString("name");
                results.add(username);
            }
            return results;
        } catch (SQLException sqe) {
            LOG.log(Level.WARNING, sqe.getMessage(), sqe);
            return null;
        }
    }

    @Override
    public void ensureUser(String username, Natural freeSpace, LocalDateTime now) {
        if (hasUser(username))
            return;
        try (Connection conn = getConnection();
             PreparedStatement insert = conn.prepareStatement("INSERT INTO users (name, free, desired, quota, expiry, balance) VALUES(?, ?, ?, ?, ?, ?);")) {
            insert.setString(1, username);
            insert.setLong(2, freeSpace.val);
            insert.setLong(3, 0);
            insert.setLong(4, 0);
            insert.setTimestamp(5, Timestamp.valueOf(now));
            insert.setLong(6, 0);
            insert.execute();
        } catch (SQLException sqe) {
            LOG.log(Level.WARNING, sqe.getMessage(), sqe);
            throw new RuntimeException(sqe);
        }
    }

    @Override
    public void setCustomer(String username, CustomerResult customer) {
        try (Connection conn = getConnection();
             PreparedStatement insert = conn.prepareStatement("UPDATE users SET customerid = ? WHERE name = ?;")) {
            insert.setString(1, customer.id);
            insert.setString(2, username);
            insert.execute();
        } catch (SQLException sqe) {
            LOG.log(Level.WARNING, sqe.getMessage(), sqe);
            throw new RuntimeException(sqe);
        }
    }

    @Override
    public CustomerResult getCustomer(String username) {
        try (Connection conn = getConnection();
             PreparedStatement count = conn.prepareStatement("SELECT customerid FROM users where name = ?;")) {
            count.setString(1, username);
            ResultSet resultSet = count.executeQuery();
            if (! resultSet.next())
                throw new IllegalStateException("No such user: " + username);
            String id = resultSet.getString(1);
            if (id == null)
                return null;
            return new CustomerResult(id);
        } catch (SQLException sqe) {
            LOG.log(Level.WARNING, sqe.getMessage(), sqe);
            throw new RuntimeException(sqe);
        }
    }

    @Override
    public void setDesiredQuota(String username, Natural quota, LocalDateTime now) {
        try (Connection conn = getConnection();
             PreparedStatement insert = conn.prepareStatement("UPDATE users SET desired = ? WHERE name = ?;")) {
            insert.setLong(1, quota.val);
            insert.setString(2, username);
            insert.execute();
        } catch (SQLException sqe) {
            LOG.log(Level.WARNING, sqe.getMessage(), sqe);
            throw new RuntimeException(sqe);
        }
    }

    @Override
    public Natural getDesiredQuota(String username) {
        try (Connection conn = getConnection();
             PreparedStatement count = conn.prepareStatement("SELECT desired FROM users where name = ?;")) {
            count.setString(1, username);
            ResultSet resultSet = count.executeQuery();
            if (! resultSet.next())
                throw new IllegalStateException("No such user: " + username);
            return new Natural(resultSet.getLong(1));
        } catch (SQLException sqe) {
            LOG.log(Level.WARNING, sqe.getMessage(), sqe);
            throw new RuntimeException(sqe);
        }
    }

    @Override
    public void setCurrentBalance(String username, Natural balance) {
        try (Connection conn = getConnection();
             PreparedStatement insert = conn.prepareStatement("UPDATE users SET balance = ? WHERE name = ?;")) {
            insert.setLong(1, balance.val);
            insert.setString(2, username);
            insert.execute();
        } catch (SQLException sqe) {
            LOG.log(Level.WARNING, sqe.getMessage(), sqe);
            throw new RuntimeException(sqe);
        }
    }

    @Override
    public Natural getCurrentBalance(String username) {
        try (Connection conn = getConnection();
             PreparedStatement count = conn.prepareStatement("SELECT balance FROM users where name = ?;")) {
            count.setString(1, username);
            ResultSet resultSet = count.executeQuery();
            if (! resultSet.next())
                throw new IllegalStateException("No such user: " + username);
            return new Natural(resultSet.getLong(1));
        } catch (SQLException sqe) {
            LOG.log(Level.WARNING, sqe.getMessage(), sqe);
            throw new RuntimeException(sqe);
        }
    }

    @Override
    public void setCurrentQuota(String username, Natural quota) {
        try (Connection conn = getConnection();
             PreparedStatement insert = conn.prepareStatement("UPDATE users SET quota = ? WHERE name = ?;")) {
            insert.setLong(1, quota.val);
            insert.setString(2, username);
            insert.execute();
        } catch (SQLException sqe) {
            LOG.log(Level.WARNING, sqe.getMessage(), sqe);
            throw new RuntimeException(sqe);
        }
    }

    @Override
    public Natural getCurrentQuota(String username) {
        try (Connection conn = getConnection();
             PreparedStatement count = conn.prepareStatement("SELECT quota FROM users where name = ?;")) {
            count.setString(1, username);
            ResultSet resultSet = count.executeQuery();
            if (! resultSet.next())
                return Natural.ZERO;
            return new Natural(resultSet.getLong(1));
        } catch (SQLException sqe) {
            LOG.log(Level.WARNING, sqe.getMessage(), sqe);
            throw new RuntimeException(sqe);
        }
    }

    @Override
    public void setFreeQuota(String username, Natural quota) {
        try (Connection conn = getConnection();
             PreparedStatement insert = conn.prepareStatement("UPDATE users SET free = ? WHERE name = ?;")) {
            insert.setLong(1, quota.val);
            insert.setString(2, username);
            insert.execute();
        } catch (SQLException sqe) {
            LOG.log(Level.WARNING, sqe.getMessage(), sqe);
            throw new RuntimeException(sqe);
        }
    }

    @Override
    public Natural getFreeQuota(String username) {
        try (Connection conn = getConnection();
             PreparedStatement count = conn.prepareStatement("SELECT free FROM users where name = ?;")) {
            count.setString(1, username);
            ResultSet resultSet = count.executeQuery();
            if (! resultSet.next())
                return Natural.ZERO;
            return new Natural(resultSet.getLong(1));
        } catch (SQLException sqe) {
            LOG.log(Level.WARNING, sqe.getMessage(), sqe);
            throw new RuntimeException(sqe);
        }
    }

    @Override
    public void setQuotaExpiry(String username, LocalDateTime expiry) {
        try (Connection conn = getConnection();
             PreparedStatement insert = conn.prepareStatement("UPDATE users SET expiry = ? WHERE name = ?;")) {
            insert.setTimestamp(1, Timestamp.from(expiry.toInstant(ZoneOffset.UTC)));
            insert.setString(2, username);
            insert.execute();
        } catch (SQLException sqe) {
            LOG.log(Level.WARNING, sqe.getMessage(), sqe);
            throw new RuntimeException(sqe);
        }
    }

    @Override
    public LocalDateTime getQuotaExpiry(String username) {
        try (Connection conn = getConnection();
             PreparedStatement count = conn.prepareStatement("SELECT expiry FROM users where name = ?;")) {
            count.setString(1, username);
            ResultSet resultSet = count.executeQuery();
            if (! resultSet.next())
                throw new IllegalStateException("No such user: " + username);
            Timestamp timestamp = resultSet.getTimestamp(1);
            return timestamp.toInstant().atZone(ZoneId.of("UTC")).toLocalDateTime();
        } catch (SQLException sqe) {
            LOG.log(Level.WARNING, sqe.getMessage(), sqe);
            throw new RuntimeException(sqe);
        }
    }

    @Override
    public void setError(String username, String error) {
        try (Connection conn = getConnection();
             PreparedStatement insert = conn.prepareStatement("UPDATE users SET error = ? WHERE name = ?;")) {
            insert.setString(1, error);
            insert.setString(2, username);
            insert.execute();
        } catch (SQLException sqe) {
            LOG.log(Level.WARNING, sqe.getMessage(), sqe);
            throw new RuntimeException(sqe);
        }
    }

    @Override
    public Optional<String> getError(String username) {
        try (Connection conn = getConnection();
             PreparedStatement count = conn.prepareStatement("SELECT error FROM users where name = ?;")) {
            count.setString(1, username);
            ResultSet resultSet = count.executeQuery();
            if (! resultSet.next())
                throw new IllegalStateException("No such user: " + username);
            return Optional.ofNullable(resultSet.getString(1));
        } catch (SQLException sqe) {
            LOG.log(Level.WARNING, sqe.getMessage(), sqe);
            throw new RuntimeException(sqe);
        }
    }
}
