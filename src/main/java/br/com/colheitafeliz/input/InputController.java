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
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;

public final class InputController extends InputAdapter {
    private final FarmRenderer renderer;
    private final GameSession session;
    private final AudioManager audio;
    private final Runnable save;
    private IsoRenderer.GridPosition hovered;

    public InputController(FarmRenderer renderer, GameSession session, AudioManager audio, Runnable save) {
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
        return mouseMoved(screenX, screenY);
    }

    @Override public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        if (button != Input.Buttons.LEFT) return false;
        GameState state = session.state();
        Vector3 p = world(screenX, screenY);
        if (renderer.hud().bannerVisible()) { renderer.hud().dismissBanner(); return true; }
        if (renderer.missions().isOpen()) { renderer.missions().click(p.x, p.y); return true; }
        if (renderer.marketPanel().handles(p.x, p.y)) {
            Snapshot before = new Snapshot(state);
            ActionResult result = renderer.marketPanel().click(p.x, p.y, state);
            if (result != null) show(result, renderer.marketPanel().selling() ? Cue.SELL : Cue.BUY, before,
                    p.x, p.y + 20, null, 0);
            return true;
        }
        Hud.Shortcut shortcut = renderer.hud().shortcutHit(p.x, p.y);
        if (shortcut != null) {
            switch (shortcut) {
                case MISSIONS -> renderer.missions().toggle();
                case SHOP -> renderer.marketPanel().showShop();
                case DEPOSIT -> renderer.marketPanel().showSell();
            }
            audio.play(Cue.PLOW);
            return true;
        }
        Tool tool = renderer.toolbar().hit(p.x, p.y);
        if (tool != null) {
            renderer.toolbar().picked(tool, state.tool());
            state.selectTool(tool);
            save.run();
            return true;
        }
        int animal = renderer.animalAt(p.x, p.y);
        if (animal >= 0) {
            Snapshot before = new Snapshot(state);
            ActionResult result = state.tendAnimal(animal);
            show(result, state.lastCollected() > 0 ? Cue.HARVEST : Cue.PLANT, before, p.x, p.y + 40, null, 0);
            return true;
        }
        CropType seed = state.tool() == Tool.SEED && renderer.toolbar().seedsOpen()
                ? renderer.toolbar().seedHit(p.x, p.y, state.catalog().all()) : null;
        if (seed != null) {
            ActionResult result = state.selectSeed(seed);
            if (!result.success()) renderer.failure(result.message());
            else renderer.toolbar().closeSeeds();
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
    private record Snapshot(int level, int xp, int coins) {
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
        int xpGained = levelUp ? 0 : state.progress().currentXp() - before.xp();
        renderer.reward(x, y, harvested, produced, xpGained, state.wallet().coins() - before.coins());
        if (cue == Cue.PLOW && state.lastTreasureCoins() > 0) renderer.treasure(state.lastTreasureCoins());
        if (levelUp) renderer.levelUp(state);
        audio.play(levelUp ? Cue.LEVEL_UP : cue);
        save.run();
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
            }
            case Input.Keys.F3 -> renderer.toggleDebug();
            case Input.Keys.M -> {
                state.setMusicEnabled(!state.musicEnabled());
                audio.applySettings();
                save.run();
            }
            case Input.Keys.N -> {
                state.setEffectsEnabled(!state.effectsEnabled());
                save.run();
            }
            case Input.Keys.T -> session.clock().setSpeed(session.clock().speed() == 1 ? 60 : 1);
            case Input.Keys.S -> save.run();
            default -> { return false; }
        }
        return true;
    }
}
