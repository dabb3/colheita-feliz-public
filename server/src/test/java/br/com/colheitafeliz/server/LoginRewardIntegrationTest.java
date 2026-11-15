package br.com.colheitafeliz.server;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("integration")
class LoginRewardIntegrationTest {
    private final UUID owner = UUID.randomUUID();
    private final TestClock clock = new TestClock(Instant.parse("2026-01-01T12:00:00Z"));
    private JdbcOnlineStore store;
    private OnlineGameService service;
    private String schema;

    @BeforeEach void setUp() {
        String url = System.getenv("TEST_DATABASE_URL");
        assertNotNull(url);
        assertTrue(url.startsWith("jdbc:postgresql://127.0.0.1:") && url.contains("/colheita_test"));
        schema = "login_reward_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        store = new JdbcOnlineStore(url, "postgres", "", schema);
        store.initialize(owner, clock.instant());
        service = new OnlineGameService(store, clock, bound -> bound - 1, 5);
    }

    @AfterEach void close() { if (store != null) store.close(); }

    @Test void claimProgressesAcrossThirtyLocalDatesAndStartsANewCycleWithoutRemovingSeeds() {
        var initial = service.farm(owner);
        var calendar = initial.getAsJsonObject("loginRewards");
        assertEquals(30, calendar.getAsJsonArray("rewards").size());
        assertEquals(29_500, calendar.getAsJsonArray("rewards").asList().stream()
                .filter(item -> item.getAsJsonObject().get("kind").getAsString().equals("GOLD"))
                .mapToInt(item -> item.getAsJsonObject().get("amount").getAsInt()).sum());
        assertTrue(calendar.get("available").getAsBoolean());
        assertEquals(0, calendar.get("completedDay").getAsInt());
        assertEquals(1, calendar.get("nextDay").getAsInt());
        assertEquals(400, assertThrows(ApiFailure.class, () -> new Command(requestId(), initial.get("revision").getAsString(),
                "CLAIM_LOGIN_REWARD", "semente-diamante", "GREEN", null, null, null)).status());
        assertEquals("2026-01-02T03:00:00Z", calendar.get("nextResetAt").getAsString());
        var initialGold = new java.math.BigDecimal(initial.get("coins").getAsString());

        for (int day = 1; day <= 30; day++) {
            if (day > 1) clock.set(Instant.parse("2026-01-01T12:00:00Z").plusSeconds((long) (day - 1) * 86_400));
            var command = claim();
            var reply = service.command(owner, command);
            assertTrue(reply.getAsJsonObject("result").get("success").getAsBoolean(), "day " + day);
            var progress = reply.getAsJsonObject("farm").getAsJsonObject("loginRewards");
            assertEquals(day, progress.get("completedDay").getAsInt());
            assertEquals(day == 30 ? 1 : day + 1, progress.get("nextDay").getAsInt());
            assertFalse(progress.get("available").getAsBoolean());
        }
        var cycleEnd = service.farm(owner).getAsJsonObject("loginRewards");
        assertEquals(30, cycleEnd.get("completedDay").getAsInt());
        assertEquals(1, cycleEnd.get("nextDay").getAsInt());
        assertFalse(cycleEnd.get("available").getAsBoolean());
        assertFalse(cycleEnd.get("streakReset").getAsBoolean());
        var completed = service.farm(owner);
        assertEquals("29500", new java.math.BigDecimal(completed.get("coins").getAsString()).subtract(initialGold).stripTrailingZeros().toPlainString());
        for (String id : java.util.List.of("semente-bronze", "semente-prata", "semente-ouro", "semente-diamante"))
            assertEquals(1, completed.getAsJsonObject("seedCounts").get(id).getAsInt());
        assertEquals(30, store.transaction(owner, tx -> tx.loginRewardProgress(owner).totalClaims()).intValue());
        assertEquals(1, store.transaction(owner, tx -> tx.loginRewardProgress(owner).cycle()).intValue());
        clock.set(Instant.parse("2026-01-31T12:00:00Z"));
        var newDay = service.farm(owner).getAsJsonObject("loginRewards");
        assertEquals(0, newDay.get("completedDay").getAsInt());
        assertEquals(1, newDay.get("nextDay").getAsInt());
        assertTrue(newDay.get("available").getAsBoolean());
        assertFalse(newDay.get("streakReset").getAsBoolean());
        var nextCycle = service.command(owner, claim());
        assertTrue(nextCycle.getAsJsonObject("result").get("success").getAsBoolean());
        assertEquals(1, nextCycle.getAsJsonObject("farm").getAsJsonObject("loginRewards").get("completedDay").getAsInt());
        assertEquals(1, nextCycle.getAsJsonObject("farm").getAsJsonObject("seedCounts").get("semente-bronze").getAsInt());
        assertEquals(31, store.transaction(owner, tx -> tx.loginRewardProgress(owner).totalClaims()).intValue());
        assertEquals(2, store.transaction(owner, tx -> tx.loginRewardProgress(owner).cycle()).intValue());
        for (String id : java.util.List.of("semente-bronze", "semente-prata", "semente-ouro", "semente-diamante"))
            assertEquals(1, nextCycle.getAsJsonObject("farm").getAsJsonObject("seedCounts").get(id).getAsInt());
    }

    @Test void claimUsesSaoPauloDateAndDoesNotChangeTheUtcAllowanceBoundary() {
        clock.set(Instant.parse("2026-01-01T02:59:59Z"));
        var before = service.farm(owner);
        assertEquals("2026-01-01T03:00:00Z", before.getAsJsonObject("loginRewards").get("nextResetAt").getAsString());
        assertTrue(service.command(owner, new Command(requestId(), before.get("revision").getAsString(),
                "CLAIM_DAILY_ALLOWANCE", null, null, null, null, null)).getAsJsonObject("result").get("success").getAsBoolean());
        assertTrue(service.command(owner, claim()).getAsJsonObject("result").get("success").getAsBoolean());
        assertFalse(service.farm(owner).getAsJsonObject("loginRewards").get("available").getAsBoolean());
        clock.set(Instant.parse("2026-01-01T03:00:00Z"));
        var after = service.farm(owner);
        assertTrue(after.getAsJsonObject("loginRewards").get("available").getAsBoolean());
        assertFalse(after.getAsJsonObject("dailyAllowance").get("available").getAsBoolean());
    }

    @Test void sameDayAndConcurrentDifferentRequestsCannotPayTwiceAndReplayIsExact() throws Exception {
        service.farm(owner);
        var same = claim();
        var firstAttempt = claim();
        var startFirst = new java.util.concurrent.CyclicBarrier(2);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { startFirst.await(); return result(() -> service.command(owner, same)); });
            var second = executor.submit(() -> { startFirst.await(); return result(() -> service.command(owner, firstAttempt)); });
            boolean sameWon = first.get();
            assertEquals(1, (sameWon ? 1 : 0) + (second.get() ? 1 : 0));
            var paidCommand = sameWon ? same : firstAttempt;
            var paid = service.command(owner, paidCommand);
            assertEquals(1, store.transaction(owner, tx -> tx.query("SELECT total_claims FROM " + schema
                    + ".login_reward_progress WHERE owner=?", row -> row.getLong(1), owner).getFirst()).longValue());
            assertFalse(service.command(owner, claim()).getAsJsonObject("result").get("success").getAsBoolean());
            assertEquals(paid, service.command(owner, paidCommand));
            var divergent = new Command(paidCommand.requestId(), Long.toString(Long.parseLong(paidCommand.expectedRevision()) + 1), "CLAIM_LOGIN_REWARD", null, null, null, null, null);
            assertEquals(409, assertThrows(ApiFailure.class, () -> service.command(owner, divergent)).status());
        }
        clock.set(clock.instant().plusSeconds(86_400));
        var one = claim(); var two = claim();
        var startSecond = new java.util.concurrent.CyclicBarrier(2);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { startSecond.await(); return result(() -> service.command(owner, one)); });
            var second = executor.submit(() -> { startSecond.await(); return result(() -> service.command(owner, two)); });
            assertEquals(1, (first.get() ? 1 : 0) + (second.get() ? 1 : 0));
        }
        assertEquals(1, store.transaction(owner, tx -> tx.query("SELECT count(*) FROM " + schema + ".login_reward_progress", row -> row.getInt(1)).getFirst()).intValue());
        assertEquals("1250", service.farm(owner).get("coins").getAsString());
    }

    @Test void progressWalletAndReceiptRollBackTogetherAndMissedDateResetsEvenAfterLogin() {
        var command = claim();
        assertThrows(IllegalStateException.class, () -> store.transaction(owner, tx -> {
            service.command(tx, owner, command);
            throw new IllegalStateException("rollback login reward");
        }));
        var before = service.farm(owner);
        assertEquals("500", before.get("coins").getAsString());
        assertTrue(before.getAsJsonObject("loginRewards").get("available").getAsBoolean());
        assertEquals(0, store.transaction(owner, tx -> tx.query("SELECT count(*) FROM " + schema + ".receipts WHERE actor=?", row -> row.getInt(1), owner).getFirst()).intValue());
        assertTrue(service.command(owner, command).getAsJsonObject("result").get("success").getAsBoolean());
        clock.set(clock.instant().plusSeconds(86_400));
        service.farm(owner); // login sem resgate não preserva a sequência
        clock.set(clock.instant().plusSeconds(86_400));
        var missed = service.farm(owner).getAsJsonObject("loginRewards");
        assertEquals(1, missed.get("nextDay").getAsInt());
        assertTrue(missed.get("streakReset").getAsBoolean());
        assertEquals(0, missed.get("completedDay").getAsInt());
        var restarted = service.command(owner, claim());
        assertTrue(restarted.getAsJsonObject("result").get("success").getAsBoolean());
        assertEquals(1, restarted.getAsJsonObject("farm").getAsJsonObject("loginRewards").get("completedDay").getAsInt());
    }

    @Test void sqlFailureWhileWritingProgressRollsBackWalletAndReceipt() {
        store.transaction(owner, tx -> {
            tx.execute("CREATE FUNCTION " + schema + ".fail_login_reward_progress() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'injected failure'; END; $$");
            tx.execute("CREATE TRIGGER fail_login_reward_progress BEFORE INSERT ON " + schema
                    + ".login_reward_progress FOR EACH ROW EXECUTE FUNCTION " + schema + ".fail_login_reward_progress()");
            return null;
        });
        var before = service.farm(owner);
        var command = claim();
        assertEquals(503, assertThrows(ApiFailure.class, () -> service.command(owner, command)).status());
        var after = service.farm(owner);
        assertEquals(before.get("coins"), after.get("coins"));
        assertTrue(after.getAsJsonObject("loginRewards").get("available").getAsBoolean());
        assertEquals(0, store.transaction(owner, tx -> tx.query("SELECT count(*) FROM " + schema + ".login_reward_progress", row -> row.getInt(1)).getFirst()).intValue());
        assertEquals(0, store.transaction(owner, tx -> tx.query("SELECT count(*) FROM " + schema + ".receipts WHERE actor=?", row -> row.getInt(1), owner).getFirst()).intValue());
    }

    @Test void fullSeedCapacityDoesNotAdvanceOrPayTheDaySevenReward() {
        service.farm(owner);
        store.transaction(owner, tx -> {
            tx.lockFarms(owner);
            var saved = tx.farm(owner);
            var game = br.com.colheitafeliz.game.GameState.restoreOnline(saved.time(), clock.instant(),
                    br.com.colheitafeliz.game.CropCatalogLoader.load(), saved.snapshot(), bound -> bound - 1);
            game.inventory().addSeedLot(new br.com.colheitafeliz.domain.inventory.ItemLot("semente-bronze",
                    br.com.colheitafeliz.domain.economy.Currency.GREEN, java.math.BigDecimal.ZERO, Integer.MAX_VALUE, false,
                    br.com.colheitafeliz.domain.inventory.AcquisitionSource.LOGIN_REWARD));
            tx.save(owner, game.snapshot(), saved.revision() + 1, clock.instant());
            return null;
        });
        for (int day = 1; day <= 6; day++) {
            if (day > 1) clock.set(Instant.parse("2026-01-01T12:00:00Z").plusSeconds((long) (day - 1) * 86_400));
            assertTrue(service.command(owner, claim()).getAsJsonObject("result").get("success").getAsBoolean());
        }
        clock.set(Instant.parse("2026-01-07T12:00:00Z"));
        var failed = service.command(owner, claim());
        assertFalse(failed.getAsJsonObject("result").get("success").getAsBoolean());
        assertEquals("6500", failed.getAsJsonObject("farm").get("coins").getAsString());
        var calendar = failed.getAsJsonObject("farm").getAsJsonObject("loginRewards");
        assertEquals(6, calendar.get("completedDay").getAsInt());
        assertEquals(7, calendar.get("nextDay").getAsInt());
        assertTrue(calendar.get("available").getAsBoolean());
        var progress = store.transaction(owner, tx -> tx.loginRewardProgress(owner));
        assertEquals(6, progress.totalClaims());
        assertEquals(6, progress.completedDay());
        assertEquals(Integer.MAX_VALUE, failed.getAsJsonObject("farm").getAsJsonObject("seedCounts").get("semente-bronze").getAsInt());
    }

    @Test void progressTableIsIdempotentAndPrivateToTheFarmOwner() {
        store.initialize(owner, clock.instant());
        assertTrue(Boolean.TRUE.equals(store.transaction(owner, tx -> tx.query("SELECT relrowsecurity FROM pg_class WHERE oid=?::regclass",
                row -> row.getBoolean(1), schema + ".login_reward_progress").getFirst())));
        UUID visitor = UUID.randomUUID();
        var invite = service.createInvitation(owner, requestId());
        service.acceptInvitation(visitor, "Visitor", invite.get("code").getAsString(), requestId());
        assertFalse(service.visit(visitor, owner).has("loginRewards"));
    }

    private Command claim() {
        var farm = service.farm(owner);
        return new Command(requestId(), farm.get("revision").getAsString(), "CLAIM_LOGIN_REWARD", null, null, null, null, null);
    }

    private UUID requestId() { return UUID.fromString(service.requestId().get("requestId").getAsString()); }

    private static boolean result(java.util.function.Supplier<JsonObject> operation) {
        try { return operation.get().getAsJsonObject("result").get("success").getAsBoolean(); }
        catch (ApiFailure e) { if (e.status() == 409) return false; throw e; }
    }

    private static final class TestClock extends Clock {
        private Instant now;
        TestClock(Instant now) { this.now = now; }
        void set(Instant instant) { now = instant; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
