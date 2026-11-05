package br.com.colheitafeliz.server;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Roda com os mesmos flags de JVM do deploy (applicationDefaultJvmArgs, via tarefa capacityTest):
 * maxConnections=48 e maxReqTime=10 são o que de fato limita conexões lentas, porque o proxy do Fly
 * não documenta timeout de leitura e o fly.toml não define hard_limit.
 */
@Tag("capacity")
class ProductionLimitsTest {
    private static final Authenticator ALLOW = token -> new Authenticator.Identity(UUID.randomUUID(), "t");
    private static final String SLOW_HEADERS = "GET /v1/catalog HTTP/1.1\r\nHost: x\r\n";
    private static final String SLOW_BODY = "POST /v1/me/actions HTTP/1.1\r\nHost: x\r\nAuthorization: Bearer t\r\n"
            + "Content-Type: application/json\r\nContent-Length: 100\r\n\r\n{\"a\":";

    @Test void runsWithDeployFlags() {
        assertEquals("48", System.getProperty("jdk.httpserver.maxConnections"));
        assertEquals("10", System.getProperty("sun.net.httpserver.maxReqTime"));
    }

    @Test void slowConnectionsBelowTheLimitKeepHealthUpAndAreClosedInBoundedTime() throws Exception {
        var server = start();
        var sockets = new ArrayList<Socket>();
        try {
            long started = System.nanoTime();
            sockets.addAll(stall(server.port(), SLOW_HEADERS, 20));
            sockets.addAll(stall(server.port(), SLOW_BODY, 20));
            assertEquals(200, health(server.port()));
            for (var socket : sockets) assertClosedByServer(socket, started, 15_000);
            assertEquals(200, health(server.port()));
        } finally { close(sockets); stop(server); }
    }

    @Test void saturationAboveTheConnectionLimitRecoversWithinMaxReqTime() throws Exception {
        var server = start();
        var sockets = new ArrayList<Socket>();
        try {
            long started = System.nanoTime();
            sockets.addAll(stall(server.port(), SLOW_HEADERS, 32));
            sockets.addAll(stall(server.port(), SLOW_BODY, 32));
            for (var socket : sockets) assertClosedByServer(socket, started, 15_000);
            assertEquals(200, health(server.port()));
        } finally { close(sockets); stop(server); }
    }

    @Test void slowButLegitimatePostIsReadAndAnswered() throws Exception {
        var server = start();
        byte[] body = "{\"campo\":\"x\"}".getBytes(StandardCharsets.UTF_8);
        try (var socket = new Socket("127.0.0.1", server.port())) {
            socket.setSoTimeout(10_000);
            OutputStream out = socket.getOutputStream();
            out.write(("POST /v1/me/actions HTTP/1.1\r\nHost: x\r\nAuthorization: Bearer t\r\nContent-Type: application/json\r\n"
                    + "Content-Length: " + body.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            for (byte b : body) { out.write(b); out.flush(); Thread.sleep(200); }
            String status = new String(socket.getInputStream().readNBytes(12), StandardCharsets.US_ASCII);
            assertTrue(status.startsWith("HTTP/1.1 400"), status);
        } finally { stop(server); }
    }

    private static GameHttpServer start() throws IOException {
        var server = new GameHttpServer(new InetSocketAddress("127.0.0.1", 0), null, ALLOW, Set.of(), false, Clock.systemUTC());
        server.start();
        return server;
    }
    /** O service nulo não é tocado nestas rotas; close() chega a ele por último e falha, o que é esperado. */
    private static void stop(GameHttpServer server) {
        try { server.close(); } catch (RuntimeException expected) { /* service nulo */ }
    }
    private static List<Socket> stall(int port, String partial, int count) throws IOException {
        var sockets = new ArrayList<Socket>();
        for (int i = 0; i < count; i++) {
            var socket = new Socket("127.0.0.1", port);
            sockets.add(socket);
            try { socket.getOutputStream().write(partial.getBytes(StandardCharsets.US_ASCII)); socket.getOutputStream().flush(); }
            catch (SocketException rejectedAtLimit) { socket.close(); /* acima de maxConnections o servidor fecha na hora */ }
        }
        return sockets;
    }
    private static void assertClosedByServer(Socket socket, long startedNanos, long limitMillis) throws IOException {
        long remaining = limitMillis - (System.nanoTime() - startedNanos) / 1_000_000;
        assertTrue(remaining > 0, "conexão lenta ainda aberta após " + limitMillis + " ms");
        socket.setSoTimeout((int) remaining);
        boolean closed;
        try { socket.getInputStream().readAllBytes(); closed = true; }
        catch (SocketTimeoutException stillOpen) { closed = false; }
        catch (SocketException reset) { closed = true; /* fechada pelo servidor */ }
        assertTrue(closed, "conexão lenta ainda aberta após " + limitMillis + " ms");
    }
    private static int health(int port) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/health"))
                .timeout(Duration.ofSeconds(2)).build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }
    private static void close(List<Socket> sockets) throws IOException {
        for (var socket : sockets) socket.close();
    }
}
