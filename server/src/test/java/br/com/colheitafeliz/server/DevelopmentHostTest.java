package br.com.colheitafeliz.server;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DevelopmentHostTest {
    private static String health(int port, String host) throws Exception {
        try (var socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(5000);
            socket.getOutputStream().write(("GET /health HTTP/1.1\r\n" + (host == null ? "" : "Host: " + host + "\r\n") + "Connection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            return new String(socket.getInputStream().readAllBytes(), StandardCharsets.US_ASCII).lines().findFirst().orElse("");
        }
    }
    private static void check(boolean development, String host, int status) throws Exception {
        Authenticator deny = token -> { throw new ApiFailure(401, "x"); };
        var server = new GameHttpServer(new InetSocketAddress("127.0.0.1", 0), null, deny, Set.of(), development, Clock.systemUTC());
        server.start();
        try { assertTrue(health(server.port(), host.replace("P", "" + server.port())).startsWith("HTTP/1.1 " + status), host); }
        finally { try { server.close(); } catch (RuntimeException expected) { /* service nulo */ } }
    }

    @Test void developmentAllowsLoopbackHosts() throws Exception {
        for (String host : new String[] {"127.0.0.1:P", "localhost:P", "[::1]:P", "localhost", "[::1]"}) check(true, host, 200);
    }
    @Test void developmentRejectsOtherHostsWith421() throws Exception {
        for (String host : new String[] {"evil.example:P", "evil.example", "127.0.0.1.evil.example:P", "[2001:db8::1]:P"}) check(true, host, 421);
    }
    @Test void productionDoesNotRestrictHost() throws Exception {
        check(false, "evil.example", 200);
    }
    @Test void developmentWithoutHostHeaderIsNotServed() throws Exception {
        var server = new GameHttpServer(new InetSocketAddress("127.0.0.1", 0), null, token -> { throw new ApiFailure(401, "x"); }, Set.of(), true, Clock.systemUTC());
        server.start();
        try { assertFalse(health(server.port(), null).startsWith("HTTP/1.1 200")); }
        finally { try { server.close(); } catch (RuntimeException expected) { /* service nulo */ } }
    }
}
