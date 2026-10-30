package br.com.colheitafeliz.desktop;

import br.com.colheitafeliz.game.CropCatalogLoader;
import br.com.colheitafeliz.persistence.GameSave;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;

public final class DesktopLauncher {
    private DesktopLauncher() {}

    public static void main(String[] args) throws IOException {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Colheita Feliz - Java");
        config.setWindowedMode(1280, 720);
        config.setForegroundFPS(60);
        Path path = Path.of(System.getenv().getOrDefault("COLHEITA_SAVE_PATH", GameSave.DEFAULT_PATH));
        try (var running = SaveStartup.open(path, Clock.systemUTC(), CropCatalogLoader.load(), SaveRecoveryDialog::choose)) {
            if (running == null) return;
            new Lwjgl3Application(new FarmGame(running.session()), config);
        }
    }
}
