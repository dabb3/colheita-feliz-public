package br.com.colheitafeliz.server;

import br.com.colheitafeliz.game.Christmas2026;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
class ChristmasGiftIntegrationTest {
    private final UUID owner=UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final TestClock clock=new TestClock();
    private JdbcOnlineStore store;
    private OnlineGameService service;
    private String schema;
    @BeforeEach void setup(){
        var url=System.getenv("TEST_DATABASE_URL");assertNotNull(url);
        assertTrue((url.startsWith("jdbc:postgresql://127.0.0.1:")||url.startsWith("jdbc:postgresql://localhost:"))&&url.contains("/colheita_test"));
        schema="test_"+UUID.randomUUID().toString().replace("-","");
        store=new JdbcOnlineStore(url,"postgres","",schema);store.initialize(owner,Instant.parse("2026-11-06T03:00:00Z"));
        service=new OnlineGameService(store,clock,bound->99,20);service.farm(owner);
    }
    @AfterEach void close(){if(store!=null)store.close();}
    private UUID request(){return RequestIds.issue(clock.instant());}
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"VISIT","STEAL","LOVE","SPREAD_INSECTS","SPREAD_WEEDS"})
    void friendCannotClaimAbsentOwnersGiftAndOwnerCanClaimOnceAfterEvent(String action) throws Exception {
        var friend=UUID.randomUUID();var invite=service.createInvitation(owner,request()).get("code").getAsString();
        service.acceptInvitation(friend,"Amigo",invite,request());service.farm(friend);
        var visited=service.visit(friend,owner);clock.at=Christmas2026.GIFT_START.plusSeconds(3600);
        if(action.equals("VISIT"))service.visit(friend,owner);
        else service.visitAction(friend,owner,new Command(request(),visited.get("revision").getAsString(),action,null,null,0,0,"0"));
        assertGiftPending(owner);assertGiftPending(friend);
        clock.at=Christmas2026.END.plusSeconds(86400);store.close();
        store=new JdbcOnlineStore(System.getenv("TEST_DATABASE_URL"),"postgres","",schema);service=new OnlineGameService(store,clock,bound->99,20);
        service.visit(friend,owner);assertGiftPending(owner);
        try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)){
            var first=executor.submit(()->service.farm(owner));var second=executor.submit(()->service.farm(owner));
            assertEquals(1,first.get().getAsJsonObject("seedCounts").get("mini-bis").getAsInt());
            assertEquals(1,second.get().getAsJsonObject("seedCounts").get("mini-bis").getAsInt());
        }
        var own=service.farm(owner);assertEquals(1,own.getAsJsonObject("seedCounts").get("mini-bis").getAsInt());
        store.transaction(owner,tx->{
            var snapshot=tx.farm(owner).snapshot();assertTrue(snapshot.christmas2026().giftGranted());
            assertEquals("2026",snapshot.notifications().observed().get("christmas-gift-2026"));
            assertEquals(1,snapshot.notifications().entries().stream().filter(n->n.type()==br.com.colheitafeliz.domain.notification.GameNotification.Type.CHRISTMAS_GIFT).count());
            assertEquals(Instant.parse("2026-11-06T03:00:00Z"),tx.player(owner).registeredAt());return null;
        });
        assertGiftPending(friend);
    }
    private void assertGiftPending(UUID player){
        store.transaction(player,tx->{var snapshot=tx.farm(player).snapshot();
            assertFalse(snapshot.christmas2026().giftGranted());
            assertTrue(snapshot.seeds().stream().noneMatch(lot->lot.itemId().equals("mini-bis")));
            assertFalse(snapshot.notifications().observed().containsKey("christmas-gift-2026"));
            assertTrue(snapshot.notifications().entries().stream().noneMatch(n->n.type()==br.com.colheitafeliz.domain.notification.GameNotification.Type.CHRISTMAS_GIFT));return null;
        });
    }
    @Test void concurrentRefreshAndReopenGrantOneSeedAndOneNoticeWithoutChargingOrCompletingMissions() throws Exception {
        var before=service.farm(owner);clock.at=Christmas2026.GIFT_START;
        try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)){
            var first=executor.submit(()->service.farm(owner));var second=executor.submit(()->service.farm(owner));
            assertEquals(1,first.get().getAsJsonObject("seedCounts").get("mini-bis").getAsInt());
            assertEquals(1,second.get().getAsJsonObject("seedCounts").get("mini-bis").getAsInt());
        }
        var after=service.farm(owner);assertEquals(before.get("coins"),after.get("coins"));assertEquals(before.get("greenCoins"),after.get("greenCoins"));assertEquals(before.get("missions"),after.get("missions"));
        assertEquals(1,after.getAsJsonArray("notifications").size());
        clock.at=Christmas2026.END.plusSeconds(86400);store.close();store=new JdbcOnlineStore(System.getenv("TEST_DATABASE_URL"),"postgres","",schema);service=new OnlineGameService(store,clock,bound->99,20);
        assertEquals(1,service.farm(owner).getAsJsonObject("seedCounts").get("mini-bis").getAsInt());assertEquals(1,service.farm(owner).getAsJsonArray("notifications").size());
    }
    @Test void rollbackDoesNotSpendGiftAndRetryGrantsExactlyOne(){
        clock.at=Christmas2026.GIFT_START;
        assertThrows(IllegalStateException.class,()->store.transaction(owner,tx->{service.farm(tx,owner,null);throw new IllegalStateException("rollback gift");}));
        store.transaction(owner,tx->{var snapshot=tx.farm(owner).snapshot();assertFalse(snapshot.christmas2026().giftGranted());assertTrue(snapshot.seeds().isEmpty());return null;});
        assertEquals(1,service.farm(owner).getAsJsonObject("seedCounts").get("mini-bis").getAsInt());
    }
    @Test void registrationOnTheTwentyFourthQualifiesEvenWithoutFirstFarmLoadButLaterRegistrationDoesNot(){
        clock.at=Christmas2026.REGISTRATION_CUTOFF.minusSeconds(1);var eligible=UUID.randomUUID();
        var invite=service.createInvitation(owner,request()).get("code").getAsString();service.acceptInvitation(eligible,"Eligible",invite,request());
        clock.at=Christmas2026.REGISTRATION_CUTOFF;var late=UUID.randomUUID();
        invite=service.createInvitation(owner,request()).get("code").getAsString();service.acceptInvitation(late,"Late",invite,request());
        clock.at=Christmas2026.END.plusSeconds(86400);
        assertEquals(1,service.farm(eligible).getAsJsonObject("seedCounts").get("mini-bis").getAsInt());
        assertEquals(0,service.farm(late).getAsJsonObject("seedCounts").get("mini-bis").getAsInt());
        assertFalse(service.friends(owner).get(0).getAsJsonObject().has("registeredAt"));
    }
    @Test void repeatedInitializationDoesNotChangeOriginalRegistration(){
        store.initialize(owner,Christmas2026.REGISTRATION_CUTOFF.plusSeconds(100));
        assertEquals(Instant.parse("2026-11-06T03:00:00Z"),store.transaction(owner,tx->tx.player(owner).registeredAt()));
    }
    @Test void historicalPlayerTableBackfillsPreexistingAccountsAndPreservesThemOnRestart(){
        store.transaction(owner,tx->{tx.execute("ALTER TABLE "+schema+".players DROP COLUMN registered_at");return null;});
        store.initialize(owner,Christmas2026.REGISTRATION_CUTOFF);
        assertEquals(Instant.EPOCH,store.transaction(owner,tx->tx.player(owner).registeredAt()));
        store.initialize(owner,Christmas2026.END);
        assertEquals(Instant.EPOCH,store.transaction(owner,tx->tx.player(owner).registeredAt()));
        clock.at=Christmas2026.END;assertEquals(1,service.farm(owner).getAsJsonObject("seedCounts").get("mini-bis").getAsInt());
    }
    @Test void corruptGiftReceiptCannotDuplicateSeedOrOverwritePersistedFarm(){
        clock.at=Christmas2026.GIFT_START;service.farm(owner);
        var original=store.transaction(owner,tx->OnlineViews.JSON.toJsonTree(tx.farm(owner).snapshot()).getAsJsonObject());
        for(var corruption:java.util.List.of("falseReceipt","missingReceipt","missingCursor")){
            var damaged=original.deepCopy();
            switch(corruption){
                case "falseReceipt"->damaged.getAsJsonObject("christmas2026").addProperty("giftGranted",false);
                case "missingReceipt"->damaged.remove("christmas2026");
                case "missingCursor"->damaged.getAsJsonObject("notifications").getAsJsonObject("observed").remove("christmas-gift-2026");
                default->throw new AssertionError(corruption);
            }
            store.transaction(owner,tx->{tx.execute("UPDATE "+schema+".farms SET snapshot=?::jsonb WHERE owner=?",damaged.toString(),owner);return null;});
            assertThrows(RuntimeException.class,()->service.farm(owner),corruption);
            var stored=store.transaction(owner,tx->tx.query("SELECT snapshot::text FROM "+schema+".farms WHERE owner=?",r->r.getString(1),owner).getFirst());
            assertEquals(damaged,com.google.gson.JsonParser.parseString(stored));
            store.transaction(owner,tx->{tx.execute("UPDATE "+schema+".farms SET snapshot=?::jsonb WHERE owner=?",original.toString(),owner);return null;});
        }
        assertEquals(1,service.farm(owner).getAsJsonObject("seedCounts").get("mini-bis").getAsInt());
    }
    private static final class TestClock extends Clock {
        Instant at=Instant.parse("2026-12-23T12:00:00Z");
        @Override public Instant instant(){return at;}
        @Override public ZoneId getZone(){return ZoneOffset.UTC;}
        @Override public Clock withZone(ZoneId zone){return this;}
    }
}
