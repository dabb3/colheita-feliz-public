package br.com.colheitafeliz.render;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class DayPeriodTest {
    @ParameterizedTest
    @CsvSource({"00:00:00,NIGHT", "05:59:59,NIGHT", "06:00:00,DAY", "12:00:00,DAY",
            "16:59:59,DAY", "17:00:00,SUNSET", "18:30:00,SUNSET", "18:30:59,SUNSET",
            "18:31:00,NIGHT", "23:59:59,NIGHT"})
    void changesAtTheExactRequestedLocalTimes(String time, DayPeriod expected) {
        Clock clock = Clock.fixed(Instant.parse("2026-10-29T" + time + "Z"), ZoneOffset.UTC);
        assertEquals(expected, DayPeriod.at(clock));
    }

    @Test void usesTheComputerLocalZoneInsteadOfUtc() {
        Instant now = Instant.parse("2026-10-29T20:00:00Z");
        assertEquals(DayPeriod.NIGHT, DayPeriod.at(Clock.fixed(now, ZoneOffset.UTC)));
        assertEquals(DayPeriod.SUNSET, DayPeriod.at(Clock.fixed(now, ZoneId.of("America/Sao_Paulo"))));
    }
}
