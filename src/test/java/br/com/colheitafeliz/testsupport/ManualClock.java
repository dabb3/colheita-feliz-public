package br.com.colheitafeliz.testsupport;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

public final class ManualClock extends Clock {
    private Instant instant = Instant.parse("2026-01-01T12:00:00Z");
    public void advanceSeconds(long seconds) { instant = instant.plusSeconds(seconds); }
    @Override public ZoneId getZone() { return ZoneOffset.UTC; }
    @Override public Clock withZone(ZoneId zone) { return this; }
    @Override public Instant instant() { return instant; }
}
