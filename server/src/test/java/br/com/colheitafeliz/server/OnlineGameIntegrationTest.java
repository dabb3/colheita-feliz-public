package br.com.colheitafeliz.server;

import com.google.gson.JsonObject;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
class OnlineGameIntegrationTest {
    private final UUID alice = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final UUID bob = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private final TestClock clock = new TestClock();
    private JdbcOnlineStore store;
    private String testSchema;
    private OnlineGameService service;

    @AfterEach void closeStore() { if (store != null) store.close(); }

    @Test void pooledConnectionSurvivesRollbackWithoutLeakingChangesToNextTransaction() {
        int backend = store.transaction(alice, tx -> tx.query("SELECT pg_backend_pid()", row -> row.getInt(1)).getFirst());
        assertThrows(IllegalStateException.class, () -> store.transaction(alice, tx -> {
            tx.setActive(alice, false);
            throw new IllegalStateException("rollback probe");
        }));
        assertTrue(store.transaction(alice, tx -> tx.player(alice).active()).booleanValue());
        int next = store.transaction(alice, tx -> tx.query("SELECT pg_backend_pid()", row -> row.getInt(1)).getFirst());
        assertEquals(backend, next);
    }

    @Test void httpRejectsForgedFieldsMalformedBodiesAndUnapprovedOrigins() throws Exception {
        Authenticator auth = header -> {
            if (!"Bearer test".equals(header)) throw new ApiFailure(401, "Login necessário.");
            return new Authenticator.Identity(alice, "Alice");
        };
        try (var http = new GameHttpServer(new java.net.InetSocketAddress("127.0.0.1", 0), service, auth,
                java.util.Set.of("http://localhost:5173"), true, clock);
             var client = java.net.http.HttpClient.newHttpClient()) {
            http.start();
            String base = "http://127.0.0.1:" + http.port();
            var missing = client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(base + "/v1/me/farm")).build(), java.net.http.HttpResponse.BodyHandlers.ofString());
            assertEquals(401, missing.statusCode());
            var own = client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(base + "/v1/me/farm"))
                    .header("Authorization", "Bearer test").build(), java.net.http.HttpResponse.BodyHandlers.ofString());
            assertEquals(200, own.statusCode());
            String timing = own.headers().firstValue("Server-Timing").orElseThrow();
            assertTrue(timing.contains("auth;dur="));
            assertTrue(timing.contains("db_connect;dur="));
            assertTrue(timing.contains("db;dur="));
            assertEquals("Alice", com.google.gson.JsonParser.parseString(own.body()).getAsJsonObject().get("name").getAsString());
            var invitation = service.createInvitation(alice, requestId());
            service.acceptInvitation(bob, "Bob", invitation.get("code").getAsString(), requestId());
            assertEquals("Alice", service.friends(bob).get(0).getAsJsonObject().get("name").getAsString());
            store.initialize(alice);
            assertEquals("Alice", service.visit(bob, alice).get("name").getAsString());
            for (String body : java.util.List.of("{", "{\"coins\":999999}", "{\"requestId\":\"bad\",\"action\":\"BUY_SEED\"}")) {
                var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create(base + "/v1/me/actions"))
                        .header("Authorization", "Bearer test").header("Content-Type", "application/json")
                        .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body)).build();
                assertEquals(400, client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode());
            }
            var origin = java.net.http.HttpRequest.newBuilder(java.net.URI.create(base + "/v1/me/farm"))
                    .header("Authorization", "Bearer test").header("Origin", "https://unapproved.example").build();
            assertEquals(403, client.send(origin, java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode());
            assertEquals("500", service.farm(alice).get("coins").getAsString());
        }
    }

    @Test void firstPlantFlowSurvivesEveryRequestReload() {
        service = new OnlineGameService(store, Clock.systemUTC(), bound -> bound - 1, 5);
        var farm = service.farm(alice);
        farm = service.command(alice, new Command(requestId(), farm.get("revision").getAsString(), "BUY_SEED", "nabo", "GOLD", null, null, null)).getAsJsonObject("farm");
        farm = service.command(alice, new Command(requestId(), farm.get("revision").getAsString(), "PLOW", null, null, 0, 0, null)).getAsJsonObject("farm");
        farm = service.farm(alice);
        var planted = service.command(alice, new Command(requestId(), farm.get("revision").getAsString(), "PLANT", "nabo", null, 0, 0, null));
        assertTrue(planted.getAsJsonObject("result").get("success").getAsBoolean());
        farm = service.farm(alice);
        var watered = service.command(alice, new Command(requestId(), farm.get("revision").getAsString(), "WATER", null, null, 0, 0, null));
        assertTrue(watered.getAsJsonObject("result").get("success").getAsBoolean());
        assertTrue(service.farm(alice).getAsJsonArray("tiles").get(0).getAsJsonObject().get("remainingSeconds").getAsInt() > 59000);
    }

    @BeforeEach void setUp() {
        String url = System.getenv("TEST_DATABASE_URL");
        assertNotNull(url, "integrationTest requires TEST_DATABASE_URL pointing to disposable PostgreSQL");
        assertTrue(url.startsWith("jdbc:postgresql://127.0.0.1:") || url.startsWith("jdbc:postgresql://localhost:"));
        assertTrue(url.contains("/colheita_test"));
        testSchema = "test_" + UUID.randomUUID().toString().replace("-", "");
        store = new JdbcOnlineStore(url, "postgres", "", testSchema);
        store.initialize(alice);
        service = new OnlineGameService(store, clock, bound -> bound - 1, 5);
    }

    @Test void profileRefreshPreservesFarmAndNeverReactivatesParticipants() {
        var original = service.farm(alice);
        var updated = service.farm(alice, "  Alice\n Silva  ");
        assertEquals("Alice Silva", updated.get("name").getAsString());
        for (String field : java.util.List.of("revision", "coins", "greenCoins", "tiles"))
            assertEquals(original.get(field), updated.get(field));
        assertEquals("A".repeat(40), service.farm(alice, "A".repeat(100)).get("name").getAsString());
        assertEquals("Fazendeiro", service.farm(alice, "\n\t ").get("name").getAsString());
        store.transaction(alice, tx -> { tx.setActive(alice, false); return null; });
        assertEquals(403, assertThrows(ApiFailure.class, () -> service.farm(alice, "Novo nome")).status());
        store.transaction(alice, tx -> {
            assertEquals("Fazendeiro", tx.player(alice).name());
            assertFalse(tx.player(alice).active());
            return null;
        });
        assertEquals(403, assertThrows(ApiFailure.class, () -> service.farm(bob, "Bob")).status());
    }

    @Test void newFarmAndRetryPreserveEconomyAndSurviveServiceRestart() {
        JsonObject farm = service.farm(alice);
        assertEquals("500", farm.get("coins").getAsString());
        Command command = new Command(requestId(), farm.get("revision").getAsString(), "BUY_SEED", "nabo", "GOLD", null, null, null);
        JsonObject bought = service.command(alice, command);
        assertTrue(bought.getAsJsonObject("result").get("success").getAsBoolean());
        assertEquals(bought, service.command(alice, command));
        assertEquals("200", service.farm(alice).get("coins").getAsString());
        assertEquals("6", service.farm(alice).get("greenCoins").getAsString());
        var restarted = new OnlineGameService(store, clock, bound -> 99, 5);
        assertEquals("200", restarted.farm(alice).get("coins").getAsString());
        Command altered = new Command(command.requestId(), command.expectedRevision(), "BUY_SEED", "cenoura", "GOLD", null, null, null);
        assertEquals(409, assertThrows(ApiFailure.class, () -> service.command(alice, altered)).status());
    }

    @Test void farmSnapshotIncludesDailyMissionProgressAndTarget() {
        var missions = service.farm(alice).getAsJsonArray("missions");
        var care = missions.get(0).getAsJsonObject();
        assertEquals("care", care.get("id").getAsString());
        assertTrue(care.has("progress"));
        assertTrue(care.has("target"));
        assertEquals(0, care.get("progress").getAsInt());
        assertEquals(1, care.get("target").getAsInt());
        var farm = service.farm(alice);
        assertEquals(0, farm.get("tutorialStep").getAsInt());
        assertEquals(6, farm.get("tutorialTotal").getAsInt());
    }

    @Test void depositCanSellAllOfOneProduct() {
        service.farm(alice);
        prepare(alice, "morango", true, false);
        JsonObject farm = service.farm(alice);
        var harvest = new Command(requestId(), farm.get("revision").getAsString(), "HARVEST", null, null, 0, 0, null);
        assertTrue(service.command(alice, harvest).getAsJsonObject("result").get("success").getAsBoolean());
        farm = service.farm(alice);
        var sell = new Command(requestId(), farm.get("revision").getAsString(), "SELL_ITEM_ALL", "morango", null, null, null, null);
        assertTrue(service.command(alice, sell).getAsJsonObject("result").get("success").getAsBoolean());
        assertEquals(0, quantity(service.farm(alice)));
    }

    @Test void friendshipAuthorizesVisitsButNeverExposesOwnerInventoryAndRevocationWinsOverReplay() {
        assertEquals(403, assertThrows(ApiFailure.class, () -> service.farm(bob)).status());
        JsonObject invite = service.createInvitation(alice, requestId());
        service.acceptInvitation(bob, "Bob", invite.get("code").getAsString(), requestId());
        JsonObject visit = service.visit(bob, alice);
        assertFalse(visit.has("coins"));
        assertFalse(visit.has("produceLots"));
        assertFalse(visit.has("seedCounts"));
        assertEquals(1, service.friends(bob).size());
        JsonObject farm = service.farm(bob);
        Command command = new Command(requestId(), farm.get("revision").getAsString(), "BUY_SEED", "nabo", "GOLD", null, null, null);
        service.command(bob, command);
        store.transaction(alice, tx -> { tx.setActive(bob, false); return null; });
        assertEquals(403, assertThrows(ApiFailure.class, () -> service.command(bob, command)).status());
        assertEquals(403, assertThrows(ApiFailure.class, () -> service.visit(bob, alice)).status());
    }

    @Test void concurrentDuplicatePaysOnceAndFailedTransactionRollsBack() throws Exception {
        JsonObject farm = service.farm(alice);
        Command command = new Command(requestId(), farm.get("revision").getAsString(), "BUY_SEED", "nabo", "GOLD", null, null, null);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var one = executor.submit(() -> service.command(alice, command));
            var two = executor.submit(() -> service.command(alice, command));
            assertEquals(one.get(), two.get());
        }
        assertEquals("200", service.farm(alice).get("coins").getAsString());
        assertThrows(IllegalStateException.class, () -> store.transaction(alice, tx -> {
            tx.setActive(alice, false);
            throw new IllegalStateException("simulated transaction failure");
        }));
        assertEquals("200", service.farm(alice).get("coins").getAsString());
    }

    @Test void ownerHarvestAndVisitorTheftRaceConsumeOnlyOneSeason() throws Exception {
        connectFriends();
        prepare(alice, "morango", true, false);
        JsonObject before = service.visit(bob, alice);
        String revision = before.get("revision").getAsString();
        String season = before.getAsJsonArray("tiles").get(0).getAsJsonObject().get("harvestRevision").getAsString();
        var harvest = new Command(requestId(), revision, "HARVEST", null, null, 0, 0, null);
        var steal = new Command(requestId(), revision, "STEAL", null, null, 0, 0, season);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var one = executor.submit(() -> confirmed(() -> service.command(alice, harvest)));
            var two = executor.submit(() -> confirmed(() -> service.visitAction(bob, alice, steal)));
            assertEquals(1, (one.get() ? 1 : 0) + (two.get() ? 1 : 0));
        }
        assertEquals(1, quantity(service.farm(alice)) + quantity(service.farm(bob)));
        var tile = service.farm(alice).getAsJsonArray("tiles").get(0).getAsJsonObject();
        assertEquals(2, tile.get("season").getAsInt());
        assertTrue(tile.get("dry").getAsBoolean());
    }

    @Test void protectionIsCheckedInsideTheVisitTransaction() {
        connectFriends();
        prepare(alice, "morango", true, true);
        var visit = service.visit(bob, alice);
        var tile = visit.getAsJsonArray("tiles").get(0).getAsJsonObject();
        assertTrue(tile.get("protected").getAsBoolean());
        var command = new Command(requestId(), visit.get("revision").getAsString(), "STEAL", null, null,
                0, 0, tile.get("harvestRevision").getAsString());
        assertFalse(service.visitAction(bob, alice, command).getAsJsonObject("result").get("success").getAsBoolean());
        assertEquals(0, quantity(service.farm(bob)));
        assertEquals("READY", service.farm(alice).getAsJsonArray("tiles").get(0).getAsJsonObject().get("state").getAsString());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
    void loveAndOwnerFertilizerShareOneApplication(boolean loveFirst) {
        connectFriends();
        prepare(alice, "nabo", false, false);
        prepare(bob, null, false, false);
        for (boolean love : new boolean[] {loveFirst, !loveFirst}) {
            JsonObject owner = service.farm(alice);
            var tile = owner.getAsJsonArray("tiles").get(0).getAsJsonObject();
            var command = new Command(requestId(), owner.get("revision").getAsString(), love ? "LOVE" : "FERTILIZE",
                    love ? "amor" : "comum", null, 0, 0, love ? tile.get("harvestRevision").getAsString() : null);
            JsonObject response = love ? service.visitAction(bob, alice, command) : service.command(alice, command);
            assertEquals(love == loveFirst, response.getAsJsonObject("result").get("success").getAsBoolean());
        }
        assertEquals(loveFirst ? 2 : 3, service.farm(bob).getAsJsonObject("fertilizers").get("amor").getAsInt());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void failedVisitDoesNotInvalidateOwnerCommands(boolean protectedCrop) {
        connectFriends();
        prepare(alice, "morango", protectedCrop, protectedCrop);
        var ownerBefore = service.farm(alice);
        var visitorBefore = service.farm(bob);
        var tile = ownerBefore.getAsJsonArray("tiles").get(0).getAsJsonObject();
        var rejected = service.visitAction(bob, alice, new Command(requestId(), ownerBefore.get("revision").getAsString(),
                "STEAL", null, null, 0, 0, tile.get("harvestRevision").getAsString()));
        assertFalse(rejected.getAsJsonObject("result").get("success").getAsBoolean());
        assertEquals(ownerBefore.get("revision"), service.farm(alice).get("revision"));
        assertEquals(visitorBefore.get("revision"), service.farm(bob).get("revision"));
        var plowed = service.command(alice, new Command(requestId(), ownerBefore.get("revision").getAsString(), "PLOW", null, null, 0, 1, null));
        assertTrue(plowed.getAsJsonObject("result").get("success").getAsBoolean());
    }

    @Test void receiptQuotaAllowsReplayAndExpiredKeysNeverExecuteAgain() {
        var farm = service.farm(alice);
        var command = new Command(requestId(), farm.get("revision").getAsString(), "BUY_SEED", "nabo", "GOLD", null, null, null);
        var original = service.command(alice, command);
        store.transaction(alice, tx -> {
            tx.execute("INSERT INTO " + testSchema + ".receipts (actor,request_id,hash,response,expires_at) "
                    + "SELECT ?, md5(i::text)::uuid, 'fixture', '{}'::jsonb, ? FROM generate_series(1,999) i",
                    alice, java.sql.Timestamp.from(clock.instant().plusSeconds(86400)));
            return null;
        });
        assertEquals(original, service.command(alice, command));
        var refused = new Command(requestId(), original.getAsJsonObject("farm").get("revision").getAsString(), "PLOW", null, null, 0, 0, null);
        assertEquals(429, assertThrows(ApiFailure.class, () -> service.command(alice, refused)).status());
        assertEquals("200", service.farm(alice).get("coins").getAsString());
        clock.advanceSeconds(86401);
        assertEquals(410, assertThrows(ApiFailure.class, () -> service.command(alice, command)).status());
        farm = service.farm(alice);
        var fresh = new Command(requestId(), farm.get("revision").getAsString(), "PLOW", null, null, 0, 0, null);
        assertTrue(service.command(alice, fresh).getAsJsonObject("result").get("success").getAsBoolean());
        assertEquals(1, store.transaction(alice, tx -> tx.query("SELECT count(*) FROM " + testSchema + ".receipts", r -> r.getInt(1)).get(0)).intValue());
        assertEquals("200", service.farm(alice).get("coins").getAsString());
    }

    private UUID requestId() { return UUID.fromString(service.requestId().get("requestId").getAsString()); }

    private void connectFriends() {
        service.farm(alice);
        var invite = service.createInvitation(alice, requestId());
        service.acceptInvitation(bob, "Bob", invite.get("code").getAsString(), requestId());
        service.farm(bob);
    }
    private void prepare(UUID actor, String cropId, boolean mature, boolean protectors) {
        store.transaction(actor, tx -> {
            tx.lockFarms(actor);
            var saved = tx.farm(actor);
            var game = br.com.colheitafeliz.game.GameState.restore(clock::instant, clock,
                    br.com.colheitafeliz.game.CropCatalogLoader.load(), saved.snapshot(), bound -> bound - 1);
            while (game.progress().level() < 61) game.progress().award(game.progress().xpToNext());
            game.wallet().credit(br.com.colheitafeliz.domain.economy.Currency.GOLD, new java.math.BigDecimal("10000000"));
            game.wallet().credit(br.com.colheitafeliz.domain.economy.Currency.GREEN, new java.math.BigDecimal("100000"));
            game.inventory().addFertilizer("amor", 3); game.inventory().addFertilizer("comum", 3);
            if (cropId != null) {
                var tile = game.farm().tile(0, 0); tile.plow(); tile.plant(game.catalog().get(cropId), clock.instant());
                tile.water(clock.instant());
                while (mature && !tile.crop().matureAt(clock.instant())) {
                    if (tile.crop().dryAt(clock.instant())) tile.water(clock.instant());
                    clock.advanceSeconds(3600); tile.refresh(clock.instant());
                }
            }
            if (protectors) for (var type : game.animalTypes())
                if (type.kind() == br.com.colheitafeliz.domain.animal.AnimalType.Kind.PROTECTOR)
                    assertTrue(game.buyAnimal(type, type.prices().keySet().iterator().next()).success());
            tx.save(actor, game.snapshot(), saved.revision() + 1, clock.instant());
            return null;
        });
    }
    private static int quantity(JsonObject farm) {
        int count = 0;
        for (var lot : farm.getAsJsonArray("produceLots")) count += lot.getAsJsonObject().get("quantity").getAsInt();
        return count;
    }
    private static boolean confirmed(java.util.function.Supplier<JsonObject> action) {
        try { return action.get().getAsJsonObject("result").get("success").getAsBoolean(); }
        catch (ApiFailure e) { if (e.status() == 409) return false; throw e; }
    }

    private static final class TestClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T12:00:00Z");
        void advanceSeconds(long seconds) { now = now.plusSeconds(seconds); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
