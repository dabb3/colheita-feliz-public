package br.com.colheitafeliz.server;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClientAddressTest {
    private static InetAddress peer() throws IOException { return InetAddress.getByAddress(new byte[] {10, 0, 0, 1}); }

    @Test void trustOffUsesPeer() throws Exception {
        assertEquals("10.0.0.1", GameHttpServer.clientAddress("203.0.113.9", peer(), false));
    }
    @Test void trustOnUsesValidIpv4() throws Exception {
        assertEquals("203.0.113.9", GameHttpServer.clientAddress("203.0.113.9", peer(), true));
    }
    @Test void trustOnNormalizesIpv6() throws Exception {
        assertEquals("2001:db8:0:0:0:0:0:1", GameHttpServer.clientAddress("2001:DB8::1", peer(), true));
    }
    @Test void malformedFallsBackToPeer() throws Exception {
        for (String value : new String[] {"evil.example", "", "1.2.3.4, 5.6.7.8", " 1.2.3.4", null, "1234", "::::::::::::", "1.2.3", "dead.beef", "999.1.1.1"})
            assertEquals("10.0.0.1", GameHttpServer.clientAddress(value, peer(), true), String.valueOf(value));
    }

    @Test void rateLimitIsPerFlyClient() throws Exception {
        Authenticator deny = token -> { throw new ApiFailure(401, "x"); };
        var server = new GameHttpServer(new InetSocketAddress("127.0.0.1", 0), null, deny, Set.of(), false, Clock.systemUTC(), true);
        server.start();
        try {
            var client = HttpClient.newHttpClient();
            for (int i = 0; i < 300; i++) assertEquals(401, status(client, server.port(), "198.51.100.1"), "request " + i);
            assertEquals(429, status(client, server.port(), "198.51.100.1"));
            assertEquals(401, status(client, server.port(), "198.51.100.2"));
        } finally {
            try { server.close(); } catch (RuntimeException expected) { /* service nulo */ }
        }
    }
    private static int status(HttpClient client, int port, String flyIp) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/nao-existe")).header("Fly-Client-IP", flyIp).build(),
                HttpResponse.BodyHandlers.ofString()).statusCode();
    }
}
