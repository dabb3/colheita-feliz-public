package br.com.colheitafeliz.server;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AuthenticationTest {
    @Test void identityComesFromAuthenticatedUpstreamAndInvalidTokenFailsClosed() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        UUID id = UUID.randomUUID();
        server.createContext("/user", exchange -> {
            assertEquals("public-key", exchange.getRequestHeaders().getFirst("apikey"));
            boolean valid = "Bearer valid-token".equals(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = (valid ? "{\"id\":\"" + id + "\",\"aud\":\"authenticated\"}" : "{}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(valid ? 200 : 403, body.length);
            exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        try (var auth = new SupabaseAuthenticator(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/user"), "public-key")) {
            assertEquals(id, auth.authenticate("Bearer valid-token").id());
            assertEquals(401, assertThrows(ApiFailure.class, () -> auth.authenticate("Bearer fake-token")).status());
            assertEquals(401, assertThrows(ApiFailure.class, () -> auth.authenticate(null)).status());
        } finally { server.stop(0); }
    }

    @Test void unexpectedNameTypesFallBackToDefaultAndNeverFail() throws Exception {
        var metadata = new java.util.concurrent.atomic.AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        UUID id = UUID.randomUUID();
        server.createContext("/user", exchange -> {
            byte[] body = ("{\"id\":\"" + id + "\",\"aud\":\"authenticated\"," + metadata.get() + "}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        try (var auth = new SupabaseAuthenticator(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/user"), "public-key")) {
            for (String unexpected : new String[] {"\"user_metadata\":{\"full_name\":{\"x\":1}}", "\"user_metadata\":\"str\"",
                    "\"user_metadata\":{\"full_name\":7}", "\"user_metadata\":{\"full_name\":null}", "\"user_metadata\":{}", "\"x\":1"}) {
                metadata.set(unexpected);
                assertEquals("Fazendeiro", auth.authenticate("Bearer valid-token").name(), unexpected);
            }
            metadata.set("\"user_metadata\":{\"full_name\":\"Ana\"}");
            assertEquals("Ana", auth.authenticate("Bearer valid-token").name());
        } finally { server.stop(0); }
    }
}
