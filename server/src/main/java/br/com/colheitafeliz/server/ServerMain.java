package br.com.colheitafeliz.server;

import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Clock;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class ServerMain {
    private ServerMain() {}
    public static void main(String[] args) throws Exception {
        Map<String, String> env = System.getenv();
        boolean development = "development".equals(env.get("AUTH_MODE"));
        String host = env.getOrDefault("HOST", development ? "127.0.0.1" : "0.0.0.0");
        if (development && !Set.of("127.0.0.1", "localhost", "::1").contains(host)) throw new IllegalArgumentException("Development requires loopback HOST");
        UUID owner = UUID.fromString(development ? "00000000-0000-0000-0000-000000000001" : required(env, "OWNER_USER_ID"));
        String url = required(env, "DATABASE_URL");
        if (!development && !url.matches(".*[?&]sslmode=(require|verify-full)(&.*)?")) throw new IllegalArgumentException("Production database requires sslmode=require or verify-full");
        var store = new JdbcOnlineStore(url, required(env, "DATABASE_USER"), env.getOrDefault("DATABASE_PASSWORD", ""), "colheita");
        Authenticator auth;
        if (development) auth = token -> {
            if ("Bearer dev-alice".equals(token)) return new Authenticator.Identity(owner, "Alice");
            if ("Bearer dev-bob".equals(token)) return new Authenticator.Identity(UUID.fromString("00000000-0000-0000-0000-000000000002"), "Bob");
            throw new ApiFailure(401, "Escolha uma conta local de teste.");
        };
        else {
            URI supabase = URI.create(required(env, "SUPABASE_URL"));
            if (!"https".equals(supabase.getScheme()) || supabase.getHost() == null || !supabase.getHost().endsWith(".supabase.co")
                    || supabase.getUserInfo() != null || supabase.getQuery() != null || supabase.getFragment() != null)
                throw new IllegalArgumentException("Use the HTTPS Supabase project URL");
            auth = new SupabaseAuthenticator(supabase.resolve("/auth/v1/user"), required(env, "SUPABASE_PUBLISHABLE_KEY"));
        }
        Set<String> origins = Arrays.stream(required(env, "ALLOWED_ORIGINS").split(",")).map(String::strip).collect(Collectors.toUnmodifiableSet());
        if (!development && origins.stream().anyMatch(origin -> !origin.startsWith("https://") || origin.contains("*")))
            throw new IllegalArgumentException("Production origins require explicit HTTPS URLs");
        store.initialize(owner);
        var server = new GameHttpServer(new InetSocketAddress(host, Integer.parseInt(env.getOrDefault("PORT", "8080"))),
                new OnlineGameService(store, Clock.systemUTC(), java.util.concurrent.ThreadLocalRandom.current()::nextInt, 5),
                auth, origins, development, Clock.systemUTC());
        Runtime.getRuntime().addShutdownHook(new Thread(server::close, "server-shutdown"));
        server.start();
        System.out.println("Colheita API listening on " + host + ":" + server.port() + (development ? " (local development only)" : ""));
    }
    private static String required(Map<String, String> env, String key) {
        String value = env.get(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing " + key);
        return value;
    }
}
