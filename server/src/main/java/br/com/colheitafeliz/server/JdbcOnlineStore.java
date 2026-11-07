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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.function.Function;

/** Persistência exclusivamente do online; não lê nem importa arquivos locais do jogador. */
final class JdbcOnlineStore implements AutoCloseable {
    static final int RECEIPTS_PER_ACTOR = 1000;
    record Player(UUID id, String name, boolean active, Instant registeredAt) {}
    record Friend(UUID id,String name,boolean active,br.com.colheitafeliz.game.PlayerAvatar avatar) {}
    record Farm(GameSnapshot snapshot, long revision, Instant time, LocalDate allowanceDay) {}
    record Receipt(String hash, JsonObject response) {}
    record Invitation(UUID owner, UUID usedBy, Instant expiresAt) {}
    private record FarmWrite(UUID owner, GameSnapshot snapshot, long revision, Instant time) {}
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
        initialize(owner,Instant.now());
    }
    void initialize(UUID owner,Instant registeredAt) {
        transaction(owner, tx -> {
            tx.execute("CREATE SCHEMA IF NOT EXISTS " + schema);
            tx.execute("CREATE TABLE IF NOT EXISTS " + schema + ".players (id uuid PRIMARY KEY, name varchar(80) NOT NULL, active boolean NOT NULL)");
            // Existing rows predate this release; new registrations receive authoritative server time.
            tx.execute("ALTER TABLE " + schema + ".players ADD COLUMN IF NOT EXISTS registered_at timestamptz NOT NULL DEFAULT '1970-01-01 00:00:00+00'");
            tx.execute("ALTER TABLE " + schema + ".players ALTER COLUMN registered_at SET DEFAULT CURRENT_TIMESTAMP");
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
            tx.execute("CREATE TABLE IF NOT EXISTS " + schema + ".theft_cooldowns (actor uuid PRIMARY KEY REFERENCES "
                    + schema + ".players(id), last_stolen_at timestamptz NOT NULL)");
            tx.execute("CREATE TABLE IF NOT EXISTS " + schema + ".pest_cooldowns (actor uuid NOT NULL REFERENCES "
                    + schema + ".players(id), kind text NOT NULL CHECK(kind IN ('SPREAD_INSECTS','SPREAD_WEEDS')), "
                    + "last_applied_at timestamptz NOT NULL, PRIMARY KEY(actor,kind))");
            tx.execute("CREATE TABLE IF NOT EXISTS " + schema + ".daily_allowances (actor uuid PRIMARY KEY REFERENCES "
                    + schema + ".players(id), last_claimed_day date NOT NULL)");
            tx.execute("REVOKE ALL ON SCHEMA " + schema + " FROM PUBLIC");
            for (String table : List.of("players", "farms", "receipts", "friendships", "invitations", "theft_cooldowns", "pest_cooldowns", "daily_allowances")) {
                tx.execute("ALTER TABLE " + schema + "." + table + " ENABLE ROW LEVEL SECURITY");
                tx.execute("REVOKE ALL ON " + schema + "." + table + " FROM PUBLIC");
            }
            for (String role : List.of("anon", "authenticated")) {
                if (!tx.query("SELECT 1 FROM pg_roles WHERE rolname=?", r -> r.getInt(1), role).isEmpty()) {
                    tx.execute("REVOKE ALL ON SCHEMA " + schema + " FROM " + role);
                    tx.execute("REVOKE ALL ON ALL TABLES IN SCHEMA " + schema + " FROM " + role);
                }
            }
            tx.execute("INSERT INTO " + schema + ".players (id,name,active,registered_at) VALUES (?, ?, true, ?) ON CONFLICT(id) DO NOTHING", owner, "Minha fazenda", java.sql.Timestamp.from(registeredAt));
            return null;
        });
    }

    <T> T transaction(UUID actor, Function<Tx, T> operation) {
        return transaction(actor, operation, false);
    }

    <T> T probe(UUID actor, Function<Tx, T> operation) {
        return transaction(actor, operation, true);
    }

    private <T> T transaction(UUID actor, Function<Tx, T> operation, boolean diagnostic) {
        long acquisitionStarted = System.nanoTime();
        try (Connection connection = pool.getConnection()) {
            RequestTimings.connection(acquisitionStarted);
            long transactionStarted = System.nanoTime();
            connection.setAutoCommit(false);
            try {
                Tx tx = new Tx(connection, diagnostic);
                tx.lock("actor:" + actor);
                T result = operation.apply(tx);
                if (tx.pendingSaves != null) throw new IllegalStateException("Incomplete receipt transaction");
                if (diagnostic) connection.rollback();
                else connection.commit();
                return result;
            } catch (RuntimeException e) {
                try {
                    connection.rollback();
                } catch (SQLException rollback) {
                    e.addSuppressed(rollback);
                }
                throw e;
            } finally {
                RequestTimings.database(transactionStarted);
            }

        } catch (SQLException e) {
            System.err.println(
                    "DB_DIAGNOSTIC sqlState=" + e.getSQLState()
                            + " exception=" + e.getClass().getSimpleName()
                            + " cause=" + (e.getCause() == null
                            ? "none"
                            : e.getCause().getClass().getSimpleName())
            );

            throw new ApiFailure(
                    503,
                    "Banco indisponível; tente novamente com o mesmo identificador de ação."
            );
        }
    }

    final class Tx {
        private final Connection connection;
        private final boolean diagnostic;
        private final Map<UUID, Player> players = new HashMap<>();
        private Map<UUID, FarmWrite> pendingSaves;
        Tx(Connection connection, boolean diagnostic) { this.connection = connection; this.diagnostic = diagnostic; }
        void benchmarkPlayer(UUID id) {
            if (!diagnostic) throw new IllegalStateException("Synthetic participants require a rollback probe");
            execute("INSERT INTO " + schema + ".players (id,name,active) VALUES (?, 'Latency benchmark', true)", id);
        }
        void lock(String key) { query("SELECT pg_advisory_xact_lock(hashtextextended(?,0))", r -> 0, key); }
        void lockFarms(UUID... owners) { Arrays.stream(owners).map(UUID::toString).distinct().sorted().forEach(id -> lock("farm:" + id)); }
        Instant lastTheft(UUID actor) {
            return first(query("SELECT last_stolen_at FROM " + schema + ".theft_cooldowns WHERE actor=?",
                    row -> row.getTimestamp(1).toInstant(), actor));
        }
        Instant lastPest(UUID actor, String kind) {
            return first(query("SELECT last_applied_at FROM " + schema + ".pest_cooldowns WHERE actor=? AND kind=?",
                    row -> row.getTimestamp(1).toInstant(), actor, kind));
        }
        void recordPest(UUID actor, String kind, Instant now) {
            execute("INSERT INTO " + schema + ".pest_cooldowns (actor,kind,last_applied_at) VALUES (?,?,?) "
                    + "ON CONFLICT(actor,kind) DO UPDATE SET last_applied_at=EXCLUDED.last_applied_at",
                    actor, kind, java.sql.Timestamp.from(now));
        }
        void recordTheft(UUID actor, Instant now) {
            execute("INSERT INTO " + schema + ".theft_cooldowns (actor,last_stolen_at) VALUES (?,?) "
                    + "ON CONFLICT(actor) DO UPDATE SET last_stolen_at=EXCLUDED.last_stolen_at",
                    actor, java.sql.Timestamp.from(now));
        }
        Player player(UUID id) {
            // FOR SHARE keeps this row stable until the transaction ends; never cache across requests.
            if (!players.containsKey(id)) players.put(id, first(query("SELECT id,name,active,registered_at FROM " + schema + ".players WHERE id=? FOR SHARE",
                    r -> new Player(r.getObject(1, UUID.class), r.getString(2), r.getBoolean(3),r.getTimestamp(4).toInstant()), id)));
            return players.get(id);
        }
        void setActive(UUID id, boolean active) {
            execute("UPDATE " + schema + ".players SET active=? WHERE id=?", active, id);
            players.remove(id);
        }
        void updateName(UUID id, String name) {
            execute("UPDATE " + schema + ".players SET name=? WHERE id=? AND active AND name<>?", name, id, name);
            players.remove(id);
        }
        void addPlayer(UUID id, String name, int limit, Instant registeredAt) {
            lock("membership");
            if (player(id) != null) return;
            int count = query("SELECT count(*) FROM " + schema + ".players WHERE active", r -> r.getInt(1)).getFirst();
            if (count >= limit) throw new ApiFailure(409, "O grupo de teste atingiu o limite de participantes.");
            execute("INSERT INTO " + schema + ".players (id,name,active,registered_at) VALUES (?,?,true,?)", id, name,java.sql.Timestamp.from(registeredAt));
            players.remove(id);
        }
        Farm farm(UUID owner) {
            return first(query("SELECT f.snapshot,f.revision,f.game_time,f.ruleset,a.last_claimed_day FROM " + schema
                    + ".farms f LEFT JOIN " + schema + ".daily_allowances a ON a.actor=f.owner WHERE f.owner=? FOR UPDATE OF f", r -> {
                if (!GameSave.supports(r.getString(4)))
                    throw new ApiFailure(503, "Versão da fazenda online incompatível.");
                var snapshot = JsonParser.parseString(r.getString(1));
                br.com.colheitafeliz.persistence.CropSaleJsonValidation.validate(snapshot);
                br.com.colheitafeliz.persistence.NotificationJsonValidation.validate(snapshot);
                br.com.colheitafeliz.persistence.AnimalSaleJsonValidation.validate(snapshot);
                var state = JSON.fromJson(snapshot, GameSnapshot.class);
                br.com.colheitafeliz.persistence.CropSaleJsonValidation.validateTerms(state);
                br.com.colheitafeliz.persistence.AnimalSaleJsonValidation.validateTerms(state);
                return new Farm(state, r.getLong(2), r.getTimestamp(3).toInstant(), r.getObject(5, LocalDate.class));
            }, owner));
        }
        void recordAllowance(UUID actor, LocalDate day) {
            execute("INSERT INTO " + schema + ".daily_allowances (actor,last_claimed_day) VALUES (?,?) "
                    + "ON CONFLICT(actor) DO UPDATE SET last_claimed_day=EXCLUDED.last_claimed_day", actor, day);
        }
        void save(UUID owner, GameSnapshot snapshot, long revision, Instant now) {
            if (pendingSaves != null) {
                pendingSaves.put(owner, new FarmWrite(owner, snapshot, revision, now));
                return;
            }
            execute("INSERT INTO " + schema + ".farms VALUES (?,?,?,?,?::jsonb) ON CONFLICT(owner) DO UPDATE SET "
                    + "ruleset=EXCLUDED.ruleset,revision=EXCLUDED.revision,game_time=EXCLUDED.game_time,snapshot=EXCLUDED.snapshot",
                    owner, GameSave.RULESET, revision, java.sql.Timestamp.from(now), JSON.toJson(snapshot));
        }
        Receipt receipt(UUID actor, UUID request) {
            return first(query("SELECT hash,response FROM " + schema + ".receipts WHERE actor=? AND request_id=?",
                    r -> new Receipt(r.getString(1), JsonParser.parseString(r.getString(2)).getAsJsonObject()), actor, request));
        }
        void prepareReceipt(UUID actor, Instant now) {
            // Bounded batches avoid unbounded cleanup work and competing cleaners skip locked rows.
            String expired = "DELETE FROM " + schema + ".receipts WHERE (actor,request_id) IN (SELECT actor,request_id FROM "
                    + schema + ".receipts WHERE expires_at<=?" + (diagnostic ? " AND actor=?" : "")
                    + " ORDER BY expires_at LIMIT 1000 FOR UPDATE SKIP LOCKED)";
            var values = new ArrayList<Object>();
            values.add(java.sql.Timestamp.from(now));
            if (diagnostic) values.add(actor);
            values.add(actor); values.add(java.sql.Timestamp.from(now));
            // Deleted expired rows and counted live rows are disjoint, even under the shared CTE snapshot.
            int count = query("WITH expired AS (" + expired + " RETURNING 1) SELECT count(*) FROM " + schema
                    + ".receipts WHERE actor=? AND expires_at>?", r -> r.getInt(1), values.toArray()).get(0);
            if (count >= RECEIPTS_PER_ACTOR)
                throw new ApiFailure(429, "Limite de 1.000 ações nas últimas 24 horas. Aguarde antes de iniciar outra ação.");
        }
        /** Defer only the writes belonging to the current receipt; remember must flush them before commit. */
        void collectSaves() { pendingSaves = new LinkedHashMap<>(); }
        void remember(UUID actor, UUID request, String hash, JsonObject response, Instant expires) {
            var sql = new StringBuilder();
            var values = new ArrayList<Object>();
            if (pendingSaves != null && !pendingSaves.isEmpty()) {
                sql.append("WITH saved AS (INSERT INTO ").append(schema).append(".farms VALUES ");
                for (var farm : pendingSaves.values()) {
                    if (!values.isEmpty()) sql.append(',');
                    sql.append("(?,?,?,?,?::jsonb)");
                    values.add(farm.owner()); values.add(GameSave.RULESET); values.add(farm.revision());
                    values.add(java.sql.Timestamp.from(farm.time())); values.add(JSON.toJson(farm.snapshot()));
                }
                sql.append(" ON CONFLICT(owner) DO UPDATE SET ruleset=EXCLUDED.ruleset,revision=EXCLUDED.revision,game_time=EXCLUDED.game_time,snapshot=EXCLUDED.snapshot RETURNING owner) ");
            }
            sql.append("INSERT INTO ").append(schema).append(".receipts (actor,request_id,hash,response,expires_at) VALUES (?,?,?,?::jsonb,?)");
            values.add(actor); values.add(request); values.add(hash); values.add(JSON.toJson(response)); values.add(java.sql.Timestamp.from(expires));
            execute(sql.toString(), values.toArray());
            pendingSaves = null;
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
        List<Friend> friends(UUID actor) {
            return query("SELECT p.id,p.name,p.active,g.snapshot->'avatar' FROM " + schema + ".players p JOIN " + schema
                    + ".friendships f ON p.id=CASE WHEN f.a=? THEN f.b ELSE f.a END LEFT JOIN " + schema
                    + ".farms g ON g.owner=p.id WHERE (f.a=? OR f.b=?) AND p.active",
                    r -> new Friend(r.getObject(1, UUID.class), r.getString(2), r.getBoolean(3),publicAvatar(r.getString(4))), actor, actor, actor);
        }
        private br.com.colheitafeliz.game.PlayerAvatar publicAvatar(String json){
            var avatar=json==null?com.google.gson.JsonNull.INSTANCE:JsonParser.parseString(json);
            if(avatar.isJsonNull())return new br.com.colheitafeliz.game.PlayerAvatar("classic",null);
            var wrapper=new com.google.gson.JsonObject();wrapper.add("avatar",avatar);
            br.com.colheitafeliz.persistence.NotificationJsonValidation.validate(wrapper);
            return JSON.fromJson(avatar,br.com.colheitafeliz.game.PlayerAvatar.class);
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
