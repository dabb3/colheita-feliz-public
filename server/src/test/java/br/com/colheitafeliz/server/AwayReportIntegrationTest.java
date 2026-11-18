package br.com.colheitafeliz.server;

import br.com.colheitafeliz.domain.animal.Animal;
import br.com.colheitafeliz.domain.animal.AnimalType;
import br.com.colheitafeliz.domain.crop.Crop;
import br.com.colheitafeliz.domain.economy.Currency;
import br.com.colheitafeliz.domain.farm.PestState;
import br.com.colheitafeliz.domain.farm.TileState;
import br.com.colheitafeliz.domain.inventory.ItemLot;
import br.com.colheitafeliz.game.CropCatalogLoader;
import br.com.colheitafeliz.game.GameState;
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

    private void edit(UUID player, java.util.function.Consumer<GameState> change) {
        store.transaction(player, tx -> { tx.lockFarms(player); var saved = tx.farm(player);
            var game = GameState.restore(clock::instant, clock, CropCatalogLoader.load(), saved.snapshot(), bound -> bound - 1);
            change.accept(game); tx.save(player, game.snapshot(), saved.revision() + 1, clock.instant()); return null; });
    }
    private AwayLedger ledger(UUID player) { return store.transaction(player, tx -> tx.awayLedger(player)); }
    /** A carrot on plot 0 matured ten minutes ago, past the owner's exclusive window; optionally guarded. */
    private void matureCrop(boolean guarded) {
        edit(owner, game -> {
            var type = game.catalog().get("cenoura");
            var origin = type.plantingOrigin(new ItemLot(type.id(), Currency.GOLD, type.seedPrices().get(Currency.GOLD), 1, false));
            var at = clock.instant().minusSeconds(600);
            var crop = new Crop(type, at, origin).water(at).advance(at, type.growthTime(1).toMillis());
            var tile = game.farm().tile(0, 0);
            tile.restore(TileState.READY, crop, PestState.NONE, null, 0, null, null, false, tile.harvestRevision() + 1);
            game.barn().restore(List.of());
            if (guarded) game.animalTypes().stream().filter(t -> t.kind() == AnimalType.Kind.PROTECTOR).limit(5).forEach(t ->
                    game.barn().add(Animal.acquire(t, new ItemLot(t.id(), Currency.GOLD, t.prices().get(Currency.GOLD), 1, false), at, clock.instant())));
        });
    }
    private com.google.gson.JsonObject steal() {
        var farm = service.visit(friend, owner);
        return service.visitAction(friend, owner, new Command(RequestIds.issue(clock.instant()), farm.get("revision").getAsString(), "STEAL", null, null, 0, 0,
                farm.getAsJsonArray("tiles").get(0).getAsJsonObject().get("harvestRevision").getAsString()));
    }

    @Test void friendVisitAccumulatesAutomationCareForOwner() {
        edit(owner, game -> {
            game.wallet().credit(Currency.GREEN, new java.math.BigDecimal("1000"));
            game.buyVip(); game.buyAutomation("water");
            var tile = game.farm().tile(0, 0); tile.plow(); tile.plant(game.catalog().get("nabo"), clock.instant());
        });
        clock.advance(2 * 3600);
        service.visit(friend, owner);
        assertTrue(ledger(owner).waterings() > 0);
        var friendLedger = ledger(friend);
        assertTrue(friendLedger == null || friendLedger.waterings() == 0);
    }

    @Test void backgroundPushAdvanceAccumulatesAutomationCareForOwner() {
        edit(owner, game -> {
            game.wallet().credit(Currency.GREEN, new java.math.BigDecimal("1000"));
            game.buyVip(); game.buyAutomation("water");
            var tile = game.farm().tile(0, 0); tile.plow(); tile.plant(game.catalog().get("nabo"), clock.instant());
        });
        clock.advance(2 * 3600);
        store.transaction(owner, tx -> { tx.lockFarms(owner); service.advanceForCropPush(tx, owner); return null; });
        assertTrue(ledger(owner).waterings() > 0);
    }

    @Test void theftAndDefenseAreRecordedForOwner() {
        matureCrop(false);
        assertTrue(steal().getAsJsonObject("result").get("success").getAsBoolean());
        var theft = ledger(owner).events().getFirst();
        assertEquals(new AwayLedger.Event("THEFT", clock.instant().toString(), "Amiga", "cenoura", 1, null), theft);
        clock.advance(24 * 3600 + 1); // past the theft cooldown on this base
        matureCrop(true);
        assertEquals("PROTECTED", steal().getAsJsonObject("theftOutcome").get("kind").getAsString());
        var defense = ledger(owner).events().getLast();
        assertEquals("DEFENDED", defense.kind()); assertEquals("Amiga", defense.actor()); assertNull(defense.protectorId());
    }

    @Test void rolledBackVisitDoesNotDoubleCount() {
        matureCrop(false);
        var farm = service.visit(friend, owner);
        var command = new Command(RequestIds.issue(clock.instant()), farm.get("revision").getAsString(), "STEAL", null, null, 0, 0,
                farm.getAsJsonArray("tiles").get(0).getAsJsonObject().get("harvestRevision").getAsString());
        assertThrows(IllegalStateException.class, () -> store.transaction(friend, tx -> {
            service.visitAction(tx, friend, owner, command); throw new IllegalStateException("rollback probe"); }));
        var after = ledger(owner);
        assertTrue(after == null || after.events().isEmpty());
    }

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

    private boolean hasReport(UUID player) { return service.farm(player).has("awayReport"); }

    @Test void firstGetOnlyRecordsLastSeen() {
        var fresh = UUID.randomUUID();
        var invite = service.createInvitation(owner, RequestIds.issue(clock.instant())).get("code").getAsString();
        service.acceptInvitation(fresh, "Nova", invite, RequestIds.issue(clock.instant()));
        assertNull(ledger(fresh));
        assertFalse(hasReport(fresh));
        assertEquals(clock.instant(), ledger(fresh).lastSeenAt());
    }

    private String reportId(UUID player) {
        var view = service.farm(player);
        return view.has("awayReport") ? view.getAsJsonObject("awayReport").get("id").getAsString() : null;
    }

    @Test void reportStaysUntilAcknowledged() {
        service.farm(owner);
        clock.advance(3 * 3600); matureCrop(false);
        var view = service.farm(owner);
        assertEquals(3 * 3600, view.getAsJsonObject("awayReport").get("awaySeconds").getAsLong());
        String id = view.getAsJsonObject("awayReport").get("id").getAsString();
        // A lost response or a reload gets the same report again.
        assertEquals(id, reportId(owner));
        service.acknowledgeAwayReport(owner, id);
        assertNull(reportId(owner));
        service.acknowledgeAwayReport(owner, id); // repeated ack is harmless
        assertNull(reportId(owner));
    }

    @Test void staleAcknowledgementKeepsNewerReport() {
        service.farm(owner);
        clock.advance(3 * 3600); matureCrop(false);
        String first = reportId(owner);
        clock.advance(3 * 3600); matureCrop(false);
        String second = reportId(owner);
        assertNotEquals(first, second, "a newer absence supersedes the unacknowledged report");
        service.acknowledgeAwayReport(owner, first);
        assertEquals(second, reportId(owner));
        service.acknowledgeAwayReport(owner, UUID.randomUUID().toString());
        assertEquals(second, reportId(owner));
    }

    @Test void identicalAbsencesGetDistinctIds() {
        service.farm(owner);
        clock.advance(3 * 3600); matureCrop(false);
        String first = reportId(owner); service.acknowledgeAwayReport(owner, first);
        clock.advance(3 * 3600); matureCrop(false);
        String second = reportId(owner);
        assertNotNull(second); assertNotEquals(first, second);
    }

    @Test void invalidAcknowledgementIsRejected() {
        var failure = assertThrows(ApiFailure.class, () -> service.acknowledgeAwayReport(owner, "nao-e-uuid"));
        assertEquals(400, failure.status());
    }

    @Test void underThreeHoursDiscards() {
        service.farm(owner);
        clock.advance(2 * 3600 + 59 * 60); matureCrop(false);
        assertFalse(hasReport(owner));
        clock.advance(3 * 3600);
        assertFalse(hasReport(owner));
    }

    @Test void concurrentGetsShareOneReport() throws Exception {
        service.farm(owner);
        clock.advance(3 * 3600); matureCrop(false);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> reportId(owner)); var second = pool.submit(() -> reportId(owner));
            assertNotNull(first.get()); assertEquals(first.get(), second.get());
        } finally { pool.shutdownNow(); }
    }

    @Test void friendVisitNeverDelivers() {
        service.farm(owner);
        clock.advance(3 * 3600); matureCrop(false);
        assertFalse(service.visit(friend, owner).has("awayReport"));
        assertTrue(hasReport(owner));
    }
}
