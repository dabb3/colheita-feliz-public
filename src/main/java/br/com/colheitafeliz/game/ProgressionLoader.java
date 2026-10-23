package br.com.colheitafeliz.game;

import br.com.colheitafeliz.domain.player.ProgressionRules;
import com.google.gson.Gson;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class ProgressionLoader {
    private ProgressionLoader() {}

    public static ProgressionRules load() {
        InputStream stream = ProgressionLoader.class.getResourceAsStream("/data/progression.json");
        if (stream == null) throw new IllegalStateException("Missing progression.json");
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return new Gson().fromJson(reader, ProgressionRules.class);
        } catch (Exception e) {
            throw new IllegalStateException("Invalid progression data", e);
        }
    }
}
