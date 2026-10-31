package br.com.colheitafeliz.server;

import java.util.Locale;

/** Per-request durations only; never includes credentials, SQL or player data. */
final class RequestTimings implements AutoCloseable {
    private static final ThreadLocal<RequestTimings> CURRENT = new ThreadLocal<>();
    private final long started = System.nanoTime();
    private long auth, connection, database;
    private RequestTimings() {}
    static RequestTimings start() {
        var timing = new RequestTimings();
        CURRENT.set(timing);
        return timing;
    }
    static void auth(long started) {
        var timing = CURRENT.get();
        if (timing != null) timing.auth += System.nanoTime() - started;
    }
    static void connection(long started) {
        var timing = CURRENT.get();
        if (timing != null) timing.connection += System.nanoTime() - started;
    }
    static void database(long started) {
        var timing = CURRENT.get();
        if (timing != null) timing.database += System.nanoTime() - started;
    }
    static String header() {
        var timing = CURRENT.get();
        if (timing == null) return null;
        return String.format(Locale.ROOT, "auth;dur=%.2f, db_connect;dur=%.2f, db;dur=%.2f, app;dur=%.2f",
                timing.auth / 1e6, timing.connection / 1e6, timing.database / 1e6, (System.nanoTime() - timing.started) / 1e6);
    }
    @Override public void close() { CURRENT.remove(); }
}
