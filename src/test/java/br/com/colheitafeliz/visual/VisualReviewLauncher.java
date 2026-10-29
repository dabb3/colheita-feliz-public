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
import java.time.Instant;

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
                "14-loja-animais.png", "15-galinhas-no-cercado.png", "16-vip.png", "17-saldos-e-xp-decimais.png", "18-deposito-duas-moedas.png", "19-deposito-vip-bloqueado.png", "20-falha-autosave.png", "21-aviso-vip.png",
                "22-cinco-estagios.png", "23-pragas-simultaneas.png", "24-temporada-reiniciada.png", "25-loja-ultima-pagina.png",
                "26-loja-fertilizantes.png", "27-bandeja-fertilizantes.png", "28-pronta-pelo-fertilizante.png", "29-animais-bebes.png", "30-animais-jovens.png", "31-animais-adultos.png",
                "32-animais-sono.png", "33-protetores-expirados.png", "34-animais-pagina2.png", "35-animais-pagina3.png", "36-animais-pagina4.png", "37-tarefas-no-tutorial.png", "38-terreno-verde-comprado.png", "39-saldos-milionarios.png", "40-planta-morta.png",
                "41-background-dia.png", "42-background-por-do-sol.png", "43-background-noite.png"
        };
        private static final int[] FRAMES = {3, 3, 3, 3, 3, 3, 3, 3, 10, 14, 3, 3, 3, 3, 3, 3, 3, 3, 3, 12, 12, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3};
        static { FRAMES[30] = 150; } // Cinco segundos: inclui deslocamento e a cauda aberta do pavão.
        private final Path output = Path.of(System.getProperty("colheita.visualOutput", "build/visual-review"));
        private FarmRenderer renderer;
        private final ManualClock backgroundClock = new ManualClock();
        private GameState fresh, states, rich, newcomer, rancher, stages, seasons, fertilized, tasks, expanded, millionaire;
        private int scene, frames;
        private GameState animalsBaby, animalsYoung, animalsAdult, animalsSleep, animalsExpired, harvested;

        @Override public void create() {
            CropCatalog catalog = CropCatalogLoader.load();
            fresh = new GameState(new ManualClock()::instant, catalog, bound -> 99);
            states = buildStates(catalog);
            stages = buildStages(catalog);
            seasons = buildSeasons(catalog);
            ManualClock harvestedClock = new ManualClock();
            harvested = new GameState(harvestedClock::instant, harvestedClock, catalog, bound -> 99);
            FarmTile harvestedTile = harvested.farm().tile(0, 2);
            harvestedTile.plow();
            harvestedTile.plant(catalog.get("nabo"), harvestedClock.instant());
            Instant harvestTime = br.com.colheitafeliz.testsupport.TestCrops.growUntilReady(harvestedTile, harvestedClock.instant());
            harvestedClock.advanceSeconds(java.time.Duration.between(harvestedClock.instant(), harvestTime).toSeconds());
            harvested.inventory().addProduceLot(harvestedTile.harvest(harvestedClock.instant()));
            harvested.selectTool(Tool.HAND);
            fertilized = buildFertilized(catalog);
            rich = new GameState(new ManualClock()::instant, catalog, bound -> 99);
            rich.wallet().credit(1150);
            rich.progress().award(60);
            rich.progress().award(80);
            rich.refresh();
            for (CropType crop : catalog.all()) if (crop.requiredLevel() <= 3) rich.inventory().addProduceLot(new br.com.colheitafeliz.domain.inventory.ItemLot(crop.id(), br.com.colheitafeliz.domain.economy.Currency.GOLD, crop.seedPrice().getValue(), 2, false));
            for (int i = 0; i < 4; i++) rich.buySeed(catalog.get("nabo"));
            rich.dailyMissions().record("buy", 3, rich.dailyMissions().day());
            rich.dailyMissions().record("plant", 1, rich.dailyMissions().day());
            ManualClock newcomerClock = new ManualClock();
            newcomer = new GameState(newcomerClock::instant, newcomerClock, catalog, bound -> 99);
            newcomer.startNewFarm();
            newcomer.selectTool(Tool.HAND);
            newcomer.act(0, 0);
            tasks = new GameState(newcomerClock::instant, newcomerClock, catalog, bound -> 99);
            tasks.startNewFarm();
            tasks.buySeed(catalog.get("nabo"));
            tasks.selectTool(Tool.HOE);
            tasks.act(0, 0);
            tasks.selectSeed(catalog.get("nabo"));
            tasks.act(0, 0);
            expanded = new GameState(newcomerClock::instant, newcomerClock, catalog, bound -> 99);
            expanded.progress().award(25);
            expanded.wallet().credit(br.com.colheitafeliz.domain.economy.Currency.GREEN, new java.math.BigDecimal("95"));
            expanded.buyPlot(1, 0);
            millionaire = new GameState(newcomerClock::instant, newcomerClock, catalog, bound -> 99);
            millionaire.wallet().credit(br.com.colheitafeliz.domain.economy.Currency.GOLD, new java.math.BigDecimal("987654321.1"));
            millionaire.wallet().credit(br.com.colheitafeliz.domain.economy.Currency.GREEN, new java.math.BigDecimal("1234567.8"));
            millionaire.progress().award(new java.math.BigDecimal("23867.125"));
            animalsBaby = animalScene(br.com.colheitafeliz.domain.animal.Animal.Stage.BABY, false, false);
            animalsYoung = animalScene(br.com.colheitafeliz.domain.animal.Animal.Stage.YOUNG, false, false);
            animalsAdult = animalScene(br.com.colheitafeliz.domain.animal.Animal.Stage.ADULT, false, false);
            animalsSleep = animalScene(br.com.colheitafeliz.domain.animal.Animal.Stage.ADULT, true, false);
            animalsExpired = animalScene(br.com.colheitafeliz.domain.animal.Animal.Stage.ADULT, false, true);
            rancher = animalsAdult;
            renderer = new FarmRenderer(catalog, backgroundClock);
            renderer.viewport().update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true);
            try { Files.createDirectories(output); }
            catch (IOException e) { throw new IllegalStateException("Cannot create visual review folder", e); }
        }

        /**
         * Primeira coluna, todos nabos (10 h, úmidos por 3 h 20 min): úmido, seco, com 2 vermes, com erva e vermes ao
         * mesmo tempo, e pronto. As pragas pausam o crescimento; a seca só o reduz à metade.
         */
        private static GameState buildStates(CropCatalog catalog) {
            ManualClock clock = new ManualClock();
            GameState game = new GameState(clock::instant, catalog, bound -> 99);
            CropType turnip = catalog.get("nabo");
            game.wallet().credit(1500);
            for (int row = 0; row < 5; row++) {
                game.buySeed(turnip);
                plant(game, turnip, row);
            }
            water(game, 1, 2, 3, 4);
            clock.advanceSeconds(12_000);
            water(game, 0, 4);
            FarmTile worms = game.farm().tile(0, 2);
            worms.checkInsects(clock.instant(), true);
            worms.worsenInsects(clock.instant());
            FarmTile both = game.farm().tile(0, 3);
            both.checkPest(clock.instant(), true);
            both.checkInsects(clock.instant(), true);
            clock.advanceSeconds(12_000);
            water(game, 4);
            clock.advanceSeconds(6_000);
            water(game, 0);
            clock.advanceSeconds(6_000);
            game.farm().refresh(clock.instant());
            game.selectTool(Tool.HAND);
            return game;
        }

        /** Cinco nabos, um estágio de diferença por linha: do pronto (linha 0) ao recém-regado (linha 4). */
        private static GameState buildStages(CropCatalog catalog) {
            ManualClock clock = new ManualClock();
            GameState game = new GameState(clock::instant, catalog, bound -> 99);
            CropType turnip = catalog.get("nabo");
            game.wallet().credit(1500);
            long quarter = turnip.growthTime(1).toSeconds() / (turnip.stages() - 1);
            for (int row = 0; row < 5; row++) {
                // A terra seca a cada terço e um estágio leva um quarto: passos de um doze avos acertam os dois.
                for (int tick = 0; row > 0 && tick < 3; tick++) {
                    clock.advanceSeconds(quarter / 3);
                    for (int earlier = 0; earlier < row; earlier++) {
                        var crop = game.farm().tile(0, earlier).crop();
                        if (crop.dryAt(clock.instant()) && !crop.matureAt(clock.instant())) water(game, earlier);
                    }
                }
                game.buySeed(turnip);
                plant(game, turnip, row);
                water(game, row);
            }
            game.farm().refresh(clock.instant());
            game.selectTool(Tool.HAND);
            return game;
        }

        /** Tomate (duas temporadas) logo depois da primeira colheita: estágio 1 da temporada 2, sem rega. */
        private static GameState buildSeasons(CropCatalog catalog) {
            ManualClock clock = new ManualClock();
            GameState game = new GameState(clock::instant, catalog, bound -> 99);
            CropType tomato = catalog.get("tomate");
            FarmTile tile = game.farm().tile(0, 0);
            tile.plow();
            tile.plant(tomato, clock.instant());
            Instant ready = br.com.colheitafeliz.testsupport.TestCrops.growUntilReady(tile, clock.instant());
            clock.advanceSeconds(java.time.Duration.between(clock.instant(), ready).toSeconds());
            game.inventory().addProduceLot(tile.harvest(clock.instant()));
            clock.advanceSeconds(600);
            game.selectTool(Tool.HAND);
            return game;
        }

        /** Nabo com 6 h de crescimento que o superfertilizante (5 h 30 min) deixa pronto na mesma temporada. */
        private static GameState buildFertilized(CropCatalog catalog) {
            ManualClock clock = new ManualClock();
            GameState game = new GameState(clock::instant, catalog, bound -> 99);
            game.wallet().credit(300);
            game.buySeed(catalog.get("nabo"));
            plant(game, catalog.get("nabo"), 0);
            water(game, 0);
            clock.advanceSeconds(12_000);
            water(game, 0);
            clock.advanceSeconds(9_600);
            game.inventory().addFertilizer("super", 1);
            game.selectFertilizer("super");
            game.selectTool(Tool.FERTILIZER);
            game.act(0, 0);
            game.selectTool(Tool.HAND);
            return game;
        }

        private static void plant(GameState game, CropType crop, int row) {
            game.selectTool(Tool.HOE);
            game.act(0, row);
            game.selectSeed(crop);
            game.act(0, row);
        }

        private static GameState animalScene(br.com.colheitafeliz.domain.animal.Animal.Stage stage, boolean night, boolean expired) {
            ManualClock real = new ManualClock(); ManualClock simulation = new ManualClock();
            if (night) simulation.advanceSeconds(12 * 3600);
            GameState game = new GameState(simulation::instant, real, CropCatalogLoader.load(), bound -> 99);
            while (game.progress().level() < 60) game.progress().award(game.progress().xpToNext());
            game.wallet().credit(10_000_000);
            game.wallet().credit(br.com.colheitafeliz.domain.economy.Currency.GREEN, new java.math.BigDecimal("500"));
            java.util.List<br.com.colheitafeliz.domain.animal.Animal> animals = new java.util.ArrayList<>();
            for (var type : game.animalTypes()) {
                long progress = type.kind() == br.com.colheitafeliz.domain.animal.AnimalType.Kind.PRODUCER ? type.requiredMillis()
                        : type.kind() != br.com.colheitafeliz.domain.animal.AnimalType.Kind.GROWING ? 0 : switch (stage) {
                    case BABY -> 0; case YOUNG -> type.babySeconds() * 1000; case ADULT -> type.requiredMillis();
                };
                var origin = new br.com.colheitafeliz.domain.inventory.ItemLot(type.id(), br.com.colheitafeliz.domain.economy.Currency.GOLD, type.prices().get(br.com.colheitafeliz.domain.economy.Currency.GOLD), 1, false);
                boolean hungry = type.id().equals("abelha") || type.id().equals("cachorro-mastiff");
                Instant anchor = hungry ? game.now().minus(type.foodDuration()) : game.now();
                Instant acquisition = expired ? real.instant().minus(java.time.Duration.ofDays(30)) : real.instant();
                animals.add(new br.com.colheitafeliz.domain.animal.Animal(type, origin, acquisition, anchor,
                        hungry ? 0 : progress, anchor.plus(type.foodDuration())));
            }
            game.barn().restore(animals);
            game.refresh();
            game.message(expired ? "Protetores removidos aos 30 dias reais" : "Fases, fome e coleta única · protetores ativos: " + game.activeProtectors());
            return game;
        }

        private static void water(GameState game, int... rows) {
            game.selectTool(Tool.WATER);
            for (int row : rows) game.act(0, row);
        }

        @Override public void render() {
            if (frames == 0) setUp(scene);
            GameState state = switch (scene) {
                case 0, 10, 17, 18, 19 -> fresh;
                case 5, 6, 7, 9, 15, 16, 20 -> rich;
                case 11, 12 -> newcomer;
                case 13, 14 -> rancher;
                case 21 -> stages;
                case 23 -> seasons;
                case 24, 25, 26 -> fresh;
                case 27 -> fertilized;
                case 28 -> animalsBaby;
                case 29 -> animalsYoung;
                case 30 -> animalsAdult;
                case 31 -> animalsSleep;
                case 32 -> animalsExpired;
                case 33, 34, 35 -> rancher;
                case 36 -> tasks;
                case 37 -> expanded;
                case 38 -> millionaire;
                case 39 -> harvested;
                case 40, 41, 42 -> animalsAdult;
                default -> states;
            };
            IsoRenderer.GridPosition hovered = switch (scene) {
                case 2 -> new IsoRenderer.GridPosition(0, 2);
                case 3 -> new IsoRenderer.GridPosition(0, 1);
                case 10, 37 -> new IsoRenderer.GridPosition(2, 0);
                case 11 -> new IsoRenderer.GridPosition(0, 0);
                case 22 -> new IsoRenderer.GridPosition(0, 3);
                case 23, 27 -> new IsoRenderer.GridPosition(0, 0);
                default -> null;
            };
            if (hovered != null) {
                Vector2 p = renderer.tileCenter(hovered.column(), hovered.row());
                renderer.pointer(p.x, p.y);
            }
            renderer.draw(state, hovered, 1);
            if ((scene == 30 || scene == 31) && frames == 0) {
                // Conferir os alvos renderizados de todos os animais sem alterar o estado ou salvar.
                float[][] targets = {{1170, 433}, {1210, 68}, {1330, 238}, {195, 108}, {1350, 358},
                        {1020, 628}, {1480, 333}, {300, 478}, {1375, 443}, {1515, 463},
                        {785, 498}, {1430, 68}, {1220, 643}, {180, 578}, {345, 53}};
                var types = state.animalTypes();
                for (int i = 0; i < targets.length; i++)
                    if (!types.get(i).id().equals(renderer.animalTypeAt(targets[i][0], targets[i][1])))
                        throw new IllegalStateException("Unreachable animal: " + types.get(i).id());
            }
            if (++frames < FRAMES[scene]) return;
            capture(FILES[scene]);
            scene++;
            frames = 0;
            if (scene == FILES.length) Gdx.app.exit();
        }

        private void setUp(int scene) {
            Instant target = Instant.parse("2026-01-01T" + switch (scene) {
                case 40 -> "06:00:00Z";
                case 41 -> "17:00:00Z";
                case 31, 42 -> "18:31:00Z";
                default -> "12:00:00Z";
            });
            backgroundClock.advanceSeconds(java.time.Duration.between(backgroundClock.instant(), target).toSeconds());
            renderer.hud().reset();
            renderer.marketPanel().close();
            renderer.missions().close();
            renderer.pointer(-1, -1);
            fresh.selectTool(Tool.HAND);
            states.selectTool(scene == 4 ? Tool.SEED : Tool.HAND);
            rich.selectTool(Tool.HAND);
            renderer.toolbar().closeTray();
            switch (scene) {
                case 4 -> renderer.toolbar().picked(Tool.SEED, Tool.HAND);
                case 25 -> {
                    fresh.wallet().credit(5_000);
                    renderer.marketPanel().showFertilizers();
                }
                case 26 -> {
                    fresh.inventory().addFertilizer("comum", 3);
                    fresh.inventory().addFertilizer("rapido", 1);
                    fresh.selectFertilizer("rapido");
                    fresh.selectTool(Tool.FERTILIZER);
                    renderer.toolbar().picked(Tool.FERTILIZER, Tool.HAND);
                }
                case 24 -> {
                    // Nível alto sem VIP: as sementes VIP liberadas pelo nível só saem com o cartão (última cena).
                    fresh.progress().award(new java.math.BigDecimal("1000000"));
                    renderer.marketPanel().showShop();
                    var vipSeeds = MarketPanel.seedGroupButton(true);
                    renderer.marketPanel().click(vipSeeds.x + vipSeeds.width / 2, vipSeeds.y + vipSeeds.height / 2, fresh);
                }
                case 20 -> renderer.hud().toast(null, "", rich.message(), Hud.ToastKind.NORMAL);
                case 19 -> {
                    var file = output.resolve("blocked-parent");
                    try {
                        Files.createDirectories(output);
                        Files.writeString(file, "fixture");
                        var session = br.com.colheitafeliz.game.GameSession.open(new ManualClock(), fresh.catalog(),
                                new br.com.colheitafeliz.persistence.JsonSaveRepository(file.resolve("save.json")), bound -> 99);
                        if (session.trySave()) throw new IllegalStateException("Expected failed save fixture");
                        renderer.failure(session.state().message());
                    } catch (IOException e) { throw new IllegalStateException(e); }
                }
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
                case 7, 12, 36 -> renderer.missions().toggle();
                case 13 -> renderer.marketPanel().showAnimals();
                case 33, 34, 35 -> {
                    renderer.marketPanel().showAnimals();
                    renderer.marketPanel().scroll(800, 400, (scene - 32) * 4, fresh);
                }
                case 28, 29, 30, 31, 32 -> {
                    GameState animalState = switch (scene) { case 28 -> animalsBaby; case 29 -> animalsYoung;
                        case 30 -> animalsAdult; case 31 -> animalsSleep; default -> animalsExpired; };
                    renderer.draw(animalState, null, 1);
                    renderer.pointer(1262, 410);
                }
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
                    renderer.reward(p.x, p.y + 70, rich.catalog().get("morango"), 1, 25, 0);
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
