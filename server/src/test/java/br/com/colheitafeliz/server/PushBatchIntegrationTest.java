package br.com.colheitafeliz.server;

import br.com.colheitafeliz.domain.animal.Animal;
import br.com.colheitafeliz.domain.economy.Currency;
import br.com.colheitafeliz.domain.inventory.ItemLot;
import br.com.colheitafeliz.game.*;
import com.google.gson.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
class PushBatchIntegrationTest {
    private final UUID owner=UUID.randomUUID(),actor=UUID.randomUUID();
    private final TestClock clock=new TestClock();
    private JdbcOnlineStore store;
    private OnlineGameService game;
    private PushService push;
    private String schema;
    private JsonObject device;
    private final List<JsonObject> sent=Collections.synchronizedList(new ArrayList<>());
    @BeforeEach void setup(){
        String url=System.getenv("TEST_DATABASE_URL");assertNotNull(url);assertTrue(url.startsWith("jdbc:postgresql://127.0.0.1:")&&url.endsWith("/colheita_test"));
        schema="test_"+UUID.randomUUID().toString().replace("-","");store=new JdbcOnlineStore(url,"postgres","",schema);store.initialize(owner,clock.now);store.initialize(actor,clock.now);
        game=new OnlineGameService(store,clock,b->b-1,20);push=new PushService(store,clock,(e,p,a,b)->201,"public");game.farm(owner);
        device=PushStoreIntegrationTest.subscription(1,1);push.subscribe(owner,device);
    }
    @AfterEach void close(){if(store!=null)store.close();}
    private void consent(String key,boolean value){device.addProperty("deviceRevision",device.get("deviceRevision").getAsLong()+1);var request=PushStoreIntegrationTest.test(device);request.remove("testId");request.addProperty(key,value);push.preferences(owner,request);}
    private void modify(java.util.function.Consumer<GameState> change){store.transaction(owner,tx->{tx.lockFarms(owner);var farm=tx.farm(owner);var state=GameState.restore(clock::instant,clock,CropCatalogLoader.load(),farm.snapshot(),b->b-1);change.accept(state);tx.save(owner,state.snapshot(),farm.revision()+1,clock.now);return null;});}
    private void animal(){modify(state->{var type=AnimalCatalogLoader.load().stream().filter(t->t.id().equals("galinha")).findFirst().orElseThrow();state.barn().add(Animal.acquire(type,new ItemLot(type.id(),Currency.GOLD,type.prices().get(Currency.GOLD),1,false),clock.now));});}
    private void plant(){modify(state->{var tile=state.farm().tile(0,0);tile.plow();tile.plant(CropCatalogLoader.load().get("nabo"),clock.now);tile.water(clock.now);});}
    private void event(String type){store.transaction(actor,tx->{store.pushStore().recordEvent(tx,UUID.randomUUID(),owner,actor,type,clock.now);return null;});}
    private CropPushService worker(){return new CropPushService(store,game,clock,(e,p,a,b)->{sent.add(JsonParser.parseString(b).getAsJsonObject());return 201;});}
    private Set<String> types(JsonObject payload){if(!payload.get("type").getAsString().equals("BATCH"))return Set.of(payload.get("type").getAsString());var result=new HashSet<String>();payload.getAsJsonArray("types").forEach(t->result.add(t.getAsString()));return result;}
    @Test void animalMaturitySchedulesOfflineAndSurvivesRestart(){
        animal();consent("animals",true);
        try(var worker=worker()){worker.tick();assertTrue(sent.isEmpty());clock.now=clock.now.plusSeconds(28800);worker.tick();worker.tick();}
        assertEquals(1,sent.size());assertEquals(Set.of("ANIMAL_READY"),types(sent.getFirst()));
        store.close();store=new JdbcOnlineStore(System.getenv("TEST_DATABASE_URL"),"postgres","",schema);store.initialize(owner,clock.now);game=new OnlineGameService(store,clock,b->b-1,20);
        try(var worker=worker()){worker.tick();}assertEquals(1,sent.size());
    }
    @Test void allFourCategoriesShareOnePrivateDeviceAttempt(){
        plant();animal();for(String key:List.of("plants","animals","thefts","pests"))consent(key,true);
        clock.now=clock.now.plusSeconds(72000);event("THEFT");event("PEST");
        try(var worker=worker()){worker.tick();worker.tick();}
        assertEquals(1,sent.size());assertEquals(Set.of("CROP_READY","ANIMAL_READY","THEFT","PEST"),types(sent.getFirst()));
        assertEquals(Set.of("type","bindingId","types"),sent.getFirst().keySet());
        assertEquals(1,store.transaction(owner,tx->tx.query("SELECT count(*) FROM "+schema+".crop_push_deliveries",r->r.getInt(1)).getFirst()).intValue());
    }
    @Test void cropThenSocialWaitForSameMinuteBoundary(){
        plant();consent("plants",true);consent("thefts",true);clock.now=clock.now.plusSeconds(72000);
        try(var worker=worker()){
            worker.tick();assertEquals(1,sent.size());clock.now=clock.now.plusSeconds(1);event("THEFT");worker.tick();assertEquals(1,sent.size());
            clock.now=clock.now.plusSeconds(58);worker.tick();assertEquals(1,sent.size());clock.now=clock.now.plusSeconds(1);worker.tick();assertEquals(2,sent.size());
        }
        assertEquals(Set.of("THEFT"),types(sent.getLast()));
    }
    @Test void twoWorkersCannotSplitOneOwnersBatch() throws Exception {
        consent("thefts",true);consent("pests",true);event("THEFT");event("PEST");var barrier=new CyclicBarrier(2);
        try(var a=worker();var b=worker();var executor=Executors.newFixedThreadPool(2)){
            var first=executor.submit(()->{barrier.await();a.tick();return null;});var second=executor.submit(()->{barrier.await();b.tick();return null;});first.get(15,TimeUnit.SECONDS);second.get(15,TimeUnit.SECONDS);
        }
        assertEquals(1,sent.size());assertEquals(Set.of("THEFT","PEST"),types(sent.getFirst()));
    }
    @Test void animalActivationAndReactivationIgnoreReadyHistory(){
        animal();clock.now=clock.now.plusSeconds(28800);consent("animals",true);
        try(var worker=worker()){worker.tick();assertTrue(sent.isEmpty());consent("animals",false);consent("animals",true);worker.tick();assertTrue(sent.isEmpty());}
    }
    @Test void automaticCarerCollectsBeforeReservation(){
        animal();modify(state->{state.wallet().credit(Currency.GREEN,new java.math.BigDecimal("1000"));assertTrue(state.buyVip().success());assertTrue(state.buyAutomation("animals").success());});
        consent("animals",true);clock.now=clock.now.plusSeconds(28800);
        try(var worker=worker()){worker.tick();}
        assertTrue(sent.isEmpty());assertTrue(store.transaction(owner,tx->tx.farm(owner).snapshot().animals().isEmpty()).booleanValue());
    }
    @Test void availabilityRequiresConfiguredTransportForEveryCategory(){
        var disabled=new PushService(store,clock,(e,p,a,b)->201,null).config(owner);var enabled=push.config(owner);
        for(String key:List.of("automaticEventsEnabled","plantEventsEnabled","animalEventsEnabled","theftEventsEnabled","pestEventsEnabled")){
            assertFalse(disabled.get(key).getAsBoolean());assertTrue(enabled.get(key).getAsBoolean());
        }
    }
    @Test void eachDeviceReceivesOnlyItsConsentedTypes(){
        consent("thefts",true);var first=device;device=PushStoreIntegrationTest.subscription(2,1);push.subscribe(owner,device);consent("pests",true);
        event("THEFT");event("PEST");try(var worker=worker()){worker.tick();}
        assertEquals(2,sent.size());
        assertEquals(Set.of("THEFT"),types(sent.stream().filter(p->p.get("bindingId").equals(first.get("bindingId"))).findFirst().orElseThrow()));
        assertEquals(Set.of("PEST"),types(sent.stream().filter(p->p.get("bindingId").equals(device.get("bindingId"))).findFirst().orElseThrow()));
    }
    @Test void revokedCategoryAndInactiveAccountCannotReserve(){
        consent("thefts",true);event("THEFT");consent("thefts",false);try(var worker=worker()){worker.tick();}assertTrue(sent.isEmpty());
        consent("thefts",true);event("THEFT");store.transaction(owner,tx->{tx.setActive(owner,false);return null;});
        try(var worker=worker()){worker.tick();}assertTrue(sent.isEmpty());
    }
    @Test void failedBatchTransportIsTerminalForAllItsSocialReceipts(){
        consent("thefts",true);consent("pests",true);event("THEFT");event("PEST");var attempts=new java.util.concurrent.atomic.AtomicInteger();
        try(var worker=new CropPushService(store,game,clock,(e,p,a,b)->{attempts.incrementAndGet();throw new java.io.IOException("test transport");})){worker.tick();clock.now=clock.now.plusSeconds(60);worker.tick();}
        assertEquals(1,attempts.get());
        assertEquals(List.of("unavailable","unavailable"),store.transaction(owner,tx->tx.query("SELECT status FROM "+schema+".social_push_deliveries ORDER BY reserved_at,id",r->r.getString(1))));
    }
    @Test void uncertainBatchAfterCrashExpiresWithoutANewAttempt(){
        consent("thefts",true);consent("pests",true);event("THEFT");event("PEST");
        store.transaction(owner,tx->{tx.lock("push-subscriptions");tx.lockFarms(owner);var events=store.socialPushStore().consume(tx,owner,clock.now);assertEquals(1,store.cropPushStore().reserveBatch(tx,owner,clock.now,false,false,events).size());return null;});
        store.close();store=new JdbcOnlineStore(System.getenv("TEST_DATABASE_URL"),"postgres","",schema);store.initialize(owner,clock.now);game=new OnlineGameService(store,clock,b->b-1,20);clock.now=clock.now.plusSeconds(60);
        try(var worker=worker()){worker.tick();}assertTrue(sent.isEmpty());
        assertEquals(List.of("unavailable","unavailable"),store.transaction(owner,tx->tx.query("SELECT status FROM "+schema+".social_push_deliveries ORDER BY reserved_at,id",r->r.getString(1))));
    }
    @Test void staleExpiryResponseCannotDeactivateANewBinding() throws Exception {
        consent("thefts",true);event("THEFT");var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var worker=new CropPushService(store,game,clock,(e,p,a,b)->{entered.countDown();assertTrue(release.await(10,TimeUnit.SECONDS));return 410;});var executor=Executors.newSingleThreadExecutor()){
            var operation=executor.submit(worker::tick);assertTrue(entered.await(10,TimeUnit.SECONDS));
            try{var replacement=device.deepCopy();replacement.addProperty("deviceRevision",3);replacement.addProperty("bindingId",PushStoreIntegrationTest.token(25));push.subscribe(actor,replacement);}
            finally{release.countDown();}
            operation.get(15,TimeUnit.SECONDS);
        }
        assertTrue(store.transaction(actor,tx->store.pushStore().find(tx,device.get("endpoint").getAsString()).active()).booleanValue());
    }
    @Test void staleSocialReceiptCannotJoinANewerDeviceRevision(){
        plant();consent("plants",true);consent("thefts",true);clock.now=clock.now.plusSeconds(72000);event("THEFT");
        var events=store.transaction(owner,tx->store.socialPushStore().consume(tx,owner,clock.now));assertEquals(1,events.size());
        consent("animals",true);
        var batch=store.transaction(owner,tx->{tx.lock("push-subscriptions");tx.lockFarms(owner);return store.cropPushStore().reserveBatch(tx,owner,clock.now,true,false,events);});
        assertEquals(List.of("CROP_READY"),batch.getFirst().types());
        assertNull(store.transaction(owner,tx->tx.query("SELECT batch_id FROM "+schema+".social_push_deliveries WHERE id=?",r->r.getObject(1,UUID.class),events.getFirst().id()).getFirst()));
    }
    @Test void terminalDeliveryRetentionIsBoundedAndKeepsRecentOrUnknownStates(){
        Instant old=clock.now.minus(Duration.ofDays(31));
        store.transaction(owner,tx->{
            for(int i=0;i<101;i++)seedDelivery(tx,"accepted",old);
            seedDelivery(tx,"accepted",clock.now);seedDelivery(tx,"accepted",clock.now.minus(Duration.ofDays(30)));seedDelivery(tx,"pending",clock.now);seedDelivery(tx,"unknown",old);
            store.cropPushStore().expire(tx,clock.now);return null;
        });
        assertEquals(5,store.transaction(owner,tx->tx.query("SELECT count(*) FROM "+schema+".crop_push_deliveries",r->r.getInt(1)).getFirst()).intValue());
        store.transaction(owner,tx->{store.cropPushStore().expire(tx,clock.now);return null;});
        assertEquals(List.of("accepted","accepted","pending","unknown"),store.transaction(owner,tx->tx.query("SELECT status FROM "+schema+".crop_push_deliveries ORDER BY status",r->r.getString(1))));
    }
    @Test void socialRetentionIsBoundedAndKeepsPermanentEventDeduplication(){
        consent("thefts",true);
        store.transaction(actor,tx->{for(int i=0;i<101;i++)store.pushStore().recordEvent(tx,UUID.randomUUID(),owner,actor,"THEFT",clock.now);return null;});
        store.transaction(owner,tx->{assertEquals(100,store.socialPushStore().consume(tx,owner,clock.now).size());assertEquals(1,store.socialPushStore().consume(tx,owner,clock.now).size());tx.execute("UPDATE "+schema+".social_push_deliveries SET status='accepted',reserved_at=?",java.sql.Timestamp.from(clock.now.minus(Duration.ofDays(31))));store.cropPushStore().expire(tx,clock.now);return null;});
        assertEquals(1,store.transaction(owner,tx->tx.query("SELECT count(*) FROM "+schema+".social_push_deliveries",r->r.getInt(1)).getFirst()).intValue());
        store.transaction(owner,tx->{store.cropPushStore().expire(tx,clock.now);assertTrue(store.socialPushStore().consume(tx,owner,clock.now).isEmpty());return null;});
        assertEquals(0,store.transaction(owner,tx->tx.query("SELECT count(*) FROM "+schema+".social_push_deliveries",r->r.getInt(1)).getFirst()).intValue());
        assertEquals(101,store.transaction(owner,tx->tx.query("SELECT count(*) FROM "+schema+".push_events",r->r.getInt(1)).getFirst()).intValue());
    }
    @Test void deletingOldDeliveriesPreservesEventReplayAndCannotDisableCurrentBinding(){
        consent("thefts",true);UUID request=UUID.randomUUID();
        store.transaction(actor,tx->{store.pushStore().recordEvent(tx,request,owner,actor,"THEFT",clock.now);return null;});
        var delivery=store.transaction(owner,tx->{var events=store.socialPushStore().consume(tx,owner,clock.now);var batch=store.cropPushStore().reserveBatch(tx,owner,clock.now,false,false,events).getFirst();store.cropPushStore().finish(tx,batch,"accepted",clock.now);return batch;});
        store.transaction(owner,tx->{var old=java.sql.Timestamp.from(clock.now.minus(Duration.ofDays(31)));tx.execute("UPDATE "+schema+".crop_push_deliveries SET reserved_at=?",old);tx.execute("UPDATE "+schema+".social_push_deliveries SET reserved_at=?",old);store.cropPushStore().expire(tx,clock.now);store.cropPushStore().finish(tx,delivery,"expired",clock.now);return null;});
        assertEquals(0,store.transaction(owner,tx->tx.query("SELECT count(*) FROM "+schema+".social_push_deliveries",r->r.getInt(1)).getFirst()).intValue());
        assertEquals(0,store.transaction(owner,tx->tx.query("SELECT count(*) FROM "+schema+".crop_push_deliveries",r->r.getInt(1)).getFirst()).intValue());
        store.transaction(actor,tx->{store.pushStore().recordEvent(tx,request,owner,actor,"THEFT",clock.now);return null;});
        assertEquals(1,store.transaction(owner,tx->tx.query("SELECT count(*) FROM "+schema+".push_events",r->r.getInt(1)).getFirst()).intValue());
        assertTrue(store.transaction(owner,tx->store.pushStore().find(tx,device.get("endpoint").getAsString()).active()).booleanValue());
        store.close();store=new JdbcOnlineStore(System.getenv("TEST_DATABASE_URL"),"postgres","",schema);store.initialize(owner,clock.now);game=new OnlineGameService(store,clock,b->b-1,20);clock.now=clock.now.plusSeconds(60);
        try(var worker=worker()){worker.tick();}assertTrue(sent.isEmpty());
    }
    @Test void retentionDoesNotRemoveSocialReceiptAheadOfOrWithoutCursor(){
        consent("thefts",true);event("THEFT");store.transaction(owner,tx->{store.socialPushStore().consume(tx,owner,clock.now);tx.execute("UPDATE "+schema+".social_push_deliveries SET status='accepted',reserved_at=?",java.sql.Timestamp.from(clock.now.minus(Duration.ofDays(31))));tx.execute("UPDATE "+schema+".social_push_cursors SET last_event=0");store.cropPushStore().expire(tx,clock.now);return null;});
        assertEquals(1,store.transaction(owner,tx->tx.query("SELECT count(*) FROM "+schema+".social_push_deliveries",r->r.getInt(1)).getFirst()).intValue());
        store.transaction(owner,tx->{tx.execute("DELETE FROM "+schema+".social_push_cursors");store.cropPushStore().expire(tx,clock.now);return null;});
        assertEquals(1,store.transaction(owner,tx->tx.query("SELECT count(*) FROM "+schema+".social_push_deliveries",r->r.getInt(1)).getFirst()).intValue());
    }
    private void seedDelivery(JdbcOnlineStore.Tx tx,String status,Instant at){tx.execute("INSERT INTO "+schema+".crop_push_deliveries(id,owner,endpoint,binding,device_revision,status,reserved_at) VALUES(?,?,?,?,?,?,?)",UUID.randomUUID(),owner,device.get("endpoint").getAsString(),device.get("bindingId").getAsString(),device.get("deviceRevision").getAsLong(),status,java.sql.Timestamp.from(at));}
    private static final class TestClock extends Clock {
        Instant now=Instant.parse("2026-11-13T12:00:00Z");
        @Override public Instant instant(){return now;}
        @Override public ZoneId getZone(){return ZoneOffset.UTC;}
        @Override public Clock withZone(ZoneId zone){return this;}
    }
}
