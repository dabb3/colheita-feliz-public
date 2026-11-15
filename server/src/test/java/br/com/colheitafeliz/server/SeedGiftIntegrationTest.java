package br.com.colheitafeliz.server;

import br.com.colheitafeliz.domain.economy.Currency;
import br.com.colheitafeliz.domain.inventory.ItemLot;
import br.com.colheitafeliz.game.CropCatalogLoader;
import br.com.colheitafeliz.game.GameState;
import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
class SeedGiftIntegrationTest {
    private final UUID alice = UUID.randomUUID(), bob = UUID.randomUUID();
    private final ProtectorBiteIntegrationTest.TestClock clock = new ProtectorBiteIntegrationTest.TestClock();
    private JdbcOnlineStore store; private OnlineGameService service; private String schema;

    @BeforeEach void setup() {
        String url = System.getenv("TEST_DATABASE_URL"); assertNotNull(url);
        assertTrue(url.startsWith("jdbc:postgresql://127.0.0.1:") && url.contains("/colheita_test"));
        schema = "test_" + UUID.randomUUID().toString().replace("-", "");
        store = new JdbcOnlineStore(url, "postgres", "", schema); store.initialize(alice, clock.instant());
        service = new OnlineGameService(store, clock, bound -> bound - 1, 20);
        service.farm(alice);
        service.acceptInvitation(bob, "Bob", service.createInvitation(alice, id()).get("code").getAsString(), id());
        service.farm(bob);
        edit(alice, game -> game.inventory().addSeedLot(new ItemLot("nabo", Currency.GREEN, new BigDecimal("3"), 5, false)));
    }
    @AfterEach void close() { if (store != null) store.close(); }

    private UUID id() { return RequestIds.issue(clock.instant()); }
    private void edit(UUID player, Consumer<GameState> change) {
        store.transaction(player, tx -> { tx.lockFarms(player); var saved = tx.farm(player);
            var game = GameState.restoreOnline(saved.time(), clock.instant(), CropCatalogLoader.load(), saved.snapshot(), n -> 99);
            change.accept(game); tx.save(player, game.snapshot(), saved.revision() + 1, clock.instant()); return null; });
    }
    private JsonObject farm(UUID player) { var view = service.farm(player); view.remove("requestLease"); view.remove("serverTime"); return view; }
    private int seeds(UUID player, String crop) { var counts = farm(player).getAsJsonObject("seedCounts"); return counts.has(crop) ? counts.get(crop).getAsInt() : 0; }
    private boolean success(JsonObject reply) { return reply.getAsJsonObject("result").get("success").getAsBoolean(); }
    private int status(Runnable call) { return assertThrows(ApiFailure.class, call::run).status(); }
    private int giftRows() { return store.transaction(alice, tx -> tx.query("SELECT count(*) FROM " + schema + ".seed_gifts", r -> r.getInt(1)).getFirst()); }
    private void rewardsUnchanged(JsonObject before, JsonObject after) {
        for (String key : List.of("coins", "greenCoins", "currentXp", "level", "missions")) assertEquals(before.get(key), after.get(key), key);
    }

    @Test void deliversLotsOnceNotifiesAndReplaysTheSameReceipt() {
        var aliceBefore = farm(alice); var bobBefore = farm(bob);
        UUID request = id();
        var reply = service.sendGift(alice, bob, "nabo", 3, request);
        assertTrue(success(reply));
        assertEquals(2, seeds(alice, "nabo")); assertEquals(3, seeds(bob, "nabo"));
        assertEquals(reply, service.sendGift(alice, bob, "nabo", 3, request));
        assertEquals(2, seeds(alice, "nabo")); assertEquals(3, seeds(bob, "nabo")); assertEquals(1, giftRows());
        rewardsUnchanged(aliceBefore, farm(alice)); rewardsUnchanged(bobBefore, farm(bob));
        var lots = store.transaction(bob, tx -> tx.farm(bob).snapshot().seeds()).stream().filter(lot -> lot.itemId().equals("nabo")).toList();
        assertEquals(3, lots.size());
        assertTrue(lots.stream().allMatch(lot -> lot.currency() == Currency.GREEN && lot.unitCost().compareTo(new BigDecimal("3")) == 0 && !lot.vipOnly()));
        var notes = store.transaction(bob, tx -> tx.farm(bob).snapshot().notifications().entries());
        assertTrue(notes.stream().anyMatch(n -> n.message().equals("Minha fazenda enviou 3 semente(s) de Nabo de presente.")
                && n.metadata().get("actorName").equals("Minha fazenda") && n.metadata().get("quantity").equals("3") && !n.read()));
        var received = service.gifts(bob).getAsJsonArray("received");
        assertEquals(1, received.size());
        var gift = received.get(0).getAsJsonObject();
        assertEquals(alice.toString(), gift.get("senderId").getAsString());
        assertEquals("nabo", gift.get("cropId").getAsString()); assertEquals(3, gift.get("quantity").getAsInt());
        assertEquals("DELIVERED", gift.get("state").getAsString()); assertEquals(clock.instant().toString(), gift.get("createdAt").getAsString());
        assertEquals(1, service.gifts(alice).getAsJsonArray("sent").size()); assertEquals(0, service.gifts(alice).getAsJsonArray("received").size());
    }

    @Test void concurrentSendsToTheSameFriendTransferOnlyOnce() throws Exception {
        var first = id(); var second = id(); var start = new java.util.concurrent.CyclicBarrier(2);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> { start.await(); return service.sendGift(alice, bob, "nabo", 2, first); });
            var b = executor.submit(() -> { start.await(); return service.sendGift(alice, bob, "nabo", 2, second); });
            assertEquals(1, (success(a.get()) ? 1 : 0) + (success(b.get()) ? 1 : 0));
        }
        assertEquals(3, seeds(alice, "nabo")); assertEquals(2, seeds(bob, "nabo")); assertEquals(1, giftRows());
    }

    @Test void insufficientStockAndInvalidQuantitiesChangeNothing() {
        edit(alice, game -> game.inventory().takeSeed("nabo"));
        var before = farm(alice);
        var reply = service.sendGift(alice, bob, "nabo", 5, id());
        assertFalse(success(reply));
        assertEquals("Você não tem 5 semente(s) de Nabo.", reply.getAsJsonObject("result").get("message").getAsString());
        assertEquals(400, status(() -> service.sendGift(alice, bob, "nabo", 0, id())));
        assertEquals(400, status(() -> service.sendGift(alice, bob, "nabo", 6, id())));
        assertEquals(400, status(() -> service.sendGift(alice, bob, "inexistente", 1, id())));
        assertEquals(before, farm(alice)); assertEquals(0, seeds(bob, "nabo")); assertEquals(0, giftRows());
    }

    @Test void onlyActiveFriendsOtherThanYourselfCanReceive() {
        assertEquals(404, status(() -> service.sendGift(alice, alice, "nabo", 1, id())));
        UUID stranger = UUID.randomUUID();
        service.acceptInvitation(stranger, "Carol", service.createInvitation(bob, id()).get("code").getAsString(), id());
        assertEquals(404, status(() -> service.sendGift(alice, stranger, "nabo", 1, id())));
        store.transaction(bob, tx -> { tx.setActive(bob, false); return null; });
        assertEquals(403, status(() -> service.sendGift(alice, bob, "nabo", 1, id())));
        store.transaction(bob, tx -> { tx.setActive(bob, true); tx.execute("DELETE FROM " + schema + ".friendships"); return null; });
        assertEquals(404, status(() -> service.sendGift(alice, bob, "nabo", 1, id())));
        assertEquals(5, seeds(alice, "nabo")); assertEquals(0, giftRows());
    }

    @Test void oneGiftPerFriendEveryTwentyFourHours() {
        assertTrue(success(service.sendGift(alice, bob, "nabo", 1, id())));
        clock.advance(86399);
        var blocked = service.sendGift(alice, bob, "nabo", 1, id());
        assertFalse(success(blocked));
        assertEquals("Você já enviou um presente a este amigo nas últimas 24 horas. Novo envio em 0h 0min 1s.",
                blocked.getAsJsonObject("result").get("message").getAsString());
        clock.advance(1);
        assertTrue(success(service.sendGift(alice, bob, "nabo", 1, id())));
        assertEquals(3, seeds(alice, "nabo")); assertEquals(2, seeds(bob, "nabo")); assertEquals(2, giftRows());
    }

    @Test void vipAndLevelGatedSeedsRequireAnEligibleRecipient() {
        edit(alice, game -> {
            game.inventory().addSeedLot(new ItemLot("batata-doce", Currency.GREEN, new BigDecimal("2"), 1, true));
            game.inventory().addSeedLot(new ItemLot("milho", Currency.GOLD, new BigDecimal("2"), 1, false));
        });
        var levelBlocked = service.sendGift(alice, bob, "milho", 1, id());
        assertEquals("Seu amigo precisa do nível 2 para plantar Milho.", levelBlocked.getAsJsonObject("result").get("message").getAsString());
        edit(bob, game -> { while (game.progress().level() < 10) game.progress().award(game.progress().xpToNext()); });
        var vipBlocked = service.sendGift(alice, bob, "batata-doce", 1, id());
        assertFalse(success(vipBlocked));
        assertTrue(vipBlocked.getAsJsonObject("result").get("message").getAsString().contains("VIP"));
        assertEquals(1, seeds(alice, "batata-doce")); assertEquals(0, seeds(bob, "batata-doce")); assertEquals(0, giftRows());
        edit(bob, game -> { game.wallet().credit(Currency.GREEN, new BigDecimal("5000")); assertTrue(game.buyVip().success()); });
        assertTrue(success(service.sendGift(alice, bob, "batata-doce", 1, id())));
        assertEquals(1, seeds(bob, "batata-doce"));
    }
}
