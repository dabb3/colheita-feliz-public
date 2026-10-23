package br.com.colheitafeliz.game;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AdjustableGameClockTest {
    @Test void speedChangesWithoutJumpingAndCanPause() {
        MutableClock source = new MutableClock();
        AdjustableGameClock game = new AdjustableGameClock(source);
        source.advanceSeconds(2);
        assertEquals(Instant.EPOCH.plusSeconds(2), game.now());
        game.setSpeed(60);
        assertEquals(Instant.EPOCH.plusSeconds(2), game.now());
        source.advanceSeconds(3);
        assertEquals(Instant.EPOCH.plusSeconds(182), game.now());
        game.setSpeed(0);
        source.advanceSeconds(10);
        assertEquals(Instant.EPOCH.plusSeconds(182), game.now());
        assertThrows(IllegalArgumentException.class, () -> game.setSpeed(-1));
    }

    private static final class MutableClock extends Clock {
        private Instant instant = Instant.EPOCH;
        void advanceSeconds(long seconds) { instant = instant.plusSeconds(seconds); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
