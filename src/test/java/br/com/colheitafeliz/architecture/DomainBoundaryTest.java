package br.com.colheitafeliz.architecture;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Source guard for explicit framework and outer-layer references, not a Java parser. */
class DomainBoundaryTest {
    private static final Pattern OUTER_REFERENCE = Pattern.compile(
            "\\b(?:com\\.badlogic\\.gdx|com\\.google\\.gson|"
                    + "br\\.com\\.colheitafeliz\\.(?:game|persistence|render|ui|input|audio|desktop))\\.");

    @Test void domainDoesNotDependOnFrameworksOrOuterLayers() throws IOException {
        Path domain = Path.of("src/main/java/br/com/colheitafeliz/domain");
        try (var files = Files.walk(domain)) {
            var sources = files.filter(file -> file.toString().endsWith(".java")).toList();
            assertFalse(sources.isEmpty(), "The source boundary must not pass on an empty directory");
            for (Path file : sources) {
                assertFalse(OUTER_REFERENCE.matcher(Files.readString(file)).find(),
                        () -> "Domain references an outer layer: " + file);
            }
        }
    }

    @Test void guardDetectsImportsAndFullyQualifiedReferences() {
        for (String source : new String[]{
                "import com.badlogic.gdx.Gdx;", "import static com.google.gson.Gson.*;",
                "br.com.colheitafeliz.persistence.SaveRepository repository;",
                "import br.com.colheitafeliz.game.GameState;"}) {
            assertTrue(OUTER_REFERENCE.matcher(source).find(), source);
        }
        assertFalse(OUTER_REFERENCE.matcher("import java.time.Instant;\n"
                + "import br.com.colheitafeliz.domain.crop.Crop;").find());
    }
}
