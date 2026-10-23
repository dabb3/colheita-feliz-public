package br.com.colheitafeliz.game;

import br.com.colheitafeliz.domain.crop.CropCatalog;
import br.com.colheitafeliz.domain.crop.CropType;
import com.google.gson.Gson;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class CropCatalogLoader {
    private CropCatalogLoader() {}

    public static CropCatalog load() {
        InputStream stream = CropCatalogLoader.class.getResourceAsStream("/data/crops.json");
        if (stream == null) throw new IllegalStateException("Missing crops.json");
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return new CropCatalog(Arrays.asList(new Gson().fromJson(reader, CropType[].class)));
        } catch (Exception e) {
            throw new IllegalStateException("Invalid crop catalog", e);
        }
    }
}
