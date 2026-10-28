package br.com.colheitafeliz.game;

import br.com.colheitafeliz.domain.crop.FertilizerType;
import com.google.gson.Gson;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

public final class FertilizerCatalogLoader {
    private FertilizerCatalogLoader() {}

    public static List<FertilizerType> load() {
        InputStream stream = FertilizerCatalogLoader.class.getResourceAsStream("/data/fertilizers.json");
        if (stream == null) throw new IllegalStateException("Missing fertilizers.json");
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return List.copyOf(Arrays.asList(new Gson().fromJson(reader, FertilizerType[].class)));
        } catch (Exception e) {
            throw new IllegalStateException("Invalid fertilizer catalog", e);
        }
    }
}
