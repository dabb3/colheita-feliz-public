package br.com.colheitafeliz.server;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
class AwayReportIntegrationTest {
    private final UUID owner = UUID.randomUUID(), friend = UUID.randomUUID();
    private final ProtectorBiteIntegrationTest.TestClock clock = new ProtectorBiteIntegrationTest.TestClock();
    private JdbcOnlineStore store; private OnlineGameService service;

    @BeforeEach void setup() {
        String url = System.getenv("TEST_DATABASE_URL"); assertNotNull(url);
        assertTrue(url.startsWith("jdbc:postgresql://127.0.0.1:") && url.contains("/colheita_test"));
        store = new JdbcOnlineStore(url, "postgres", "", "test_" + UUID.randomUUID().toString().replace("-", ""));
        store.initialize(owner, clock.instant());
        service = new OnlineGameService(store, clock, bound -> bound - 1, 20);
        service.farm(owner);
        var invite = service.createInvitation(owner, RequestIds.issue(clock.instant())).get("code").getAsString();
        service.acceptInvitation(friend, "Amiga", invite, RequestIds.issue(clock.instant()));
        service.farm(friend);
    }
    @AfterEach void close() { if (store != null) store.close(); }

    @Test void awayLedgerPersistsAndUpserts() {
        Instant seen = clock.instant();
        var ledger = new AwayLedger(seen, 2, 1, 0, Map.of("ovo", 1),
                List.of(new AwayLedger.Event("THEFT", seen.toString(), "Bob", "morango", 2, null)));
        store.transaction(owner, tx -> { tx.saveAwayLedger(owner, ledger); return null; });
        assertEquals(ledger, store.transaction(owner, tx -> tx.awayLedger(owner)));
        var reset = AwayLedger.empty(seen.plusSeconds(60));
        store.transaction(owner, tx -> { tx.saveAwayLedger(owner, reset); return null; });
        assertEquals(reset, store.transaction(owner, tx -> tx.awayLedger(owner)));
    }
}
