package br.com.colheitafeliz.server;

import br.com.colheitafeliz.domain.animal.Animal;
import br.com.colheitafeliz.domain.animal.AnimalType;
import br.com.colheitafeliz.domain.crop.Crop;
import br.com.colheitafeliz.domain.economy.Currency;
import br.com.colheitafeliz.domain.farm.PestState;
import br.com.colheitafeliz.domain.farm.TileState;
import br.com.colheitafeliz.domain.inventory.ItemLot;
import br.com.colheitafeliz.game.CropCatalogLoader;
import br.com.colheitafeliz.game.GameSnapshot;
import br.com.colheitafeliz.game.GameState;
import com.google.gson.JsonElement;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import static br.com.colheitafeliz.server.OnlineViews.JSON;
import static br.com.colheitafeliz.server.OnlineViews.object;

/** Opt-in synthetic command measurements. Every fixture and command is rolled back. */
final class LatencyBenchmark {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String SCOPE = "service_tx;synthetic;rollback;actor_receipt_cleanup;excludes_fixture_auth_network_begin_commit";
    record Measurement(String action, String label, List<Double> samplesMs, String scope) {
        Measurement { samplesMs = List.copyOf(samplesMs); }
    }
    private LatencyBenchmark() {}

    static List<Measurement> run(JdbcOnlineStore store) {
        var service = new OnlineGameService(store, CLOCK, bound -> bound - 1, 5);
        var measurements = new ArrayList<Measurement>();
        for (String label : List.of("BUY_SEED", "BUY_ANIMAL", "BUY_FEED", "BUY_FERTILIZER", "BUY_VIP", "BUY_PLOT",
                "PLOW", "PLANT", "WATER", "WEED", "PESTICIDE", "FERTILIZE", "HARVEST", "SELL_ITEM", "SELL_ITEM_ALL",
                "SELL_ALL", "TEND_ANIMAL_FEED", "TEND_ANIMAL_COLLECT", "SELL_ANIMAL", "FARM", "FRIENDS", "VISIT", "STEAL", "LOVE")) {
            String action = label.startsWith("TEND_ANIMAL_") ? "TEND_ANIMAL" : label;
            var samples = new ArrayList<Double>();
            try {
                for (int iteration = 0; iteration < 4; iteration++) {
                    double milliseconds = sample(store, service, action, label);
                    if (iteration > 0) samples.add(milliseconds);
                }
            } catch (RuntimeException failure) {
                System.out.println("LATENCY_BENCHMARK " + object("action", action, "label", label, "error", "scenario_failed", "scope", SCOPE));
                throw new IllegalStateException("Synthetic benchmark failed: " + label, failure);
            }
            var measurement = new Measurement(action, label, samples, SCOPE);
            measurements.add(measurement);
            System.out.println("LATENCY_BENCHMARK " + JSON.toJson(measurement));
        }
        return List.copyOf(measurements);
    }

    private static double sample(JdbcOnlineStore store, OnlineGameService service, String action, String label) {
        UUID actor = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        return store.probe(actor, tx -> {
            tx.benchmarkPlayer(actor);
            GameState game = fixture(label);
            tx.save(actor, game.snapshot(), 1, NOW);
            boolean social = List.of("FRIENDS", "VISIT", "STEAL", "LOVE").contains(action);
            if (social) {
                tx.benchmarkPlayer(owner);
                tx.befriend(actor, owner);
                tx.save(owner, fixture(label).snapshot(), 1, NOW);
            }
            Command command = command(game, action);
            long started = System.nanoTime();
            JsonElement response = switch (action) {
                case "FARM" -> service.farm(tx, actor, "Latency benchmark");
                case "FRIENDS" -> service.friends(tx, actor);
                case "VISIT" -> service.visit(tx, actor, owner);
                case "STEAL", "LOVE" -> service.visitAction(tx, actor, owner, command);
                default -> service.command(tx, actor, command);
            };
            double elapsed = (System.nanoTime() - started) / 1_000_000.0;
            if (!List.of("FARM", "FRIENDS", "VISIT").contains(action)
                    && !response.getAsJsonObject().getAsJsonObject("result").get("success").getAsBoolean())
                throw new IllegalStateException("Synthetic action did not succeed: " + label);
            return elapsed;
        });
    }

    private static Command command(GameState game, String action) {
        String item = switch (action) {
            case "BUY_SEED", "PLANT", "SELL_ITEM", "SELL_ITEM_ALL" -> "nabo";
            case "BUY_ANIMAL" -> animal(game, AnimalType.Kind.PRODUCER).id();
            case "BUY_FEED" -> "producer";
            case "BUY_FERTILIZER", "FERTILIZE" -> "comum";
            case "TEND_ANIMAL" -> animal(game, AnimalType.Kind.PRODUCER).id();
            case "SELL_ANIMAL" -> animal(game, AnimalType.Kind.GROWING).id();
            default -> null;
        };
        // Read-only scenarios still need no domain command; use a valid placeholder outside the timed region.
        String commandAction = List.of("FARM", "FRIENDS", "VISIT").contains(action) ? "PLOW" : action;
        return new Command(RequestIds.issue(NOW), "1", commandAction, item, "GOLD",
                action.equals("BUY_PLOT") ? game.land().next().column() : 0,
                action.equals("BUY_PLOT") ? game.land().next().row() : 0,
                action.equals("STEAL") || action.equals("LOVE") ? "1" : null);
    }

    static GameState fixture(String label) {
        var catalog = CropCatalogLoader.load();
        var initial = new GameState(CLOCK::instant, CLOCK, catalog, bound -> bound - 1).snapshot();
        var snapshot = new GameSnapshot(new BigDecimal("1000000"), initial.seeds(), initial.produce(), initial.tiles(),
                initial.selectedSeedId(), initial.tool(), 61, BigDecimal.ZERO, initial.musicEnabled(), initial.effectsEnabled(),
                initial.dailyMissionDay(), initial.dailyMissionProgress(), initial.completedDailyMissions(), initial.unlockedPlotIds(),
                6, initial.animals(), initial.feed(), initial.fertilizers(), initial.selectedFertilizerId(),
                new BigDecimal("1000000"), null, initial.protection());
        var game = GameState.restore(CLOCK::instant, CLOCK, catalog, snapshot, bound -> bound - 1);
        var crop = catalog.get("nabo");
        game.inventory().addSeedLot(new ItemLot(crop.id(), Currency.GOLD, crop.seedPrices().get(Currency.GOLD), 3, false));
        game.inventory().addFertilizer("comum", 3);
        game.inventory().addFertilizer("amor", 3);
        if (List.of("SELL_ITEM", "SELL_ITEM_ALL", "SELL_ALL").contains(label))
            game.inventory().addProduceLot(new ItemLot(crop.id(), Currency.GOLD, crop.seedPrices().get(Currency.GOLD), 3, false));
        if (List.of("PLANT", "WATER", "WEED", "PESTICIDE", "FERTILIZE", "HARVEST", "STEAL", "LOVE").contains(label)) {
            var tile = game.farm().tile(0, 0);
            tile.plow();
            if (!label.equals("PLANT")) {
                tile.plant(crop, NOW);
                if (!label.equals("WATER")) tile.water(NOW);
                switch (label) {
                    case "WEED" -> tile.checkPest(NOW, true);
                    case "PESTICIDE" -> tile.checkInsects(NOW, true);
                    case "HARVEST", "STEAL" -> tile.restore(TileState.READY,
                            new Crop(crop, NOW, NOW, null, NOW, crop.growthTime(1).toMillis(), tile.crop().origin(), 1),
                            PestState.NONE, NOW.plusSeconds(600), 0, NOW.plusSeconds(600), null, false, 1);
                    default -> { }
                }
            }
        }
        if (label.equals("BUY_FEED") || label.startsWith("TEND_ANIMAL") || label.equals("SELL_ANIMAL")) {
            var type = animal(game, label.equals("SELL_ANIMAL") ? AnimalType.Kind.GROWING : AnimalType.Kind.PRODUCER);
            var origin = new ItemLot(type.id(), Currency.GOLD, type.prices().get(Currency.GOLD), 1, false);
            boolean mature = label.equals("SELL_ANIMAL") || label.equals("TEND_ANIMAL_COLLECT");
            game.barn().add(new Animal(type, origin, NOW, NOW, mature ? type.requiredMillis() : 0, NOW));
            game.inventory().restoreFeed(java.util.Map.of("producer", 3, "growth", 3));
        }
        return game;
    }

    private static AnimalType animal(GameState game, AnimalType.Kind kind) {
        return game.animalTypes().stream().filter(type -> type.kind() == kind).findFirst().orElseThrow();
    }
}
