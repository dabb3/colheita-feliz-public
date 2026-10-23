package br.com.colheitafeliz.domain.animal;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Um animal do cercado. Sem ração ({@code fedAt} nulo) ele não produz; alimentado, o produto fica pronto
 * depois de {@link AnimalType#productionTime()}, também com o jogo fechado. Coletar volta para a fome.
 */
public record Animal(AnimalType type, Instant fedAt) {
    public enum Status { HUNGRY, PRODUCING, READY }

    public Animal {
        Objects.requireNonNull(type);
    }

    public static Animal hungry(AnimalType type) { return new Animal(type, null); }

    public Status statusAt(Instant now) {
        if (fedAt == null) return Status.HUNGRY;
        return now.isBefore(readyAt()) ? Status.PRODUCING : Status.READY;
    }

    public Instant readyAt() { return fedAt == null ? null : fedAt.plus(type.productionTime()); }

    public Duration remainingAt(Instant now) {
        if (fedAt == null) return type.productionTime();
        Duration left = Duration.between(now, readyAt());
        return left.isNegative() ? Duration.ZERO : left;
    }

    public Animal feed(Instant now) {
        if (fedAt != null) throw new IllegalStateException("Animal already fed");
        return new Animal(type, now);
    }

    public Animal collect(Instant now) {
        if (statusAt(now) != Status.READY) throw new IllegalStateException("Nothing to collect");
        return hungry(type);
    }
}
