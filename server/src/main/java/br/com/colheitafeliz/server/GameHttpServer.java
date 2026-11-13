package br.com.colheitafeliz.server;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.Strictness;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import static br.com.colheitafeliz.server.OnlineViews.object;

final class GameHttpServer implements AutoCloseable {
    static final int WORKERS = 4, QUEUE = 32, IO_THREADS = 64;
    private static final Set<String> COMMAND_FIELDS = Set.of("requestId", "expectedRevision", "action", "itemId", "currency", "column", "row", "harvestRevision", "photoUrl");
    private final HttpServer http;
    private final OnlineGameService service;
    private final Authenticator auth;
    private final Set<String> origins;
    private final boolean development;
    private final boolean trustFlyClientIp;
    // Threads de plataforma de propósito: o HttpServer do JDK fixa a thread virtual (pinning) em leituras lentas de socket,
    // e com ActiveProcessorCount=2 bastariam 2 conexões lentas para travar tudo. Estas threads só esperam I/O; o trabalho
    // de verdade fica limitado por `workers`.
    private final ExecutorService threads = new ThreadPoolExecutor(IO_THREADS, IO_THREADS, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(QUEUE), new ThreadPoolExecutor.AbortPolicy());
    private final Semaphore workers = new Semaphore(WORKERS, true);
    private final AtomicInteger admitted = new AtomicInteger();
    private final Map<String, Rate> rates = new LinkedHashMap<>();
    private final Clock clock;
    private record Rate(long minute, int count) {}

    GameHttpServer(InetSocketAddress address, OnlineGameService service, Authenticator auth,
                   Set<String> origins, boolean development, Clock clock) throws IOException {
        this(address, service, auth, origins, development, clock, false);
    }
    GameHttpServer(InetSocketAddress address, OnlineGameService service, Authenticator auth,
                   Set<String> origins, boolean development, Clock clock, boolean trustFlyClientIp) throws IOException {
        this.trustFlyClientIp = trustFlyClientIp;
        if (development && !address.getAddress().isLoopbackAddress()) throw new IllegalArgumentException("Development auth requires loopback");
        this.service = service; this.auth = auth; this.origins = Set.copyOf(origins); this.development = development; this.clock = clock;
        http = HttpServer.create(address, 32);
        http.setExecutor(threads);
        http.createContext("/", this::handle);
    }
    void start() { http.start(); }
    int admitted() { return admitted.get(); }
    int port() { return http.getAddress().getPort(); }

    private void handle(HttpExchange exchange) throws IOException {
        boolean counted = false, held = false;
        try (var timing = RequestTimings.start()) {
            java.util.Objects.requireNonNull(timing);
            var headers = exchange.getResponseHeaders();
            headers.set("Cache-Control", "no-store"); headers.set("X-Content-Type-Options", "nosniff"); headers.set("Vary", "Origin");
            if (development && !LOOPBACK_HOSTS.contains(hostName(exchange.getRequestHeaders().getFirst("Host")))) throw new ApiFailure(421, "Host não permitido.");
            String origin = exchange.getRequestHeaders().getFirst("Origin");
            if (origin != null) {
                if (!origins.contains(origin)) throw new ApiFailure(403, "Origem não permitida.");
                headers.set("Access-Control-Allow-Origin", origin);
                headers.set("Access-Control-Allow-Headers", "Authorization, Content-Type");
                headers.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
                headers.set("Access-Control-Expose-Headers", "Server-Timing, Date");
                headers.set("Timing-Allow-Origin", origin);
            }
            if (exchange.getRequestMethod().equals("OPTIONS")) { exchange.sendResponseHeaders(204, -1); return; }
            String path = exchange.getRequestURI().getPath(), method = exchange.getRequestMethod();
            if (method.equals("GET") && path.equals("/health")) { send(exchange, 200, object("ok", true, "development", development)); return; }
            byte[] raw = method.equals("POST") ? preRead(exchange) : null;
            if (admitted.incrementAndGet() > WORKERS + QUEUE) { admitted.decrementAndGet(); exchange.getResponseHeaders().set("Retry-After", "5"); throw new ApiFailure(503, "Servidor ocupado. Tente novamente em instantes."); }
            counted = true;
            workers.acquireUninterruptibly(); held = true;
            rate("ip:" + clientAddress(exchange.getRequestHeaders().getFirst("Fly-Client-IP"), exchange.getRemoteAddress().getAddress(), trustFlyClientIp), 300);
            if (method.equals("GET") && path.equals("/v1/catalog")) { send(exchange, 200, service.catalog()); return; }
            Authenticator.Identity identity;
            long authStarted = System.nanoTime();
            try { identity = auth.authenticate(exchange.getRequestHeaders().getFirst("Authorization")); }
            finally { RequestTimings.auth(authStarted); }
            UUID actor = identity.id(); rate("actor:" + actor, 120);
            if (method.equals("GET") && path.equals("/v1/diagnostics")) {
                send(exchange, 200, object("ok", true, "serverTime", clock.instant().toString(), "originAllowed", origin != null));
                return;
            }
            Object response;
            if (method.equals("GET") && path.equals("/v1/request-id")) response = service.requestId();
            else if (method.equals("GET") && path.equals("/v1/me/farm")) response = service.farm(actor, identity.name());
            else if (method.equals("GET") && path.equals("/v1/friends")) response = service.friends(actor);
            else if (method.equals("POST") && path.equals("/v1/me/actions")) response = service.command(actor, command(read(exchange, raw, COMMAND_FIELDS)));
            else if (method.equals("POST") && path.equals("/v1/friend-invitations")) response = service.createInvitation(actor,
                    UUID.fromString(string(read(exchange, raw, Set.of("requestId")), "requestId", true)));
            else if (method.equals("POST") && path.equals("/v1/friend-invitations/accept")) {
                var body = read(exchange, raw, Set.of("requestId", "code"));
                response = service.acceptInvitation(actor, identity.name(), string(body, "code", true), UUID.fromString(string(body, "requestId", true)));
            } else if (path.matches("/v1/farms/[0-9a-fA-F-]{36}/visit(-actions)?")) {
                UUID owner = UUID.fromString(path.split("/")[3]);
                if (method.equals("GET") && path.endsWith("/visit")) response = service.visit(actor, owner);
                else if (method.equals("POST") && path.endsWith("/visit-actions")) response = service.visitAction(actor, owner, command(read(exchange, raw, COMMAND_FIELDS)));
                else throw new ApiFailure(405, "Método não permitido.");
            } else throw new ApiFailure(404, "Rota indisponível.");
            send(exchange, 200, response);
        } catch (ApiFailure e) {
            if (e.status() == 429) exchange.getResponseHeaders().set("Retry-After", "60");
            send(exchange, e.status(), object("error", e.getMessage()));
        } catch (IllegalArgumentException | IllegalStateException | com.google.gson.JsonParseException e) {
            send(exchange, 400, object("error", "Pedido inválido."));
        } catch (RuntimeException e) {
            System.err.println("API request failed: " + e.getClass().getSimpleName());
            send(exchange, 500, object("error", "Não foi possível concluir o pedido."));
        } finally {
            if (held) workers.release();
            if (counted) admitted.decrementAndGet();
            exchange.close();
        }
    }

    /** Lê o corpo antes de ocupar um worker; um cliente lento só segura uma thread de I/O. Null quando read() vai recusar o pedido (413). */
    private static byte[] preRead(HttpExchange exchange) throws IOException {
        String length = exchange.getRequestHeaders().getFirst("Content-Length");
        if (length != null && Long.parseLong(length) > 16_384) return null;
        return exchange.getRequestBody().readNBytes(16_385);
    }

    private static final Set<String> LOOPBACK_HOSTS = Set.of("127.0.0.1", "localhost", "[::1]");
    /** Host sem a porta; vazio quando ausente, para nunca casar com a lista. */
    private static String hostName(String host) {
        host = java.util.Objects.requireNonNullElse(host, "");
        int end = host.startsWith("[") ? host.indexOf(']') + 1 : host.lastIndexOf(':');
        String name = end > 0 ? host.substring(0, end) : host;
        return (name.endsWith(".") ? name.substring(0, name.length() - 1) : name).toLowerCase(java.util.Locale.ROOT);
    }
    private static final java.util.regex.Pattern IPV4 = java.util.regex.Pattern.compile("(?:25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])(?:\\.(?:25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])){3}");
    private static final java.util.regex.Pattern IPV6 = java.util.regex.Pattern.compile("[0-9A-Fa-f:.]{2,45}");

    /** IP usado no limite por IP. Atrás do Fly vale o Fly-Client-IP, mas só literais IP: nunca hostname, então nunca DNS. */
    static String clientAddress(String flyClientIp, java.net.InetAddress peer, boolean trust) {
        if (trust && flyClientIp != null) {
            if (IPV4.matcher(flyClientIp).matches() || (flyClientIp.indexOf(':') >= 0 && IPV6.matcher(flyClientIp).matches())) {
                try { return java.net.InetAddress.getByName(flyClientIp).getHostAddress(); }
                catch (java.net.UnknownHostException e) { /* literal inválido: cai no peer */ }
            }
        }
        return peer.getHostAddress();
    }

    private JsonObject read(HttpExchange exchange, byte[] raw, Set<String> allowed) throws IOException {
        String type = exchange.getRequestHeaders().getFirst("Content-Type");
        if (type == null || !type.split(";")[0].equalsIgnoreCase("application/json")) throw new ApiFailure(415, "Use JSON no pedido.");
        String length = exchange.getRequestHeaders().getFirst("Content-Length");
        if (length != null && Long.parseLong(length) > 16_384) throw new ApiFailure(413, "Pedido muito grande.");
        byte[] body = raw;
        if (body.length > 16_384) throw new ApiFailure(413, "Pedido muito grande.");
        JsonObject json = new GsonBuilder().setStrictness(Strictness.STRICT).create().fromJson(new String(body, StandardCharsets.UTF_8), JsonObject.class);
        if (json == null || !allowed.containsAll(json.keySet())) throw new ApiFailure(400, "Campos não permitidos.");
        return json;
    }
    private static Command command(JsonObject json) {
        return new Command(UUID.fromString(string(json, "requestId", true)), string(json, "expectedRevision", true),
                string(json, "action", true), string(json, "itemId", false), string(json, "currency", false),
                integer(json, "column"), integer(json, "row"), string(json, "harvestRevision", false),string(json,"photoUrl",false));
    }
    private static String string(JsonObject object, String field, boolean required) {
        var value = object.get(field);
        if (value == null || value.isJsonNull()) { if (required) throw new ApiFailure(400, "Falta o campo " + field + "."); return null; }
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new ApiFailure(400, "Campo inválido: " + field);
        return value.getAsString();
    }
    private static Integer integer(JsonObject json, String field) {
        var value = json.get(field);
        if (value == null || value.isJsonNull()) return null;
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new ApiFailure(400, "Coordenada inválida.");
        try { return value.getAsBigDecimal().intValueExact(); }
        catch (ArithmeticException e) { throw new ApiFailure(400, "Coordenada inválida."); }
    }
    private synchronized void rate(String key, int limit) {
        long minute = clock.instant().getEpochSecond() / 60;
        Rate before = rates.get(key);
        int count = before == null || before.minute() != minute ? 1 : before.count() + 1;
        rates.put(key, new Rate(minute, count));
        if (rates.size() > 2048) rates.remove(rates.keySet().iterator().next());
        if (count > limit) throw new ApiFailure(429, "Muitas ações. Aguarde um minuto.");
    }
    private static void send(HttpExchange exchange, int status, Object value) throws IOException {
        byte[] bytes = OnlineViews.JSON.toJson(value).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        String timing = RequestTimings.header();
        if (timing != null) exchange.getResponseHeaders().set("Server-Timing", timing);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }
    @Override public void close() { http.stop(1); threads.shutdownNow(); auth.close(); service.close(); }
}
