package br.com.colheitafeliz.render;

import br.com.colheitafeliz.domain.crop.CropType;
import br.com.colheitafeliz.game.CropCatalogLoader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** O jogo quebra ao abrir se um asset carregado não existe; os testes de lógica não abrem texturas. */
class AssetPathsTest {
    private static final Path ASSETS = Path.of("assets");
    private static final Pattern ASSET_LITERAL = Pattern.compile("\"([^\"]+\\.(?:png|jpg|wav|mp3|ttf))\"");

    @Test void everyAssetNamedInTheCodeExists() throws IOException {
        List<String> missing = new ArrayList<>();
        try (var files = Files.walk(Path.of("src/main/java"))) {
            var sources = files.filter(file -> file.toString().endsWith(".java")).toList();
            assertFalse(sources.isEmpty(), "The asset guard must not pass on an empty directory");
            for (Path file : sources) {
                var literals = ASSET_LITERAL.matcher(Files.readString(file));
                while (literals.find()) {
                    String path = literals.group(1);
                    // O AudioManager prefixa "sounds/" nos efeitos .wav.
                    boolean sound = path.endsWith(".wav") && Files.exists(ASSETS.resolve("sounds").resolve(path));
                    if (!Files.exists(ASSETS.resolve(path)) && !sound)
                        missing.add(file.getFileName() + ": " + path);
                }
            }
        }
        assertEquals(List.of(), missing);
    }

    @Test void guardFindsFullAssetPathsButNotFragments() {
        var matcher = ASSET_LITERAL.matcher("load(\"classic/icons/xp.png\"); load(\"a_\" + i + \".png\");");
        assertTrue(matcher.find());
        assertEquals("classic/icons/xp.png", matcher.group(1));
        assertFalse(matcher.find());
    }

    @Test void everyCropInTheCatalogHasItsStagesAndSeed() {
        List<String> missing = new ArrayList<>();
        for (CropType type : CropCatalogLoader.load().all()) {
            for (int stage = 1; stage <= type.stages(); stage++)
                expect(GameAssets.cropPath(type.spriteStem(), stage), missing);
            expect(GameAssets.seedPath(type.spriteStem()), missing);
        }
        assertEquals(List.of(), missing);
    }

    @Test void everyWeedAndInsectStageExists() {
        List<String> missing = new ArrayList<>();
        for (int count = 1; count <= GameAssets.WEED_STAGES; count++) expect(GameAssets.weedPath(count), missing);
        for (int count = 1; count <= GameAssets.INSECT_STAGES; count++) expect(GameAssets.insectPath(count), missing);
        assertEquals(List.of(), missing);
    }

    private static void expect(String path, List<String> missing) {
        if (!Files.exists(ASSETS.resolve(path))) missing.add(path);
    }
}
