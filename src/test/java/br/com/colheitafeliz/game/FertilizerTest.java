package br.com.colheitafeliz.game;

import br.com.colheitafeliz.domain.crop.CropType;
import br.com.colheitafeliz.domain.crop.FertilizerType;
import br.com.colheitafeliz.domain.economy.Currency;
import br.com.colheitafeliz.domain.farm.FarmTile;
import br.com.colheitafeliz.domain.farm.TileState;
import br.com.colheitafeliz.testsupport.ManualClock;
import br.com.colheitafeliz.testsupport.TestCrops;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FertilizerTest {
    private final ManualClock clock = new ManualClock();
    private final GameState game = new GameState(clock::instant, clock, CropCatalogLoader.load(), bound -> 99);
    private final CropType strawberry = TestCrops.strawberry();

    @Test void catalogFollowsSectionFive() {
        assertFertilizer("comum", 3_600, 10, Map.of(Currency.GOLD, new BigDecimal("1000")), false);
        assertFertilizer("rapido", 9_000, 20, Map.of(Currency.GREEN, BigDecimal.ONE), false);
        assertFertilizer("super", 19_800, 30, Map.of(Currency.GREEN, new BigDecimal("2")), false);
        assertFertilizer("amor", 3_600, 15, Map.of(Currency.GOLD, new BigDecimal("2000")), true);
        assertFertilizer("ultra", 32_400, 40, Map.of(Currency.GREEN, new BigDecimal("3")), false);
        assertFertilizer("mega", 50_400, 50, Map.of(Currency.GREEN, new BigDecimal("5")), false);
        assertEquals(6, game.fertilizers().size());
    }

    @Test void superfertilizerMakesTheStrawberryReadyInTheSameSeasonAndTheExcessIsLost() {
        FarmTile tile = plantedStrawberry();
        Instant fourHoursLeft = waterUntil(tile, Duration.ofHours(27));
        game.inventory().addFertilizer("super", 1);
        assertTrue(apply("super").success());
        tile.refresh(fourHoursLeft);
        assertEquals(TileState.READY, tile.state(), "5 h 30 min cobrem as 4 h restantes");
        assertEquals(1, tile.crop().season(), "o fertilizante não colhe nem avança a temporada");
        assertEquals(0, game.inventory().fertilizer("super"));
        tile.harvest(fourHoursLeft);
        tile.water(fourHoursLeft);
        assertEquals(Duration.ofHours(12), tile.crop().currentDuration());
        assertEquals(0, tile.crop().grownMillisAt(fourHoursLeft), "o excedente não passa para a próxima temporada");
    }

    @Test void onlyOneApplicationPerSeasonEvenWithAnotherType() {
        FarmTile tile = plantedStrawberry();
        Instant now = waterUntil(tile, Duration.ofHours(1));
        game.inventory().addFertilizer("comum", 1);
        game.inventory().addFertilizer("rapido", 1);
        assertTrue(apply("comum").success());
        assertEquals(Duration.ofHours(2).toMillis(), tile.crop().grownMillisAt(now));
        assertFalse(apply("rapido").success(), "trocar de tipo não libera outra aplicação");
        assertEquals(1, game.inventory().fertilizer("rapido"));

        Instant ready = TestCrops.growUntilReady(tile, now);
        tile.harvest(ready);
        clock.advanceSeconds(Duration.between(clock.instant(), ready).toSeconds());
        tile.water(ready);
        assertTrue(apply("rapido").success(), "a nova temporada permite exatamente uma aplicação");
        assertFalse(apply("rapido").success());
    }

    @Test void unwateredDryOrPestPlantsRefuseWithoutSpending() {
        FarmTile tile = plantedStrawberry();
        game.inventory().addFertilizer("comum", 1);
        assertFalse(apply("comum").success(), "nunca regada");
        Instant start = clock.instant();
        tile.water(start);
        clock.advanceSeconds(tile.crop().moistDuration().toSeconds());
        assertFalse(apply("comum").success(), "seca");
        tile.water(clock.instant());
        tile.checkPest(clock.instant(), true);
        assertFalse(apply("comum").success(), "com erva");
        tile.removePest(clock.instant());
        tile.checkInsects(clock.instant(), true);
        assertFalse(apply("comum").success(), "com insetos");
        assertEquals(1, game.inventory().fertilizer("comum"));
        tile.removeInsects(clock.instant());
        assertTrue(apply("comum").success());
        assertEquals(0, game.inventory().fertilizer("comum"));
    }

    @Test void loveFertilizerIsNotUsedOnTheOwnFarm() {
        FarmTile tile = plantedStrawberry();
        waterUntil(tile, Duration.ofHours(1));
        game.inventory().addFertilizer("amor", 1);
        var result = apply("amor");
        assertFalse(result.success());
        assertTrue(result.message().contains("amigos"), result.message());
        assertEquals(1, game.inventory().fertilizer("amor"));
        assertFalse(tile.fertilized());
    }

    @Test void purchasesFollowLevelAndCurrencyAndNeverConvert() {
        game.wallet().credit(100_000);
        game.wallet().credit(Currency.GREEN, new BigDecimal("10"));
        reachLevel(9);
        BigDecimal gold = game.wallet().coins();
        assertFalse(game.buyFertilizer("comum", Currency.GOLD).success(), "comum só no nível 10");
        assertEquals(gold, game.wallet().coins());
        reachLevel(10);
        gold = game.wallet().coins();
        assertTrue(game.buyFertilizer("comum", Currency.GOLD).success());
        assertEquals(0, gold.subtract(new BigDecimal("1000")).compareTo(game.wallet().coins()));
        assertFalse(game.buyFertilizer("comum", Currency.GREEN).success(), "comum não sai por verdes");
        reachLevel(20);
        BigDecimal green = game.greenCoins();
        assertTrue(game.buyFertilizer("rapido", Currency.GREEN).success());
        assertEquals(0, green.subtract(BigDecimal.ONE).compareTo(game.greenCoins()));
        gold = game.wallet().coins();
        assertFalse(game.buyFertilizer("rapido", Currency.GOLD).success());
        assertEquals(0, gold.compareTo(game.wallet().coins()));
        assertFalse(game.buyFertilizer("super", Currency.GOLD).success(), "super só no nível 30");
        reachLevel(30);
        green = game.greenCoins();
        assertTrue(game.buyFertilizer("super", Currency.GREEN).success());
        assertEquals(0, green.subtract(new BigDecimal("2")).compareTo(game.greenCoins()));
        assertEquals(1, game.inventory().fertilizer("rapido"));
        assertEquals(1, game.inventory().fertilizer("super"));
    }

    @Test void loveFertilizerCostsTwoThousandGoldAtLevelFifteenAndPointsToVisits() {
        game.wallet().credit(10_000);
        reachLevel(14);
        assertFalse(game.buyFertilizer("amor", Currency.GOLD).success());
        reachLevel(15);
        BigDecimal before = game.wallet().coins();
        var result = game.buyFertilizer("amor", Currency.GOLD);
        assertTrue(result.success());
        assertTrue(result.message().contains("amigos"), result.message());
        assertEquals(0, before.subtract(new BigDecimal("2000")).compareTo(game.wallet().coins()));
        assertEquals(1, game.inventory().fertilizer("amor"));
        assertFalse(game.buyFertilizer("amor", Currency.GREEN).success());
    }

    @Test void advancedFertilizersEnforceLevelsCurrencyAndBalance() {
        for (String id : new String[]{"super", "ultra", "mega"}) {
            int level = switch (id) { case "super" -> 30; case "ultra" -> 40; default -> 50; };
            int price = switch (id) { case "super" -> 2; case "ultra" -> 3; default -> 5; };
            reachLevel(level - 1);
            assertFalse(game.buyFertilizer(id, Currency.GREEN).success());
            reachLevel(level);
            assertFalse(game.buyFertilizer(id, Currency.GOLD).success());
            if (game.greenCoins().signum() > 0) game.wallet().debit(Currency.GREEN, game.greenCoins());
            assertFalse(game.buyFertilizer(id, Currency.GREEN).success());
            game.wallet().credit(Currency.GREEN, BigDecimal.valueOf(price));
            assertTrue(game.buyFertilizer(id, Currency.GREEN).success());
            assertTrue(game.greenCoins().compareTo(BigDecimal.ONE) <= 0, "a missão de compra pode premiar 1 verde");
            assertEquals(1, game.inventory().fertilizer(id));
        }
    }

    @Test void ultraAndMegaConsumeOnceClampGrowthAndRestoreAllStocks() {
        for (String id : new String[]{"ultra", "mega"}) {
            var localClock = new ManualClock();
            var state = new GameState(localClock::instant, localClock, CropCatalogLoader.load(), bound -> 99);
            var tile = state.farm().tile(0, 0);
            tile.plow();
            tile.plant(strawberry, localClock.instant(), TestCrops.origin(strawberry));
            Instant start = localClock.instant();
            Duration grown = Duration.ofHours(30).plusMinutes(59);
            for (Duration t = Duration.ZERO; t.compareTo(grown) < 0; t = t.plus(tile.crop().moistDuration()))
                tile.water(start.plus(t));
            localClock.advanceSeconds(grown.toSeconds());
            for (var type : state.fertilizers()) state.inventory().addFertilizer(type.id(), 2);
            state.selectFertilizer(id);
            state.selectTool(Tool.FERTILIZER);
            assertTrue(state.act(0, 0).success());
            assertEquals(TileState.READY, tile.state());
            assertEquals(1, tile.crop().season());
            assertEquals(1, state.inventory().fertilizer(id));
            assertFalse(state.act(0, 0).success());
            var restored = GameState.restore(localClock::instant, localClock, state.catalog(), state.snapshot(), bound -> 99);
            assertEquals(state.inventory().fertilizerSnapshot(), restored.inventory().fertilizerSnapshot());
            assertEquals(id, restored.snapshot().selectedFertilizerId());
            assertEquals(1, restored.farm().tile(0, 0).crop().season());
            assertFalse(restored.act(0, 0).success());
        }
    }

    @Test void insufficientBalanceDoesNotChangeAnything() {
        reachLevel(10);
        game.wallet().debit(Currency.GOLD, game.wallet().coins().subtract(new BigDecimal("49")));
        assertFalse(game.buyFertilizer("comum", Currency.GOLD).success());
        assertEquals(0, new BigDecimal("49").compareTo(game.wallet().coins()));
        assertEquals(0, game.inventory().fertilizer("comum"));
    }

    private void reachLevel(int level) {
        while (game.progress().level() < level)
            game.progress().award(game.progress().xpToNext().subtract(game.progress().currentXp()));
    }

    /** Seleciona o tipo e usa a ferramenta no terreno (0, 0), como faz a interface. */
    private ActionResult apply(String id) {
        assertTrue(game.selectFertilizer(id).success());
        game.selectTool(Tool.FERTILIZER);
        return game.act(0, 0);
    }

    private void assertFertilizer(String id, long seconds, int level, Map<Currency, BigDecimal> prices, boolean friendsOnly) {
        FertilizerType type = game.fertilizer(id);
        assertEquals(Duration.ofSeconds(seconds), type.reduction(), id);
        assertEquals(level, type.requiredLevel(), id);
        assertEquals(prices.keySet(), type.prices().keySet(), id);
        prices.forEach((currency, price) -> assertEquals(0, price.compareTo(type.prices().get(currency)), id));
        assertEquals(friendsOnly, type.friendsOnly(), id);
    }

    private FarmTile plantedStrawberry() {
        FarmTile tile = game.farm().tile(0, 0);
        tile.plow();
        tile.plant(strawberry, clock.instant(), TestCrops.origin(strawberry));
        return tile;
    }

    /** Rega a cada terço da temporada (terra sempre úmida) até o crescimento chegar a {@code grown}; acerta o relógio. */
    private Instant waterUntil(FarmTile tile, Duration grown) {
        Instant start = clock.instant();
        Duration moist = tile.crop().moistDuration();
        for (Duration t = Duration.ZERO; t.compareTo(grown) < 0; t = t.plus(moist)) tile.water(start.plus(t));
        clock.advanceSeconds(grown.toSeconds());
        return clock.instant();
    }
}
