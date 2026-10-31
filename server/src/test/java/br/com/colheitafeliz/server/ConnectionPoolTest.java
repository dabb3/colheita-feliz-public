package br.com.colheitafeliz.server;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.PrintWriter;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConnectionPoolTest {
    @Test void anIncompleteReceiptCannotCommitDeferredFarmWrites() {
        var source = new FakeDatabase();
        var config = new HikariConfig();
        config.setDataSource(source);
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(1);
        try (var store = new JdbcOnlineStore(new HikariDataSource(config), "test_receipt")) {
            assertThrows(IllegalStateException.class, () -> store.transaction(source.playerId, tx -> {
                tx.collectSaves();
                return null;
            }));
            assertEquals(0, source.commits);
            assertEquals(1, source.rollbacks);
        }
    }
    @Test void lockedPlayerLookupUsesOneRoundTripAndInvalidatesAfterWritesAndAcrossTransactions() {
        var source = new FakeDatabase();
        var config = new HikariConfig();
        config.setDataSource(source);
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(1);
        try (var store = new JdbcOnlineStore(new HikariDataSource(config), "test_player")) {
            store.transaction(source.playerId, tx -> {
                assertTrue(tx.player(source.playerId).active());
                assertTrue(tx.player(source.playerId).active());
                assertEquals(1, source.playerQueries);
                tx.setActive(source.playerId, false);
                assertFalse(tx.player(source.playerId).active());
                assertEquals(2, source.playerQueries);
                return null;
            });
            store.transaction(source.playerId, tx -> {
                assertFalse(tx.player(source.playerId).active());
                assertEquals(3, source.playerQueries);
                return null;
            });
        }
    }
    @Test void diagnosticTransactionsAlwaysRollbackAndCannotCommitFixtureData() {
        var source = new FakeDatabase();
        var config = new HikariConfig();
        config.setDataSource(source);
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(1);
        try (var store = new JdbcOnlineStore(new HikariDataSource(config), "test_probe")) {
            assertEquals(9, store.probe(UUID.randomUUID(), tx -> 9).intValue());
            assertThrows(IllegalStateException.class, () -> store.probe(UUID.randomUUID(), tx -> {
                throw new IllegalStateException("probe failure");
            }));
            assertEquals(0, source.commits);
            assertEquals(2, source.rollbacks);
        }
    }
    @Test void repeatedTransactionsReusePhysicalConnectionAndRollbackBeforeReuse() {
        var source = new FakeDatabase();
        var config = new HikariConfig();
        config.setDataSource(source);
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(1);
        try (var store = new JdbcOnlineStore(new HikariDataSource(config), "test_pool")) {
            var actor = UUID.randomUUID();
            assertThrows(IllegalStateException.class, () -> store.transaction(actor, tx -> {
                throw new IllegalStateException("failed operation");
            }));
            assertEquals(7, store.transaction(actor, tx -> 7).intValue());
            assertEquals(1, source.opened);
            assertEquals(1, source.rollbacks);
            assertEquals(1, source.commits);
            assertFalse(source.closed);
        }
        assertTrue(source.closed);
    }

    @Test void poolIsBoundedAndPreservesDriverTlsProperties() {
        try (var pool = JdbcOnlineStore.pool("jdbc:postgresql://example/farm?sslmode=verify-full", "api", "secret")) {
            assertEquals(4, pool.getMaximumPoolSize());
            assertEquals(1, pool.getMinimumIdle());
            assertEquals(5000, pool.getConnectionTimeout());
            assertTrue(pool.getJdbcUrl().endsWith("sslmode=verify-full"));
            assertEquals("0", pool.getDataSourceProperties().getProperty("prepareThreshold"));
            assertEquals("15", pool.getDataSourceProperties().getProperty("socketTimeout"));
        }
    }

    private static final class FakeDatabase implements DataSource {
        int opened, rollbacks, commits, playerQueries;
        final UUID playerId = UUID.randomUUID();
        boolean active = true;
        boolean closed, autoCommit = true;
        @Override public Connection getConnection() {
            opened++;
            return proxy(Connection.class, (method, args) -> switch (method) {
                case "isValid" -> !closed;
                case "isClosed" -> closed;
                case "close" -> { closed = true; yield null; }
                case "getAutoCommit" -> autoCommit;
                case "setAutoCommit" -> { autoCommit = (boolean) args[0]; yield null; }
                case "commit" -> { commits++; yield null; }
                case "rollback" -> { rollbacks++; yield null; }
                case "getTransactionIsolation" -> Connection.TRANSACTION_READ_COMMITTED;
                case "prepareStatement" -> {
                    String sql = (String) args[0];
                    yield proxy(PreparedStatement.class, (name, statementArgs) -> {
                        if (name.equals("setObject") && sql.contains("SET active=") && (int) statementArgs[0] == 1) active = (boolean) statementArgs[1];
                        if (!name.equals("executeQuery")) return null;
                        boolean playerQuery = sql.contains(".players WHERE id=");
                        if (playerQuery) playerQueries++;
                        var read = new java.util.concurrent.atomic.AtomicBoolean();
                        return proxy(ResultSet.class, (rowMethod, rowArgs) -> switch (rowMethod) {
                            case "next" -> playerQuery && !read.getAndSet(true);
                            case "getObject" -> playerId;
                            case "getString" -> "Player";
                            case "getBoolean" -> active;
                            default -> null;
                        });
                    });
                }
                default -> null;
            });
        }
        @Override public Connection getConnection(String user, String password) { return getConnection(); }
        @Override public PrintWriter getLogWriter() { return null; }
        @Override public void setLogWriter(PrintWriter writer) {}
        @Override public void setLoginTimeout(int seconds) {}
        @Override public int getLoginTimeout() { return 0; }
        @Override public Logger getParentLogger() { return Logger.getGlobal(); }
        @Override public <T> T unwrap(Class<T> type) { throw new UnsupportedOperationException(); }
        @Override public boolean isWrapperFor(Class<?> type) { return false; }
    }
    @FunctionalInterface private interface Call { Object invoke(String method, Object[] args); }
    private static <T> T proxy(Class<T> type, Call call) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (self, method, args) -> {
            Object result = call.invoke(method.getName(), args);
            if (result != null || !method.getReturnType().isPrimitive()) return result;
            if (method.getReturnType() == boolean.class) return false;
            if (method.getReturnType() == int.class) return 0;
            if (method.getReturnType() == long.class) return 0L;
            return null;
        }));
    }
}
