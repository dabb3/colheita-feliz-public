package br.com.colheitafeliz.server;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RequestTimingsTest {
    @Test void timingsAreScopedToOneRequestAndContainOnlyDurations() {
        assertNull(RequestTimings.header());
        try (var timing = RequestTimings.start()) {
            assertNotNull(timing);
            RequestTimings.auth(System.nanoTime() - 2_000_000);
            RequestTimings.connection(System.nanoTime() - 1_000_000);
            RequestTimings.database(System.nanoTime() - 3_000_000);
            assertTrue(RequestTimings.header().matches("auth;dur=[0-9.]+, db_connect;dur=[0-9.]+, db;dur=[0-9.]+, app;dur=[0-9.]+"));
        }
        assertNull(RequestTimings.header());
    }
}
