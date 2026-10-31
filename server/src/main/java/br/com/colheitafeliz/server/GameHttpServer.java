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
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import static br.com.colheitafeliz.server.OnlineViews.object;

final class GameHttpServer implements AutoCloseable {
    private static final Set<String> COMMAND_FIELDS = Set.of("requestId", "expectedRevision", "action", "itemId", "currency", "column", "row", "harvestRevision");
    private final HttpServer http;
    private final OnlineGameService service;
    private final Authenticator auth;
    private final Set<String> origins;
    private final boolean development;
    private final ExecutorService threads = new ThreadPoolExecutor(4, 4, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(32), new ThreadPoolExecutor.AbortPolicy());
    private final Map<String, Rate> rates = new LinkedHashMap<>();
    private final Clock clock;
    private record Rate(long minute, int count) {}

    GameHttpServer(InetSocketAddress address, OnlineGameService service, Authenticator auth,
                   Set<String> origins, boolean development, Clock clock) throws IOException {
        if (development && !address.getAddress().isLoopbackAddress()) throw new IllegalArgumentException("Development auth requires loopback");
        this.service = service; this.auth = auth; this.origins = Set.copyOf(origins); this.development = development; this.clock = clock;
        http = HttpServer.create(address, 32);
        http.setExecutor(threads);
        http.createContext("/", this::handle);
    }
    void start() { http.start(); }
    int port() { return http.getAddress().getPort(); }

    private void handle(HttpExchange exchange) throws IOException {
        try (var timing = RequestTimings.start()) {
            java.util.Objects.requireNonNull(timing);
            var headers = exchange.getResponseHeaders();
            headers.set("Cache-Control", "no-store"); headers.set("X-Content-Type-Options", "nosniff"); headers.set("Vary", "Origin");
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
            rate("ip:" + exchange.getRemoteAddress().getAddress().getHostAddress(), 300);
            if (method.equals("GET") && path.equals("/v1/catalog")) { send(exchange, 200, service.catalog()); return; }
            Authenticator.Identity identity;
            long authStarted = System.nanoTime();
            try { identity = auth.authenticate(exchange.getRequestHeaders().getFirst("Authorization")); }
            finally { RequestTimings.auth(authStarted); }
            UUID actor = identity.id(); rate("actor:" + actor, 120);
            Object response;
            if (method.equals("GET") && path.equals("/v1/request-id")) response = service.requestId();
            else if (method.equals("GET") && path.equals("/v1/me/farm")) response = service.farm(actor, identity.name());
            else if (method.equals("GET") && path.equals("/v1/friends")) response = service.friends(actor);
            else if (method.equals("POST") && path.equals("/v1/me/actions")) response = service.command(actor, command(read(exchange, COMMAND_FIELDS)));
            else if (method.equals("POST") && path.equals("/v1/friend-invitations")) response = service.createInvitation(actor,
                    UUID.fromString(string(read(exchange, Set.of("requestId")), "requestId", true)));
            else if (method.equals("POST") && path.equals("/v1/friend-invitations/accept")) {
                var body = read(exchange, Set.of("requestId", "code"));
                response = service.acceptInvitation(actor, identity.name(), string(body, "code", true), UUID.fromString(string(body, "requestId", true)));
            } else if (path.matches("/v1/farms/[0-9a-fA-F-]{36}/visit(-actions)?")) {
                UUID owner = UUID.fromString(path.split("/")[3]);
                if (method.equals("GET") && path.endsWith("/visit")) response = service.visit(actor, owner);
                else if (method.equals("POST") && path.endsWith("/visit-actions")) response = service.visitAction(actor, owner, command(read(exchange, COMMAND_FIELDS)));
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
        } finally { exchange.close(); }
    }

    private JsonObject read(HttpExchange exchange, Set<String> allowed) throws IOException {
        String type = exchange.getRequestHeaders().getFirst("Content-Type");
        if (type == null || !type.split(";")[0].equalsIgnoreCase("application/json")) throw new ApiFailure(415, "Use JSON no pedido.");
        String length = exchange.getRequestHeaders().getFirst("Content-Length");
        if (length != null && Long.parseLong(length) > 16_384) throw new ApiFailure(413, "Pedido muito grande.");
        byte[] body = exchange.getRequestBody().readNBytes(16_385);
        if (body.length > 16_384) throw new ApiFailure(413, "Pedido muito grande.");
        JsonObject json = new GsonBuilder().setStrictness(Strictness.STRICT).create().fromJson(new String(body, StandardCharsets.UTF_8), JsonObject.class);
        if (json == null || !allowed.containsAll(json.keySet())) throw new ApiFailure(400, "Campos não permitidos.");
        return json;
    }
    private static Command command(JsonObject json) {
        return new Command(UUID.fromString(string(json, "requestId", true)), string(json, "expectedRevision", true),
                string(json, "action", true), string(json, "itemId", false), string(json, "currency", false),
                integer(json, "column"), integer(json, "row"), string(json, "harvestRevision", false));
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
