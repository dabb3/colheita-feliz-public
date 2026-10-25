package br.com.colheitafeliz.game;

import br.com.colheitafeliz.domain.crop.CropCatalog;
import br.com.colheitafeliz.audio.AudioManager;
import br.com.colheitafeliz.input.InputController;
import br.com.colheitafeliz.persistence.JsonSaveRepository;
import br.com.colheitafeliz.render.FarmRenderer;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Cursor;
import com.badlogic.gdx.graphics.Pixmap;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;

public final class FarmGame extends ApplicationAdapter {
    private GameSession session;
    private FarmRenderer renderer;
    private InputController input;
    private AudioManager audio;
    private long lastPeriodicSave;
    private Cursor cursor;

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
        cursor = arrowCursor();
        Gdx.graphics.setCursor(cursor);
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

    private boolean save() {
        boolean saved = session.trySave();
        if (!saved) Gdx.app.error("save", session.state().message());
        return saved;
    }

    @Override public void resize(int width, int height) {
        if (renderer != null) renderer.viewport().update(width, height, true);
    }

    @Override public void dispose() {
        if (session != null) save();
        if (audio != null) audio.close();
        if (renderer != null) renderer.close();
        if (cursor != null) cursor.dispose();
    }

    /** Seta clássica no lugar do cursor do sistema: recorta o desenho e reduz em etapas até 32 × 32. */
    private static Cursor arrowCursor() {
        Pixmap source = new Pixmap(Gdx.files.internal("classic/icons/cursor-action.png"));
        int minX = source.getWidth(), minY = source.getHeight(), maxX = 0, maxY = 0;
        for (int y = 0; y < source.getHeight(); y++)
            for (int x = 0; x < source.getWidth(); x++)
                if ((source.getPixel(x, y) & 0xff) > 20) {
                    minX = Math.min(minX, x); minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x); maxY = Math.max(maxY, y);
                }
        int side = Math.max(maxX - minX, maxY - minY) + 1;
        Pixmap image = new Pixmap(side, side, Pixmap.Format.RGBA8888);
        image.drawPixmap(source, minX, minY, maxX - minX + 1, maxY - minY + 1, 0, 0, maxX - minX + 1, maxY - minY + 1);
        source.dispose();
        while (image.getWidth() > 32) {
            int next = Math.max(32, image.getWidth() / 2);
            Pixmap half = new Pixmap(next, next, Pixmap.Format.RGBA8888);
            half.setFilter(Pixmap.Filter.BiLinear);
            half.drawPixmap(image, 0, 0, image.getWidth(), image.getHeight(), 0, 0, next, next);
            image.dispose();
            image = half;
        }
        Cursor arrow = Gdx.graphics.newCursor(image, 1, 1);
        image.dispose();
        return arrow;
    }
}
