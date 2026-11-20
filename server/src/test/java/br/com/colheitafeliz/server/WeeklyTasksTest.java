package br.com.colheitafeliz.server;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WeeklyTasksTest {
    @Test void weekStartsOnSundayMidnightInSaoPaulo() {
        assertEquals(LocalDate.parse("2026-11-22"), WeeklyTasks.weekStart(Instant.parse("2026-11-29T02:59:59Z")));
        assertEquals(LocalDate.parse("2026-11-29"), WeeklyTasks.weekStart(Instant.parse("2026-11-29T03:00:00Z")));
        assertEquals(LocalDate.parse("2026-12-27"), WeeklyTasks.weekStart(Instant.parse("2027-01-01T12:00:00Z")), "virada de ano");
        assertEquals(LocalDate.parse("2027-01-03"), WeeklyTasks.weekStart(Instant.parse("2027-01-03T03:00:00Z")));
    }

    @Test void olderWeekResetsButALaggingClockKeepsTheStoredWeek() {
        var stored = new WeeklyTasks.Progress(LocalDate.parse("2026-11-29"), 12, 5, 30);
        assertEquals(stored, WeeklyTasks.current(stored, Instant.parse("2026-11-29T02:59:59Z")));
        assertEquals(stored, WeeklyTasks.current(stored, Instant.parse("2026-12-05T12:00:00Z")));
        assertEquals(WeeklyTasks.Progress.empty(LocalDate.parse("2026-12-06")), WeeklyTasks.current(stored, Instant.parse("2026-12-06T03:00:00Z")));
        assertEquals(WeeklyTasks.Progress.empty(LocalDate.parse("2026-11-22")), WeeklyTasks.current(null, Instant.parse("2026-11-23T12:00:00Z")));
    }

    @Test void progressStopsAtTheTargetSoTheRewardCannotRepeat() {
        var progress = new WeeklyTasks.Progress(LocalDate.parse("2026-11-22"), 11, 4, 29);
        var full = progress.plusOne(WeeklyTasks.Task.HARVEST).plusOne(WeeklyTasks.Task.BUY_ANIMAL).plusOne(WeeklyTasks.Task.LOVE);
        assertEquals(new WeeklyTasks.Progress(LocalDate.parse("2026-11-22"), 12, 5, 30), full);
        for (var task : WeeklyTasks.all()) assertSame(full, full.plusOne(task));
        assertThrows(IllegalArgumentException.class, () -> WeeklyTasks.Progress.empty(LocalDate.parse("2026-11-23")));
    }
}
