package br.com.colheitafeliz.game;

import br.com.colheitafeliz.domain.animal.Animal;
import br.com.colheitafeliz.domain.animal.AnimalType;
import br.com.colheitafeliz.domain.economy.Currency;
import br.com.colheitafeliz.domain.farm.TileState;
import br.com.colheitafeliz.testsupport.ManualClock;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Rotinas de aceitação usando as ações reais do jogo e relógios manuais, sem injetar saldo ou XP. */
class ProgressionSimulationTest {
    @Test void activeRoutineReachesThirtyNearOneMonthAndSensitivityRemainsSlower() {
        var active = simulate(new Routine(3, true, "nabo", false, false, 30));
        var daily = simulate(new Routine(1, true, "nabo", false, false, 30));
        var plants = simulate(new Routine(3, false, "nabo", false, false, 30));
        System.out.println("DOMAIN CALIBRATION active=" + active + "; daily=" + daily + "; plants=" + plants);
        assertTrue(active.reached().get(30) >= 28 && active.reached().get(30) <= 35, active.toString());
        assertTrue(daily.reached().get(30) > active.reached().get(30));
        assertTrue(plants.reached().get(30) > active.reached().get(30));
    }

    @Test void baseRoutineReachesHigherLevelsWithoutInventingRewards() {
        var result = simulate(new Routine(3, true, "nabo", false, false, 61));
        System.out.println("DOMAIN HIGH LEVELS " + result);
        assertTrue(result.reached().get(61) > result.reached().get(50));
        assertTrue(result.reached().get(50) > result.reached().get(30));
    }

    @Test void seasonalPestsAndPurchasedBoostsExerciseRealRules() {
        var pests = simulate(new Routine(3, true, "tomate", true, false, 30));
        var boosts = simulate(new Routine(3, true, "tomate", false, true, 30));
        System.out.println("DOMAIN VARIANTS pests=" + pests + "; boosts=" + boosts);
        assertTrue(pests.pestsRemoved() > 0);
        assertTrue(pests.extraSeasons() > 0);
        assertTrue(boosts.extraSeasons() > 0);
        assertTrue(boosts.vipPurchases() > 0);
        assertTrue(boosts.fertilizersUsed() > 0);
    }

    private record Routine(int visits, boolean animals, String crop, boolean pests, boolean boosts, int target) {}
    private record Outcome(Map<Integer, Double> reached, BigDecimal gold, BigDecimal green, BigDecimal xp,
                           int pestsRemoved, int extraSeasons, int vipPurchases, int fertilizersUsed) {}

    private static Outcome simulate(Routine routine) {
        var clock = new ManualClock();
        clock.advanceSeconds(-12 * 3600); // UTC midnight anchors daily payments.
        var random = new java.util.Random(20261029L);
        var game = new GameState(clock::instant, clock, CropCatalogLoader.load(),
                bound -> routine.pests() ? random.nextInt(bound) : 99);
        game.startNewFarm();
        var turnip = game.catalog().get("nabo");
        Map<Integer, Double> reached = new HashMap<>();
        int pestsRemoved = 0, extraSeasons = 0, vipPurchases = 0, fertilizersUsed = 0;
        for (int visit = 0; visit <= 240 * routine.visits(); visit++) {
            game.refresh();
            var preferred = game.catalog().get(routine.crop());
            var seed = game.unlocked(preferred) ? preferred : turnip;
            if (routine.boosts() && !game.vipActive() && game.greenCoins().compareTo(new BigDecimal("100")) >= 0) {
                assertTrue(game.buyVip().success());
                vipPurchases++;
            }
            for (int row = 0; row < GameState.ROWS; row++) for (int col = 0; col < GameState.COLUMNS; col++) {
                if (!game.land().isUnlocked(col, row)) continue;
                var tile = game.farm().tile(col, row);
                if (tile.pest() == br.com.colheitafeliz.domain.farm.PestState.WEEDS) {
                    game.selectTool(Tool.WEEDER);
                    assertTrue(game.act(col, row).success());
                    pestsRemoved++;
                }
                if (tile.insects() > 0) {
                    game.selectTool(Tool.PESTICIDE);
                    assertTrue(game.act(col, row).success());
                    pestsRemoved++;
                }
                if (tile.state() == TileState.READY) {
                    if (tile.crop().season() > 1) extraSeasons++;
                    game.selectTool(Tool.HAND);
                    assertTrue(game.act(col, row).success());
                    assertTrue(game.sellEverything().success());
                }
                if (tile.state() == TileState.DEAD) {
                    game.selectTool(Tool.HOE);
                    assertTrue(game.act(col, row).success());
                }
                if (tile.state() == TileState.GROWING && tile.crop().dryAt(game.now())) {
                    game.selectTool(Tool.WATER);
                    assertTrue(game.act(col, row).success());
                }
                if (routine.boosts() && tile.state() == TileState.GROWING && !tile.fertilized()
                        && game.progress().level() >= game.fertilizer("comum").requiredLevel()
                        && game.wallet().coins().compareTo(seed.seedPrice().getValue().add(new BigDecimal("50"))) >= 0) {
                    assertTrue(game.buyFertilizer("comum", Currency.GOLD).success());
                    assertTrue(game.selectFertilizer("comum").success());
                    game.selectTool(Tool.FERTILIZER);
                    assertTrue(game.act(col, row).success());
                    fertilizersUsed++;
                }
            }
            for (var animal : java.util.List.copyOf(game.barn().animals())) {
                if (animal.statusAt(game.now()) == Animal.Status.READY) {
                    assertTrue(game.tendAnimal(animal.type().id()).success());
                    if (animal.type().kind() == AnimalType.Kind.PRODUCER) assertTrue(game.sellEverything().success());
                } else if (animal.hungryAt(game.now()) && game.wallet().coins().compareTo(BigDecimal.valueOf(animal.type().feedPrice())) >= 0) {
                    assertTrue(game.buyFeed(animal.type()).success());
                    assertTrue(game.tendAnimal(animal.type().id()).success());
                }
            }
            while (game.land().next() != null && game.progress().level() >= game.land().next().requiredLevel()
                    && game.greenCoins().compareTo(new BigDecimal("50")) >= 0
                    // Esta sensibilidade prioriza o cartão, reservando 100 verdes enquanto estiver inativo.
                    && (!routine.boosts() || game.vipActive() || game.greenCoins().compareTo(new BigDecimal("150")) >= 0)) {
                var plot = game.land().next();
                assertTrue(game.buyPlot(plot.column(), plot.row()).success());
            }
            for (int row = 0; row < GameState.ROWS; row++) for (int col = 0; col < GameState.COLUMNS; col++) {
                if (!game.land().isUnlocked(col, row)) continue;
                var tile = game.farm().tile(col, row);
                if (tile.crop() != null || game.wallet().coins().compareTo(seed.seedPrice().getValue()) < 0) continue;
                if (tile.state() == TileState.GRASS) {
                    game.selectTool(Tool.HOE);
                    assertTrue(game.act(col, row).success());
                }
                assertTrue(game.buySeed(seed).success());
                assertTrue(game.selectSeed(seed).success());
                assertTrue(game.act(col, row).success());
                game.selectTool(Tool.WATER);
                assertTrue(game.act(col, row).success());
            }
            if (routine.animals()) for (var type : game.animalTypes()) {
                if (type.kind() == AnimalType.Kind.PROTECTOR || game.progress().level() < type.requiredLevel()
                        || game.barn().animals().stream().anyMatch(a -> a.type().id().equals(type.id()))) continue;
                var price = type.prices().get(Currency.GOLD);
                if (price != null && game.wallet().coins().compareTo(price.add(new BigDecimal("300"))) >= 0)
                    assertTrue(game.buyAnimal(type, Currency.GOLD).success());
            }
            for (int level : new int[]{10, 20, 30, 50, 61})
                if (game.progress().level() >= level) reached.putIfAbsent(level, visit / (double) routine.visits());
            assertTrue(game.wallet().coins().signum() >= 0);
            assertTrue(game.greenCoins().signum() >= 0);
            if (reached.containsKey(routine.target())) return new Outcome(Map.copyOf(reached), game.wallet().coins(),
                    game.greenCoins(), game.progress().currentXp(), pestsRemoved, extraSeasons, vipPurchases, fertilizersUsed);
            clock.advanceSeconds(86400 / routine.visits());
        }
        throw new AssertionError("Target not reached: " + routine + " " + reached);
    }
}
