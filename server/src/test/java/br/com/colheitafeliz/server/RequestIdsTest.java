package br.com.colheitafeliz.server;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RequestIdsTest {
    @Test void immutableTimestampBoundsReplaysWithoutClientClock() {
        var now = Instant.parse("2026-10-29T12:00:00Z");
        UUID id = RequestIds.issue(now);
        assertEquals(7, id.version());
        assertEquals(2, id.variant());
        assertEquals(now.plusSeconds(86400), RequestIds.validate(id, now));
        assertEquals(410, assertThrows(ApiFailure.class, () -> RequestIds.validate(id, now.plusSeconds(86400))).status());
        assertEquals(410, assertThrows(ApiFailure.class, () -> RequestIds.validate(id, now.minusSeconds(1))).status());
        assertEquals(410, assertThrows(ApiFailure.class, () -> RequestIds.validate(UUID.randomUUID(), now)).status());
    }
}
