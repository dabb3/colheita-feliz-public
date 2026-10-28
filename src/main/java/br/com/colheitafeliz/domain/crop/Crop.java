package br.com.colheitafeliz.domain.crop;

import br.com.colheitafeliz.domain.inventory.ItemLot;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Growth is accumulated from an anchor: {@code grownAtAnchorMillis} of growth existed at {@code growthAnchorAt}.
 * After that, moist soil grows at full speed, dry soil at half speed, and a pest (weeds or insects) freezes growth
 * at {@code pausedAt}. {@code wateredAt} is the last watering; the soil dries {@link #moistDuration()} after it.
 * Each season of the plant is a fresh crop: {@code plantedAt} is when the season started.
 */
public record Crop(CropType type, Instant plantedAt, Instant wateredAt, Instant pausedAt,
                   Instant growthAnchorAt, long grownAtAnchorMillis, ItemLot origin, int season) {
    public static final long MIN_MOIST_SECONDS = 300;

    public Crop {
        Objects.requireNonNull(type);
        Objects.requireNonNull(plantedAt);
        Objects.requireNonNull(origin);
        if (!origin.itemId().equals(type.id()) || origin.quantity() != 1)
            throw new IllegalArgumentException("Invalid crop origin");
        if (season < 1 || season > type.seasons())
            throw new IllegalArgumentException("Invalid crop season");
        if (wateredAt != null && wateredAt.isBefore(plantedAt))
            throw new IllegalArgumentException("Watering predates planting");
        if (pausedAt != null && wateredAt == null)
            throw new IllegalArgumentException("Invalid pause timestamp");
        if ((wateredAt == null) != (growthAnchorAt == null) || grownAtAnchorMillis < 0
                || (growthAnchorAt != null && growthAnchorAt.isBefore(wateredAt)))
            throw new IllegalArgumentException("Invalid growth anchor");
    }

    /** Primeira temporada de uma semente recém-plantada. */
    public Crop(CropType type, Instant plantedAt, ItemLot origin) {
        this(type, plantedAt, origin, 1);
    }

    /** Início de uma temporada: estágio 1, sem rega e sem pausa. */
    public Crop(CropType type, Instant plantedAt, ItemLot origin, int season) {
        this(type, plantedAt, null, null, null, 0, origin, season);
    }

    public boolean watered() { return wateredAt != null; }
    public boolean paused() { return pausedAt != null; }

    /** Duração nominal da temporada atual. */
    public Duration currentDuration() { return type.growthTime(season); }

    public Duration moistDuration() {
        return Duration.ofSeconds(Math.max(MIN_MOIST_SECONDS, currentDuration().toSeconds() / 3));
    }

    /** Never watered, or the last watering has dried out. */
    public boolean dryAt(Instant now) {
        return !watered() || !now.isBefore(wateredAt.plus(moistDuration()));
    }

    public Crop water(Instant now) {
        if (now.isBefore(plantedAt)) throw new IllegalArgumentException("Watering predates planting");
        if (!dryAt(now)) throw new IllegalStateException("Soil is still moist");
        return new Crop(type, plantedAt, now, pausedAt, now, grownMillisAt(now), origin, season);
    }

    /** Congela o crescimento; pausar o que já está pausado mantém a âncora da primeira condição. */
    public Crop pause(Instant now) {
        if (!watered() || now.isBefore(growthAnchorAt)) throw new IllegalStateException("Crop cannot be paused");
        if (paused()) return this;
        return new Crop(type, plantedAt, wateredAt, now, growthAnchorAt, grownAtAnchorMillis, origin, season);
    }

    /** Retoma o crescimento a partir de agora, com o progresso que havia na pausa. */
    public Crop resume(Instant now) {
        if (!paused() || now.isBefore(pausedAt)) throw new IllegalStateException("Crop is not paused");
        return new Crop(type, plantedAt, wateredAt, null, now, grownMillisAt(now), origin, season);
    }

    /** Adianta o crescimento (fertilizante) sem mexer na água: o tempo seguinte segue as mesmas regras. */
    public Crop advance(Instant now, long millis) {
        if (!watered() || millis < 0) throw new IllegalStateException("Crop cannot be advanced");
        return new Crop(type, plantedAt, wateredAt, pausedAt, now, grownMillisAt(now) + millis, origin, season);
    }

    public long grownMillisAt(Instant now) {
        if (!watered()) return 0;
        long total = currentDuration().toMillis();
        Instant end = pausedAt != null && pausedAt.isBefore(now) ? pausedAt : now;
        if (!end.isAfter(growthAnchorAt)) return Math.min(total, grownAtAnchorMillis);
        Instant dries = wateredAt.plus(moistDuration());
        Instant moistEnd = end.isBefore(dries) ? end : dries;
        long moist = Math.max(0, Duration.between(growthAnchorAt, moistEnd).toMillis());
        long dry = Duration.between(growthAnchorAt, end).toMillis() - moist;
        return Math.min(total, grownAtAnchorMillis + moist + dry / 2);
    }

    public int stageAt(Instant now) {
        long total = currentDuration().toMillis();
        return 1 + (int) Math.min(type.stages() - 1, grownMillisAt(now) * (type.stages() - 1) / total);
    }

    /** Time until maturity if nobody waters again; pests are ignored because they are shown separately. */
    public Duration remainingAt(Instant now) {
        if (!watered()) return currentDuration();
        long left = currentDuration().toMillis() - grownMillisAt(now);
        if (left <= 0) return Duration.ZERO;
        long moistLeft = Math.max(0, Duration.between(now, wateredAt.plus(moistDuration())).toMillis());
        if (left <= moistLeft) return Duration.ofMillis(left);
        return Duration.ofMillis(moistLeft + (left - moistLeft) * 2);
    }

    public boolean matureAt(Instant now) {
        return watered() && grownMillisAt(now) >= currentDuration().toMillis();
    }
}
