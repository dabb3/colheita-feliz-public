package br.com.colheitafeliz.server;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

/** O Auth do projeto verifica assinatura, emissor e sessão; não confiar em um JWT apenas decodificado. */
final class SupabaseAuthenticator implements Authenticator {
    private final URI endpoint;
    private final String publicKey;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    SupabaseAuthenticator(URI endpoint, String publicKey) { this.endpoint = endpoint; this.publicKey = publicKey; }

    @Override public Identity authenticate(String authorization) {
        if (authorization == null || !authorization.matches("Bearer [A-Za-z0-9_.-]{1,8192}"))
            throw new ApiFailure(401, "Entre na sua conta para continuar.");
        HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(5))
                .header("apikey", publicKey).header("Authorization", authorization).GET().build();
        try {
            var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (var body = response.body()) {
                if (response.statusCode() == 401 || response.statusCode() == 403 || response.statusCode() == 400)
                    throw new ApiFailure(401, "Sessão inválida ou expirada. Entre novamente.");
                if (response.statusCode() != 200) throw new ApiFailure(503, "Login temporariamente indisponível.");
                byte[] bytes = body.readNBytes(65_537);
                if (bytes.length > 65_536) throw new ApiFailure(503, "Resposta de autenticação inválida.");
                var user = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
                if (!user.has("aud") || user.get("aud").isJsonNull() || !"authenticated".equals(user.get("aud").getAsString())) throw new ApiFailure(401, "Sessão não autorizada.");
                if (!user.has("id") || user.get("id").isJsonNull()) throw new ApiFailure(503, "Resposta de autenticação inválida.");
                UUID id = UUID.fromString(user.get("id").getAsString());
                var metadata = user.getAsJsonObject("user_metadata");
                String name = metadata != null && metadata.has("full_name") && !metadata.get("full_name").isJsonNull()
                        ? metadata.get("full_name").getAsString() : "Fazendeiro";
                return new Identity(id, name);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiFailure(503, "Login interrompido. Tente novamente.");
        } catch (IOException e) { throw new ApiFailure(503, "Login temporariamente indisponível."); }
        catch (IllegalArgumentException | IllegalStateException | com.google.gson.JsonParseException e) { throw new ApiFailure(503, "Resposta de autenticação inválida."); }
    }
    @Override public void close() { client.close(); }
}
