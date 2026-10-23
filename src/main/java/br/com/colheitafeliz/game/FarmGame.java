package br.com.colheitafeliz.game;

import br.com.colheitafeliz.domain.crop.CropCatalog;
import br.com.colheitafeliz.audio.AudioManager;
import br.com.colheitafeliz.input.InputController;
import br.com.colheitafeliz.persistence.JsonSaveRepository;
import br.com.colheitafeliz.render.FarmRenderer;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;

public final class FarmGame extends ApplicationAdapter {
    private GameSession session;
    private FarmRenderer renderer;
    private InputController input;
    private AudioManager audio;
    private long lastPeriodicSave;

    @Override public void create() {
        CropCatalog catalog = CropCatalogLoader.load();
        try {
            session = GameSession.open(Clock.systemUTC(), catalog,
                    new JsonSaveRepository(Path.of(System.getenv().getOrDefault(
                            "COLHEITA_SAVE_PATH", "save/game.json"))));
        } catch (IOException e) {
            throw new IllegalStateException("Could not load saved game", e);
        }
        if (session.fresh()) session.state().startNewFarm();
        renderer = new FarmRenderer(catalog);
        audio = new AudioManager(session.state());
        input = new InputController(renderer, session, audio, this::save);
        renderer.viewport().update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true);
        Gdx.input.setInputProcessor(input);
        lastPeriodicSave = System.nanoTime();
    }

    @Override public void render() {
        if (session.state().refresh()) {
            renderer.pestAlert(session.state().lastOutbreak());
            save();
        }
        renderer.draw(session.state(), input.hovered(), session.clock().speed());
        if (System.nanoTime() - lastPeriodicSave >= 30_000_000_000L) {
            save();
            lastPeriodicSave = System.nanoTime();
        }
    }

    private void save() {
        try {
            session.save();
        } catch (IOException e) {
            session.state().message("Erro ao salvar: " + e.getMessage());
            Gdx.app.error("save", "Could not save game", e);
        }
    }

    @Override public void resize(int width, int height) {
        if (renderer != null) renderer.viewport().update(width, height, true);
    }

    @Override public void dispose() {
        if (session != null) save();
        if (audio != null) audio.close();
        if (renderer != null) renderer.close();
    }
}
