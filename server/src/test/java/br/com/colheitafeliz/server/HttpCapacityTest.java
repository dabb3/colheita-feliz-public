package br.com.colheitafeliz.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HttpCapacityTest {
    private static final Authenticator DENY = token -> { throw new ApiFailure(401, "x"); };
    private static final Authenticator ALLOW = token -> {
        if (!"Bearer t".equals(token)) throw new ApiFailure(401, "x");
        return new Authenticator.Identity(UUID.randomUUID(), "t");
    };

    /** O service nulo não é tocado nestas rotas; close() chega a ele por último e falha, o que é esperado. */
    private static void stop(GameHttpServer server) {
        try { server.close(); } catch (RuntimeException expected) { /* service nulo */ }
    }
    private static GameHttpServer start(Authenticator auth) throws IOException {
        var server = new GameHttpServer(new InetSocketAddress("127.0.0.1", 0), null, auth, Set.of(), false, Clock.systemUTC());
        server.start();
        return server;
    }
    private static List<Socket> stall(int port, String partial) throws IOException { return stall(port, partial, GameHttpServer.WORKERS); }
    private static List<Socket> stall(int port, String partial, int count) throws IOException {
        var sockets = new ArrayList<Socket>();
        for (int i = 0; i < count; i++) {
            var socket = new Socket("127.0.0.1", port);
            socket.getOutputStream().write(partial.getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            sockets.add(socket);
        }
        return sockets;
    }
    private static void assertHealthAnswers(int port) throws Exception {
        long started = System.nanoTime();
        var response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/health"))
                .timeout(Duration.ofSeconds(2)).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(System.nanoTime() - started < 2_000_000_000L);
    }
    private static int post(int port, String contentType, String authorization, int length) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/v1/me/actions"))
                .timeout(Duration.ofSeconds(5)).header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofString("a".repeat(length)));
        if (authorization != null) builder.header("Authorization", authorization);
        return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString()).statusCode();
    }

    @Test void healthAnswersWhileFourSocketsStallMidHeaders() throws Exception {
        var server = start(DENY);
        List<Socket> sockets = List.of();
        try {
            sockets = stall(server.port(), "GET /v1/catalog HTTP/1.1\r\nHost: x\r\n");
            assertHealthAnswers(server.port());
        } finally {
            for (var socket : sockets) socket.close();
            stop(server);
        }
    }

    @Test void healthAnswersWhileFourPostsStallMidBody() throws Exception {
        var server = start(ALLOW);
        List<Socket> sockets = List.of();
        try {
            sockets = stall(server.port(), "POST /v1/me/actions HTTP/1.1\r\nHost: x\r\nAuthorization: Bearer t\r\n"
                    + "Content-Type: application/json\r\nContent-Length: 100\r\n\r\n0123456789");
            assertHealthAnswers(server.port());
        } finally {
            for (var socket : sockets) socket.close();
            stop(server);
        }
    }

    @Test void unauthenticatedPostWithWrongContentTypeStill401() throws Exception {
        var server = start(DENY);
        try { assertEquals(401, post(server.port(), "text/plain", null, 5)); }
        finally { stop(server); }
    }

    @Test void oversizedBodyStill413AfterAuth() throws Exception {
        var server = start(ALLOW);
        try { assertEquals(413, post(server.port(), "application/json", "Bearer t", 20_000)); }
        finally { stop(server); }
    }

    @Test void healthAnswersWhileMoreSocketsThanWorkersAndQueueStall() throws Exception {
        var server = start(DENY);
        List<Socket> sockets = List.of();
        try {
            sockets = stall(server.port(), "GET /v1/catalog HTTP/1.1\r\nHost: x\r\n", GameHttpServer.WORKERS + GameHttpServer.QUEUE + 4);
            assertHealthAnswers(server.port());
        } finally {
            for (var socket : sockets) socket.close();
            stop(server);
        }
    }

    @Test void overloadBeyondWorkersPlusQueueAnswers503WithRetryAfterAndHealthStaysUp() throws Exception {
        var release = new CountDownLatch(1);
        var entered = new CountDownLatch(GameHttpServer.WORKERS);
        Authenticator slow = token -> {
            entered.countDown();
            try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            throw new ApiFailure(401, "x");
        };
        var server = start(slow);
        var client = HttpClient.newHttpClient();
        var busy = new ArrayList<CompletableFuture<HttpResponse<String>>>();
        try {
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + "/v1/request-id")).timeout(Duration.ofSeconds(10)).build();
            for (int i = 0; i < GameHttpServer.WORKERS + GameHttpServer.QUEUE; i++) busy.add(client.sendAsync(request, HttpResponse.BodyHandlers.ofString()));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            Thread.sleep(500);
            var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(503, response.statusCode());
            assertEquals("5", response.headers().firstValue("Retry-After").orElseThrow());
            assertHealthAnswers(server.port());
        } finally {
            release.countDown();
            for (var future : busy) future.get(10, TimeUnit.SECONDS);
            stop(server);
        }
    }

    @Test void malformedContentLengthIsRejectedWithoutReadingBody() throws Exception {
        var server = start(ALLOW);
        try (var socket = new Socket("127.0.0.1", server.port())) {
            socket.setSoTimeout(5000);
            socket.getOutputStream().write(("POST /v1/me/actions HTTP/1.1\r\nHost: x\r\nAuthorization: Bearer t\r\n"
                    + "Content-Type: application/json\r\nContent-Length: abc\r\nConnection: close\r\n\r\n{}").getBytes(StandardCharsets.US_ASCII));
            String status = new String(socket.getInputStream().readNBytes(12), StandardCharsets.US_ASCII);
            assertTrue(status.startsWith("HTTP/1.1 400"), status);
        } finally { stop(server); }
    }
}
