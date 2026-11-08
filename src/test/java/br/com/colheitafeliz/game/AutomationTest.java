package br.com.colheitafeliz.game;

import br.com.colheitafeliz.domain.economy.Currency;
import br.com.colheitafeliz.testsupport.ManualClock;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AutomationTest {
    @Test void naturalPestReplayAndUtcTaskRewardsMatchAcrossReloads() {
        var clock = new ManualClock();
        var game = new GameState(clock::instant,clock,CropCatalogLoader.load(),n -> 0);
        game.wallet().credit(Currency.GREEN,new java.math.BigDecimal("1000"));
        game.buyVip(); game.buyAutomation("water"); game.buyAutomation("pests");
        var tile = game.farm().tile(0,0); tile.plow(); tile.plant(game.catalog().get("nabo"),clock.instant());
        game.runAutomationsNow(); game.selectTool(Tool.HAND);
        var saved = game.snapshot();
        var start = clock.instant(); var middle = start.plusSeconds(12000); var end = start.plusSeconds(172800);
        var once = GameState.restoreOnline(start,end,game.catalog(),saved,n -> 0);
        var split = GameState.restoreOnline(start,middle,game.catalog(),saved,n -> 0);
        assertTrue(split.farm().tile(0,0).crop().grownMillisAt(middle)<12000000L);
        split = GameState.restoreOnline(middle,end,game.catalog(),split.snapshot(),n -> 0);
        assertEquals(once.snapshot(),split.snapshot());
        assertEquals(Tool.HAND,once.tool());
        assertEquals(br.com.colheitafeliz.domain.farm.TileState.READY,once.farm().tile(0,0).state());
        assertEquals(0,once.inventory().produce(game.catalog().get("nabo")));
        assertEquals("2026-01-03",once.dailyMissions().day().toString());
    }

    @Test void pestArrivalCannotPredateThePauseOrLieAfterRestoreTime() {
        var clock = new ManualClock();
        var game = new GameState(clock::instant, clock, CropCatalogLoader.load(), n -> 99);
        var tile = game.farm().tile(0,0); tile.plow(); tile.plant(game.catalog().get("nabo"),clock.instant());
        tile.water(clock.instant()); clock.advanceSeconds(120); tile.checkPest(clock.instant(),true);
        assertThrows(IllegalArgumentException.class, () -> tile.restorePestArrivals(clock.instant().minusSeconds(1),null));
        var gson = new com.google.gson.Gson();
        var json = gson.toJsonTree(game.snapshot()).getAsJsonObject();
        json.getAsJsonObject("automations").getAsJsonObject("weedsSince").addProperty("0",clock.instant().plusSeconds(1).toString());
        var snapshot = gson.fromJson(json,GameSnapshot.class);
        assertThrows(IllegalArgumentException.class, () -> GameState.restoreOnline(clock.instant(),clock.instant(),game.catalog(),snapshot,n -> 99));
    }

    @Test void missingArrivalMetadataWaitsFromFirstObservationAndSurvivesRestart() {
        var clock = new ManualClock();
        var game = new GameState(clock::instant, clock, CropCatalogLoader.load(), n -> 99);
        game.wallet().credit(Currency.GREEN, new java.math.BigDecimal("1000"));
        game.buyVip(); game.buyAutomation("pests");
        var tile = game.farm().tile(0,0); tile.plow(); tile.plant(game.catalog().get("nabo"),clock.instant());
        tile.water(clock.instant()); tile.checkPest(clock.instant(),true);
        var gson = new com.google.gson.Gson();
        var json = gson.toJsonTree(game.snapshot()).getAsJsonObject();
        json.getAsJsonObject("automations").getAsJsonObject("weedsSince").remove("0");
        var snapshot = gson.fromJson(json,GameSnapshot.class);
        var firstAccess = clock.instant().plusSeconds(3600);
        var loaded = GameState.restoreOnline(clock.instant(),firstAccess,game.catalog(),snapshot,n -> 99);
        assertEquals(br.com.colheitafeliz.domain.farm.PestState.WEEDS,loaded.farm().tile(0,0).pest());
        assertEquals(firstAccess.toString(),loaded.snapshot().automations().weedsSince().get("0"));
        loaded = GameState.restoreOnline(firstAccess,firstAccess.plusSeconds(899),game.catalog(),loaded.snapshot(),n -> 99);
        assertEquals(br.com.colheitafeliz.domain.farm.PestState.WEEDS,loaded.farm().tile(0,0).pest());
        loaded = GameState.restoreOnline(firstAccess.plusSeconds(899),firstAccess.plusSeconds(900),game.catalog(),loaded.snapshot(),n -> 99);
        assertEquals(br.com.colheitafeliz.domain.farm.PestState.NONE,loaded.farm().tile(0,0).pest());
        var stable = loaded.snapshot();
        assertEquals(stable,GameState.restoreOnline(firstAccess.plusSeconds(900),firstAccess.plusSeconds(900),game.catalog(),stable,n -> 99).snapshot());
    }

    @Test void serviceExpiresExclusivelyAndVipSuspensionDoesNotExtendIt() {
        var clock = new ManualClock();
        var game = new GameState(clock::instant, clock, CropCatalogLoader.load(), n -> 99);
        game.wallet().credit(Currency.GREEN, new java.math.BigDecimal("1000"));
        game.buyVip(); game.buyAutomation("water"); game.buyAutomation("water");
        var expiry = clock.instant().plusSeconds(5184000);
        clock.advanceSeconds(2592000);
        var tile = game.farm().tile(0,0); tile.plow(); tile.plant(game.catalog().get("nabo"),clock.instant());
        game.runAutomationsNow();
        assertFalse(tile.crop().watered());
        assertFalse(game.buyAutomation("pests").success());
        assertEquals(expiry,game.automationExpiresAt("water"));
        game.buyVip(); game.runAutomationsNow();
        assertTrue(tile.crop().watered());
        clock.advanceSeconds(2592000);
        var other = game.farm().tile(1,0); other.plow(); other.plant(game.catalog().get("nabo"),clock.instant());
        game.runAutomationsNow();
        assertFalse(other.crop().watered());
        assertEquals(expiry,game.automationExpiresAt("water"));
    }

    @Test void fullProductStockAndMissingFeedKeepAnimalResourcesIntact() {
        var clock = new ManualClock();
        var game = new GameState(clock::instant, clock, CropCatalogLoader.load(), n -> 99);
        game.wallet().credit(Currency.GREEN, new java.math.BigDecimal("1000"));
        game.buyVip(); game.buyAutomation("animals");
        for (String id : java.util.List.of("galinha","abelha")) {
            var type = game.animalType(id);
            var lot = new br.com.colheitafeliz.domain.inventory.ItemLot(id,Currency.GOLD,
                    type.prices().get(Currency.GOLD),1,false).withAnimalSale(type.saleTerms(Currency.GOLD));
            game.barn().add(br.com.colheitafeliz.domain.animal.Animal.acquire(type,lot,clock.instant()));
        }
        var chicken = game.animalType("galinha");
        game.inventory().addProduceLot(new br.com.colheitafeliz.domain.inventory.ItemLot("ovo",Currency.GOLD,
                chicken.prices().get(Currency.GOLD),Integer.MAX_VALUE,false).withAnimalSale(chicken.saleTerms(Currency.GOLD)));
        clock.advanceSeconds(40000); game.runAutomationsNow();
        assertEquals(2,game.barn().size());
        assertEquals(Integer.MAX_VALUE,game.inventory().product("ovo"));
        assertEquals(0,game.inventory().feed("producer"));
        assertEquals(br.com.colheitafeliz.domain.animal.Animal.Status.HUNGRY,game.barn().get(1).statusAt(clock.instant()));
        game.inventory().addFeed("producer",1); game.runAutomationsNow();
        assertEquals(0,game.inventory().feed("producer"));
        assertEquals(br.com.colheitafeliz.domain.animal.Animal.Status.PRODUCING,game.barn().get(1).statusAt(clock.instant()));
    }

    @Test void pestCleaningWaitsFifteenMinutesForEachArrivalIncludingSocialChecks() throws Exception {
        var clock = new ManualClock();
        var game = new GameState(clock::instant, clock, CropCatalogLoader.load(), n -> 99);
        game.wallet().credit(Currency.GREEN, new java.math.BigDecimal("1000"));
        game.buyVip();
        var buy = assertDoesNotThrow(() -> GameState.class.getMethod("buyAutomation", String.class));
        buy.invoke(game, "pests");
        var tile = game.farm().tile(0,0);
        tile.plow(); tile.plant(game.catalog().get("nabo"),clock.instant()); tile.water(clock.instant());
        tile.checkPest(clock.instant(),true);
        clock.advanceSeconds(300);
        tile.checkInsects(clock.instant(),true);
        var run = GameState.class.getMethod("runAutomationsNow");
        clock.advanceSeconds(599); run.invoke(game);
        assertEquals(br.com.colheitafeliz.domain.farm.PestState.WEEDS,tile.pest());
        clock.advanceSeconds(1); run.invoke(game);
        assertEquals(br.com.colheitafeliz.domain.farm.PestState.NONE,tile.pest());
        assertTrue(tile.insects()>0);
        clock.advanceSeconds(300); run.invoke(game);
        assertEquals(0,tile.insects());
        assertFalse(tile.crop().paused());
    }

    @Test void animalServiceCollectsOnlyProducersAndNeverCreatesFeedOrSellsAdults() throws Exception {
        var clock = new ManualClock();
        var game = new GameState(clock::instant, clock, CropCatalogLoader.load(), n -> 99);
        game.wallet().credit(Currency.GREEN, new java.math.BigDecimal("1000"));
        game.buyVip();
        var buy = assertDoesNotThrow(() -> GameState.class.getMethod("buyAutomation", String.class));
        buy.invoke(game, "animals");
        for (String id : java.util.List.of("galinha","pombo")) {
            var type = game.animalType(id);
            var lot = new br.com.colheitafeliz.domain.inventory.ItemLot(id,Currency.GOLD,
                    type.prices().get(Currency.GOLD),1,false).withAnimalSale(type.saleTerms(Currency.GOLD));
            game.barn().add(br.com.colheitafeliz.domain.animal.Animal.acquire(type,lot,clock.instant()));
        }
        game.inventory().addFeed("growth",2);
        clock.advanceSeconds(200000);
        GameState.class.getMethod("runAutomationsNow").invoke(game);
        assertEquals(1,game.inventory().product("ovo"));
        assertEquals(1,game.barn().size());
        assertEquals("pombo",game.barn().get(0).type().id());
        assertEquals(br.com.colheitafeliz.domain.animal.Animal.Stage.ADULT,game.barn().get(0).stageAt(clock.instant()));
        assertEquals(0,game.inventory().feed("growth"));
        assertEquals(new java.math.BigDecimal("500.0"),game.wallet().coins());
    }

    @Test void offlineWateringIsChronologicalAndDoesNotHarvest() throws Exception {
        var clock = new ManualClock();
        var game = new GameState(clock::instant, clock, CropCatalogLoader.load(), n -> 99);
        game.wallet().credit(Currency.GREEN, new java.math.BigDecimal("1000"));
        game.buyVip();
        var buy = assertDoesNotThrow(() -> GameState.class.getMethod("buyAutomation", String.class));
        buy.invoke(game, "water");
        game.farm().tile(0,0).plow();
        game.farm().tile(0,0).plant(game.catalog().get("nabo"),clock.instant());
        var run = GameState.class.getMethod("runAutomationsNow");
        run.invoke(game);
        assertTrue(game.farm().tile(0,0).crop().watered());
        var saved = game.snapshot();
        var restore = GameState.class.getMethod("restoreOnline", java.time.Instant.class, java.time.Instant.class,
                br.com.colheitafeliz.domain.crop.CropCatalog.class, GameSnapshot.class,
                br.com.colheitafeliz.domain.farm.RandomSource.class);
        var end = clock.instant().plusSeconds(36000);
        var once = (GameState) restore.invoke(null, clock.instant(),end,game.catalog(),saved,
                (br.com.colheitafeliz.domain.farm.RandomSource) n -> 99);
        var middle = clock.instant().plusSeconds(13000);
        var split = (GameState) restore.invoke(null,clock.instant(),middle,game.catalog(),saved,
                (br.com.colheitafeliz.domain.farm.RandomSource) n -> 99);
        split = (GameState) restore.invoke(null,middle,end,game.catalog(),split.snapshot(),
                (br.com.colheitafeliz.domain.farm.RandomSource) n -> 99);
        assertEquals(br.com.colheitafeliz.domain.farm.TileState.READY, once.farm().tile(0,0).state());
        assertEquals(once.snapshot(), split.snapshot());
        assertEquals(0,once.inventory().produce(game.catalog().get("nabo")));
    }

    @Test void servicePurchaseRequiresVipAndRenewalKeepsIndependentTerms() throws Exception {
        var clock = new ManualClock();
        var game = new GameState(clock::instant, clock, CropCatalogLoader.load(), n -> 99);
        game.wallet().credit(Currency.GREEN, new java.math.BigDecimal("1000"));
        var buy = assertDoesNotThrow(() -> GameState.class.getMethod("buyAutomation", String.class));
        assertFalse(((ActionResult) buy.invoke(game, "water")).success());
        assertTrue(game.buyVip().success());
        assertTrue(((ActionResult) buy.invoke(game, "water")).success());
        var expires = GameState.class.getMethod("automationExpiresAt", String.class);
        assertEquals(clock.instant().plusSeconds(2592000), expires.invoke(game, "water"));
        assertNull(expires.invoke(game, "animals"));
        assertTrue(((ActionResult) buy.invoke(game, "water")).success());
        assertEquals(clock.instant().plusSeconds(5184000), expires.invoke(game, "water"));
    }
}
