package br.com.colheitafeliz.domain;

import br.com.colheitafeliz.domain.economy.CropSale;
import br.com.colheitafeliz.domain.economy.Currency;
import br.com.colheitafeliz.domain.economy.Market;
import br.com.colheitafeliz.domain.economy.Wallet;
import br.com.colheitafeliz.domain.inventory.Inventory;
import br.com.colheitafeliz.domain.inventory.ItemLot;
import br.com.colheitafeliz.game.CropCatalogLoader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CropEconomyV1Test {
    // Independent values from the approved authority table, in catalog order.
    private static final long[] REVENUES = {3450,4400,6500,8480,10520,13580,15995,18350,20765,
            25400,31650,36130,40850,53310,60020,78510,86345,98500,103645,110530,119155,
            126520,135625,148770,162055,185380,270060,476800,50,108,174,248,330};
    private static final long[] COSTS = {300,350,500,650,800,950,1100,1250,1400,1550,1800,1975,
            2150,3075,3350,4125,3875,4600,4600,4750,5050,5200,5500,6180,6850,8500,15000,25000,4,8,12,16,20};
    private static final long[] HOURS = {10,15,20,22,24,34,37,39,41,55,70,78,86,94,104,138,
            167,175,183,191,199,207,215,223,231,239,276,504,18,48,90,144,210};

    @Test void allThirtyThreeCyclesMatchTableProfitAndNominalSlope() {
        var crops = CropCatalogLoader.load().all();
        assertEquals(33, crops.size());
        for (int i = 0; i < crops.size(); i++) {
            var crop = crops.get(i);
            assertEquals(REVENUES[i], crop.cycleRevenueTenths(), crop.id());
            assertEquals(0, BigDecimal.valueOf(COSTS[i]).compareTo(crop.seedPrice().getValue()), crop.id());
            long hours = (crop.growthSeconds() + (crop.seasons() - 1L) * crop.regrowthSeconds()) / 3600;
            assertEquals(HOURS[i], hours, crop.id());
            long collected = 0, bonus = 0;
            for (int season = 1; season <= crop.seasons(); season++) {
                collected += CropSale.share(crop.cycleRevenueTenths(), crop.seasons(), season);
                bonus += CropSale.share(crop.vipOnly() ? 0 : COSTS[i], crop.seasons(), season);
            }
            assertEquals(REVENUES[i], collected, crop.id());
            assertEquals(crop.vipOnly() ? 0 : COSTS[i], bonus, crop.id());
            long profitTenths = collected - COSTS[i] * 10;
            if (!crop.vipOnly()) assertEquals((45 + i * 15L) * hours, profitTenths, crop.id());
            else assertEquals(COSTS[i] * (25 + (i - 28) * 10L), profitTenths * 10, crop.id());
        }
    }

    private static List<ItemLot> harvests(String id) {
        var type = CropCatalogLoader.load().get(id);
        var price = type.seedPrice();
        var origin = type.plantingOrigin(new ItemLot(id, price.getKey(), price.getValue(), 1, type.vipOnly()));
        List<ItemLot> lots = new ArrayList<>();
        for (int season = 1; season <= type.seasons(); season++) lots.add(origin.harvested(season));
        return lots;
    }

    @Test void everyCropHasIdenticalIndividualBatchAndReverseOrderRevenueWithFractionalBalances() {
        for (var crop : CropCatalogLoader.load().all()) for (boolean vip : new boolean[]{false,true}) {
            if (crop.vipOnly() && !vip) continue;
            var lots = harvests(crop.id());
            var individual = new Wallet(new BigDecimal("0.1"), new BigDecimal("0.1"));
            var inventory = new Inventory();
            lots.forEach(inventory::addProduceLot);
            var market = new Market(individual, inventory);
            var quoted = market.quoteEverything(vip);
            for (int i = 0; i < lots.size(); i++) market.sellProduce(crop.id(), 1, vip);
            assertThrows(IllegalStateException.class, () -> market.sellProduce(crop.id(), 1, vip));
            var batch = new Wallet(new BigDecimal("0.1"), new BigDecimal("0.1"));
            Collections.reverse(lots);
            inventory = new Inventory();
            lots.forEach(inventory::addProduceLot);
            assertEquals(quoted, new Market(batch, inventory).sellEverything(vip));
            assertEquals(individual.coins(), batch.coins(), crop.id());
            assertEquals(individual.balance(Currency.GREEN), batch.balance(Currency.GREEN), crop.id());
        }
    }

    @Test void vipBonusOnlyAppliesToSeasonsSoldWhileActive() {
        var lots = harvests("morango");
        var inventory = new Inventory();
        lots.forEach(inventory::addProduceLot);
        var wallet = new Wallet(0);
        var market = new Market(wallet, inventory);
        market.sellProduce("morango", 1, false);
        market.sellProduce("morango", 1, true);
        market.sellProduce("morango", 1, false);
        assertEquals(new BigDecimal("2591.7"), wallet.coins()); // Bonus for season 2 is 51.7.
    }

    @Test void allVipCombinationsFollowTheSoldSeasonEvenInReverseOrder() {
        for (var crop : CropCatalogLoader.load().all()) {
            if (crop.vipOnly()) continue;
            for (int mask = 0; mask < (1 << crop.seasons()); mask++) {
                var lots = harvests(crop.id());
                Collections.reverse(lots);
                var inventory = new Inventory();
                lots.forEach(inventory::addProduceLot);
                var wallet = new Wallet(0);
                var market = new Market(wallet, inventory);
                long expected = crop.cycleRevenueTenths();
                for (var lot : lots) {
                    int index = lot.cropSale().seasonIndex();
                    boolean vip = (mask & (1 << (index - 1))) != 0;
                    if (vip) expected += CropSale.share(crop.seedPrice().getValue().longValueExact(), crop.seasons(), index);
                    market.sellProduce(crop.id(), 1, vip);
                }
                assertEquals(BigDecimal.valueOf(expected, 1), wallet.coins(), crop.id() + " mask " + mask);
            }
        }
    }

    @Test void coconutNeedsVipAndSixHarvestsYieldThirtyThreeNotOneHundredFifty() {
        var inventory = new Inventory();
        var wallet = new Wallet(BigDecimal.ZERO, new BigDecimal("20"));
        var market = new Market(wallet, inventory);
        var coco = CropCatalogLoader.load().get("coco");
        market.buySeed(coco, Currency.GREEN);
        assertEquals(new BigDecimal("0.0"), wallet.balance(Currency.GREEN));
        var origin = coco.plantingOrigin(inventory.takeSeed("coco"));
        for (int season = 1; season <= 6; season++) inventory.addProduceLot(origin.harvested(season));
        assertThrows(IllegalStateException.class, () -> market.sellProduce("coco", 1, false));
        assertTrue(market.quoteEverything(false).isEmpty());
        for (int i = 0; i < 6; i++) assertEquals(new BigDecimal("5.5"), market.sellProduce("coco", 1, true).get(Currency.GREEN));
        assertEquals(new BigDecimal("33.0"), wallet.balance(Currency.GREEN));
        assertEquals(new BigDecimal("13.0"), wallet.balance(Currency.GREEN).subtract(new BigDecimal("20")));
    }

    @Test void sellEverythingKeepsVipLockedProductsAndLegacyTermsAndAnimalPrices() {
        var inventory = new Inventory();
        harvests("coco").forEach(inventory::addProduceLot);
        harvests("tomate").forEach(inventory::addProduceLot);
        inventory.addProduceLot(new ItemLot("tomate", Currency.GOLD, new BigDecimal("950"), 1, false));
        inventory.addProduceLot(new ItemLot("ovo", Currency.GREEN, new BigDecimal("9"), 1, false));
        var wallet = new Wallet(0);
        var market = new Market(wallet, inventory);
        market.sellEverything(false);
        assertEquals(new BigDecimal("2450.5"), wallet.coins());
        assertEquals(new BigDecimal("10.4"), wallet.balance(Currency.GREEN));
        assertEquals(6, inventory.product("coco"));
        assertEquals(new BigDecimal("33.0"), market.sellEverything(true).get(Currency.GREEN));
    }

    @Test void coconutProductsStayBlockedAfterVipExpiryAndUnlockOnRenewal() {
        var clock = new br.com.colheitafeliz.testsupport.ManualClock();
        var game = new br.com.colheitafeliz.game.GameState(clock::instant, clock, CropCatalogLoader.load(), bound -> 99);
        while (game.progress().level() < 50) game.progress().award(game.progress().xpToNext());
        game.wallet().credit(Currency.GREEN, new BigDecimal("300"));
        assertTrue(game.buyVip().success());
        var coconut = game.catalog().get("coco");
        var before = game.greenCoins();
        assertTrue(game.buySeed(coconut, Currency.GREEN).success());
        assertEquals(new BigDecimal("20.0"), before.subtract(game.greenCoins()));
        var origin = coconut.plantingOrigin(game.inventory().takeSeed("coco"));
        for (int season = 1; season <= 6; season++) game.inventory().addProduceLot(origin.harvested(season));
        clock.advanceSeconds(java.time.Duration.ofDays(30).toSeconds());
        assertFalse(game.vipActive());
        assertFalse(game.sellAllProduce(coconut).success());
        assertEquals(6, game.inventory().produce(coconut));
        assertTrue(game.depositTotals().isEmpty());
        assertTrue(game.buyVip().success());
        assertEquals(new BigDecimal("33.0"), game.depositTotals().get(Currency.GREEN));
        assertTrue(game.sellAllProduce(coconut).success());
        assertEquals(0, game.inventory().produce(coconut));
    }

    @Test void documentsLegacyRoundingExposureInMixedV0V1SalesWithoutChangingItsFormula() {
        for (boolean vip : new boolean[]{false,true}) {
            var old = new ItemLot("melancia", Currency.GOLD, new BigDecimal("1975"), 2, false);
            var individualInventory = new Inventory();
            var batchInventory = new Inventory();
            individualInventory.addProduceLot(old); batchInventory.addProduceLot(old);
            for (var product : harvests("melancia")) {
                individualInventory.addProduceLot(product); batchInventory.addProduceLot(product);
            }
            var individualWallet = new Wallet(0); var batchWallet = new Wallet(0);
            var individual = new Market(individualWallet, individualInventory);
            while (individualInventory.product("melancia") > 0) individual.sellProduce("melancia", 1, vip);
            new Market(batchWallet, batchInventory).sellEverything(vip);
            assertEquals(new BigDecimal("0.1"), individualWallet.coins().subtract(batchWallet.coins()),
                    "The approved migration retains the old ceiling rule only on v0 lots");
        }
    }
}
