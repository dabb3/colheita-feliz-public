package br.com.colheitafeliz.server;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** UUIDv7 keeps its expiry inseparable from the id, even after its receipt is deleted. */
final class RequestIds {
    static final Duration REPLAY_WINDOW = Duration.ofHours(24);
    private RequestIds() {}
    static UUID issue(Instant now) {
        UUID random = UUID.randomUUID();
        return new UUID((now.toEpochMilli() << 16) | 0x7000L | (random.getMostSignificantBits() & 0xfffL),
                (random.getLeastSignificantBits() & 0x3fffffffffffffffL) | 0x8000000000000000L);
    }
    static Instant validate(UUID id, Instant now) {
        if (id == null || id.version() != 7 || id.variant() != 2)
            throw new ApiFailure(410, "Identificador antigo. Atualize a fazenda para conferir o resultado antes de uma nova ação.");
        Instant issued = Instant.ofEpochMilli(id.getMostSignificantBits() >>> 16);
        Instant expires = issued.plus(REPLAY_WINDOW);
        if (now.isBefore(issued) || !now.isBefore(expires))
            throw new ApiFailure(410, "Prazo de confirmação encerrado. Atualize a fazenda antes de uma nova ação.");
        return expires;
    }
}
