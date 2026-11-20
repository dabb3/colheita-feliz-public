package br.com.colheitafeliz.game;

import br.com.colheitafeliz.domain.crop.CropType;
import br.com.colheitafeliz.domain.economy.Currency;
import br.com.colheitafeliz.domain.inventory.ItemLot;
import br.com.colheitafeliz.testsupport.ManualClock;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BatchPurchaseTest {
    private final ManualClock clock = new ManualClock();
    private final GameState game = new GameState(clock::instant, clock, CropCatalogLoader.load(), bound -> 99);
    private final CropType turnip = game.catalog().get("nabo");

    @Test void ninetyNineSeedsCostTheExactTotalInOneDebitAndOneLot() {
        BigDecimal total = turnip.seedPrices().get(Currency.GOLD).multiply(BigDecimal.valueOf(99));
        setGold(total);
        var result = game.buySeed(turnip, Currency.GOLD, 99);
        assertTrue(result.success(), result.message());
        assertTrue(result.message().startsWith("+99 sementes de Nabo"), result.message());
        assertEquals(0, game.wallet().coins().signum());
        assertEquals(99, game.inventory().seeds(turnip));
        assertEquals(List.of(99), game.inventory().seedLots().stream().map(ItemLot::quantity).toList());
    }

    @Test void oneCoinShortBuysNothing() {
        BigDecimal total = turnip.seedPrices().get(Currency.GOLD).multiply(BigDecimal.valueOf(2));
        setGold(total.subtract(BigDecimal.ONE));
        var result = game.buySeed(turnip, Currency.GOLD, 2);
        assertFalse(result.success());
        assertEquals("Moedas insuficientes.", result.message());
        assertEquals(0, total.subtract(BigDecimal.ONE).compareTo(game.wallet().coins()));
        assertEquals(0, game.inventory().seeds(turnip));
    }

    @Test void quantityOutsideOneToNinetyNineIsRefused() {
        setGold(new BigDecimal("1000000"));
        for (int quantity : new int[] {0, -1, 100}) {
            assertFalse(game.buySeed(turnip, Currency.GOLD, quantity).success());
            assertFalse(game.buyFertilizer("comum", Currency.GOLD, quantity).success());
        }
        assertEquals(0, new BigDecimal("1000000").compareTo(game.wallet().coins()));
        assertEquals(0, game.inventory().seeds(turnip));
    }

    @Test void calendarSeedsAreNeverSoldInBatch() {
        var result = game.buySeed(game.catalog().get("semente-ouro"), Currency.GOLD, 5);
        assertFalse(result.success());
        assertTrue(result.message().contains("recompensa do calendário"), result.message());
    }

    @Test void levelAndVipStillGateBatchPurchases() {
        setGold(new BigDecimal("1000000"));
        CropType locked = game.catalog().all().stream().filter(type -> !type.vipOnly() && type.requiredLevel() > 1
                && type.seedPrices().containsKey(Currency.GOLD)).findFirst().orElseThrow();
        assertFalse(game.buySeed(locked, Currency.GOLD, 3).success());
        assertFalse(game.buyFertilizer("comum", Currency.GOLD, 3).success(), "comum libera no nível 10");
        CropType vip = game.catalog().all().stream().filter(CropType::vipOnly).findFirst().orElseThrow();
        while (game.progress().level() < vip.requiredLevel()) game.progress().award(game.progress().xpToNext());
        Currency currency = vip.seedPrices().keySet().iterator().next();
        assertFalse(game.buySeed(vip, currency, 3).success());
        assertEquals(0, game.inventory().seeds(locked));
        assertEquals(0, game.inventory().seeds(vip));
        assertEquals(0, game.inventory().fertilizer("comum"));
    }

    @Test void fullSeedStockRefusesWithoutCharging() {
        setGold(new BigDecimal("1000000"));
        game.inventory().restoreLots(List.of(new ItemLot("nabo", Currency.GOLD, BigDecimal.ONE, Integer.MAX_VALUE - 1, false)), List.of());
        var result = game.buySeed(turnip, Currency.GOLD, 2);
        assertFalse(result.success());
        assertEquals(0, new BigDecimal("1000000").compareTo(game.wallet().coins()));
        assertEquals(Integer.MAX_VALUE - 1, game.inventory().seeds(turnip));
    }

    @Test void fertilizerBatchDebitsOnceAndRefusesFullStock() {
        while (game.progress().level() < 10) game.progress().award(game.progress().xpToNext());
        setGold(new BigDecimal("12000"));
        var result = game.buyFertilizer("comum", Currency.GOLD, 12);
        assertTrue(result.success(), result.message());
        assertEquals(12, game.inventory().fertilizer("comum"));
        assertEquals(0, game.wallet().coins().signum());

        setGold(new BigDecimal("1000000"));
        game.inventory().restoreFertilizers(Map.of("comum", Integer.MAX_VALUE));
        assertFalse(game.buyFertilizer("comum", Currency.GOLD, 1).success());
        assertEquals(0, new BigDecimal("1000000").compareTo(game.wallet().coins()));
    }

    @Test void batchCompletesTheDailyTradeMissionOnlyOnce() {
        setGold(new BigDecimal("1000000"));
        BigDecimal green = game.wallet().balance(Currency.GREEN);
        assertTrue(game.buySeed(turnip, Currency.GOLD, 5).success());
        assertTrue(game.buySeed(turnip, Currency.GOLD, 5).success());
        assertEquals(0, green.add(BigDecimal.ONE).compareTo(game.wallet().balance(Currency.GREEN)));
    }

    private void setGold(BigDecimal amount) {
        BigDecimal current = game.wallet().coins();
        if (current.signum() > 0) game.wallet().debit(Currency.GOLD, current);
        if (amount.signum() > 0) game.wallet().credit(Currency.GOLD, amount);
    }
}
