package br.com.colheitafeliz.persistence;

import br.com.colheitafeliz.game.CropCatalogLoader;
import br.com.colheitafeliz.game.GameState;
import br.com.colheitafeliz.domain.economy.Currency;
import br.com.colheitafeliz.testsupport.ManualClock;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class AutomationSaveTest {
    @TempDir Path directory;
    @Test void termsAndArrivalRoundTripAndMalformedMetadataCannotBeOverwritten() throws Exception {
        var clock = new ManualClock();
        var game = new GameState(clock::instant,clock,CropCatalogLoader.load(),n -> 99);
        game.wallet().credit(Currency.GREEN,new java.math.BigDecimal("1000"));
        game.buyVip(); game.buyAutomation("pests");
        var tile = game.farm().tile(0,0); tile.plow(); tile.plant(game.catalog().get("nabo"),clock.instant());
        tile.water(clock.instant()); tile.checkPest(clock.instant(),true); tile.checkInsects(clock.instant(),true);
        var snapshot = game.snapshot();
        var file = directory.resolve("automation.json"); var repository = new JsonSaveRepository(file);
        repository.save(snapshot,clock.instant(),clock.instant());
        assertEquals(snapshot,repository.load().orElseThrow().snapshot());
        var json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        assertEquals("game-rules-2026-11-vip-services-v1",json.get("ruleset").getAsString());
        json.getAsJsonObject("snapshot").getAsJsonObject("automations").getAsJsonObject("expiresAt").addProperty("pests",true);
        Files.writeString(file,json.toString()); var corrupted = Files.readString(file);
        assertThrows(java.io.IOException.class,repository::load);
        assertThrows(java.io.IOException.class,() -> repository.save(snapshot,clock.instant(),clock.instant()));
        assertEquals(corrupted,Files.readString(file));
    }
}
