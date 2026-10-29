package br.com.colheitafeliz.game;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DailyMissionBoardTest {
    @Test void gamePaymentsUseUtcEvenWhenTheInjectedClockHasAnotherZone() {
        var clock = java.time.Clock.fixed(java.time.Instant.parse("2026-10-28T23:30:00Z"), java.time.ZoneOffset.ofHours(14));
        var game = new GameState(clock::instant, clock, CropCatalogLoader.load(), bound -> 99);
        assertTrue(game.buyFeed("growth").success());
        assertEquals(LocalDate.of(2026, 10, 28), game.dailyMissions().day());
    }
    @Test void onlyTwoCategoriesPayOnceAndRollbackCannotReopenPaidDays() {
        var day = LocalDate.of(2026, 10, 28);
        var board = new DailyMissionBoard(day);
        var sale = new DailyMissionBoard(day);
        assertEquals("trade", sale.record("animal-sale", 1, day).getFirst().id());
        assertEquals(java.util.List.of("trade"), sale.completedSnapshot());
        assertTrue(board.record("plow", 1, day).isEmpty());
        assertTrue(board.record("plant", 0, day).isEmpty());
        assertEquals("trade", board.record("buy", 1, day).getFirst().id());
        assertEquals("care", board.record("plant", 1, day).getFirst().id());
        for (String action : new String[]{"sell", "water", "collect", "feed", "animal-sale"})
            assertTrue(board.record(action, 1, day).isEmpty());
        var restored = new DailyMissionBoard(day.minusDays(1));
        restored.restore(board.day().toString(), board.progressSnapshot(), board.completedSnapshot(), day.minusDays(1));
        assertTrue(restored.record("buy", 1, day.minusDays(1)).isEmpty());
        assertTrue(restored.record("plant", 1, day).isEmpty());
        assertEquals(1, restored.record("plant", 1, day.plusDays(4)).size());
        assertEquals(1, restored.record("sell", 1, day.plusDays(4)).size());
        assertTrue(restored.record("plant", 1, day.plusDays(3)).isEmpty());
        assertEquals(day.plusDays(4), restored.day());
    }
}
