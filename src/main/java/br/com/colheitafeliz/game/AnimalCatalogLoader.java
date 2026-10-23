package br.com.colheitafeliz.game;

import br.com.colheitafeliz.domain.animal.AnimalType;
import com.google.gson.Gson;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

public final class AnimalCatalogLoader {
    private AnimalCatalogLoader() {}

    public static List<AnimalType> load() {
        InputStream stream = AnimalCatalogLoader.class.getResourceAsStream("/data/animals.json");
        if (stream == null) throw new IllegalStateException("Missing animals.json");
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return List.copyOf(Arrays.asList(new Gson().fromJson(reader, AnimalType[].class)));
        } catch (Exception e) {
            throw new IllegalStateException("Invalid animal catalog", e);
        }
    }
}
