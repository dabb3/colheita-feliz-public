package br.com.colheitafeliz.game;

import br.com.colheitafeliz.domain.economy.Currency;
import br.com.colheitafeliz.domain.inventory.AcquisitionSource;
import br.com.colheitafeliz.domain.inventory.ItemLot;
import br.com.colheitafeliz.domain.inventory.Inventory;
import br.com.colheitafeliz.domain.economy.Market;
import br.com.colheitafeliz.domain.economy.Wallet;
import br.com.colheitafeliz.domain.farm.TileState;
import br.com.colheitafeliz.testsupport.ManualClock;
import br.com.colheitafeliz.testsupport.TestCrops;
import java.math.BigDecimal;
import java.util.Map;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.file.Files;
import br.com.colheitafeliz.persistence.JsonSaveRepository;
import com.google.gson.JsonParser;

class LoginRewardCropTest {
    @TempDir Path temp;
    @Test void rewardCropsHaveFixedTermsAndKeepRewardOriginThroughHarvestAndSale() {
        var clock = new ManualClock();
        var game = new GameState(clock::instant, clock, CropCatalogLoader.load(), bound -> 99);
        String[] ids = {"semente-bronze", "semente-prata", "semente-ouro", "semente-diamante"};
        int[] hours = {24, 36, 48, 72};
        int[] green = {5, 10, 15, 20};
        int[] xp = {36, 54, 72, 108};
        var products = new ArrayList<ItemLot>();
        for (int i = 0; i < ids.length; i++) {
            String id = ids[i];
            var crop = game.catalog().get(id);
            assertEquals(hours[i] * 3600L, crop.growthSeconds());
            assertEquals(Map.of(), crop.seedPrices());
            assertEquals(xp[i], crop.harvestXp(1).intValueExact());
            BigDecimal gold = game.wallet().coins();
            BigDecimal greens = game.greenCoins();
            assertTrue(game.grantLoginRewardSeed(id).success());
            assertEquals(gold, game.wallet().coins());
            assertEquals(greens, game.greenCoins());
            var seed = game.inventory().seedLots().stream().filter(lot -> lot.itemId().equals(id)).findFirst().orElseThrow();
            assertEquals(AcquisitionSource.LOGIN_REWARD, seed.acquisitionSource());
            assertEquals(0, seed.unitCost().signum());
            assertNull(seed.cropSale());
            assertFalse(game.buySeed(crop).success());
            game.selectTool(Tool.HOE);
            if (game.farm().tile(0, 0).state() == TileState.DEAD) assertTrue(game.act(0, 0).success());
            else if (game.farm().tile(0, 0).state() == TileState.GRASS) assertTrue(game.act(0, 0).success());
            assertEquals(TileState.PLOWED, game.farm().tile(0, 0).state());
            game.selectSeed(crop); assertTrue(game.act(0, 0).success());
            var origin = game.farm().tile(0, 0).crop().origin();
            assertEquals(new BigDecimal("0"), origin.unitCost());
            assertEquals(AcquisitionSource.LOGIN_REWARD, origin.acquisitionSource());
            assertEquals(50L + i * 50L, origin.cropSale().cycleRevenueTenths());
            assertEquals(0, origin.cropSale().cycleVipBonusTenths());
            game.selectTool(Tool.WATER);
            var readyAt = TestCrops.growUntilReady(game.farm().tile(0, 0), clock.instant());
            clock.advanceSeconds(java.time.Duration.between(clock.instant(), readyAt).toSeconds());
            game.refresh();
            var visitor = new GameState(clock::instant, clock, game.catalog(), bound -> 99);
            assertFalse(new VisitService().steal(game, visitor, 0, 0,
                    game.farm().tile(0, 0).harvestRevision()).success());
            assertEquals(0, visitor.inventory().product(id));
            game.selectTool(Tool.HAND);
            assertTrue(game.act(0, 0).success());
            var product = game.inventory().produceLots().stream().filter(lot -> lot.itemId().equals(id)).findFirst().orElseThrow();
            assertEquals(AcquisitionSource.LOGIN_REWARD, product.acquisitionSource());
            assertEquals(1, product.cropSale().seasonIndex());
            assertEquals(Currency.GREEN, product.currency());
            assertEquals(Map.of(Currency.GREEN, BigDecimal.valueOf(green[i]).setScale(1)), game.quoteItem(id));
            assertEquals(BigDecimal.valueOf(green[i]).setScale(1),
                    br.com.colheitafeliz.domain.economy.Market.unitRevenue(product, true));
            products.add(product);
            assertTrue(game.sellProduce(crop).success());
            assertTrue(game.greenCoins().compareTo(greens.add(BigDecimal.valueOf(green[i]))) >= 0);
            assertEquals(TileState.DEAD, game.farm().tile(0, 0).state());
        }
        for (boolean vip : new boolean[]{false, true}) {
            var inventory = new Inventory();
            products.forEach(inventory::addProduceLot);
            var wallet = new Wallet(BigDecimal.ZERO, BigDecimal.ZERO);
            var market = new Market(wallet, inventory);
            assertEquals(Map.of(Currency.GREEN, new BigDecimal("50.0")), market.sellEverything(vip));
            assertEquals(new BigDecimal("50.0"), wallet.balance(Currency.GREEN));
            assertEquals(0, inventory.produceLots().size());
        }
    }

    @Test void v20RoundTripKeepsRewardOriginsAndMissingLegacySourceDefaultsToShop() throws Exception {
        var clock = new ManualClock();
        var game = new GameState(clock::instant, clock, CropCatalogLoader.load(), bound -> 99);
        assertTrue(game.grantLoginRewardSeed("semente-bronze").success());
        assertTrue(game.grantLoginRewardSeed("semente-prata").success());
        assertTrue(game.grantLoginRewardSeed("semente-ouro").success());
        game.inventory().addSeedLot(new ItemLot("nabo", Currency.GOLD, new BigDecimal("300"), 1, false));
        var bronze = game.catalog().get("semente-bronze");
        game.selectTool(Tool.HOE); assertTrue(game.act(0, 0).success());
        game.selectSeed(bronze); assertTrue(game.act(0, 0).success());
        var ready = TestCrops.growUntilReady(game.farm().tile(0, 0), clock.instant());
        clock.advanceSeconds(java.time.Duration.between(clock.instant(), ready).toSeconds()); game.refresh();
        game.selectTool(Tool.HAND); assertTrue(game.act(0, 0).success());
        game.selectTool(Tool.HOE); assertTrue(game.act(0, 0).success());
        game.selectTool(Tool.SEED); game.selectSeed(game.catalog().get("semente-prata"));
        assertTrue(game.act(0, 0).success());

        Path file = temp.resolve("game.json");
        var repository = new JsonSaveRepository(file);
        repository.save(game.snapshot(), clock.instant(), clock.instant());
        var json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        var seeds = json.getAsJsonObject("snapshot").getAsJsonArray("seeds");
        seeds.asList().stream().filter(lot -> lot.getAsJsonObject().get("itemId").getAsString().equals("nabo"))
                .findFirst().orElseThrow().getAsJsonObject().remove("acquisitionSource");
        Files.writeString(file, json.toString());
        var loaded = repository.load().orElseThrow().snapshot();
        assertEquals(AcquisitionSource.SHOP, loaded.seeds().stream().filter(lot -> lot.itemId().equals("nabo"))
                .findFirst().orElseThrow().acquisitionSource());
        var restored = GameState.restore(clock::instant, clock, game.catalog(), loaded, bound -> 99);
        assertEquals(AcquisitionSource.LOGIN_REWARD, restored.inventory().seedLots().stream()
                .filter(lot -> lot.itemId().equals("semente-ouro")).findFirst().orElseThrow().acquisitionSource());
        assertEquals(AcquisitionSource.LOGIN_REWARD, restored.inventory().produceLots().getFirst().acquisitionSource());
        assertEquals(AcquisitionSource.LOGIN_REWARD, restored.farm().tile(0, 0).crop().origin().acquisitionSource());

        json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        seeds = json.getAsJsonObject("snapshot").getAsJsonArray("seeds");
        seeds.asList().stream().filter(lot -> lot.getAsJsonObject().get("itemId").getAsString().equals("semente-ouro"))
                .findFirst().orElseThrow().getAsJsonObject().add("acquisitionSource", com.google.gson.JsonNull.INSTANCE);
        Files.writeString(file, json.toString());
        assertThrows(java.io.IOException.class, repository::load);

        json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        seeds = json.getAsJsonObject("snapshot").getAsJsonArray("seeds");
        var rewardSeed = seeds.asList().stream().filter(lot -> lot.getAsJsonObject()
                .get("itemId").getAsString().equals("semente-ouro")).findFirst().orElseThrow().getAsJsonObject();
        rewardSeed.addProperty("acquisitionSource", "LOGIN_REWARD");
        rewardSeed.addProperty("quantity", 1.5);
        Files.writeString(file, json.toString());
        assertThrows(java.io.IOException.class, repository::load);
    }

    @Test void invalidAndFullCapacityGrantsFailWithoutChangingWalletOrInventory() {
        var clock = new ManualClock();
        var game = new GameState(clock::instant, clock, CropCatalogLoader.load(), bound -> 99);
        var gold = game.wallet().coins(); var green = game.greenCoins();
        assertFalse(game.grantLoginRewardSeed("missing").success());
        assertFalse(game.grantLoginRewardSeed("nabo").success());
        game.inventory().addSeedLot(new ItemLot("semente-bronze", Currency.GREEN, BigDecimal.ZERO,
                Integer.MAX_VALUE, false, AcquisitionSource.LOGIN_REWARD));
        assertFalse(game.grantLoginRewardSeed("semente-bronze").success());
        assertEquals(Integer.MAX_VALUE, game.inventory().seeds(game.catalog().get("semente-bronze")));
        assertEquals(gold, game.wallet().coins()); assertEquals(green, game.greenCoins());
    }

    @Test void rewardSeedsCannotBeGiftedOrStolenAndSnapshotValidationRejectsForgedOrigins() {
        var clock = new ManualClock();
        var owner = new GameState(clock::instant, clock, CropCatalogLoader.load(), bound -> 99);
        var visitor = new GameState(clock::instant, clock, CropCatalogLoader.load(), bound -> 99);
        assertTrue(owner.grantLoginRewardSeed("semente-bronze").success());
        assertFalse(new VisitService().giftSeeds(owner, visitor, "semente-bronze", 1, "A", "g1").success());
        var snapshot = owner.snapshot();
        assertDoesNotThrow(() -> GameState.restore(clock::instant, clock, owner.catalog(), snapshot, bound -> 99));
        var forged = new ItemLot("semente-bronze", Currency.GREEN, BigDecimal.ONE, 1, false);
        var invalid = new GameSnapshot(snapshot.coins(), java.util.List.of(forged), snapshot.produce(), snapshot.tiles(),
                snapshot.selectedSeedId(), snapshot.tool(), snapshot.level(), snapshot.currentXp(), snapshot.musicEnabled(),
                snapshot.effectsEnabled(), snapshot.dailyMissionDay(), snapshot.dailyMissionProgress(),
                snapshot.completedDailyMissions(), snapshot.unlockedPlotIds(), snapshot.tutorialStep(), snapshot.animals(),
                snapshot.feed(), snapshot.fertilizers(), snapshot.selectedFertilizerId(), snapshot.greenCoins(),
                snapshot.vipExpiresAt(), snapshot.protection(), snapshot.notifications(), snapshot.christmas2026(),
                snapshot.avatar(), snapshot.automations());
        assertThrows(IllegalArgumentException.class,
                () -> GameState.restore(clock::instant, clock, owner.catalog(), invalid, bound -> 99));
        var missingTerms = new ItemLot("semente-bronze", Currency.GREEN, BigDecimal.ZERO, 1, false,
                AcquisitionSource.LOGIN_REWARD);
        var productSnapshot = new GameSnapshot(snapshot.coins(), snapshot.seeds(), java.util.List.of(missingTerms), snapshot.tiles(),
                snapshot.selectedSeedId(), snapshot.tool(), snapshot.level(), snapshot.currentXp(), snapshot.musicEnabled(),
                snapshot.effectsEnabled(), snapshot.dailyMissionDay(), snapshot.dailyMissionProgress(),
                snapshot.completedDailyMissions(), snapshot.unlockedPlotIds(), snapshot.tutorialStep(), snapshot.animals(),
                snapshot.feed(), snapshot.fertilizers(), snapshot.selectedFertilizerId(), snapshot.greenCoins(),
                snapshot.vipExpiresAt(), snapshot.protection(), snapshot.notifications(), snapshot.christmas2026(),
                snapshot.avatar(), snapshot.automations());
        assertThrows(IllegalArgumentException.class,
                () -> GameState.restore(clock::instant, clock, owner.catalog(), productSnapshot, bound -> 99));
    }
}
