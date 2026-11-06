package br.com.colheitafeliz.persistence;

import br.com.colheitafeliz.domain.crop.Crop;
import br.com.colheitafeliz.domain.economy.Currency;
import br.com.colheitafeliz.domain.farm.TileState;
import br.com.colheitafeliz.domain.inventory.ItemLot;
import br.com.colheitafeliz.game.CropCatalogLoader;
import br.com.colheitafeliz.game.GameSession;
import br.com.colheitafeliz.game.GameState;
import br.com.colheitafeliz.testsupport.ManualClock;
import br.com.colheitafeliz.testsupport.TestCrops;
import com.google.gson.JsonParser;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class CropEconomyMigrationTest {
    @TempDir Path directory;

    @Test void oldPlantsAndProductsRetainV0WhileOwnedSeedsPlantedAfterUpgradeUseV1() throws Exception {
        var clock = new ManualClock();
        var catalog = CropCatalogLoader.load();
        var type = catalog.get("tomate");
        var origin = new ItemLot(type.id(), Currency.GOLD, new BigDecimal("950"), 1, false);
        var old = new GameState(clock::instant, clock, catalog, bound -> 99);
        old.inventory().addProduceLot(origin);
        old.inventory().addSeedLot(origin);
        old.farm().tile(0, 0).restore(TileState.GROWING, new Crop(type, clock.instant(), origin));
        Path path = directory.resolve("upgrade.json");
        var repository = new JsonSaveRepository(path);
        repository.save(old.snapshot(), clock.instant(), clock.instant());
        var json = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        json.addProperty("ruleset", GameSave.PREVIOUS_RULESET);
        Files.writeString(path, json.toString());
        var restored = GameSession.open(clock, catalog, repository, bound -> 99);
        var state = restored.state();
        assertNull(state.farm().tile(0, 0).crop().origin().cropSale());
        assertNull(state.inventory().produceLots().getFirst().cropSale());
        var first = state.farm().tile(0, 0);
        var ready = TestCrops.growUntilReady(first, clock.instant());
        var harvested = first.harvest(ready);
        assertNull(harvested.cropSale());
        assertNull(first.crop().origin().cropSale(), "later seasons of an old plant also remain v0");
        state.inventory().addProduceLot(harvested);
        var second = state.farm().tile(0, 1);
        second.plow();
        second.plant(type, clock.instant(), state.inventory().takeSeed(type.id()));
        assertEquals(1, second.crop().origin().cropSale().economyVersion());
        state.inventory().addProduceLot(second.harvest(TestCrops.growUntilReady(second, clock.instant())));
        restored.save();
        assertEquals(GameSave.RULESET, JsonParser.parseString(Files.readString(path)).getAsJsonObject().get("ruleset").getAsString());
        var again = GameSession.open(clock, catalog, repository, bound -> 99).state();
        assertEquals(state.inventory().produceLots(), again.inventory().produceLots());
        assertEquals(new BigDecimal("2864.0"), again.depositTotals().get(Currency.GOLD)); // 2 v0 + first v1.
    }

    @Test void newRulesetIsRefusedByPreviousReaderAndUnknownTermsAreNeverTreatedAsLegacy() throws Exception {
        var clock = new ManualClock();
        Path path = directory.resolve("versioned.json");
        var repository = new JsonSaveRepository(path);
        var session = GameSession.open(clock, CropCatalogLoader.load(), repository, bound -> 99);
        var type = session.state().catalog().get("coco");
        var lot = type.plantingOrigin(new ItemLot("coco", Currency.GREEN, new BigDecimal("20"), 1, true)).harvested(6);
        session.state().inventory().addProduceLot(lot);
        session.save();
        assertThrows(java.io.IOException.class, () -> new br.com.colheitafeliz.persistence.stage5.Stage5JsonSaveRepository(path).load());
        String original = Files.readString(path);
        Files.writeString(path, original.replace("\"economyVersion\": 1", "\"economyVersion\": 2"));
        byte[] invalid = Files.readAllBytes(path);
        assertThrows(java.io.IOException.class, repository::load);
        assertArrayEquals(invalid, Files.readAllBytes(path));
    }
}
