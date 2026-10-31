package br.com.colheitafeliz.server;

import br.com.colheitafeliz.game.GameSnapshot;
import br.com.colheitafeliz.persistence.GameSave;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.function.Function;

/** Persistência exclusivamente do online; não lê nem importa arquivos locais do jogador. */
final class JdbcOnlineStore implements AutoCloseable {
    static final int RECEIPTS_PER_ACTOR = 1000;
    record Player(UUID id, String name, boolean active) {}
    record Farm(GameSnapshot snapshot, long revision, Instant time) {}
    record Receipt(String hash, JsonObject response) {}
    record Invitation(UUID owner, UUID usedBy, Instant expiresAt) {}
    private static final Gson JSON = new Gson();
    private final String schema;
    private final HikariDataSource pool;

    JdbcOnlineStore(String url, String user, String password, String schema) {
        if (!url.startsWith("jdbc:postgresql://") || !schema.matches("[a-z][a-z0-9_]{0,50}"))
            throw new IllegalArgumentException("Invalid PostgreSQL configuration");
        this.schema = schema;
        this.pool = pool(url, user, password);
    }

    JdbcOnlineStore(HikariDataSource pool, String schema) {
        if (!schema.matches("[a-z][a-z0-9_]{0,50}")) throw new IllegalArgumentException("Invalid schema");
        this.pool = pool;
        this.schema = schema;
    }

    static HikariDataSource pool(String url, String user, String password) {
        var properties = new Properties();
        properties.setProperty("user", user);
        properties.setProperty("password", password);
        properties.setProperty("connectTimeout", "5");
        properties.setProperty("socketTimeout", "15");
        properties.setProperty("prepareThreshold", "0");
        var pool = new HikariDataSource();
        pool.setJdbcUrl(url);
        pool.setDataSourceProperties(properties);
        pool.setMaximumPoolSize(4);
        pool.setMinimumIdle(1);
        pool.setConnectionTimeout(5000);
        pool.setValidationTimeout(2000);
        pool.setMaxLifetime(600_000);
        return pool;
    }

    @Override public void close() { pool.close(); }

    void initialize(UUID owner) {
        transaction(owner, tx -> {
            tx.execute("CREATE SCHEMA IF NOT EXISTS " + schema);
            tx.execute("CREATE TABLE IF NOT EXISTS " + schema + ".players (id uuid PRIMARY KEY, name varchar(80) NOT NULL, active boolean NOT NULL)");
            tx.execute("CREATE TABLE IF NOT EXISTS " + schema + ".farms (owner uuid PRIMARY KEY REFERENCES " + schema
                    + ".players(id), ruleset text NOT NULL, revision bigint NOT NULL CHECK(revision > 0), game_time timestamptz NOT NULL, snapshot jsonb NOT NULL)");
            tx.execute("CREATE TABLE IF NOT EXISTS " + schema + ".receipts (actor uuid NOT NULL, request_id uuid NOT NULL, hash text NOT NULL, response jsonb NOT NULL, PRIMARY KEY(actor,request_id))");
            tx.execute("ALTER TABLE " + schema + ".receipts ADD COLUMN IF NOT EXISTS expires_at timestamptz NOT NULL DEFAULT (CURRENT_TIMESTAMP + INTERVAL '24 hours')");
            tx.execute("CREATE INDEX IF NOT EXISTS receipts_expiry_idx ON " + schema + ".receipts(expires_at)");
            tx.execute("CREATE INDEX IF NOT EXISTS receipts_actor_expiry_idx ON " + schema + ".receipts(actor,expires_at)");
            tx.execute("CREATE TABLE IF NOT EXISTS " + schema + ".friendships (a uuid NOT NULL REFERENCES " + schema
                    + ".players(id), b uuid NOT NULL REFERENCES " + schema + ".players(id), PRIMARY KEY(a,b), CHECK(a::text < b::text))");
            tx.execute("CREATE TABLE IF NOT EXISTS " + schema + ".invitations (hash text PRIMARY KEY, owner uuid NOT NULL REFERENCES "
                    + schema + ".players(id), used_by uuid, expires_at timestamptz NOT NULL)");
            tx.execute("REVOKE ALL ON SCHEMA " + schema + " FROM PUBLIC");
            for (String table : List.of("players", "farms", "receipts", "friendships", "invitations")) {
                tx.execute("ALTER TABLE " + schema + "." + table + " ENABLE ROW LEVEL SECURITY");
                tx.execute("REVOKE ALL ON " + schema + "." + table + " FROM PUBLIC");
            }
            for (String role : List.of("anon", "authenticated")) {
                if (!tx.query("SELECT 1 FROM pg_roles WHERE rolname=?", r -> r.getInt(1), role).isEmpty()) {
                    tx.execute("REVOKE ALL ON SCHEMA " + schema + " FROM " + role);
                    tx.execute("REVOKE ALL ON ALL TABLES IN SCHEMA " + schema + " FROM " + role);
                }
            }
            tx.execute("INSERT INTO " + schema + ".players VALUES (?, ?, true) ON CONFLICT(id) DO NOTHING", owner, "Minha fazenda");
            return null;
        });
    }

    <T> T transaction(UUID actor, Function<Tx, T> operation) {
        long acquisitionStarted = System.nanoTime();
        try (Connection connection = pool.getConnection()) {
            RequestTimings.connection(acquisitionStarted);
            long transactionStarted = System.nanoTime();
            connection.setAutoCommit(false);
            try {
                Tx tx = new Tx(connection);
                tx.lock("actor:" + actor);
                T result = operation.apply(tx);
                connection.commit();
                return result;
            } catch (RuntimeException e) {
                try { connection.rollback(); } catch (SQLException rollback) { e.addSuppressed(rollback); }
                throw e;
            } finally { RequestTimings.database(transactionStarted); }
        } catch (SQLException e) { throw new ApiFailure(503, "Banco indisponível; tente novamente com o mesmo identificador de ação."); }
    }

    final class Tx {
        private final Connection connection;
        Tx(Connection connection) { this.connection = connection; }
        void lock(String key) { query("SELECT pg_advisory_xact_lock(hashtextextended(?,0))", r -> 0, key); }
        void lockFarms(UUID... owners) { Arrays.stream(owners).map(UUID::toString).distinct().sorted().forEach(id -> lock("farm:" + id)); }
        Player player(UUID id) {
            return first(query("SELECT id,name,active FROM " + schema + ".players WHERE id=? FOR SHARE",
                    r -> new Player(r.getObject(1, UUID.class), r.getString(2), r.getBoolean(3)), id));
        }
        void setActive(UUID id, boolean active) { execute("UPDATE " + schema + ".players SET active=? WHERE id=?", active, id); }
        void updateName(UUID id, String name) {
            execute("UPDATE " + schema + ".players SET name=? WHERE id=? AND active AND name<>?", name, id, name);
        }
        void addPlayer(UUID id, String name, int limit) {
            lock("membership");
            if (player(id) != null) return;
            int count = query("SELECT count(*) FROM " + schema + ".players WHERE active", r -> r.getInt(1)).getFirst();
            if (count >= limit) throw new ApiFailure(409, "O grupo de teste atingiu o limite de participantes.");
            execute("INSERT INTO " + schema + ".players VALUES (?,?,true)", id, name);
        }
        Farm farm(UUID owner) {
            return first(query("SELECT snapshot,revision,game_time,ruleset FROM " + schema + ".farms WHERE owner=? FOR UPDATE", r -> {
                if (!GameSave.RULESET.equals(r.getString(4))) throw new ApiFailure(503, "Versão da fazenda online incompatível.");
                return new Farm(JSON.fromJson(r.getString(1), GameSnapshot.class), r.getLong(2), r.getTimestamp(3).toInstant());
            }, owner));
        }
        void save(UUID owner, GameSnapshot snapshot, long revision, Instant now) {
            execute("INSERT INTO " + schema + ".farms VALUES (?,?,?,?,?::jsonb) ON CONFLICT(owner) DO UPDATE SET "
                    + "revision=EXCLUDED.revision,game_time=EXCLUDED.game_time,snapshot=EXCLUDED.snapshot",
                    owner, GameSave.RULESET, revision, java.sql.Timestamp.from(now), JSON.toJson(snapshot));
        }
        Receipt receipt(UUID actor, UUID request) {
            return first(query("SELECT hash,response FROM " + schema + ".receipts WHERE actor=? AND request_id=?",
                    r -> new Receipt(r.getString(1), JsonParser.parseString(r.getString(2)).getAsJsonObject()), actor, request));
        }
        void prepareReceipt(UUID actor, Instant now) {
            // Bounded batches avoid unbounded cleanup work and competing cleaners skip locked rows.
            execute("DELETE FROM " + schema + ".receipts WHERE (actor,request_id) IN (SELECT actor,request_id FROM "
                    + schema + ".receipts WHERE expires_at<=? ORDER BY expires_at LIMIT 1000 FOR UPDATE SKIP LOCKED)",
                    java.sql.Timestamp.from(now));
            int count = query("SELECT count(*) FROM " + schema + ".receipts WHERE actor=? AND expires_at>?",
                    r -> r.getInt(1), actor, java.sql.Timestamp.from(now)).get(0);
            if (count >= RECEIPTS_PER_ACTOR)
                throw new ApiFailure(429, "Limite de 1.000 ações nas últimas 24 horas. Aguarde antes de iniciar outra ação.");
        }
        void remember(UUID actor, UUID request, String hash, JsonObject response, Instant expires) {
            execute("INSERT INTO " + schema + ".receipts (actor,request_id,hash,response,expires_at) VALUES (?,?,?,?::jsonb,?)",
                    actor, request, hash, JSON.toJson(response), java.sql.Timestamp.from(expires));
        }
        void invite(String hash, UUID owner, Instant expires) {
            execute("INSERT INTO " + schema + ".invitations VALUES (?,?,NULL,?)", hash, owner, java.sql.Timestamp.from(expires));
        }
        Invitation invitation(String hash) {
            return first(query("SELECT owner,used_by,expires_at FROM " + schema + ".invitations WHERE hash=? FOR UPDATE",
                    r -> new Invitation(r.getObject(1, UUID.class), r.getObject(2, UUID.class), r.getTimestamp(3).toInstant()), hash));
        }
        void accept(String hash, UUID user) { execute("UPDATE " + schema + ".invitations SET used_by=? WHERE hash=?", user, hash); }
        void befriend(UUID one, UUID two) {
            List<String> ids = List.of(one.toString(), two.toString()).stream().sorted().toList();
            execute("INSERT INTO " + schema + ".friendships VALUES (?,?) ON CONFLICT DO NOTHING", UUID.fromString(ids.get(0)), UUID.fromString(ids.get(1)));
        }
        boolean friends(UUID one, UUID two) {
            return !query("SELECT 1 FROM " + schema + ".friendships WHERE (a=? AND b=?) OR (a=? AND b=?)", r -> 1, one, two, two, one).isEmpty();
        }
        List<Player> friends(UUID actor) {
            return query("SELECT p.id,p.name,p.active FROM " + schema + ".players p JOIN " + schema
                    + ".friendships f ON p.id=CASE WHEN f.a=? THEN f.b ELSE f.a END WHERE (f.a=? OR f.b=?) AND p.active",
                    r -> new Player(r.getObject(1, UUID.class), r.getString(2), r.getBoolean(3)), actor, actor, actor);
        }
        void execute(String sql, Object... values) {
            try (PreparedStatement statement = prepare(sql, values)) { statement.execute(); }
            catch (SQLException e) { throw new ApiFailure(503, "Não foi possível confirmar a transação."); }
        }
        <T> List<T> query(String sql, Mapper<T> mapper, Object... values) {
            try (PreparedStatement statement = prepare(sql, values); ResultSet rows = statement.executeQuery()) {
                List<T> result = new ArrayList<>();
                while (rows.next()) result.add(mapper.map(rows));
                return result;
            } catch (SQLException e) { throw new ApiFailure(503, "Não foi possível consultar a fazenda."); }
        }
        private PreparedStatement prepare(String sql, Object[] values) throws SQLException {
            PreparedStatement statement = connection.prepareStatement(sql);
            try {
                for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
                return statement;
            } catch (SQLException e) { statement.close(); throw e; }
        }
    }
    @FunctionalInterface interface Mapper<T> { T map(ResultSet rows) throws SQLException; }
    private static <T> T first(List<T> values) { return values.isEmpty() ? null : values.getFirst(); }
}
