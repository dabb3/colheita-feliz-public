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

/** Acceptance routine: real actions only, injected time, no pests, VIP or fertilizer. */
class ProgressionSimulationTest {
    @Test void activeRoutineReachesThirtyNearOneMonthAndSensitivityRemainsSlower() {
        var active = simulate(3, true);
        var daily = simulate(1, true);
        var plants = simulate(3, false);
        System.out.println("DOMAIN CALIBRATION active=" + active + "; daily=" + daily + "; plants=" + plants);
        assertTrue(active.get(30) >= 28 && active.get(30) <= 35, active.toString());
        assertTrue(daily.get(30) > active.get(30));
        assertTrue(plants.get(30) > active.get(30));
    }

    private static Map<Integer, Double> simulate(int visits, boolean animals) {
        var clock = new ManualClock();
        clock.advanceSeconds(-12 * 3600); // UTC midnight anchors daily payments.
        var game = new GameState(clock::instant, clock, CropCatalogLoader.load(), bound -> 99);
        game.startNewFarm();
        var turnip = game.catalog().get("nabo");
        Map<Integer, Double> reached = new HashMap<>();
        for (int visit = 0; visit <= 120 * visits; visit++) {
            game.refresh();
            for (int row = 0; row < GameState.ROWS; row++) for (int col = 0; col < GameState.COLUMNS; col++) {
                if (!game.land().isUnlocked(col, row)) continue;
                var tile = game.farm().tile(col, row);
                if (tile.state() == TileState.READY) {
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
                    && game.greenCoins().compareTo(new BigDecimal("50")) >= 0) {
                var plot = game.land().next();
                assertTrue(game.buyPlot(plot.column(), plot.row()).success());
            }
            for (int row = 0; row < GameState.ROWS; row++) for (int col = 0; col < GameState.COLUMNS; col++) {
                if (!game.land().isUnlocked(col, row)) continue;
                var tile = game.farm().tile(col, row);
                if (tile.crop() != null || game.wallet().coins().compareTo(new BigDecimal("300")) < 0) continue;
                if (tile.state() == TileState.GRASS) {
                    game.selectTool(Tool.HOE);
                    assertTrue(game.act(col, row).success());
                }
                assertTrue(game.buySeed(turnip).success());
                assertTrue(game.selectSeed(turnip).success());
                assertTrue(game.act(col, row).success());
                game.selectTool(Tool.WATER);
                assertTrue(game.act(col, row).success());
            }
            if (animals) for (var type : game.animalTypes()) {
                if (type.kind() == AnimalType.Kind.PROTECTOR || game.progress().level() < type.requiredLevel()
                        || game.barn().animals().stream().anyMatch(a -> a.type().id().equals(type.id()))) continue;
                var price = type.prices().get(Currency.GOLD);
                if (price != null && game.wallet().coins().compareTo(price.add(new BigDecimal("300"))) >= 0)
                    assertTrue(game.buyAnimal(type, Currency.GOLD).success());
            }
            for (int level : new int[]{10, 20, 30})
                if (game.progress().level() >= level) reached.putIfAbsent(level, visit / (double) visits);
            assertTrue(game.wallet().coins().signum() >= 0);
            assertTrue(game.greenCoins().signum() >= 0);
            if (reached.containsKey(30)) return Map.copyOf(reached);
            clock.advanceSeconds(86400 / visits);
        }
        fail("Level 30 not reached: " + reached);
        return Map.of();
    }
}
