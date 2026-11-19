package br.com.colheitafeliz.server;

import com.google.gson.JsonObject;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AwayLedgerTest {
    private static final Instant SEEN = Instant.parse("2026-11-13T12:00:00Z");

    private static AwayLedger.Event theft(int minute) {
        return new AwayLedger.Event("THEFT", SEEN.plusSeconds(minute * 60L).toString(), "Bob", "morango", 1, null);
    }

    @Test void keepsTwentyMostRecentEvents() {
        var ledger = AwayLedger.empty(SEEN);
        for (int i = 0; i < 25; i++) ledger = ledger.withEvent(theft(i));
        assertEquals(20, ledger.events().size());
        assertTrue(ledger.events().stream().noneMatch(e -> e.at().equals(theft(4).at())));
        assertTrue(ledger.events().stream().anyMatch(e -> e.at().equals(theft(24).at())));
    }

    @Test void roundTripsJson() {
        var ledger = AwayLedger.empty(SEEN).withEvent(theft(1))
                .withEvent(new AwayLedger.Event("DEFENDED", SEEN.plusSeconds(120).toString(), "Carol", null, 0, "cachorro-robo"));
        ledger = new AwayLedger(SEEN, 18, 6, 3, Map.of("ovo", 3), ledger.events());
        assertEquals(ledger, AwayLedger.fromJson(SEEN, ledger.toJson()));
        assertTrue(ledger.hasActivity());
        assertFalse(AwayLedger.empty(SEEN).hasActivity());
    }

    @Test void invalidJsonFallsBackToEmpty() {
        var json = new JsonObject(); json.addProperty("waterings", "muitas");
        assertEquals(AwayLedger.empty(SEEN), AwayLedger.fromJson(SEEN, json));
        var badEvent = AwayLedger.empty(SEEN).withEvent(theft(1)).toJson();
        badEvent.getAsJsonArray("events").get(0).getAsJsonObject().addProperty("kind", "PARTY");
        assertEquals(AwayLedger.empty(SEEN), AwayLedger.fromJson(SEEN, badEvent));
    }

    @Test void plusSumsTallyAndProducts() {
        var first = new AwayLedger(SEEN, 1, 2, 3, Map.of("ovo", 1), java.util.List.of());
        var sum = first.plus(4, 5, 6, Map.of("ovo", 2, "leite", 1));
        assertEquals(5, sum.waterings()); assertEquals(7, sum.pestsRemoved()); assertEquals(9, sum.animalCares());
        assertEquals(Map.of("ovo", 3, "leite", 1), sum.collected());
    }
}
