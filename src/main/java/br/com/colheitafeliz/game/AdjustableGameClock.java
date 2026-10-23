package br.com.colheitafeliz.game;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public final class AdjustableGameClock implements GameClock {
    private final Clock source;
    private Instant realAnchor;
    private Instant gameAnchor;
    private double speed = 1.0;

    public AdjustableGameClock(Clock source) {
        this(source, source.instant());
    }

    public AdjustableGameClock(Clock source, Instant initialGameTime) {
        this.source = Objects.requireNonNull(source);
        realAnchor = source.instant();
        gameAnchor = Objects.requireNonNull(initialGameTime);
    }

    @Override public Instant now() {
        long nanos = Duration.between(realAnchor, source.instant()).toNanos();
        return gameAnchor.plusNanos((long) (nanos * speed));
    }

    public void setSpeed(double speed) {
        if (!Double.isFinite(speed) || speed < 0) throw new IllegalArgumentException("Invalid speed");
        gameAnchor = now();
        realAnchor = source.instant();
        this.speed = speed;
    }

    public double speed() { return speed; }
}
