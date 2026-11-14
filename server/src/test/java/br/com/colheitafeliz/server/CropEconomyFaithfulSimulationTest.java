package br.com.colheitafeliz.server;

import br.com.colheitafeliz.domain.animal.AnimalType;
import br.com.colheitafeliz.domain.economy.Currency;
import br.com.colheitafeliz.game.CropCatalogLoader;
import br.com.colheitafeliz.game.GameState;
import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Uses real server transactions; only login/attacker schedules and purchase strategies are assumptions. */
@Tag("integration")
class CropEconomyFaithfulSimulationTest {
    private enum Policy { EXPAND, VIP, PROTECT }
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test void thirtyDaysFromFivePlotsWithEarnedExpansionAndRealSocialRules() throws Exception {
        String url = System.getenv("TEST_DATABASE_URL");
        assertNotNull(url);
        assertTrue(url.startsWith("jdbc:postgresql://127.0.0.1:") && url.endsWith("/colheita_test"));
        StringBuilder csv = new StringBuilder("logins,attackers,policy,level,plots,gold,green,plot_green_spent,vip_green_spent,green_earned,harvests,thefts,attempts,window_denials,protection_denials,cooldown_denials,protectors,book_investment\n");
        for (int logins : new int[]{1,3,6}) for (int attackers : new int[]{0,1,3}) for (Policy policy : Policy.values()) {
            var clock = new AuditClock();
            String schema = "test_" + UUID.randomUUID().toString().replace("-", "");
            try (var store = new JdbcOnlineStore(url, "postgres", "", schema)) {
                store.initialize(OWNER);
                var service = new OnlineGameService(store, clock, bound -> bound - 1, 20);
                var run = new Run(service, clock, logins, attackers, policy);
                run.simulate();
                BigDecimal investment = store.transaction(OWNER, tx -> {
                    BigDecimal total = BigDecimal.ZERO;
                    for (var tile : tx.farm(OWNER).snapshot().tiles()) {
                        if (tile.origin() == null || tile.cropId() == null) continue;
                        var type = CropCatalogLoader.load().get(tile.cropId());
                        total = total.add(tile.origin().unitCost().multiply(BigDecimal.valueOf(type.seasons() - tile.season() + 1L))
                                .divide(BigDecimal.valueOf(type.seasons()), 8, java.math.RoundingMode.HALF_EVEN));
                    }
                    return total;
                });
                run.append(csv, investment);
                store.transaction(OWNER, tx -> { tx.execute("DROP SCHEMA " + schema + " CASCADE"); return null; });
            }
        }
        Path output = Path.of("build/reports/economy-v1/faithful-progression-30-days.csv");
        Files.createDirectories(java.util.Objects.requireNonNull(output.getParent()));
        Files.writeString(output, csv);
    }

    private static final class Run {
        private final OnlineGameService service;
        private final AuditClock clock;
        private final int logins;
        private final Policy policy;
        private final List<UUID> attackers = new ArrayList<>();
        private final Map<UUID, Instant> lastTheft = new HashMap<>();
        private final List<AnimalType> protectors;
        private JsonObject own;
        private int plotsBought, vipBought, harvests, thefts, attempts, windowDenied, protectionDenied, cooldownDenied;

        Run(OnlineGameService service, AuditClock clock, int logins, int count, Policy policy) {
            this.service = service; this.clock = clock; this.logins = logins; this.policy = policy;
            own = service.farm(OWNER);
            assertEquals("500", own.get("coins").getAsString());
            assertEquals("5", own.get("greenCoins").getAsString());
            assertEquals(5, unlocked());
            var catalog = CropCatalogLoader.load();
            protectors = new GameState(clock::instant, clock, catalog, bound -> 99).animalTypes().stream()
                    .filter(type -> type.kind() == AnimalType.Kind.PROTECTOR).toList();
            for (int i = 0; i < count; i++) {
                UUID actor = new UUID(0, 100 + i);
                var invitation = service.createInvitation(OWNER, id());
                service.acceptInvitation(actor, "Visitor " + i, invitation.get("code").getAsString(), id());
                attackers.add(actor);
            }
        }

        private UUID id() { return RequestIds.issue(clock.instant()); }
        private int unlocked() { int total = 0; for (var tile : own.getAsJsonArray("tiles")) if (tile.getAsJsonObject().get("unlocked").getAsBoolean()) total++; return total; }
        private BigDecimal balance(String field) { return own.get(field).getAsBigDecimal(); }
        private JsonObject tile(int index) { return own.getAsJsonArray("tiles").get(index).getAsJsonObject(); }
        private boolean action(String action, String item, String currency, Integer col, Integer row) {
            var result = service.command(OWNER, new Command(id(), own.get("revision").getAsString(), action, item, currency, col, row, null));
            own = result.getAsJsonObject("farm");
            return result.getAsJsonObject("result").get("success").getAsBoolean();
        }

        void simulate() {
            Instant start = clock.instant();
            for (int hour = 0; hour < 720; hour++) {
                clock.now = start.plusSeconds(hour * 3600L);
                if (hour % (24 / logins) == 0) login(); // Owner wins when schedules coincide.
                raid();
            }
            clock.now = start.plus(Duration.ofDays(30));
            own = service.farm(OWNER);
            assertEquals(5 + plotsBought, unlocked());
            assertTrue(balance("coins").signum() >= 0);
            assertTrue(balance("greenCoins").signum() >= 0);
            assertTrue(thefts <= attackers.size() * 30);
        }

        private void login() {
            own = service.farm(OWNER);
            if (own.getAsJsonObject("dailyAllowance").get("available").getAsBoolean())
                assertTrue(action("CLAIM_DAILY_ALLOWANCE", null, null, null, null));
            for (int i = 0; i < 30; i++) if (tile(i).get("state").getAsString().equals("READY")) {
                assertTrue(action("HARVEST", null, null, i % 6, i / 6)); harvests++;
            }
            if (!own.getAsJsonArray("produceLots").isEmpty()) assertTrue(action("SELL_ALL", null, null, null, null));
            if (policy == Policy.VIP && vipBought == 0 && balance("greenCoins").compareTo(new BigDecimal("100")) >= 0) {
                assertTrue(action("BUY_VIP", null, null, null, null)); vipBought++;
            }
            if (policy != Policy.VIP || vipBought > 0) while (balance("greenCoins").compareTo(new BigDecimal("50")) >= 0
                    && !own.get("nextPlot").isJsonNull()) {
                var plot = own.getAsJsonObject("nextPlot");
                if (own.get("level").getAsInt() < plot.get("requiredLevel").getAsInt()) break;
                assertTrue(action("BUY_PLOT", null, null, plot.get("column").getAsInt(), plot.get("row").getAsInt())); plotsBought++;
            }
            if (policy == Policy.PROTECT) careForProtectors();
            var catalog = CropCatalogLoader.load();
            for (int i = 0; i < 30; i++) {
                if (!tile(i).get("unlocked").getAsBoolean()) continue;
                String state = tile(i).get("state").getAsString();
                if (state.equals("GRASS") || state.equals("DEAD")) assertTrue(action("PLOW", null, null, i % 6, i / 6));
                if (tile(i).get("state").getAsString().equals("PLOWED")) {
                    var seed = catalog.all().stream().filter(type -> !type.vipOnly() && type.requiredLevel() <= own.get("level").getAsInt()
                            && type.seedPrice().getValue().compareTo(balance("coins")) <= 0).reduce((first, second) -> second);
                    if (seed.isPresent()) {
                        assertTrue(action("BUY_SEED", seed.get().id(), "GOLD", null, null));
                        assertTrue(action("PLANT", seed.get().id(), null, i % 6, i / 6));
                    }
                }
                if (tile(i).get("state").getAsString().equals("GROWING") && tile(i).get("dry").getAsBoolean())
                    assertTrue(action("WATER", null, null, i % 6, i / 6));
            }
        }

        private void careForProtectors() {
            for (var type : protectors) {
                boolean owned = false;
                for (var animal : own.getAsJsonArray("animals")) if (animal.getAsJsonObject().get("id").getAsString().equals(type.id())) owned = true;
                if (!owned && type.requiredLevel() <= own.get("level").getAsInt()
                        && balance("coins").compareTo(type.prices().get(Currency.GOLD).add(BigDecimal.valueOf(unlocked() * 300L))) >= 0)
                    assertTrue(action("BUY_ANIMAL", type.id(), "GOLD", null, null));
            }
            var hungry = new ArrayList<String>();
            for (var animal : own.getAsJsonArray("animals")) if (animal.getAsJsonObject().get("status").getAsString().equals("HUNGRY"))
                hungry.add(animal.getAsJsonObject().get("id").getAsString());
            for (String animal : hungry) if (balance("coins").compareTo(new BigDecimal("80")) >= 0) {
                assertTrue(action("BUY_FEED", "growth", null, null, null));
                assertTrue(action("TEND_ANIMAL", animal, null, null, null));
            }
        }

        private void raid() {
            if (attackers.isEmpty()) return;
            var visit = service.visit(attackers.getFirst(), OWNER);
            for (var actor : attackers) for (int i = 0; i < 30; i++) {
                var target = visit.getAsJsonArray("tiles").get(i).getAsJsonObject();
                if (!target.get("state").getAsString().equals("READY")) continue;
                attempts++;
                var reply = service.visitAction(actor, OWNER, new Command(id(), visit.get("revision").getAsString(), "STEAL", null, null,
                        i % 6, i / 6, target.get("harvestRevision").getAsString()));
                visit = reply.getAsJsonObject("visitedFarm");
                var result = reply.getAsJsonObject("result");
                String message = result.get("message").getAsString();
                if (result.get("success").getAsBoolean()) {
                    var last = lastTheft.put(actor, clock.instant());
                    assertTrue(last == null || !clock.instant().isBefore(last.plus(OnlineGameService.THEFT_COOLDOWN)));
                    thefts++; break;
                }
                if (message.contains("reservada ao dono")) windowDenied++;
                else if (message.contains("últimas 6 horas")) { cooldownDenied++; break; }
                else if (target.get("protected").getAsBoolean()) protectionDenied++;
                else fail("Unexpected theft refusal: " + message);
            }
        }

        void append(StringBuilder csv, BigDecimal investment) {
            BigDecimal spentPlots = BigDecimal.valueOf(plotsBought * 50L), spentVip = BigDecimal.valueOf(vipBought * 100L);
            var earned = balance("greenCoins").add(spentPlots).add(spentVip).subtract(new BigDecimal("5"));
            assertTrue(earned.signum() >= 0);
            csv.append(logins).append(',').append(attackers.size()).append(',').append(policy).append(',')
                    .append(own.get("level").getAsInt()).append(',').append(unlocked()).append(',').append(balance("coins"))
                    .append(',').append(balance("greenCoins")).append(',').append(spentPlots).append(',').append(spentVip)
                    .append(',').append(earned).append(',').append(harvests).append(',').append(thefts).append(',').append(attempts)
                    .append(',').append(windowDenied).append(',').append(protectionDenied).append(',').append(cooldownDenied)
                    .append(',').append(own.getAsJsonArray("animals").size()).append(',').append(investment).append('\n');
        }
    }

    private static final class AuditClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");
        @Override public Instant instant() { return now; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
    }
}
