package br.com.colheitafeliz.visual;

import br.com.colheitafeliz.domain.crop.CropCatalog;
import br.com.colheitafeliz.domain.crop.CropType;
import br.com.colheitafeliz.domain.farm.FarmTile;
import br.com.colheitafeliz.domain.farm.PestSystem;
import br.com.colheitafeliz.game.CropCatalogLoader;
import br.com.colheitafeliz.game.GameState;
import br.com.colheitafeliz.game.Tool;
import br.com.colheitafeliz.render.FarmRenderer;
import br.com.colheitafeliz.render.IsoRenderer;
import br.com.colheitafeliz.testsupport.ManualClock;
import br.com.colheitafeliz.ui.Hud;
import br.com.colheitafeliz.ui.MarketPanel;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.ScreenUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Cenas reproduzíveis do HUD "Flash 2010 remasterizado". Não lê nem grava o save do jogador. */
public final class VisualReviewLauncher {
    private VisualReviewLauncher() {}

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Colheita Feliz — revisão visual");
        config.setWindowedMode(1280, 720);
        config.setForegroundFPS(30);
        new Lwjgl3Application(new ReviewApp(), config);
    }

    private static final class ReviewApp extends ApplicationAdapter {
        private static final String[] FILES = {
                "01-fazenda.png", "02-estados-do-canteiro.png", "03-tooltip-verme.png", "04-tooltip-seca.png",
                "05-sementes.png", "06-loja.png", "07-deposito.png", "08-missoes.png",
                "09-avisos.png", "10-nivel-e-recompensas.png", "11-lote-a-venda.png",
                "12-primeiros-passos.png", "13-tutorial-nas-missoes.png",
                "14-loja-animais.png", "15-galinhas-no-cercado.png", "16-vip.png", "17-saldos-e-xp-decimais.png", "18-deposito-duas-moedas.png", "19-deposito-vip-bloqueado.png"
        };
        private static final int[] FRAMES = {3, 3, 3, 3, 3, 3, 3, 3, 10, 14, 3, 3, 3, 3, 3, 3, 3, 3, 3};
        private final Path output = Path.of(System.getProperty("colheita.visualOutput", "build/visual-review"));
        private FarmRenderer renderer;
        private GameState fresh, states, rich, newcomer, rancher;
        private int scene, frames;

        @Override public void create() {
            CropCatalog catalog = CropCatalogLoader.load();
            fresh = new GameState(new ManualClock()::instant, catalog, bound -> 99);
            states = buildStates(catalog);
            rich = new GameState(new ManualClock()::instant, catalog, bound -> 99);
            rich.wallet().credit(1150);
            rich.progress().award(60);
            rich.progress().award(80);
            rich.refresh();
            for (CropType crop : catalog.all()) if (crop.requiredLevel() <= 3) rich.inventory().addProduce(crop, 2);
            for (int i = 0; i < 4; i++) rich.buySeed(catalog.get("nabo"));
            rich.dailyMissions().record("buy", 3, rich.dailyMissions().day());
            rich.dailyMissions().record("plant", 1, rich.dailyMissions().day());
            ManualClock newcomerClock = new ManualClock();
            newcomer = new GameState(newcomerClock::instant, newcomerClock, catalog, bound -> 99);
            newcomer.startNewFarm();
            newcomer.selectTool(Tool.HAND);
            newcomer.act(0, 0);
            ManualClock rancherClock = new ManualClock();
            rancher = new GameState(rancherClock::instant, rancherClock, catalog, bound -> 99);
            rancher.progress().award(50 + 75 + 100 + 125 + 150);
            rancher.wallet().credit(10_000);
            for (String id : new String[]{"galinha", "galinha", "pato", "ovelha", "ovelha", "vaca"}) {
                var type = rancher.animalType(id);
                rancher.buyAnimal(type);
                rancher.buyFeed(type);
            }
            for (int i : new int[]{0, 2, 3, 5}) rancher.tendAnimal(i);
            rancherClock.advanceSeconds(43_200);
            rancher.inventory().addProduceLot(new br.com.colheitafeliz.domain.inventory.ItemLot("ovo", br.com.colheitafeliz.domain.economy.Currency.GOLD, java.math.BigDecimal.valueOf(rancher.animalType("galinha").price()), 3, false));
            rancher.inventory().addProduceLot(new br.com.colheitafeliz.domain.inventory.ItemLot("leite", br.com.colheitafeliz.domain.economy.Currency.GOLD, java.math.BigDecimal.valueOf(rancher.animalType("vaca").price()), 2, false));
            rancher.wallet().debit(br.com.colheitafeliz.domain.economy.Currency.GOLD, rancher.wallet().coins().subtract(java.math.BigDecimal.valueOf(1200)));
            renderer = new FarmRenderer(catalog);
            renderer.viewport().update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true);
            try { Files.createDirectories(output); }
            catch (IOException e) { throw new IllegalStateException("Cannot create visual review folder", e); }
        }

        /** Primeira coluna: nabo úmido, nabo seco, morango com 2 vermes, nabo com erva daninha e nabo pronto. */
        private static GameState buildStates(CropCatalog catalog) {
            ManualClock clock = new ManualClock();
            GameState game = new GameState(clock::instant, catalog, bound -> 99);
            CropType turnip = catalog.get("nabo");
            CropType strawberry = catalog.get("morango");
            for (int i = 0; i < 4; i++) game.buySeed(turnip);
            game.buySeed(strawberry);
            plant(game, turnip, 4);
            plant(game, strawberry, 2);
            clock.advanceSeconds(300);
            game.selectTool(Tool.WATER);
            game.act(0, 4);
            for (int row : new int[]{0, 1, 3}) plant(game, turnip, row);
            clock.advanceSeconds(320);
            game.selectTool(Tool.WATER);
            for (int row : new int[]{0, 3}) game.act(0, row);
            FarmTile worms = game.farm().tile(0, 2);
            worms.checkInsects(clock.instant(), true);
            worms.worsenInsects(clock.instant());
            game.farm().tile(0, 3).checkPest(clock.instant(), true);
            game.farm().refresh(clock.instant());
            game.selectTool(Tool.HAND);
            return game;
        }

        private static void plant(GameState game, CropType crop, int row) {
            game.selectTool(Tool.HOE);
            game.act(0, row);
            game.selectSeed(crop);
            game.act(0, row);
            game.selectTool(Tool.WATER);
            game.act(0, row);
        }

        @Override public void render() {
            if (frames == 0) setUp(scene);
            GameState state = switch (scene) {
                case 0, 10, 17, 18 -> fresh;
                case 5, 6, 7, 9, 15, 16 -> rich;
                case 11, 12 -> newcomer;
                case 13, 14 -> rancher;
                default -> states;
            };
            IsoRenderer.GridPosition hovered = switch (scene) {
                case 2 -> new IsoRenderer.GridPosition(0, 2);
                case 3 -> new IsoRenderer.GridPosition(0, 1);
                case 10 -> new IsoRenderer.GridPosition(1, 0);
                case 11 -> new IsoRenderer.GridPosition(0, 0);
                default -> null;
            };
            if (hovered != null) {
                Vector2 p = renderer.tileCenter(hovered.column(), hovered.row());
                renderer.pointer(p.x, p.y);
            }
            renderer.draw(state, hovered, 1);
            if (++frames < FRAMES[scene]) return;
            capture(FILES[scene]);
            scene++;
            frames = 0;
            if (scene == FILES.length) Gdx.app.exit();
        }

        private void setUp(int scene) {
            renderer.hud().reset();
            renderer.marketPanel().close();
            renderer.missions().close();
            renderer.pointer(-1, -1);
            fresh.selectTool(Tool.HAND);
            states.selectTool(scene == 4 ? Tool.SEED : Tool.HAND);
            rich.selectTool(Tool.HAND);
            switch (scene) {
                case 17 -> {
                    fresh.inventory().addProduceLot(new br.com.colheitafeliz.domain.inventory.ItemLot("nabo", br.com.colheitafeliz.domain.economy.Currency.GOLD, new java.math.BigDecimal("1975"), 2, false));
                    fresh.inventory().addProduceLot(new br.com.colheitafeliz.domain.inventory.ItemLot("cenoura", br.com.colheitafeliz.domain.economy.Currency.GREEN, new java.math.BigDecimal("9"), 2, false));
                    renderer.marketPanel().showSell();
                }
                case 18 -> {
                    fresh.inventory().addProduceLot(new br.com.colheitafeliz.domain.inventory.ItemLot("milho", br.com.colheitafeliz.domain.economy.Currency.GREEN, new java.math.BigDecimal("4"), 1, true));
                    renderer.marketPanel().showSell();
                }
                case 15 -> {
                    rich.wallet().credit(br.com.colheitafeliz.domain.economy.Currency.GREEN, new java.math.BigDecimal("100"));
                    rich.buyVip();
                    renderer.marketPanel().showVip();
                }
                case 16 -> {
                    rich.wallet().credit(br.com.colheitafeliz.domain.economy.Currency.GOLD, new java.math.BigDecimal("0.1"));
                    rich.wallet().credit(br.com.colheitafeliz.domain.economy.Currency.GREEN, new java.math.BigDecimal("0.1"));
                    rich.progress().award(new java.math.BigDecimal("14.4375"));
                }
                case 5 -> {
                    renderer.marketPanel().showShop();
                    Rectangle cell = MarketPanel.cell(1);
                    renderer.pointer(cell.x + cell.width / 2f, cell.y + cell.height / 2f);
                }
                case 6 -> renderer.marketPanel().showSell();
                case 7, 12 -> renderer.missions().toggle();
                case 13 -> renderer.marketPanel().showAnimals();
                case 14 -> {
                    renderer.draw(rancher, null, 1);
                    renderer.pointer(1446, 500);
                }
                case 8 -> {
                    renderer.pestAlert(new PestSystem.Outbreak(false, true));
                    Rectangle shop = Hud.shortcutBounds(Hud.Shortcut.SHOP);
                    renderer.pointer(shop.x + shop.width / 2f, shop.y + 50);
                }
                case 9 -> {
                    renderer.levelUp(rich);
                    Vector2 p = renderer.tileCenter(0, 0);
                    renderer.reward(p.x, p.y + 70, rich.catalog().get("morango"), 4, 25, 0);
                }
                default -> { }
            }
        }

        private void capture(String name) {
            int width = Gdx.graphics.getWidth();
            int height = Gdx.graphics.getHeight();
            byte[] pixels = ScreenUtils.getFrameBufferPixels(0, 0, width, height, true);
            Pixmap image = new Pixmap(width, height, Pixmap.Format.RGBA8888);
            image.getPixels().put(pixels);
            image.getPixels().position(0);
            PixmapIO.writePNG(Gdx.files.absolute(output.resolve(name).toAbsolutePath().toString()), image);
            image.dispose();
        }

        @Override public void resize(int width, int height) {
            if (renderer != null) renderer.viewport().update(width, height, true);
        }

        @Override public void dispose() { if (renderer != null) renderer.close(); }
    }
}
