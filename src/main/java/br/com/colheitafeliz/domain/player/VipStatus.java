package br.com.colheitafeliz.domain.player;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record VipStatus(Instant expiresAt) {
    public boolean activeAt(Instant now) {
        return expiresAt != null && Objects.requireNonNull(now).isBefore(expiresAt);
    }

    public VipStatus purchase(Instant now) {
        Objects.requireNonNull(now);
        return new VipStatus((activeAt(now) ? expiresAt : now).plus(Duration.ofDays(30)));
    }
}
