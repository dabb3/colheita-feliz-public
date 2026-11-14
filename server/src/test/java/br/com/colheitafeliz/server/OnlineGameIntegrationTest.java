package br.com.colheitafeliz.server;

import com.google.gson.JsonObject;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import java.math.BigDecimal;
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

    @Test void productionLimitAdmitsTwentyActiveParticipantsIncludingOwnerAndRejectsTwentyFirst() {
        service = new OnlineGameService(store, clock, bound -> bound - 1, ServerMain.PARTICIPANT_LIMIT);
        for (int count = 2; count <= 20; count++) {
            String code = service.createInvitation(alice, requestId()).get("code").getAsString();
            UUID player = UUID.randomUUID();
            assertTrue(service.acceptInvitation(player, "Player " + count, code, requestId()).get("success").getAsBoolean());
            assertEquals(player.toString(), service.farm(player).get("owner").getAsString());
        }
        UUID extra = UUID.randomUUID();
        String code = service.createInvitation(alice, requestId()).get("code").getAsString();
        ApiFailure failure = assertThrows(ApiFailure.class,
                () -> service.acceptInvitation(extra, "Twenty-first", code, requestId()));
        assertEquals(409, failure.status());
        assertEquals("O grupo de teste atingiu o limite de participantes.", failure.getMessage());
        assertEquals(20, store.transaction(alice, tx -> tx.query("SELECT count(*) FROM " + testSchema
                + ".players WHERE active", row -> row.getInt(1)).getFirst()).intValue());
        assertNull(store.transaction(alice, tx -> tx.player(extra)));
    }

    @Test void farmAndMutationCarryTheNextServerIdAndReplayKeepsTheExactReply() {
        var farm = service.farm(alice);
        var lease = farm.getAsJsonObject("requestLease");
        assertNotNull(lease);
        UUID request = UUID.fromString(lease.get("requestId").getAsString());
        assertEquals(RequestIds.validate(request, clock.instant()).toString(), lease.get("expiresAt").getAsString());
        var command = new Command(request, farm.get("revision").getAsString(), "BUY_SEED", "nabo", "GOLD", null, null, null);
        var reply = service.command(alice, command);
        UUID next = UUID.fromString(reply.getAsJsonObject("requestLease").get("requestId").getAsString());
        assertNotEquals(request, next);
        assertDoesNotThrow(() -> RequestIds.validate(next, clock.instant()));
        assertEquals(reply, service.command(alice, command));
        assertEquals("200", service.farm(alice).get("coins").getAsString());
    }

    @Test void farmAndReceiptBatchRollBackTogetherWhenReceiptInsertFails() {
        var original = service.farm(alice);
        UUID request = requestId();
        var bought = service.command(alice, new Command(request, original.get("revision").getAsString(), "BUY_SEED", "nabo", "GOLD", null, null, null)).getAsJsonObject("farm");
        assertEquals(503, assertThrows(ApiFailure.class, () -> store.transaction(alice, tx -> {
            tx.lockFarms(alice);
            var saved = tx.farm(alice);
            var game = br.com.colheitafeliz.game.GameState.restore(clock::instant, clock,
                    br.com.colheitafeliz.game.CropCatalogLoader.load(), saved.snapshot(), bound -> bound - 1);
            game.wallet().credit(br.com.colheitafeliz.domain.economy.Currency.GOLD, new java.math.BigDecimal("99"));
            tx.collectSaves();
            tx.save(alice, game.snapshot(), saved.revision() + 1, clock.instant());
            tx.remember(alice, request, "duplicate", new JsonObject(), clock.instant().plusSeconds(86400));
            return null;
        })).status());
        var after = service.farm(alice);
        assertEquals(bought.get("coins"), after.get("coins"));
        assertEquals(bought.get("revision"), after.get("revision"));
    }

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

    @Test void httpHarvestKeepsOpenRevisionAfterBackgroundAdvance() throws Exception {
        var farm=service.farm(alice);
        farm=service.command(alice,new Command(requestId(),farm.get("revision").getAsString(),"BUY_SEED","nabo","GOLD",null,null,null)).getAsJsonObject("farm");
        farm=service.command(alice,new Command(requestId(),farm.get("revision").getAsString(),"PLOW",null,null,0,0,null)).getAsJsonObject("farm");
        farm=service.command(alice,new Command(requestId(),farm.get("revision").getAsString(),"PLANT","nabo",null,0,0,null)).getAsJsonObject("farm");
        farm=service.command(alice,new Command(requestId(),farm.get("revision").getAsString(),"WATER",null,null,0,0,null)).getAsJsonObject("farm");
        var openRevision=farm.get("revision").getAsString();clock.now=clock.now.plusSeconds(72000);
        store.transaction(alice,tx->{service.advanceForCropPush(tx,alice);return null;});
        Authenticator auth=header->{if(!"Bearer test".equals(header))throw new ApiFailure(401,"Login necessário.");return new Authenticator.Identity(alice,"Alice");};
        try(var http=new GameHttpServer(new java.net.InetSocketAddress("127.0.0.1",0),service,auth,java.util.Set.of("http://localhost:5173"),true,clock);var client=java.net.http.HttpClient.newHttpClient()){
            http.start();var command=new Command(requestId(),openRevision,"HARVEST",null,null,0,0,null);var body=OnlineViews.JSON.toJsonTree(command).getAsJsonObject();
            var response=client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:"+http.port()+"/v1/me/actions")).header("Authorization","Bearer test").header("Content-Type","application/json").POST(java.net.http.HttpRequest.BodyPublishers.ofString(body.toString())).build(),java.net.http.HttpResponse.BodyHandlers.ofString());
            assertEquals(200,response.statusCode(),response.body());assertTrue(com.google.gson.JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result").get("success").getAsBoolean(),response.body());
        }
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

    @Test void concurrentV1SalesAndReplayCreditOnlyOnePaymentAndRollbackRetainsStock() throws Exception {
        service.farm(alice);
        seedV1Produce(alice, "tomate", 1);
        var farm = service.farm(alice);
        assertEquals("679.0", farm.getAsJsonArray("produceLots").get(0).getAsJsonObject().get("unitSale").getAsString());
        var one = new Command(requestId(), farm.get("revision").getAsString(), "SELL_ITEM", "tomate", null, null, null, null);
        var two = new Command(requestId(), farm.get("revision").getAsString(), "SELL_ALL", null, null, null, null, null);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> confirmed(() -> service.command(alice, one)));
            var second = executor.submit(() -> confirmed(() -> service.command(alice, two)));
            assertEquals(1, (first.get() ? 1 : 0) + (second.get() ? 1 : 0));
        }
        assertEquals("1179.1", service.farm(alice).get("coins").getAsString());
        assertEquals(0, quantity(service.farm(alice)));
        seedV1Produce(alice, "tomate", 2);
        farm = service.farm(alice);
        var sell = new Command(requestId(), farm.get("revision").getAsString(), "SELL_ITEM_ALL", "tomate", null, null, null, null);
        assertThrows(IllegalStateException.class, () -> store.transaction(alice, tx -> {
            service.command(tx, alice, sell);
            throw new IllegalStateException("rollback sale");
        }));
        assertEquals(1, quantity(service.farm(alice)));
        var reply = service.command(alice, sell);
        assertEquals(reply, service.command(alice, sell));
        assertEquals(0, quantity(service.farm(alice)));
        assertEquals("1858.1", service.farm(alice).get("coins").getAsString());
    }

    @Test void previousRulesetLoadsUnchangedAndBothWritePathsUpgradeItsLabel() {
        service.farm(alice);
        var oldRuleset = br.com.colheitafeliz.persistence.GameSave.PREVIOUS_RULESET;
        var newRuleset = br.com.colheitafeliz.persistence.GameSave.RULESET;
        store.transaction(alice, tx -> {
            tx.execute("UPDATE " + testSchema + ".farms SET ruleset=? WHERE owner=?", oldRuleset, alice);
            return null;
        });
        var before = service.farm(alice);
        assertEquals("500", before.get("coins").getAsString());
        var purchase = new Command(requestId(), before.get("revision").getAsString(), "BUY_SEED", "nabo", "GOLD", null, null, null);
        store.transaction(alice, tx -> {
            tx.execute("UPDATE " + testSchema + ".farms SET ruleset=? WHERE owner=?", oldRuleset, alice);
            return null;
        });
        assertTrue(service.command(alice, purchase).getAsJsonObject("result").get("success").getAsBoolean());
        store.transaction(alice, tx -> {
            assertEquals(newRuleset, tx.query("SELECT ruleset FROM " + testSchema + ".farms WHERE owner=?", r -> r.getString(1), alice).getFirst());
            var saved = tx.farm(alice);
            tx.execute("UPDATE " + testSchema + ".farms SET ruleset=? WHERE owner=?", oldRuleset, alice);
            tx.save(alice, saved.snapshot(), saved.revision(), clock.instant());
            assertEquals(newRuleset, tx.query("SELECT ruleset FROM " + testSchema + ".farms WHERE owner=?", r -> r.getString(1), alice).getFirst());
            return null;
        });
        assertEquals("200", service.farm(alice).get("coins").getAsString());
    }

    private void seedV1Produce(UUID actor, String id, int season) {
        store.transaction(actor, tx -> {
            tx.lockFarms(actor);
            var saved = tx.farm(actor);
            var game = br.com.colheitafeliz.game.GameState.restore(clock::instant, clock,
                    br.com.colheitafeliz.game.CropCatalogLoader.load(), saved.snapshot(), bound -> bound - 1);
            var type = game.catalog().get(id);
            var price = type.seedPrice();
            var origin = new br.com.colheitafeliz.domain.inventory.ItemLot(id, price.getKey(), price.getValue(), 1, type.vipOnly());
            game.inventory().addProduceLot(type.plantingOrigin(origin).harvested(season));
            if (game.wallet().coins().compareTo(new BigDecimal("500")) == 0)
                game.wallet().credit(br.com.colheitafeliz.domain.economy.Currency.GOLD, new BigDecimal("0.1"));
            tx.save(actor, game.snapshot(), saved.revision() + 1, clock.instant());
            return null;
        });
    }

    @Test void jdbcRejectsCorruptedV1WithoutNormalizingOrOverwritingTheSnapshot() {
        service.farm(alice);
        seedV1Produce(alice, "tomate", 1);
        String original = store.transaction(alice, tx -> tx.query("SELECT snapshot::text FROM " + testSchema
                + ".farms WHERE owner=?", row -> row.getString(1), alice).getFirst());
        for (String corrupt : new String[]{"fraction", "missing", "terms", "seasons"}) {
            var damaged = com.google.gson.JsonParser.parseString(original).getAsJsonObject();
            var lot = damaged.getAsJsonArray("produce").get(0).getAsJsonObject();
            switch (corrupt) {
                case "fraction" -> lot.getAsJsonObject("cropSale").addProperty("seasonIndex", new BigDecimal("1.9"));
                case "missing" -> lot.getAsJsonObject("cropSale").remove("cycleVipBonusTenths");
                case "terms" -> lot.remove("cropSale");
                default -> lot.getAsJsonObject("cropSale").addProperty("seasons", 1);
            }
            store.transaction(alice, tx -> {
                tx.execute("UPDATE " + testSchema + ".farms SET snapshot=?::jsonb WHERE owner=?", damaged.toString(), alice);
                return null;
            });
            String before = store.transaction(alice, tx -> tx.query("SELECT snapshot::text FROM " + testSchema
                    + ".farms WHERE owner=?", row -> row.getString(1), alice).getFirst());
            assertThrows(RuntimeException.class, () -> service.farm(alice), corrupt);
            String after = store.transaction(alice, tx -> tx.query("SELECT snapshot::text FROM " + testSchema
                    + ".farms WHERE owner=?", row -> row.getString(1), alice).getFirst());
            assertEquals(before, after, corrupt);
        }
    }

    @Test void stolenV1SeasonKeepsItsPriceAndOnlyVisitorCanSellItWithoutHarvestXp() {
        connectFriends();
        prepare(alice, "morango", true, false);
        clock.advanceSeconds(300);
        JsonObject ownerBefore = service.farm(alice), visitorBefore = service.farm(bob);
        JsonObject visit = service.visit(bob, alice);
        String harvestRevision = visit.getAsJsonArray("tiles").get(0).getAsJsonObject().get("harvestRevision").getAsString();
        var steal = new Command(requestId(), visit.get("revision").getAsString(), "STEAL", null, null, 0, 0, harvestRevision);
        var result = service.visitAction(bob, alice, steal);
        assertTrue(result.getAsJsonObject("result").get("success").getAsBoolean());
        assertEquals(result, service.visitAction(bob, alice, steal));
        var stolen = service.farm(bob).getAsJsonArray("produceLots").get(0).getAsJsonObject();
        assertEquals(1, stolen.getAsJsonObject("cropSale").get("seasonIndex").getAsInt());
        assertEquals("846.7", stolen.get("unitSale").getAsString());
        assertEquals(ownerBefore.get("currentXp"), service.farm(alice).get("currentXp"));
        assertEquals(visitorBefore.get("currentXp"), service.farm(bob).get("currentXp"));
        for (UUID actor : new UUID[]{alice,bob}) {
            var farm = service.farm(actor);
            var sell = new Command(requestId(), farm.get("revision").getAsString(), "SELL_ITEM", "morango", null, null, null, null);
            assertEquals(actor.equals(bob), service.command(actor, sell).getAsJsonObject("result").get("success").getAsBoolean());
        }
        assertEquals(0, quantity(service.farm(alice)) + quantity(service.farm(bob)));
        assertEquals(2, service.farm(alice).getAsJsonArray("tiles").get(0).getAsJsonObject().get("season").getAsInt());
    }

    @Test void friendshipAuthorizesVisitsButNeverExposesOwnerInventoryAndRevocationWinsOverReplay() {
        assertEquals(403, assertThrows(ApiFailure.class, () -> service.farm(bob)).status());
        JsonObject invite = service.createInvitation(alice, requestId());
        service.acceptInvitation(bob, "Bob", invite.get("code").getAsString(), requestId());
        JsonObject visit = service.visit(bob, alice);
        assertEquals(service.farm(alice).get("coins"),visit.get("coins"));
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

    @Test void theftCooldownIsGlobalSurvivesRestartAndExpiresAtExactlyTwentyFourHours() {
        UUID carol = prepareTheftFriends();
        var first = theftCommand(bob, alice);
        var success = service.visitAction(bob, alice, first);
        assertTrue(success.getAsJsonObject("result").get("success").getAsBoolean());
        clock.advanceSeconds(3600);
        assertEquals(success, service.visitAction(bob, alice, first));
        store.close();
        store = new JdbcOnlineStore(System.getenv("TEST_DATABASE_URL"), "postgres", "", testSchema);
        store.initialize(alice);
        service = new OnlineGameService(store, clock, bound -> bound - 1, 5);
        var before = service.farm(bob);
        var ownerBefore = service.farm(carol);
        var refused = service.visitAction(bob, carol, theftCommand(bob, carol));
        assertFalse(refused.getAsJsonObject("result").get("success").getAsBoolean());
        assertTrue(refused.getAsJsonObject("result").get("message").getAsString().contains("23h 0min 0s"));
        assertEquals(before.get("revision"), service.farm(bob).get("revision"));
        assertEquals(ownerBefore.get("revision"), service.farm(carol).get("revision"));
        clock.advanceSeconds(82799);
        assertFalse(service.visitAction(bob, carol, theftCommand(bob, carol)).getAsJsonObject("result").get("success").getAsBoolean());
        clock.advanceSeconds(1);
        assertTrue(service.visitAction(bob, carol, theftCommand(bob, carol)).getAsJsonObject("result").get("success").getAsBoolean());
        assertEquals(2, quantity(service.farm(bob)));
    }

    @Test void refusedTheftDoesNotSpendCooldownAndLoveRemainsAvailable() {
        connectFriends();
        prepare(bob, null, false, false);
        assertFalse(service.visitAction(bob, alice, theftCommand(bob, alice)).getAsJsonObject("result").get("success").getAsBoolean());
        prepare(alice, "morango", true, false);
        assertTrue(service.visitAction(bob, alice, theftCommand(bob, alice)).getAsJsonObject("result").get("success").getAsBoolean());
        var visit = service.visit(bob, alice);
        assertTrue(service.command(alice, new Command(requestId(), visit.get("revision").getAsString(), "WATER", null, null, 0, 0, null))
                .getAsJsonObject("result").get("success").getAsBoolean());
        var love = theftCommand(bob, alice);
        assertTrue(service.visitAction(bob, alice, new Command(love.requestId(), love.expectedRevision(), "LOVE", "amor", null, 0, 0, love.harvestRevision()))
                .getAsJsonObject("result").get("success").getAsBoolean());
    }

    @Test void concurrentTheftsInDifferentFarmsTransferOnlyOneUnit() throws Exception {
        UUID carol = prepareTheftFriends();
        var one = theftCommand(bob, alice);
        var two = theftCommand(bob, carol);
        var start = new java.util.concurrent.CyclicBarrier(2);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                start.await(5, java.util.concurrent.TimeUnit.SECONDS);
                return confirmed(() -> service.visitAction(bob, alice, one));
            });
            var second = executor.submit(() -> {
                start.await(5, java.util.concurrent.TimeUnit.SECONDS);
                return confirmed(() -> service.visitAction(bob, carol, two));
            });
            assertEquals(1, (first.get() ? 1 : 0) + (second.get() ? 1 : 0));
        }
        assertEquals(1, quantity(service.farm(bob)));
    }

    @Test void rollbackAfterSuccessfulTheftDoesNotSpendCooldownOrTransferProduct() {
        prepareTheftFriends();
        var command = theftCommand(bob, alice);
        assertThrows(IllegalStateException.class, () -> store.transaction(bob, tx -> {
            assertTrue(service.visitAction(tx, bob, alice, command).getAsJsonObject("result").get("success").getAsBoolean());
            throw new IllegalStateException("rollback after theft");
        }));
        assertEquals(0, quantity(service.farm(bob)));
        assertTrue(service.visitAction(bob, alice, command).getAsJsonObject("result").get("success").getAsBoolean());
    }

    @Test void theftCooldownBelongsToTheThiefRatherThanTheVictimOrOtherPlayers() {
        UUID carol = prepareTheftFriends();
        prepare(bob, "morango", true, false);
        assertTrue(service.visitAction(bob, carol, theftCommand(bob, carol)).getAsJsonObject("result").get("success").getAsBoolean());
        assertTrue(service.visitAction(alice, bob, theftCommand(alice, bob)).getAsJsonObject("result").get("success").getAsBoolean());
        assertFalse(service.visitAction(bob, alice, theftCommand(bob, alice)).getAsJsonObject("result").get("success").getAsBoolean());
    }

    @Test void ownerHasFiveMinutesBeforeTheftAndRefusalDoesNotSpendDailyLimit() {
        connectFriends();
        prepareMatureNow(alice, "morango", 1);
        var before = service.farm(bob);
        var refused = service.visitAction(bob, alice, theftCommand(bob, alice));
        assertFalse(refused.getAsJsonObject("result").get("success").getAsBoolean());
        assertTrue(refused.getAsJsonObject("result").get("message").getAsString().contains("5min 0s"));
        assertNull(store.transaction(bob, tx -> tx.lastTheft(bob)));
        assertEquals(before.get("revision"), service.farm(bob).get("revision"));
        assertEquals(0, quantity(service.farm(bob)));
        clock.advanceSeconds(299);
        clock.now = clock.now.plusNanos(999_000_000);
        assertFalse(service.visitAction(bob, alice, theftCommand(bob, alice)).getAsJsonObject("result").get("success").getAsBoolean());
        clock.now = clock.now.plusNanos(1_000_000);
        assertTrue(service.visitAction(bob, alice, theftCommand(bob, alice)).getAsJsonObject("result").get("success").getAsBoolean());
        assertEquals(1, quantity(service.farm(bob)));
        assertFalse(service.visitAction(bob, alice, theftCommand(bob, alice)).getAsJsonObject("result").get("success").getAsBoolean());
    }

    @Test void ownerCanHarvestImmediatelyDuringExclusiveWindow() {
        connectFriends();
        prepareMatureNow(alice, "morango", 1);
        var farm = service.farm(alice);
        assertTrue(service.command(alice, new Command(requestId(), farm.get("revision").getAsString(), "HARVEST", null, null, 0, 0, null))
                .getAsJsonObject("result").get("success").getAsBoolean());
        assertEquals(1, quantity(service.farm(alice)));
        assertEquals(0, quantity(service.farm(bob)));
    }

    @Test void realFertilizerActionStartsTheOwnerWindowWhenItCompletesGrowth() {
        connectFriends();
        prepare(alice, "nabo", false, false);
        clock.advanceSeconds(58200); // 15min of growth left; water before applying fertilizer.
        var farm = service.farm(alice);
        var watered = service.command(alice, new Command(requestId(), farm.get("revision").getAsString(), "WATER", null, null, 0, 0, null));
        assertTrue(watered.getAsJsonObject("result").get("success").getAsBoolean());
        var completed = service.command(alice, new Command(requestId(), watered.getAsJsonObject("farm").get("revision").getAsString(),
                "FERTILIZE", "comum", null, 0, 0, null));
        assertTrue(completed.getAsJsonObject("result").get("success").getAsBoolean());
        assertEquals("READY", completed.getAsJsonObject("farm").getAsJsonArray("tiles").get(0).getAsJsonObject().get("state").getAsString());
        assertFalse(service.visitAction(bob, alice, theftCommand(bob, alice)).getAsJsonObject("result").get("success").getAsBoolean());
        clock.advanceSeconds(300);
        assertTrue(service.visitAction(bob, alice, theftCommand(bob, alice)).getAsJsonObject("result").get("success").getAsBoolean());
    }

    @Test void offlineMaturityAndRestartDoNotRestartOwnerWindow() {
        connectFriends();
        prepare(alice, "nabo", false, false);
        // No reads while growing: 10h crop, 3h20 moist + 13h20 dry, then the 5min window.
        clock.advanceSeconds(60301);
        store.close();
        store = new JdbcOnlineStore(System.getenv("TEST_DATABASE_URL"), "postgres", "", testSchema);
        service = new OnlineGameService(store, clock, bound -> bound - 1, 5);
        assertTrue(service.visitAction(bob, alice, theftCommand(bob, alice)).getAsJsonObject("result").get("success").getAsBoolean());
    }

    @Test void eachNewSeasonGetsItsOwnExclusiveWindow() {
        connectFriends();
        prepareMatureNow(alice, "morango", 1);
        var farm = service.farm(alice);
        service.command(alice, new Command(requestId(), farm.get("revision").getAsString(), "HARVEST", null, null, 0, 0, null));
        prepareMatureNow(alice, "morango", 2);
        assertFalse(service.visitAction(bob, alice, theftCommand(bob, alice)).getAsJsonObject("result").get("success").getAsBoolean());
        clock.advanceSeconds(300);
        assertTrue(service.visitAction(bob, alice, theftCommand(bob, alice)).getAsJsonObject("result").get("success").getAsBoolean());
        assertEquals(3, service.farm(alice).getAsJsonArray("tiles").get(0).getAsJsonObject().get("season").getAsInt());
    }

    private void prepareMatureNow(UUID owner, String cropId, int season) {
        store.transaction(owner, tx -> {
            tx.lockFarms(owner);
            var saved = tx.farm(owner);
            var game = br.com.colheitafeliz.game.GameState.restore(clock::instant, clock,
                    br.com.colheitafeliz.game.CropCatalogLoader.load(), saved.snapshot(), bound -> bound - 1);
            var type = game.catalog().get(cropId);
            var origin = new br.com.colheitafeliz.domain.inventory.ItemLot(type.id(), br.com.colheitafeliz.domain.economy.Currency.GOLD,
                    type.seedPrices().get(br.com.colheitafeliz.domain.economy.Currency.GOLD), 1, false);
            var crop = new br.com.colheitafeliz.domain.crop.Crop(type, clock.instant(), origin, season).water(clock.instant())
                    .advance(clock.instant(), type.growthTime(season).toMillis());
            var tile = game.farm().tile(0, 0);
            tile.restore(br.com.colheitafeliz.domain.farm.TileState.READY, crop, br.com.colheitafeliz.domain.farm.PestState.NONE,
                    null, 0, null, null, false, tile.harvestRevision() + 1);
            tx.save(owner, game.snapshot(), saved.revision() + 1, clock.instant());
            return null;
        });
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"SPREAD_INSECTS,43140,false","SPREAD_INSECTS,43200,true","SPREAD_INSECTS,43260,true","SPREAD_WEEDS,43140,false","SPREAD_WEEDS,43200,true","SPREAD_WEEDS,43260,true"})
    void historicalPestTimestampUsesTwelveHoursWithoutResettingIt(String kind,long elapsed,boolean allowed){
        preparePestFriends(false);var historical=clock.instant().minusSeconds(elapsed);
        store.transaction(bob,tx->{tx.recordPest(bob,kind,historical);return null;});
        store.close();store=new JdbcOnlineStore(System.getenv("TEST_DATABASE_URL"),"postgres","",testSchema);store.initialize(alice);
        service=new OnlineGameService(store,clock,bound->bound-1,5);
        assertEquals(historical,store.transaction(bob,tx->tx.lastPest(bob,kind)));
        var response=service.visitAction(bob,alice,pestCommand(bob,alice,kind));
        assertEquals(allowed,response.getAsJsonObject("result").get("success").getAsBoolean());
        assertEquals(allowed?clock.instant():historical,store.transaction(bob,tx->tx.lastPest(bob,kind)));
        assertNull(store.transaction(bob,tx->tx.lastPest(bob,kind.equals("SPREAD_WEEDS")?"SPREAD_INSECTS":"SPREAD_WEEDS")));
        assertNull(store.transaction(bob,tx->tx.lastTheft(bob)));
    }
    @Test void pestLimitsAreIndependentGlobalPersistedAndExpireAfterExactlyTwelveHours() {
        UUID carol = preparePestFriends(false);
        var before = service.farm(bob);
        var insect = pestCommand(bob, alice, "SPREAD_INSECTS");
        var first = service.visitAction(bob, alice, insect);
        assertTrue(first.getAsJsonObject("result").get("success").getAsBoolean());
        assertEquals(first, service.visitAction(bob, alice, insect));
        assertTrue(service.visitAction(bob, alice, pestCommand(bob, alice, "SPREAD_WEEDS")).getAsJsonObject("result").get("success").getAsBoolean());
        var after = service.farm(bob);
        for (String field : java.util.List.of("coins", "greenCoins", "currentXp", "level", "seedCounts", "produceLots", "fertilizers", "missions"))
            assertEquals(before.get(field), after.get(field), field);
        assertNull(store.transaction(bob, tx -> tx.lastTheft(bob)));
        store.close();
        store = new JdbcOnlineStore(System.getenv("TEST_DATABASE_URL"), "postgres", "", testSchema);
        store.initialize(alice);
        service = new OnlineGameService(store, clock, bound -> bound - 1, 5);
        clock.advanceSeconds(43199);
        for (String kind : java.util.List.of("SPREAD_INSECTS", "SPREAD_WEEDS")) {
            var refused = service.visitAction(bob, carol, pestCommand(bob, carol, kind));
            assertFalse(refused.getAsJsonObject("result").get("success").getAsBoolean());
            assertTrue(refused.getAsJsonObject("result").get("message").getAsString().contains("0h 0min 1s"));
        }
        clock.advanceSeconds(1);
        for (String kind : java.util.List.of("SPREAD_INSECTS", "SPREAD_WEEDS"))
            assertTrue(service.visitAction(bob, carol, pestCommand(bob, carol, kind)).getAsJsonObject("result").get("success").getAsBoolean());
        var tile = service.farm(carol).getAsJsonArray("tiles").get(0).getAsJsonObject();
        assertEquals(1, tile.get("insects").getAsInt());
        assertTrue(tile.get("weeds").getAsBoolean());
        assertTrue(tile.get("paused").getAsBoolean());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"SPREAD_INSECTS", "SPREAD_WEEDS"})
    void protectedPestRefusalPreservesStateAndDoesNotSpendTheVisitorsTurn(String kind) {
        UUID carol = preparePestFriends(true);
        var ownerBefore = service.farm(alice);
        var visitorBefore = service.farm(bob);
        assertFalse(service.visitAction(bob, alice, pestCommand(bob, alice, kind)).getAsJsonObject("result").get("success").getAsBoolean());
        assertEquals(ownerBefore.get("revision"), service.farm(alice).get("revision"));
        assertEquals(visitorBefore.get("revision"), service.farm(bob).get("revision"));
        assertTrue(service.visitAction(bob, carol, pestCommand(bob, carol, kind)).getAsJsonObject("result").get("success").getAsBoolean());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"SPREAD_INSECTS", "SPREAD_WEEDS"})
    void concurrentPestAttacksInDifferentFarmsAllowOnlyOneSuccessPerType(String kind) throws Exception {
        UUID carol = preparePestFriends(false);
        var one = pestCommand(bob, alice, kind); var two = pestCommand(bob, carol, kind);
        var start = new java.util.concurrent.CyclicBarrier(2);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { start.await(5, java.util.concurrent.TimeUnit.SECONDS); return confirmed(() -> service.visitAction(bob, alice, one)); });
            var second = executor.submit(() -> { start.await(5, java.util.concurrent.TimeUnit.SECONDS); return confirmed(() -> service.visitAction(bob, carol, two)); });
            assertEquals(1, (first.get() ? 1 : 0) + (second.get() ? 1 : 0));
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"SPREAD_INSECTS", "SPREAD_WEEDS"})
    void concurrentVisitorsCannotSpendTwoAllowancesOnTheSamePest(String kind) throws Exception {
        UUID carol = preparePestFriends(false);
        var invitation = service.createInvitation(alice, requestId());
        service.acceptInvitation(carol, "Carol", invitation.get("code").getAsString(), requestId());
        var one = pestCommand(bob, alice, kind); var two = pestCommand(carol, alice, kind);
        var start = new java.util.concurrent.CyclicBarrier(2);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { start.await(5, java.util.concurrent.TimeUnit.SECONDS); return confirmed(() -> service.visitAction(bob, alice, one)); });
            var second = executor.submit(() -> { start.await(5, java.util.concurrent.TimeUnit.SECONDS); return confirmed(() -> service.visitAction(carol, alice, two)); });
            assertEquals(1, (first.get() ? 1 : 0) + (second.get() ? 1 : 0));
        }
        assertEquals(1, store.transaction(alice, tx -> tx.query("SELECT count(*) FROM " + testSchema + ".pest_cooldowns", r -> r.getInt(1)).getFirst()).intValue());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"SPREAD_INSECTS", "SPREAD_WEEDS"})
    void pestRollbackRestoresPlantAndAllowanceAndCleanupDoesNotRenewAttack(String kind) {
        preparePestFriends(false);
        var command = pestCommand(bob, alice, kind);
        assertThrows(IllegalStateException.class, () -> store.transaction(bob, tx -> {
            assertTrue(service.visitAction(tx, bob, alice, command).getAsJsonObject("result").get("success").getAsBoolean());
            throw new IllegalStateException("rollback pest");
        }));
        var before = service.farm(alice).getAsJsonArray("tiles").get(0).getAsJsonObject();
        assertEquals(0, before.get("insects").getAsInt()); assertFalse(before.get("weeds").getAsBoolean());
        assertTrue(service.visitAction(bob, alice, command).getAsJsonObject("result").get("success").getAsBoolean());
        var ownerFarm = service.farm(alice);
        var cure = kind.equals("SPREAD_INSECTS") ? "PESTICIDE" : "WEED";
        assertTrue(service.command(alice, new Command(requestId(), ownerFarm.get("revision").getAsString(), cure, null, null, 0, 0, null))
                .getAsJsonObject("result").get("success").getAsBoolean());
        assertFalse(service.visitAction(bob, alice, pestCommand(bob, alice, kind)).getAsJsonObject("result").get("success").getAsBoolean());
    }

    @Test void pestRoutesRequireFriendshipAndCannotBeUsedAsOwnFarmCommands() {
        UUID carol = preparePestFriends(false);
        for (String kind : java.util.List.of("SPREAD_INSECTS", "SPREAD_WEEDS")) {
            var own = service.farm(bob);
            var command = new Command(requestId(), own.get("revision").getAsString(), kind, null, null, 0, 0, "0");
            assertEquals(400, assertThrows(ApiFailure.class, () -> service.command(bob, command)).status());
            assertEquals(404, assertThrows(ApiFailure.class, () -> service.visitAction(bob, bob, command)).status());
            assertEquals(404, assertThrows(ApiFailure.class, () -> service.visitAction(carol, alice, command)).status());
        }
        assertTrue(service.visitAction(bob, alice, pestCommand(bob, alice, "SPREAD_INSECTS")).getAsJsonObject("result").get("success").getAsBoolean());
        prepare(bob, "morango", false, false);
        assertTrue(service.visitAction(carol, bob, pestCommand(carol, bob, "SPREAD_INSECTS")).getAsJsonObject("result").get("success").getAsBoolean());
    }

    private UUID preparePestFriends(boolean protectors) {
        connectFriends();
        UUID carol = UUID.randomUUID();
        String code = service.createInvitation(bob, requestId()).get("code").getAsString();
        service.acceptInvitation(carol, "Carol", code, requestId()); service.farm(carol);
        prepare(alice, "morango", false, protectors);
        prepare(carol, "morango", false, false);
        return carol;
    }

    private Command pestCommand(UUID actor, UUID owner, String kind) {
        var base = theftCommand(actor, owner);
        return new Command(base.requestId(), base.expectedRevision(), kind, null, null, base.column(), base.row(), base.harvestRevision());
    }

    @Test void dailyAllowanceRequiresExplicitClaimPaysOnlyGoldAndReplaysExactly() {
        var before = service.farm(alice);
        assertTrue(before.getAsJsonObject("dailyAllowance").get("available").getAsBoolean());
        assertEquals("100", before.getAsJsonObject("dailyAllowance").get("amount").getAsString());
        assertEquals("500", service.farm(alice).get("coins").getAsString());
        var command = allowanceCommand(alice);
        var reply = service.command(alice, command);
        assertTrue(reply.getAsJsonObject("result").get("success").getAsBoolean());
        var paid = reply.getAsJsonObject("farm");
        assertEquals("600", paid.get("coins").getAsString());
        assertFalse(paid.getAsJsonObject("dailyAllowance").get("available").getAsBoolean());
        for (String field : java.util.List.of("greenCoins", "currentXp", "level", "seedCounts", "produceLots", "fertilizers", "missions", "tutorialStep"))
            assertEquals(before.get(field), paid.get(field), field);
        assertEquals(reply, service.command(alice, command));
        assertFalse(service.command(alice, allowanceCommand(alice)).getAsJsonObject("result").get("success").getAsBoolean());
        assertEquals("600", service.farm(alice).get("coins").getAsString());
    }

    @Test void allowanceRenewsAtUtcMidnightWithoutAccumulatingAbsencesOrAllowingClockRollback() {
        service.command(alice, allowanceCommand(alice));
        clock.advanceSeconds(43199);
        assertFalse(service.farm(alice).getAsJsonObject("dailyAllowance").get("available").getAsBoolean());
        clock.advanceSeconds(1);
        assertTrue(service.farm(alice).getAsJsonObject("dailyAllowance").get("available").getAsBoolean());
        service.command(alice, allowanceCommand(alice));
        assertEquals("700", service.farm(alice).get("coins").getAsString());
        clock.advanceSeconds(4 * 86400);
        service.command(alice, allowanceCommand(alice));
        assertEquals("800", service.farm(alice).get("coins").getAsString());
        clock.advanceSeconds(-86400);
        assertFalse(service.command(alice, allowanceCommand(alice)).getAsJsonObject("result").get("success").getAsBoolean());
        assertEquals("800", service.farm(alice).get("coins").getAsString());
    }

    @Test void dailyAllowanceSurvivesRestartAndIsPrivateAndIndependentPerPlayer() {
        connectFriends();
        service.command(alice, allowanceCommand(alice));
        assertFalse(service.visit(bob, alice).has("dailyAllowance"));
        assertTrue(service.command(bob, allowanceCommand(bob)).getAsJsonObject("result").get("success").getAsBoolean());
        store.close();
        store = new JdbcOnlineStore(System.getenv("TEST_DATABASE_URL"), "postgres", "", testSchema);
        store.initialize(alice);
        service = new OnlineGameService(store, clock, bound -> bound - 1, 5);
        assertFalse(service.farm(alice).getAsJsonObject("dailyAllowance").get("available").getAsBoolean());
        assertEquals("600", service.farm(alice).get("coins").getAsString());
        assertFalse(service.command(bob, allowanceCommand(bob)).getAsJsonObject("result").get("success").getAsBoolean());
        var other = allowanceCommand(alice);
        assertEquals(400, assertThrows(ApiFailure.class, () -> service.visitAction(alice, bob, other)).status());
        var revoked = allowanceCommand(bob);
        store.transaction(alice, tx -> { tx.setActive(bob, false); return null; });
        assertEquals(403, assertThrows(ApiFailure.class, () -> service.command(bob, revoked)).status());
    }

    @Test void concurrentDailyClaimsCreditOneHundredOnlyOnce() throws Exception {
        var one = allowanceCommand(alice); var two = allowanceCommand(alice);
        var start = new java.util.concurrent.CyclicBarrier(2);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { start.await(5, java.util.concurrent.TimeUnit.SECONDS); return confirmed(() -> service.command(alice, one)); });
            var second = executor.submit(() -> { start.await(5, java.util.concurrent.TimeUnit.SECONDS); return confirmed(() -> service.command(alice, two)); });
            assertEquals(1, (first.get() ? 1 : 0) + (second.get() ? 1 : 0));
        }
        assertEquals("600", service.farm(alice).get("coins").getAsString());
    }

    @Test void allowanceAndReceiptRollBackTogether() {
        var claim = allowanceCommand(alice);
        assertThrows(IllegalStateException.class, () -> store.transaction(alice, tx -> {
            assertTrue(service.command(tx, alice, claim).getAsJsonObject("result").get("success").getAsBoolean());
            throw new IllegalStateException("rollback allowance");
        }));
        assertEquals("500", service.farm(alice).get("coins").getAsString());
        assertTrue(service.farm(alice).getAsJsonObject("dailyAllowance").get("available").getAsBoolean());
        assertTrue(service.command(alice, claim).getAsJsonObject("result").get("success").getAsBoolean());
    }

    @Test void allowanceHttpRejectsAClientSuppliedAmountAndAcceptsOnlyTheFixedReward() throws Exception {
        var claim = allowanceCommand(alice);
        Authenticator auth = header -> new Authenticator.Identity(alice, "Alice");
        try (var http = new GameHttpServer(new java.net.InetSocketAddress("127.0.0.1", 0), service, auth,
                java.util.Set.of("http://localhost:5173"), true, clock);
             var client = java.net.http.HttpClient.newHttpClient()) {
            http.start();
            var uri = java.net.URI.create("http://127.0.0.1:" + http.port() + "/v1/me/actions");
            var forged = OnlineViews.JSON.toJsonTree(claim).getAsJsonObject(); forged.addProperty("amount", 9999);
            var bad = java.net.http.HttpRequest.newBuilder(uri).header("Content-Type", "application/json")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(forged.toString())).build();
            assertEquals(400, client.send(bad, java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode());
            var valid = java.net.http.HttpRequest.newBuilder(uri).header("Content-Type", "application/json")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(OnlineViews.JSON.toJson(claim))).build();
            var response = client.send(valid, java.net.http.HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            assertEquals("600", com.google.gson.JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("farm").get("coins").getAsString());
        }
    }

    private Command allowanceCommand(UUID actor) {
        return new Command(requestId(), service.farm(actor).get("revision").getAsString(), "CLAIM_DAILY_ALLOWANCE", null, null, null, null, null);
    }

    @Test void theftNotificationNamesActorIsPrivateAndReadCommandsSurviveReopen() {
        connectFriends();prepare(alice,"morango",true,false);clock.advanceSeconds(301);
        var steal=theftCommand(bob,alice);var reply=service.visitAction(bob,alice,steal);
        assertTrue(reply.getAsJsonObject("result").get("success").getAsBoolean());
        assertEquals(reply,service.visitAction(bob,alice,steal));
        assertFalse(reply.getAsJsonObject("visitedFarm").has("notifications"));
        var notes=service.farm(alice).getAsJsonArray("notifications");
        var theft=java.util.stream.StreamSupport.stream(notes.spliterator(),false).map(e->e.getAsJsonObject()).filter(n->n.get("type").getAsString().equals("THEFT")).toList();
        assertEquals(1,theft.size());assertEquals("Bob furtou 1 Morango da sua fazenda.",theft.getFirst().get("message").getAsString());
        assertEquals("1",theft.getFirst().getAsJsonObject("metadata").get("quantity").getAsString());
        assertEquals(clock.instant().toString(),theft.getFirst().get("createdAt").getAsString());
        var id=theft.getFirst().get("id").getAsString();
        var other=new Command(requestId(),service.farm(bob).get("revision").getAsString(),"READ_NOTIFICATION",id,null,null,null,null);
        assertFalse(service.command(bob,other).getAsJsonObject("result").get("success").getAsBoolean());
        var before=service.farm(alice);
        var read=new Command(requestId(),before.get("revision").getAsString(),"READ_NOTIFICATION",id,null,null,null,null);
        assertTrue(service.command(alice,read).getAsJsonObject("result").get("success").getAsBoolean());
        var all=new Command(requestId(),service.farm(alice).get("revision").getAsString(),"READ_ALL_NOTIFICATIONS",null,null,null,null,null);
        var readReply=service.command(alice,all);assertEquals(readReply,service.command(alice,all));
        assertEquals(before.get("coins"),service.farm(alice).get("coins"));assertEquals(before.get("greenCoins"),service.farm(alice).get("greenCoins"));
        store.close();store=new JdbcOnlineStore(System.getenv("TEST_DATABASE_URL"),"postgres","",testSchema);service=new OnlineGameService(store,clock,bound->99,5);
        assertEquals(0,service.farm(alice).get("unreadNotifications").getAsInt());
        assertEquals(notes.size(),service.farm(alice).getAsJsonArray("notifications").size());
    }

    @Test void theftRollbackAndFailedAttemptsCannotLeaveNotificationsBehind() {
        connectFriends();prepare(alice,"morango",true,false);clock.advanceSeconds(301);
        var before=service.farm(alice);var command=theftCommand(bob,alice);
        assertThrows(IllegalStateException.class,()->store.transaction(bob,tx->{service.visitAction(tx,bob,alice,command);throw new IllegalStateException("rollback notification and theft");}));
        assertEquals(before.get("notifications"),service.farm(alice).get("notifications"));assertEquals(0,quantity(service.farm(bob)));
        assertTrue(service.visitAction(bob,alice,command).getAsJsonObject("result").get("success").getAsBoolean());
        var notes=service.farm(alice).get("notifications");
        assertFalse(service.visitAction(bob,alice,theftCommand(bob,alice)).getAsJsonObject("result").get("success").getAsBoolean());
        assertEquals(notes,service.farm(alice).get("notifications"));
    }

    private UUID prepareTheftFriends() {
        connectFriends();
        UUID carol = UUID.randomUUID();
        String code = service.createInvitation(bob, requestId()).get("code").getAsString();
        service.acceptInvitation(carol, "Carol", code, requestId());
        service.farm(carol);
        prepare(alice, "morango", true, false);
        prepare(carol, "morango", true, false);
        return carol;
    }

    private Command theftCommand(UUID actor, UUID owner) {
        var visit = service.visit(actor, owner);
        return new Command(requestId(), visit.get("revision").getAsString(), "STEAL", null, null, 0, 0,
                visit.getAsJsonArray("tiles").get(0).getAsJsonObject().get("harvestRevision").getAsString());
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
