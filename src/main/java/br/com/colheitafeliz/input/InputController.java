package br.com.colheitafeliz.input;

import br.com.colheitafeliz.audio.AudioManager;
import br.com.colheitafeliz.audio.AudioManager.Cue;
import br.com.colheitafeliz.domain.crop.CropType;
import br.com.colheitafeliz.domain.farm.FarmTile;
import br.com.colheitafeliz.game.ActionResult;
import br.com.colheitafeliz.game.GameSession;
import br.com.colheitafeliz.game.GameState;
import br.com.colheitafeliz.game.Tool;
import br.com.colheitafeliz.render.FarmRenderer;
import br.com.colheitafeliz.render.IsoRenderer;
import br.com.colheitafeliz.ui.Hud;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import java.util.function.BooleanSupplier;

public final class InputController extends InputAdapter {
    private final FarmRenderer renderer;
    private final GameSession session;
    private final AudioManager audio;
    private final BooleanSupplier save;
    private IsoRenderer.GridPosition hovered;
    private boolean draggingMarketScroll;

    public InputController(FarmRenderer renderer, GameSession session, AudioManager audio, BooleanSupplier save) {
        this.renderer = renderer;
        this.session = session;
        this.audio = audio;
        this.save = save;
    }

    public IsoRenderer.GridPosition hovered() { return hovered; }
    private Vector3 world(int x, int y) { return renderer.viewport().unproject(new Vector3(x, y, 0)); }

    @Override public boolean mouseMoved(int screenX, int screenY) {
        Vector3 p = world(screenX, screenY);
        renderer.pointer(p.x, p.y);
        hovered = renderer.iso().screenToGrid(p.x, p.y);
        return false;
    }

    @Override public boolean touchDragged(int screenX, int screenY, int pointer) {
        if (draggingMarketScroll) {
            Vector3 p = world(screenX, screenY);
            renderer.pointer(p.x, p.y);
            renderer.marketPanel().dragScroll(p.y, session.state());
            return true;
        }
        return mouseMoved(screenX, screenY);
    }

    @Override public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        if (!draggingMarketScroll) return false;
        draggingMarketScroll = false;
        renderer.marketPanel().endScrollDrag();
        return true;
    }

    @Override public boolean scrolled(float amountX, float amountY) {
        Vector3 p = world(Gdx.input.getX(), Gdx.input.getY());
        return renderer.marketPanel().scroll(p.x, p.y, amountY, session.state());
    }

    @Override public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        if (button != Input.Buttons.LEFT) return false;
        GameState state = session.state();
        Vector3 p = world(screenX, screenY);
        if (renderer.hud().bannerVisible()) { renderer.hud().dismissBanner(); return true; }
        if (renderer.missions().isOpen()) { renderer.missions().click(p.x, p.y); return true; }
        if (renderer.marketPanel().handles(p.x, p.y)) {
            if (renderer.marketPanel().beginScrollDrag(p.x, p.y, state)) {
                draggingMarketScroll = true;
                return true;
            }
            Snapshot before = new Snapshot(state);
            ActionResult result = renderer.marketPanel().click(p.x, p.y, state);
            if (result != null) show(result, renderer.marketPanel().selling() ? Cue.SELL : Cue.BUY, before,
                    p.x, p.y + 20, null, 0);
            return true;
        }
        Hud.Shortcut shortcut = renderer.hud().shortcutHit(p.x, p.y);
        if (renderer.hud().panelOpen() && shortcut != Hud.Shortcut.ALERTS && shortcut != Hud.Shortcut.SETTINGS) {
            Hud.Setting setting = renderer.hud().panelClick(p.x, p.y);
            if (setting != null) apply(setting);
            return true;
        }
        if (shortcut != null) {
            switch (shortcut) {
                case ALERTS -> renderer.hud().toggleAlerts();
                case MISSIONS -> renderer.missions().toggle();
                case SHOP -> renderer.marketPanel().showShop();
                case DEPOSIT -> renderer.marketPanel().showSell();
                case SETTINGS -> renderer.hud().toggleSettings();
            }
            audio.play(Cue.PLOW);
            return true;
        }
        Tool tool = renderer.toolbar().hit(p.x, p.y);
        if (tool != null) {
            renderer.toolbar().picked(tool, state.tool());
            state.selectTool(tool);
            save.getAsBoolean();
            return true;
        }
        String animal = renderer.animalTypeAt(p.x, p.y);
        if (animal != null) {
            Snapshot before = new Snapshot(state);
            ActionResult result = state.tendAnimal(animal);
            show(result, state.lastCollected() > 0 ? Cue.HARVEST : Cue.PLANT, before, p.x, p.y + 40, null, 0);
            return true;
        }
        CropType seed = state.tool() == Tool.SEED && renderer.toolbar().trayOpen()
                ? renderer.toolbar().seedHit(p.x, p.y, state.catalog().all()) : null;
        if (seed != null) {
            ActionResult result = state.selectSeed(seed);
            if (!result.success()) renderer.failure(result.message());
            else renderer.toolbar().closeTray();
            return true;
        }
        var fertilizer = state.tool() == Tool.FERTILIZER && renderer.toolbar().trayOpen()
                ? renderer.toolbar().fertilizerHit(p.x, p.y, state.fertilizers()) : null;
        if (fertilizer != null) {
            state.selectFertilizer(fertilizer.id());
            renderer.toolbar().closeTray();
            return true;
        }
        IsoRenderer.GridPosition tile = renderer.iso().screenToGrid(p.x, p.y);
        if (!state.farm().contains(tile.column(), tile.row())) return true;
        Tool actionTool = state.tool();
        Snapshot before = new Snapshot(state);
        boolean lockedPlot = !state.land().isUnlocked(tile.column(), tile.row());
        if (!lockedPlot && actionTool == Tool.CURSOR) return true;
        FarmTile farmTile = state.farm().tile(tile.column(), tile.row());
        CropType crop = farmTile.crop() == null ? null : farmTile.crop().type();
        ActionResult result = lockedPlot ? state.buyPlot(tile.column(), tile.row())
                : state.act(tile.column(), tile.row());
        Cue cue = lockedPlot ? Cue.BUY : switch (actionTool) {
            case HAND -> Cue.HARVEST;
            case CURSOR -> Cue.PLOW;
            case HOE -> Cue.PLOW;
            case WATER -> Cue.WATER;
            case SEED -> Cue.PLANT;
            case WEEDER, PESTICIDE -> Cue.WEED;
            case FERTILIZER -> Cue.PLANT;
        };
        Vector2 center = renderer.tileCenter(tile.column(), tile.row());
        boolean harvested = !lockedPlot && actionTool == Tool.HAND && result.success();
        show(result, cue, before, center.x, center.y + 70, harvested ? crop : null,
                harvested ? state.lastHarvestAmount() : 0);
        return true;
    }

    /** Valores antes da ação, para calcular o que o jogador ganhou. */
    private record Snapshot(int level, java.math.BigDecimal xp, java.math.BigDecimal coins) {
        Snapshot(GameState state) {
            this(state.progress().level(), state.progress().currentXp(), state.wallet().coins());
        }
    }

    private void show(ActionResult result, Cue cue, Snapshot before, float x, float y, CropType harvested, int produced) {
        GameState state = session.state();
        if (!result.success()) {
            renderer.failure(result.message());
            return;
        }
        boolean levelUp = state.progress().level() > before.level();
        java.math.BigDecimal xpGained = levelUp ? java.math.BigDecimal.ZERO : state.progress().currentXp().subtract(before.xp());
        renderer.reward(x, y, harvested, produced, xpGained, state.wallet().coins().subtract(before.coins()));
        if (levelUp) renderer.levelUp(state);
        audio.play(levelUp ? Cue.LEVEL_UP : cue);
        save.getAsBoolean();
    }

    @Override public boolean keyDown(int keycode) {
        GameState state = session.state();
        if (keycode >= Input.Keys.NUM_1 && keycode < Input.Keys.NUM_1 + Tool.BAR.size()) {
            Tool tool = Tool.BAR.get(keycode - Input.Keys.NUM_1);
            renderer.toolbar().picked(tool, state.tool());
            state.selectTool(tool);
            return true;
        }
        switch (keycode) {
            case Input.Keys.B -> { renderer.missions().close(); renderer.marketPanel().showShop(); }
            case Input.Keys.V -> { renderer.missions().close(); renderer.marketPanel().showSell(); }
            case Input.Keys.Q -> { renderer.marketPanel().close(); renderer.missions().toggle(); }
            case Input.Keys.ESCAPE -> {
                renderer.marketPanel().close();
                renderer.missions().close();
                renderer.hud().dismissBanner();
                renderer.hud().closePanel();
            }
            case Input.Keys.F3 -> apply(Hud.Setting.DEBUG);
            case Input.Keys.M -> apply(Hud.Setting.MUSIC);
            case Input.Keys.N -> apply(Hud.Setting.EFFECTS);
            case Input.Keys.T -> apply(Hud.Setting.SPEED);
            case Input.Keys.S -> apply(Hud.Setting.SAVE);
            default -> { return false; }
        }
        return true;
    }

    private void apply(Hud.Setting setting) {
        switch (setting) {
            case MUSIC -> {
                session.state().setMusicEnabled(!session.state().musicEnabled());
                audio.applySettings();
                save.getAsBoolean();
            }
            case EFFECTS -> {
                session.state().setEffectsEnabled(!session.state().effectsEnabled());
                save.getAsBoolean();
            }
            case SPEED -> session.clock().setSpeed(session.clock().speed() == 1 ? 60 : 1);
            case DEBUG -> renderer.toggleDebug();
            case SAVE -> {
                if (save.getAsBoolean()) renderer.hud().toast(null, "", "Jogo salvo.", Hud.ToastKind.NORMAL);
                else renderer.failure(session.state().message());
            }
        }
    }
}
