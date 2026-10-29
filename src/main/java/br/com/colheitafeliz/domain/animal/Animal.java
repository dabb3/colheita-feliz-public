package br.com.colheitafeliz.domain.animal;

import br.com.colheitafeliz.domain.inventory.ItemLot;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Progresso avança apenas durante a refeição; a validade do protetor usa o relógio real. */
public record Animal(AnimalType type, ItemLot origin, Instant acquiredAt, Instant growthAnchorAt,
                     long grownAtAnchorMillis, Instant fedUntil) {
    public enum Status { HUNGRY, PRODUCING, READY }
    public enum Stage { BABY, YOUNG, ADULT }
    public Animal {
        Objects.requireNonNull(type); Objects.requireNonNull(origin); Objects.requireNonNull(acquiredAt);
        Objects.requireNonNull(growthAnchorAt); Objects.requireNonNull(fedUntil);
        if (!origin.itemId().equals(type.id()) || origin.quantity() != 1 || origin.vipOnly()
                || grownAtAnchorMillis < 0 || grownAtAnchorMillis > type.requiredMillis()
                || fedUntil.isBefore(growthAnchorAt)
                || Duration.between(growthAnchorAt, fedUntil).compareTo(type.foodDuration()) > 0)
            throw new IllegalArgumentException("Invalid animal state");
    }
    public static Animal acquire(AnimalType type, ItemLot origin, Instant now) { return acquire(type, origin, now, now); }
    public static Animal acquire(AnimalType type, ItemLot origin, Instant realNow, Instant simulationNow) {
        return new Animal(type, origin, realNow, simulationNow, 0, simulationNow.plus(type.foodDuration()));
    }
    public long grownMillisAt(Instant now) {
        Instant until = now.isBefore(fedUntil) ? now : fedUntil;
        long elapsed = until.isAfter(growthAnchorAt) ? Duration.between(growthAnchorAt, until).toMillis() : 0;
        return Math.min(type.requiredMillis(), Math.addExact(grownAtAnchorMillis, elapsed));
    }
    public boolean hungryAt(Instant now) { return !now.isBefore(fedUntil); }
    public Status statusAt(Instant now) {
        if (type.kind() != AnimalType.Kind.PROTECTOR && grownMillisAt(now) >= type.requiredMillis()) return Status.READY;
        return hungryAt(now) ? Status.HUNGRY : Status.PRODUCING;
    }
    public Stage stageAt(Instant now) {
        if (type.kind() != AnimalType.Kind.GROWING) return Stage.ADULT;
        long grown = grownMillisAt(now);
        return grown < type.babySeconds() * 1000 ? Stage.BABY : grown < type.requiredMillis() ? Stage.YOUNG : Stage.ADULT;
    }
    public Duration remainingAt(Instant now) { return Duration.ofMillis(type.requiredMillis() - grownMillisAt(now)); }
    public Animal feed(Instant now) {
        if (!hungryAt(now) || (type.kind() != AnimalType.Kind.PROTECTOR && statusAt(now) == Status.READY))
            throw new IllegalStateException("Animal does not need food");
        return new Animal(type, origin, acquiredAt, now, grownMillisAt(now), now.plus(type.foodDuration()));
    }
    public Instant expiresAt() { return acquiredAt.plus(Duration.ofDays(30)); }
    public boolean expiredAt(Instant realNow) { return type.kind() == AnimalType.Kind.PROTECTOR && !realNow.isBefore(expiresAt()); }
    public boolean activeAt(Instant now) { return activeAt(now, now); }
    public boolean activeAt(Instant realNow, Instant simulationNow) {
        return type.kind() == AnimalType.Kind.PROTECTOR && !expiredAt(realNow) && !hungryAt(simulationNow);
    }
}
