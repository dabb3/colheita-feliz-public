package br.com.colheitafeliz.server;

import com.google.gson.JsonObject;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
class SocialPushIntegrationTest {
    private final UUID owner=UUID.randomUUID(),actor=UUID.randomUUID();
    private final Instant now=Instant.parse("2026-11-13T12:00:00Z");
    private JdbcOnlineStore store;
    private PushService push;
    private String schema;
    private JsonObject device;
    @BeforeEach void setup(){
        String url=System.getenv("TEST_DATABASE_URL");
        assertNotNull(url);assertTrue(url.startsWith("jdbc:postgresql://127.0.0.1:")&&url.contains("/colheita_test"));
        schema="test_"+UUID.randomUUID().toString().replace("-","");
        store=new JdbcOnlineStore(url,"postgres","",schema);store.initialize(owner,now);store.initialize(actor,now);
        push=new PushService(store,Clock.fixed(now,ZoneOffset.UTC),(e,p,a,b)->{fail("No network during consumption");return 201;},"public");
        device=PushStoreIntegrationTest.subscription(1,1);push.subscribe(owner,device);
    }
    @AfterEach void close(){if(store!=null)store.close();}
    private void consent(String key,boolean value){
        long revision=device.get("deviceRevision").getAsLong()+1;device.addProperty("deviceRevision",revision);
        var request=PushStoreIntegrationTest.test(device);request.remove("testId");request.addProperty(key,value);push.preferences(owner,request);
    }
    private UUID event(String type){UUID id=UUID.randomUUID();store.transaction(actor,tx->{store.pushStore().recordEvent(tx,id,owner,actor,type,"{}",now);return null;});return id;}
    private List<SocialPushStore.Delivery> consume(){return store.transaction(owner,tx->store.socialPushStore().consume(tx,owner,now));}
    @Test void optInSkipsHistoryAndReservesNewEventsOnceAcrossRestart(){
        event("THEFT");consent("thefts",true);UUID fresh=event("THEFT");
        var deliveries=consume();assertEquals(1,deliveries.size());assertEquals("THEFT",deliveries.getFirst().type());
        assertEquals(fresh,store.transaction(owner,tx->tx.query("SELECT request_id FROM "+schema+".push_events WHERE id=?",r->r.getObject(1,UUID.class),deliveries.getFirst().event()).getFirst()));
        store.close();store=new JdbcOnlineStore(System.getenv("TEST_DATABASE_URL"),"postgres","",schema);store.initialize(owner,now);
        assertTrue(consume().isEmpty());
    }
    @Test void categoriesHaveIndependentBaselinesAndUnconfirmedDevicesGetNothing(){
        event("THEFT");event("PEST");assertTrue(consume().isEmpty());
        consent("thefts",true);event("THEFT");event("PEST");
        assertEquals(List.of("THEFT"),consume().stream().map(SocialPushStore.Delivery::type).toList());
        consent("pests",true);event("PEST");event("THEFT");
        assertEquals(List.of("PEST","THEFT"),consume().stream().map(SocialPushStore.Delivery::type).toList());
    }
    @Test void reactivationSkipsDisabledPeriodAndAccountOrRevisionChangesInvalidateReservations(){
        consent("thefts",true);event("THEFT");var reserved=consume().getFirst();
        assertTrue(store.transaction(owner,tx->store.socialPushStore().current(tx,reserved)).booleanValue());
        consent("thefts",false);assertFalse(store.transaction(owner,tx->store.socialPushStore().current(tx,reserved)).booleanValue());
        event("THEFT");consent("thefts",true);assertTrue(consume().isEmpty());
        event("THEFT");final var latest=consume().getFirst();
        store.transaction(owner,tx->{tx.setActive(owner,false);return null;});
        assertFalse(store.transaction(owner,tx->store.socialPushStore().current(tx,latest)).booleanValue());
    }
    @Test void rollbackDoesNotConsumeAndOwnerIsolationIsPreserved(){
        consent("thefts",true);event("THEFT");
        assertThrows(IllegalStateException.class,()->store.transaction(owner,tx->{assertEquals(1,store.socialPushStore().consume(tx,owner,now).size());throw new IllegalStateException();}));
        assertTrue(store.transaction(actor,tx->store.socialPushStore().consume(tx,actor,now)).isEmpty());
        assertEquals(1,consume().size());assertTrue(consume().isEmpty());
    }
    @Test void newDeviceAndCategoryRevisionDoNotReceiveOlderEvents(){
        consent("thefts",true);event("THEFT");var first=device;
        device=PushStoreIntegrationTest.subscription(2,1);push.subscribe(owner,device);consent("thefts",true);
        var older=consume();assertEquals(1,older.size());assertEquals(first.get("endpoint").getAsString(),older.getFirst().subscription().endpoint());
        consent("pests",true);event("THEFT");assertEquals(2,consume().size());
    }
    @Test void transferInvalidatesReservationAndDoesNotCarryConsent(){
        consent("thefts",true);event("THEFT");var delivery=consume().getFirst();
        var transfer=device.deepCopy();transfer.addProperty("deviceRevision",3);transfer.addProperty("bindingId",PushStoreIntegrationTest.token(25));push.subscribe(actor,transfer);
        assertFalse(store.transaction(owner,tx->store.socialPushStore().current(tx,delivery)).booleanValue());
        event("THEFT");assertTrue(consume().isEmpty());
    }
    @Test void concurrentConsumersReserveOnce() throws Exception {
        consent("thefts",true);event("THEFT");
        var barrier=new java.util.concurrent.CyclicBarrier(2);
        try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)){
            var first=executor.submit(()->{barrier.await();return consume().size();});
            var second=executor.submit(()->{barrier.await();return consume().size();});
            assertEquals(1,first.get(10,java.util.concurrent.TimeUnit.SECONDS)+second.get(10,java.util.concurrent.TimeUnit.SECONDS));
        }
    }
    @Test void boundedPagesPreserveOrderEvenWhenEventClockMovesBackwards(){
        consent("thefts",true);
        store.transaction(actor,tx->{for(int i=0;i<101;i++)store.pushStore().recordEvent(tx,UUID.randomUUID(),owner,actor,i%2==0?"THEFT":"PEST","{}",now.minusSeconds(i));return null;});
        assertEquals(50,consume().size());assertEquals(1,consume().size());assertTrue(consume().isEmpty());
    }
    @Test void migrationProtectsPrivateConsumerTables(){
        store.initialize(owner,now);store.initialize(owner,now);
        for(String table:List.of("social_push_devices","social_push_cursors","social_push_deliveries")){
            assertTrue(store.transaction(owner,tx->tx.query("SELECT relrowsecurity FROM pg_class JOIN pg_namespace ON pg_namespace.oid=relnamespace WHERE nspname=? AND relname=?",r->r.getBoolean(1),schema,table).getFirst()).booleanValue());
            assertEquals(0,store.transaction(owner,tx->tx.query("SELECT count(*) FROM information_schema.table_privileges WHERE table_schema=? AND table_name=? AND grantee IN ('PUBLIC','anon','authenticated')",r->r.getInt(1),schema,table).getFirst()).intValue());
        }
    }
    @Test void legacyConfirmedSubscriptionStartsAtCurrentBaseline(){
        event("THEFT");
        store.transaction(owner,tx->{tx.execute("UPDATE "+schema+".push_subscriptions SET preferences='{\"thefts\":true,\"theftsConfirmed\":true}'::jsonb WHERE owner=?",owner);return null;});
        assertTrue(consume().isEmpty());event("THEFT");assertEquals(1,consume().size());
    }
    @Test void producerCommitOrderCannotCrossTheConsumerCursor() throws Exception {
        consent("thefts",true);UUID third=UUID.randomUUID();store.initialize(third,now);
        var entered=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);
        try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)){
            var first=executor.submit(()->store.transaction(actor,tx->{
                store.pushStore().recordEvent(tx,UUID.randomUUID(),owner,actor,"THEFT","{}",now);
                entered.countDown();try{assertTrue(release.await(10,java.util.concurrent.TimeUnit.SECONDS));}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}return null;
            }));
            assertTrue(entered.await(10,java.util.concurrent.TimeUnit.SECONDS));
            var second=executor.submit(()->store.transaction(third,tx->{store.pushStore().recordEvent(tx,UUID.randomUUID(),owner,third,"THEFT","{}",now.minusSeconds(1));return null;}));
            try{assertThrows(java.util.concurrent.TimeoutException.class,()->second.get(200,java.util.concurrent.TimeUnit.MILLISECONDS));}
            finally{release.countDown();}
            first.get(10,java.util.concurrent.TimeUnit.SECONDS);second.get(10,java.util.concurrent.TimeUnit.SECONDS);
        }finally{release.countDown();}
        assertEquals(2,consume().size());assertTrue(consume().isEmpty());
    }
}
