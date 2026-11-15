package br.com.colheitafeliz.game;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DailyMissionBoardTest {
    @Test void gamePaymentsUseUtcEvenWhenTheInjectedClockHasAnotherZone() {
        var clock = java.time.Clock.fixed(java.time.Instant.parse("2026-10-28T23:30:00Z"), java.time.ZoneOffset.ofHours(14));
        var game = new GameState(clock::instant, clock, CropCatalogLoader.load(), bound -> 99);
        assertTrue(game.buySeed(game.catalog().get("nabo")).success());
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
    @Test void cooperativeMissionNeedsTwoActionsPersistsPartialProgressAndPaysBonusOnce() {
        var day = LocalDate.of(2026, 11, 14);
        var board = new DailyMissionBoard(day, true);
        var nature = board.missions().stream().filter(m -> m.id().equals("nature")).findFirst().orElseThrow();
        assertEquals(2, nature.target());
        assertEquals(0, nature.green());
        assertTrue(board.record("weed", 1, day).stream().noneMatch(m -> m.id().equals("nature")));
        assertTrue(board.record("friend-pest", 1, day).isEmpty());
        assertEquals(1, board.progress(nature));
        var partial = new DailyMissionBoard(day, true);
        partial.restore(day.toString(), board.progressSnapshot(), board.completedSnapshot(), day);
        assertEquals(1, partial.progress(nature));
        assertFalse(partial.cooperativeBonusPaid());
        var rewards = partial.record("friend-pest", 1, day);
        assertEquals(java.util.List.of(nature, DailyMissionBoard.COOPERATIVE_BONUS), rewards);
        assertTrue(partial.record("friend-pest", 1, day).isEmpty());
        var paid = new DailyMissionBoard(day, true);
        paid.restore(day.toString(), partial.progressSnapshot(), partial.completedSnapshot(), day);
        assertTrue(paid.cooperativeBonusPaid());
        assertTrue(paid.record("friend-pest", 1, day).isEmpty());
        assertEquals(java.util.List.of(nature, DailyMissionBoard.COOPERATIVE_BONUS), paid.record("friend-pest", 2, day.plusDays(1)));
    }
    @Test void oldSavesLoadWithoutCooperativeKeysAndInvalidValuesAreRejected() {
        var day = LocalDate.of(2026, 11, 14);
        var online = new DailyMissionBoard(day, true);
        online.restore(day.toString(), java.util.Map.of("care", 1), java.util.List.of("care"), day);
        assertEquals(0, online.progress(online.missions().getLast()));
        String bonus = DailyMissionBoard.COOPERATIVE_BONUS.id();
        for (var invalid : java.util.List.<Runnable>of(
                () -> new DailyMissionBoard(day, true).restore(day.toString(), java.util.Map.of("nature", -1), java.util.List.of(), day),
                () -> new DailyMissionBoard(day, true).restore(day.toString(), java.util.Map.of("nature", 3), java.util.List.of("nature"), day),
                () -> new DailyMissionBoard(day, true).restore(day.toString(), java.util.Map.of("nature", 1), java.util.List.of("nature"), day),
                () -> new DailyMissionBoard(day, true).restore(day.toString(), java.util.Map.of("nature", 2), java.util.List.of(), day),
                () -> new DailyMissionBoard(day, true).restore(day.toString(), java.util.Map.of("nature", 2), java.util.List.of("nature"), day),
                () -> new DailyMissionBoard(day, true).restore(day.toString(), java.util.Map.of(), java.util.List.of(bonus), day),
                () -> new DailyMissionBoard(day, true).restore(day.toString(), java.util.Map.of("other", 0), java.util.List.of(), day),
                () -> new DailyMissionBoard(day).restore(day.toString(), java.util.Map.of("nature", 1), java.util.List.of(), day),
                () -> new DailyMissionBoard(day).restore(day.toString(), java.util.Map.of("nature", 2), java.util.List.of("nature", bonus), day)))
            assertThrows(IllegalArgumentException.class, invalid::run);
    }
    @Test void desktopBoardHidesCooperativeMissionsAndOnlineFarmShowsThem() {
        var day = LocalDate.of(2026, 11, 14);
        var desktop = new DailyMissionBoard(day);
        assertEquals(java.util.List.of("care", "trade"), desktop.missions().stream().map(DailyMissionBoard.Mission::id).toList());
        assertTrue(desktop.record("friend-pest", 2, day).isEmpty());
        assertTrue(desktop.completedSnapshot().isEmpty());
        var clock = java.time.Clock.fixed(java.time.Instant.parse("2026-11-14T12:00:00Z"), java.time.ZoneOffset.UTC);
        assertFalse(new GameState(clock::instant, clock, CropCatalogLoader.load(), bound -> 99).dailyMissions().cooperative());
        var online = GameState.newOnline(clock, CropCatalogLoader.load(), bound -> 99);
        assertTrue(GameState.restoreOnline(clock.instant(), clock.instant(), online.catalog(), online.snapshot(), bound -> 99).dailyMissions().cooperative());
        assertFalse(GameState.restore(clock::instant, clock, online.catalog(), online.snapshot(), bound -> 99).dailyMissions().cooperative());
    }
}
