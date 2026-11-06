package br.com.colheitafeliz.persistence;

import br.com.colheitafeliz.game.*;
import com.google.gson.JsonParser;
import java.time.*;
import java.nio.file.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class ChristmasSaveTest {
    @TempDir Path dir;
    @org.junit.jupiter.api.Test void validHistoricalSaveWithoutGiftMetadataRemainsEligible() throws Exception {
        var clock=Clock.fixed(Christmas2026.GIFT_START,ZoneOffset.UTC);var game=new GameState(clock::instant,clock,CropCatalogLoader.load(),bound->99);
        var file=dir.resolve("historical.json");var repo=new JsonSaveRepository(file);repo.save(game.snapshot(),clock.instant(),clock.instant());
        var json=JsonParser.parseString(Files.readString(file)).getAsJsonObject();json.addProperty("ruleset",GameSave.ANIMALS_RULESET);json.getAsJsonObject("snapshot").remove("christmas2026");Files.writeString(file,json.toString());
        var restored=GameState.restore(clock::instant,clock,CropCatalogLoader.load(),repo.load().orElseThrow().snapshot(),bound->99);
        assertTrue(restored.claimChristmasGift());repo.save(restored.snapshot(),clock.instant(),clock.instant());
        restored=GameState.restore(clock::instant,clock,CropCatalogLoader.load(),repo.load().orElseThrow().snapshot(),bound->99);
        assertFalse(restored.claimChristmasGift());assertEquals(1,restored.inventory().seeds(restored.catalog().get("mini-bis")));
    }
    @ParameterizedTest
    @ValueSource(strings={"falseReceipt","missingReceipt","missingCursor","wrongCursor","lateRegistration","booleanString","date"})
    void invalidGiftReceiptCannotLoadOrOverwrite(String corruption) throws Exception {
        var clock=Clock.fixed(Christmas2026.GIFT_START,ZoneOffset.UTC);var g=new GameState(clock::instant,clock,CropCatalogLoader.load(),bound->99);
        assertTrue(g.claimChristmasGift(Instant.EPOCH));var file=dir.resolve("gift.json");var repo=new JsonSaveRepository(file);repo.save(g.snapshot(),clock.instant(),clock.instant());
        var json=JsonParser.parseString(Files.readString(file)).getAsJsonObject();var snapshot=json.getAsJsonObject("snapshot");var gift=snapshot.getAsJsonObject("christmas2026");var cursors=snapshot.getAsJsonObject("notifications").getAsJsonObject("observed");
        switch(corruption){
            case "falseReceipt"->gift.addProperty("giftGranted",false);
            case "missingReceipt"->snapshot.remove("christmas2026");
            case "missingCursor"->cursors.remove("christmas-gift-2026");
            case "wrongCursor"->cursors.addProperty("christmas-gift-2026","2025");
            case "lateRegistration"->gift.addProperty("registeredAt",Christmas2026.REGISTRATION_CUTOFF.toString());
            case "booleanString"->gift.addProperty("giftGranted","true");
            case "date"->gift.addProperty("registeredAt","not-a-date");
            default->throw new AssertionError(corruption);
        }
        Files.writeString(file,json.toString());var original=Files.readAllBytes(file);
        assertThrows(java.io.IOException.class,repo::load);assertThrows(java.io.IOException.class,()->repo.save(g.snapshot(),clock.instant(),clock.instant()));assertArrayEquals(original,Files.readAllBytes(file));
    }
}
