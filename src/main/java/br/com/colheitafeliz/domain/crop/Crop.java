package br.com.colheitafeliz.domain.crop;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Growth is accumulated from an anchor: {@code grownAtAnchorMillis} of growth existed at {@code growthAnchorAt}.
 * After that, moist soil grows at full speed, dry soil at half speed, and weeds freeze growth at {@code pestAt}.
 * {@code wateredAt} is the last watering; the soil dries {@link #moistDuration()} after it.
 */
public record Crop(CropType type, Instant plantedAt, Instant wateredAt, Instant pestAt,
                   Instant growthAnchorAt, long grownAtAnchorMillis) {
    public static final long MIN_MOIST_SECONDS = 300;

    public Crop {
        Objects.requireNonNull(type);
        Objects.requireNonNull(plantedAt);
        if (wateredAt != null && wateredAt.isBefore(plantedAt))
            throw new IllegalArgumentException("Watering predates planting");
        if (pestAt != null && wateredAt == null)
            throw new IllegalArgumentException("Invalid pest timestamp");
        if ((wateredAt == null) != (growthAnchorAt == null) || grownAtAnchorMillis < 0
                || (growthAnchorAt != null && growthAnchorAt.isBefore(wateredAt)))
            throw new IllegalArgumentException("Invalid growth anchor");
    }

    public Crop(CropType type, Instant plantedAt) {
        this(type, plantedAt, null, null, null, 0);
    }

    public boolean watered() { return wateredAt != null; }

    public Duration moistDuration() {
        return Duration.ofSeconds(Math.max(MIN_MOIST_SECONDS, type.growthSeconds() / 3));
    }

    /** Never watered, or the last watering has dried out. */
    public boolean dryAt(Instant now) {
        return !watered() || !now.isBefore(wateredAt.plus(moistDuration()));
    }

    public Crop water(Instant now) {
        if (now.isBefore(plantedAt)) throw new IllegalArgumentException("Watering predates planting");
        if (!dryAt(now)) throw new IllegalStateException("Soil is still moist");
        return new Crop(type, plantedAt, now, pestAt, now, grownMillisAt(now));
    }

    public Crop infect(Instant now) {
        if (!watered() || pestAt != null || now.isBefore(growthAnchorAt))
            throw new IllegalStateException("Crop cannot be infected");
        return new Crop(type, plantedAt, wateredAt, now, growthAnchorAt, grownAtAnchorMillis);
    }

    public Crop removePest(Instant now) {
        if (pestAt == null || now.isBefore(pestAt)) throw new IllegalStateException("No active pest");
        return new Crop(type, plantedAt, wateredAt, null, now, grownMillisAt(now));
    }

    /** Adianta o crescimento (adubo) sem mexer na água: o tempo seguinte segue as mesmas regras. */
    public Crop advance(Instant now, long millis) {
        if (!watered() || millis < 0) throw new IllegalStateException("Crop cannot be advanced");
        return new Crop(type, plantedAt, wateredAt, pestAt, now, grownMillisAt(now) + millis);
    }

    public long grownMillisAt(Instant now) {
        if (!watered()) return 0;
        long total = type.growthTime().toMillis();
        Instant end = pestAt != null && pestAt.isBefore(now) ? pestAt : now;
        if (!end.isAfter(growthAnchorAt)) return Math.min(total, grownAtAnchorMillis);
        Instant dries = wateredAt.plus(moistDuration());
        Instant moistEnd = end.isBefore(dries) ? end : dries;
        long moist = Math.max(0, Duration.between(growthAnchorAt, moistEnd).toMillis());
        long dry = Duration.between(growthAnchorAt, end).toMillis() - moist;
        return Math.min(total, grownAtAnchorMillis + moist + dry / 2);
    }

    public int stageAt(Instant now) {
        long total = type.growthTime().toMillis();
        return 1 + (int) Math.min(type.stages() - 1, grownMillisAt(now) * (type.stages() - 1) / total);
    }

    /** Time until maturity if nobody waters again; weeds are ignored because they are shown separately. */
    public Duration remainingAt(Instant now) {
        if (!watered()) return type.growthTime();
        long left = type.growthTime().toMillis() - grownMillisAt(now);
        if (left <= 0) return Duration.ZERO;
        long moistLeft = Math.max(0, Duration.between(now, wateredAt.plus(moistDuration())).toMillis());
        if (left <= moistLeft) return Duration.ofMillis(left);
        return Duration.ofMillis(moistLeft + (left - moistLeft) * 2);
    }

    public boolean matureAt(Instant now) {
        return watered() && grownMillisAt(now) >= type.growthTime().toMillis();
    }
}
