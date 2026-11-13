package br.com.colheitafeliz.server;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DiagnosticsTest {
    @Test void diagnosticAuthenticatesWithoutLoadingFarmOrExposingIdentity() throws Exception {
        var clock = Clock.fixed(Instant.parse("2026-11-13T12:00:00Z"), ZoneOffset.UTC);
        Authenticator auth = token -> {
            if (!"Bearer test".equals(token)) throw new ApiFailure(401, "Sessão inválida.");
            return new Authenticator.Identity(UUID.fromString("00000000-0000-0000-0000-000000000001"), "Private Name");
        };
        // A null service makes any accidental farm, database or receipt access fail this test.
        var server = new GameHttpServer(new InetSocketAddress("127.0.0.1", 0), null, auth, Set.of("https://game.example"), false, clock);
        server.start();
        try (var client = HttpClient.newHttpClient()) {
            var uri = URI.create("http://127.0.0.1:" + server.port() + "/v1/diagnostics");
            var response = client.send(HttpRequest.newBuilder(uri).header("Origin", "https://game.example").header("Authorization", "Bearer test").GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            assertEquals("{\"ok\":true,\"serverTime\":\"2026-11-13T12:00:00Z\",\"originAllowed\":true}", response.body());
            assertEquals("https://game.example", response.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());
            assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
            var preflight = client.send(HttpRequest.newBuilder(uri).header("Origin", "https://game.example").header("Access-Control-Request-Method", "GET").header("Access-Control-Request-Headers", "authorization").method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.discarding());
            assertEquals(204, preflight.statusCode());
            assertTrue(preflight.headers().firstValue("Access-Control-Allow-Headers").orElseThrow().contains("Authorization"));
            var direct = client.send(HttpRequest.newBuilder(uri).header("Authorization", "Bearer test").GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, direct.statusCode());
            assertTrue(direct.body().contains("\"originAllowed\":false"));
            assertEquals(401, client.send(HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.discarding()).statusCode());
            assertEquals(403, client.send(HttpRequest.newBuilder(uri).header("Origin", "https://bad.example").header("Authorization", "Bearer test").GET().build(), HttpResponse.BodyHandlers.discarding()).statusCode());
            assertNotEquals(200, client.send(HttpRequest.newBuilder(uri).header("Authorization", "Bearer test").POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.discarding()).statusCode());
        } finally {
            assertThrows(NullPointerException.class, server::close); // No service to close.
        }
    }
}
