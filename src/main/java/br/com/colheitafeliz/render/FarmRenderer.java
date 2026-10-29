package br.com.colheitafeliz.render;

import br.com.colheitafeliz.domain.animal.Animal;
import br.com.colheitafeliz.domain.crop.CropCatalog;
import br.com.colheitafeliz.domain.crop.CropType;
import br.com.colheitafeliz.domain.farm.FarmTile;
import br.com.colheitafeliz.domain.farm.PestState;
import br.com.colheitafeliz.domain.farm.PestSystem;
import br.com.colheitafeliz.domain.farm.TileState;
import br.com.colheitafeliz.game.DailyMissionBoard;
import br.com.colheitafeliz.game.GameState;
import br.com.colheitafeliz.game.GameText;
import br.com.colheitafeliz.game.LandExpansion;
import br.com.colheitafeliz.ui.Hud;
import br.com.colheitafeliz.ui.MarketPanel;
import br.com.colheitafeliz.ui.MissionsWindow;
import br.com.colheitafeliz.ui.Toolbar;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.FitViewport;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Desenha a fazenda (cena, canteiros e seus estados) e orquestra o HUD, a toolbar e as janelas. */
public final class FarmRenderer implements AutoCloseable {
    /** Tamanho de desenho dos tiles clássicos: o losango cobre a grade com uma leve sobreposição. */
    private static final float TILE_W = 188, TILE_H = 86, TILE_TOP = 42;

    private final GameAssets assets;
    private final UiSkin skin = new UiSkin();
    private final SpriteBatch batch = new SpriteBatch();
    private final ShapeRenderer shapes = new ShapeRenderer();
    private final IsoRenderer iso = new IsoRenderer();
    private final DebugOverlay debugOverlay = new DebugOverlay();
    private final Toolbar toolbar = new Toolbar();
    private final MarketPanel marketPanel;
    private final MissionsWindow missions = new MissionsWindow();
    private final Hud hud = new Hud();
    private final Scenery scenery;
    private final FitViewport viewport = new FitViewport(1600, 900);
    private boolean debugVisible;
    private float pointerX = -1, pointerY = -1;
    private Set<Integer> observedReadyPlots = new HashSet<>();
    private Set<String> observedCompletedMissions = new HashSet<>();
    private LocalDate observedMissionDay;
    private int observedNextPlotId = -1;
    private boolean observedPlotAvailable;
    private boolean notificationStateInitialized;

    public FarmRenderer(CropCatalog catalog) {
        assets = new GameAssets(catalog);
        marketPanel = new MarketPanel(catalog);
        scenery = new Scenery(assets, new Pen(assets.fencePost, assets.fenceRail, skin.groundShadow), skin.groundShadow);
    }

    public FitViewport viewport() { return viewport; }
    public IsoRenderer iso() { return iso; }
    public Toolbar toolbar() { return toolbar; }
    public MarketPanel marketPanel() { return marketPanel; }
    public MissionsWindow missions() { return missions; }
    public Hud hud() { return hud; }
    public GameAssets assets() { return assets; }
    public void toggleDebug() { debugVisible = !debugVisible; }
    public boolean debugVisible() { return debugVisible; }
    public void pointer(float x, float y) { pointerX = x; pointerY = y; }

    /** Índice do animal do jogador sob o ponto (o animal ou o ponto de produto), ou -1. */
    public int animalAt(float x, float y) { return scenery.animalAt(x, y); }
    public String animalTypeAt(float x, float y) { return scenery.animalTypeAt(x, y); }

    /** Centro do losango do canteiro, em coordenadas lógicas. */
    public Vector2 tileCenter(int col, int row) { return iso.gridToScreen(col, row); }

    // ---------- retorno das ações ----------

    public void failure(String message) { hud.toast(null, "", message, Hud.ToastKind.ALERT); }

    public void pestAlert(PestSystem.Outbreak outbreak) {
        if (outbreak.insects()) hud.toast(assets.insects[1], "Insetos", "na plantação. Use o pesticida (6).", Hud.ToastKind.ALERT);
        if (outbreak.weeds()) hud.toast(assets.weeds[3], "Ervas daninhas", "pausaram uma planta. Use o herbicida (5).", Hud.ToastKind.ALERT);
    }



    public void reward(float x, float y, CropType harvested, int produced, int xp, int coins) {
        reward(x, y, harvested, produced, java.math.BigDecimal.valueOf(xp), java.math.BigDecimal.valueOf(coins));
    }

    public void reward(float x, float y, CropType harvested, int produced, java.math.BigDecimal xp, java.math.BigDecimal coins) {
        int count = (harvested != null && produced > 0 ? 1 : 0) + (coins.signum() != 0 ? 1 : 0) + (xp.signum() > 0 ? 1 : 0);
        float spacing = 78, left = x - (count - 1) * spacing / 2f;
        int index = 0;
        if (harvested != null && produced > 0) {
            float fx = left + index * spacing; long delay = index++ * 150_000_000L;
            hud.floater(fx, y, assets.crop(harvested, harvested.stages()), "+" + produced, Hud.FloaterStyle.ITEM, delay);
        }
        if (coins.signum() != 0) {
            float fx = left + index * spacing; long delay = index++ * 150_000_000L;
            hud.floater(fx, y, assets.coin, (coins.signum() > 0 ? "+" : "") + GameText.money(coins), Hud.FloaterStyle.COIN, delay);
        }
        if (xp.signum() > 0) {
            float fx = left + index * spacing; long delay = index * 150_000_000L;
            hud.floater(fx, y, assets.star, "+" + GameText.xp(xp), Hud.FloaterStyle.XP, delay);
        }
    }



    public void levelUp(GameState state) {
        List<CropType> unlocked = new ArrayList<>();
        for (CropType crop : state.catalog().all())
            if (crop.requiredLevel() == state.progress().level()) unlocked.add(crop);
        hud.levelUp(state.progress().level(), unlocked);
    }

    // ---------- desenho ----------

    public void draw(GameState state, IsoRenderer.GridPosition hovered, double speed) {
        updateNotifications(state);
        viewport.apply();
        batch.setProjectionMatrix(viewport.getCamera().combined);
        shapes.setProjectionMatrix(viewport.getCamera().combined);
        Gdx.gl.glClearColor(.55f, .77f, .18f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        Instant now = state.now();
        boolean windowOpen = marketPanel.isOpen() || missions.isOpen();
        boolean hoverFarm = hovered != null && state.farm().contains(hovered.column(), hovered.row()) && !windowOpen
                && toolbar.hit(pointerX, pointerY) == null && hud.shortcutHit(pointerX, pointerY) == null;

        batch.begin();
        float backgroundScale = Math.max(viewport.getWorldWidth() / assets.background.getWidth(),
                viewport.getWorldHeight() / assets.background.getHeight());
        float backgroundWidth = assets.background.getWidth() * backgroundScale;
        float backgroundHeight = assets.background.getHeight() * backgroundScale;
        batch.draw(assets.background, (viewport.getWorldWidth() - backgroundWidth) / 2f,
                viewport.getWorldHeight() - backgroundHeight, backgroundWidth, backgroundHeight);
        scenery.syncAnimals(state.barn().animals(), now);
        scenery.draw(batch, Scenery.Layer.BACK);
        drawTiles(state, now, hoverFarm ? hovered : null);
        drawPlants(state, now);
        scenery.draw(batch, Scenery.Layer.FRONT);
        drawPlotSign(state);
        batch.end();
        if (debugVisible) debugOverlay.draw(shapes, batch, assets, iso, state, speed);

        batch.begin();
        if (hoverFarm) {
            Vector2 p = iso.gridToScreen(hovered.column(), hovered.row());
            hud.drawTooltip(batch, skin, assets, state, hovered.column(), hovered.row(), p.x, p.y);
        } else if (!windowOpen) {
            int animal = scenery.animalAt(pointerX, pointerY);
            if (animal >= 0) {
                float[] anchor = scenery.animalAnchor(animal);
                hud.animalTip(batch, skin, state.barn().get(animal), now, anchor[0], anchor[1], state);
            }
        }
        hud.drawTop(batch, skin, assets, state, windowOpen ? -1 : pointerX, windowOpen ? -1 : pointerY, speed, debugVisible);
        toolbar.draw(batch, skin, assets, state, windowOpen ? -1 : pointerX, windowOpen ? -1 : pointerY);
        marketPanel.draw(batch, skin, assets, state, pointerX, pointerY);
        missions.draw(batch, skin, assets, state, pointerX, pointerY);
        hud.drawOverlay(batch, skin);
        batch.setColor(Color.WHITE);
        batch.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    private void drawTiles(GameState state, Instant now, IsoRenderer.GridPosition hovered) {
        for (int row = 0; row < state.farm().rows(); row++) {
            for (int col = 0; col < state.farm().columns(); col++) {
                FarmTile tile = state.farm().tile(col, row);
                Vector2 p = iso.gridToScreen(col, row);
                boolean unlocked = state.land().isUnlocked(col, row);
                Texture ground = !unlocked || tile.state() == TileState.GRASS ? assets.tileGrass
                        : tile.state() == TileState.DEAD ? assets.tileDry
                        : tile.crop() == null ? assets.tilePlowed
                        : tile.crop().dryAt(now) && !tile.crop().matureAt(now) ? assets.tileDry : assets.tileWet;
                if (!unlocked) batch.setColor(.70f, .78f, .62f, .72f);
                batch.draw(ground, p.x - TILE_W / 2f, p.y + TILE_TOP - TILE_H, TILE_W, TILE_H);
                batch.setColor(Color.WHITE);
                if (hovered != null && hovered.column() == col && hovered.row() == row) {
                    float pulse = .65f + .35f * MathUtils.sin(System.nanoTime() / 220_000_000f);
                    batch.setColor(1, 1, 1, pulse);
                    batch.draw(assets.tileHighlight, p.x - TILE_W / 2f - 2, p.y + TILE_TOP - TILE_H - 1, TILE_W + 4, TILE_H + 2);
                    batch.setColor(Color.WHITE);
                }
            }
        }
    }

    private void drawPlants(GameState state, Instant now) {
        for (int row = 0; row < state.farm().rows(); row++) {
            for (int col = 0; col < state.farm().columns(); col++) {
                FarmTile tile = state.farm().tile(col, row);
                if (!state.land().isUnlocked(col, row)) continue;
                if (tile.state() == TileState.DEAD) {
                    drawDeadPlant(tile.withered(), iso.gridToScreen(col, row));
                    continue;
                }
                if (tile.crop() == null) continue;
                Vector2 p = iso.gridToScreen(col, row);
                boolean mature = tile.crop().matureAt(now);
                if (mature) {
                    float pulse = .55f + .45f * MathUtils.sin(System.nanoTime() / 260_000_000f + col + row);
                    skin.glow(batch, p.x, p.y + 30, 150, pulse);
                }
                if (tile.pest() == PestState.WEEDS) {
                    int frame = (int) ((System.nanoTime() / 160_000_000L) % assets.weeds.length);
                    batch.draw(assets.weeds[frame], p.x + 2, p.y - 22, 72, 72);
                }
                int stage = tile.crop().stageAt(now);
                Texture plant = assets.crop(tile.crop().type(), stage);
                float height = 32 + stage * 16;
                float width = height * plant.getWidth() / plant.getHeight();
                boolean dry = tile.crop().dryAt(now) && !mature;
                if (dry) batch.setColor(Palette.DRY_PLANT);
                batch.draw(plant, p.x - width / 2f, p.y - 8, width, height);
                batch.setColor(Color.WHITE);
                if (tile.insects() > 0) {
                    Texture worms = assets.insects[tile.insects() - 1];
                    float ww = 70, wh = ww * worms.getHeight() / worms.getWidth();
                    float wiggle = MathUtils.sin(System.nanoTime() / 200_000_000f + col * 3) * 1.5f;
                    batch.draw(worms, p.x - ww / 2f - 4 + wiggle, p.y - 24, ww, wh);
                }
            }
        }
    }

    /** Planta morta na terra rachada: o sprite maduro achatado, tombado e cor de palha. */
    private void drawDeadPlant(CropType type, Vector2 p) {
        Texture plant = assets.crop(type, type.stages());
        float width = 86;
        float height = width * plant.getHeight() / plant.getWidth() * .55f;
        batch.setColor(Palette.DEAD_PLANT);
        batch.draw(plant, p.x - width / 2f, p.y - 10, width / 2f, 0, width, height, 1, 1, -12,
                0, 0, plant.getWidth(), plant.getHeight(), false, false);
        batch.setColor(Color.WHITE);
    }

    /** Plaquinha no próximo lote à venda; balança quando o jogador já pode comprar. */
    private void drawPlotSign(GameState state) {
        LandExpansion.Plot next = state.land().next();
        if (next == null) return;
        Vector2 p = iso.gridToScreen(next.column(), next.row());
        boolean levelOk = state.progress().level() >= next.requiredLevel();
        boolean ready = levelOk && state.wallet().balance(next.currency()).compareTo(next.price()) >= 0;
        String main = levelOk ? br.com.colheitafeliz.game.GameText.money(next.price()) : "Nível " + next.requiredLevel();
        String sub = levelOk ? "Comprar lote" : next.price() + " verdes";
        float mainW = skin.width(skin.button, main) + (levelOk ? 22 : 0);
        float w = Math.max(mainW, skin.width(skin.micro, sub)) + 20, h = 40;
        float angle = ready ? MathUtils.sin(System.nanoTime() / 160_000_000f) * 3f
                * Math.max(0, MathUtils.sin(System.nanoTime() / 900_000_000f)) : 0;
        float baseX = p.x, baseY = p.y - 6;
        skin.rect(batch, Palette.WOOD_INK, baseX - 3, baseY, 6, 26);
        float bx = baseX - w / 2f + angle * 1.2f, by = baseY + 24;
        skin.dropShadow(batch, bx, by, w, h, 2);
        skin.draw(batch, skin.signBoard, bx, by, w, h);
        float textX = bx + w / 2f + (levelOk ? 11 : 0);
        if (levelOk) Toolbar.drawFitted(batch, assets.greenCoin, textX - mainW / 2f + 2, by + 26, 18);
        skin.shadowed(batch, skin.button, main, textX, by + h - 9, Palette.ON_COLOR, Palette.WOOD_INK, Align.center);
        skin.text(batch, skin.micro, sub, bx + w / 2f, by + 12, Color.valueOf("fff3d6"), Align.center);
    }

    private void updateNotifications(GameState state) {
        Set<Integer> readyPlots = new HashSet<>();
        List<CropType> newlyReady = new ArrayList<>();
        for (int row = 0; row < state.farm().rows(); row++) {
            for (int col = 0; col < state.farm().columns(); col++) {
                FarmTile tile = state.farm().tile(col, row);
                if (!state.land().isUnlocked(col, row) || tile.state() != TileState.READY) continue;
                int plotId = row * state.farm().columns() + col;
                readyPlots.add(plotId);
                if (notificationStateInitialized && !observedReadyPlots.contains(plotId))
                    newlyReady.add(tile.crop().type());
            }
        }
        if (!newlyReady.isEmpty()) {
            CropType first = newlyReady.getFirst();
            if (newlyReady.size() == 1)
                hud.toast(assets.crop(first, first.stages()), first.name(), "pronto para colher!", Hud.ToastKind.REWARD);
            else hud.toast(assets.crop(first, first.stages()), newlyReady.size() + " canteiros",
                    "prontos para colher!", Hud.ToastKind.REWARD);
        } else if (!notificationStateInitialized && !readyPlots.isEmpty()) {
            hud.toast(assets.tool(br.com.colheitafeliz.game.Tool.HAND), readyPlots.size() == 1 ? "1 canteiro"
                    : readyPlots.size() + " canteiros", readyPlots.size() == 1 ? "pronto para colher!"
                    : "prontos para colher!", Hud.ToastKind.REWARD);
        }
        observedReadyPlots = readyPlots;

        LocalDate missionDay = state.dailyMissions().day();
        Set<String> completedMissions = new HashSet<>(state.dailyMissions().completedSnapshot());
        if (notificationStateInitialized && missionDay.equals(observedMissionDay)) {
            for (DailyMissionBoard.Mission mission : state.dailyMissions().missions()) {
                if (completedMissions.contains(mission.id()) && !observedCompletedMissions.contains(mission.id()))
                    hud.toast(assets.greenCoin, "Missão concluída:", mission.title() + " (+" + mission.green()
                            + " verde)", Hud.ToastKind.REWARD);
            }
        }
        observedMissionDay = missionDay;
        observedCompletedMissions = completedMissions;

        LandExpansion.Plot nextPlot = state.land().next();
        boolean plotAvailable = nextPlot != null
                && state.progress().level() >= nextPlot.requiredLevel()
                && state.wallet().balance(nextPlot.currency()).compareTo(nextPlot.price()) >= 0;
        if (notificationStateInitialized && plotAvailable
                && (!observedPlotAvailable || nextPlot.id() != observedNextPlotId))
            hud.toast(assets.coin, "Lote disponível.", "Clique na plaquinha para comprar.", Hud.ToastKind.NORMAL);
        observedNextPlotId = nextPlot == null ? -1 : nextPlot.id();
        observedPlotAvailable = plotAvailable;
        notificationStateInitialized = true;
    }

    @Override public void close() {
        assets.close();
        skin.close();
        batch.dispose();
        shapes.dispose();
    }
}
