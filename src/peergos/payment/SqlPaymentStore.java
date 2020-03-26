package peergos.payment;

import peergos.payment.util.*;
import peergos.server.util.Logging;

import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.logging.*;

public class SqlPaymentStore implements PaymentStore {

    private static final Logger LOG = Logging.LOG();

    private static final String CREATE_USER_TABLE = "CREATE TABLE IF NOT EXISTS users " +
            "(name varchar(32) primary key not null, customerid text, free integer not null, desired integer not null, " +
            "quota integer not null, expiry integer, balance integer not null);";

    private Connection conn;

    public SqlPaymentStore(Connection conn) {
        this.conn = conn;
        init();
    }

    private synchronized void init() {
        try {
            createTable(CREATE_USER_TABLE, conn);
        } catch (Exception e) {
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
        try (PreparedStatement count = conn.prepareStatement("SELECT COUNT(*) FROM users;")) {
            ResultSet resultSet = count.executeQuery();
            return resultSet.getLong(1);
        } catch (SQLException sqe) {
            LOG.log(Level.WARNING, sqe.getMessage(), sqe);
            throw new RuntimeException(sqe);
        }
    }

    @Override
    public boolean hasUser(String username) {
        try (PreparedStatement count = conn.prepareStatement("SELECT COUNT(*) FROM users where name = ?;")) {
            count.setString(1, username);
            ResultSet resultSet = count.executeQuery();
            return resultSet.getLong(1) == 1;
        } catch (SQLException sqe) {
            LOG.log(Level.WARNING, sqe.getMessage(), sqe);
            throw new RuntimeException(sqe);
        }
    }

    @Override
    public List<String> getAllUsernames() {
        try (PreparedStatement select = conn.prepareStatement("SELECT name FROM users;")) {
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
        try (PreparedStatement insert = conn.prepareStatement("INSERT INTO users (name, free, desired, quota, balance) VALUES(?, ?, ?, ?, ?);")) {
            insert.setString(1, username);
            insert.setLong(2, freeSpace.val);
            insert.setLong(3, 0);
            insert.setLong(4, 0);
            insert.setLong(5, 0);
            insert.execute();
        } catch (SQLException sqe) {
            LOG.log(Level.WARNING, sqe.getMessage(), sqe);
            throw new RuntimeException(sqe);
        }
    }

    @Override
    public void setCustomer(String username, CustomerResult customer) {
        try (PreparedStatement insert = conn.prepareStatement("UPDATE users SET customerid = ? WHERE name = ?;")) {
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
        try (PreparedStatement count = conn.prepareStatement("SELECT customerid FROM users where name = ?;")) {
            count.setString(1, username);
            ResultSet resultSet = count.executeQuery();
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
        try (PreparedStatement insert = conn.prepareStatement("UPDATE users SET desired = ? WHERE name = ?;")) {
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
        try (PreparedStatement count = conn.prepareStatement("SELECT desired FROM users where name = ?;")) {
            count.setString(1, username);
            ResultSet resultSet = count.executeQuery();
            return new Natural(resultSet.getLong(1));
        } catch (SQLException sqe) {
            LOG.log(Level.WARNING, sqe.getMessage(), sqe);
            throw new RuntimeException(sqe);
        }
    }

    @Override
    public void setCurrentBalance(String username, Natural balance) {
        try (PreparedStatement insert = conn.prepareStatement("UPDATE users SET balance = ? WHERE name = ?;")) {
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
        try (PreparedStatement count = conn.prepareStatement("SELECT balance FROM users where name = ?;")) {
            count.setString(1, username);
            ResultSet resultSet = count.executeQuery();
            return new Natural(resultSet.getLong(1));
        } catch (SQLException sqe) {
            LOG.log(Level.WARNING, sqe.getMessage(), sqe);
            throw new RuntimeException(sqe);
        }
    }

    @Override
    public void setCurrentQuota(String username, Natural quota) {
        try (PreparedStatement insert = conn.prepareStatement("UPDATE users SET quota = ? WHERE name = ?;")) {
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
        try (PreparedStatement count = conn.prepareStatement("SELECT quota FROM users where name = ?;")) {
            count.setString(1, username);
            ResultSet resultSet = count.executeQuery();
            return new Natural(resultSet.getLong(1));
        } catch (SQLException sqe) {
            LOG.log(Level.WARNING, sqe.getMessage(), sqe);
            throw new RuntimeException(sqe);
        }
    }

    @Override
    public void setFreeQuota(String username, Natural quota) {
        try (PreparedStatement insert = conn.prepareStatement("UPDATE users SET free = ? WHERE name = ?;")) {
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
        try (PreparedStatement count = conn.prepareStatement("SELECT free FROM users where name = ?;")) {
            count.setString(1, username);
            ResultSet resultSet = count.executeQuery();
            return new Natural(resultSet.getLong(1));
        } catch (SQLException sqe) {
            LOG.log(Level.WARNING, sqe.getMessage(), sqe);
            throw new RuntimeException(sqe);
        }
    }

    @Override
    public void setQuotaExpiry(String username, LocalDateTime expiry) {
        try (PreparedStatement insert = conn.prepareStatement("UPDATE users SET expiry = ? WHERE name = ?;")) {
            insert.setLong(1, expiry.toEpochSecond(ZoneOffset.UTC));
            insert.setString(2, username);
            insert.execute();
        } catch (SQLException sqe) {
            LOG.log(Level.WARNING, sqe.getMessage(), sqe);
            throw new RuntimeException(sqe);
        }
    }

    @Override
    public LocalDateTime getQuotaExpiry(String username) {
        try (PreparedStatement count = conn.prepareStatement("SELECT expiry FROM users where name = ?;")) {
            count.setString(1, username);
            ResultSet resultSet = count.executeQuery();
            return LocalDateTime.ofEpochSecond(resultSet.getLong(1), 0, ZoneOffset.UTC);
        } catch (SQLException sqe) {
            LOG.log(Level.WARNING, sqe.getMessage(), sqe);
            throw new RuntimeException(sqe);
        }
    }
}
