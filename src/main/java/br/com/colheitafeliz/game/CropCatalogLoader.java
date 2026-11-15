package br.com.colheitafeliz.game;

import br.com.colheitafeliz.domain.crop.CropCatalog;
import br.com.colheitafeliz.domain.crop.CropType;
import com.google.gson.Gson;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import com.google.gson.JsonParser;
import br.com.colheitafeliz.domain.inventory.AcquisitionSource;

public final class CropCatalogLoader {
    private CropCatalogLoader() {}

    public static CropCatalog load() {
        InputStream stream = CropCatalogLoader.class.getResourceAsStream("/data/crops.json");
        if (stream == null) throw new IllegalStateException("Missing crops.json");
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return load(reader);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Invalid crop catalog", e);
        }
    }

    /** Lê um catálogo em JSON; qualquer linha inválida recusa o catálogo inteiro, sem cultura padrão. */
    static CropCatalog load(java.io.Reader reader) {
        try {
            var json = JsonParser.parseReader(reader);
            if (!json.isJsonArray()) throw new IllegalArgumentException("Crop catalog must be an array");
            for (var item : json.getAsJsonArray()) {
                if (!item.isJsonObject()) throw new IllegalArgumentException("Invalid crop entry");
                var object = item.getAsJsonObject();
                if (object.has("acquisitionSource")) {
                    var source = object.get("acquisitionSource");
                    if (!source.isJsonPrimitive() || !source.getAsJsonPrimitive().isString())
                        throw new IllegalArgumentException("Invalid acquisition source");
                    AcquisitionSource.valueOf(source.getAsString());
                }
            }
            CropType[] crops = new Gson().fromJson(json, CropType[].class);
            if (crops == null) throw new IllegalArgumentException("Empty crop catalog");
            return new CropCatalog(Arrays.asList(crops));
        } catch (RuntimeException e) {
            throw new IllegalStateException("Invalid crop catalog", e);
        }
    }
}
