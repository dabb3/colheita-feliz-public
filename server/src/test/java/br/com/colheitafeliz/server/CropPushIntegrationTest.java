package br.com.colheitafeliz.server;

import br.com.colheitafeliz.game.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.*;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
class CropPushIntegrationTest {
    final UUID owner=UUID.randomUUID();
    final TestClock clock=new TestClock();
    JdbcOnlineStore store;
    OnlineGameService game;
    PushService push;
    String schema;
    @BeforeEach void setup(){
        String url=System.getenv("TEST_DATABASE_URL");
        assertNotNull(url);assertTrue(url.startsWith("jdbc:postgresql://127.0.0.1:")&&url.contains("/colheita_test"));
        schema="test_"+UUID.randomUUID().toString().replace("-","");
        store=new JdbcOnlineStore(url,"postgres","",schema);store.initialize(owner,clock.instant());
        game=new OnlineGameService(store,clock,b->b-1,20);
        push=new PushService(store,clock,(e,p,a,b)->201,"public");
    }
    @AfterEach void close(){store.close();}
    void subscribeConsent(JsonObject subscription){subscribeConsent(owner,subscription);}
    void subscribeConsent(UUID account,JsonObject subscription){
        push.subscribe(account,subscription);
        var request=PushStoreIntegrationTest.test(subscription);request.remove("testId");
        request.addProperty("deviceRevision",subscription.get("deviceRevision").getAsLong()+1);request.addProperty("plants",true);
        push.preferences(account,request);
    }
    @Test void enablingPlantsCreatesPrivatePersistentSchedule(){
        game.farm(owner);subscribeConsent(PushStoreIntegrationTest.subscription(1,1));
        assertEquals(1,store.transaction(owner,tx->tx.query("SELECT count(*) FROM "+schema+".crop_push_jobs WHERE owner=?",r->r.getInt(1),owner).getFirst()).intValue());
        assertTrue(push.config(owner).get("plantEventsEnabled").getAsBoolean());
    }
    @Test void backgroundAdvanceKeepsPlayerRevisionForAnOpenFarm(){
        plantAndWater();subscribeConsent(PushStoreIntegrationTest.subscription(1,1));
        var before=store.transaction(owner,tx->tx.farm(owner).revision());clock.now=clock.now.plusSeconds(72000);
        store.transaction(owner,tx->{game.advanceForCropPush(tx,owner);return null;});
        assertEquals(before,store.transaction(owner,tx->tx.farm(owner).revision()));
    }
    @Test void legacySubscriptionsNeedExplicitConsentAndReadingNeverConfirms(){
        plantAndWater();var legacy=PushStoreIntegrationTest.subscription(1,1);push.subscribe(owner,legacy);
        var before=store.transaction(owner,tx->tx.query("SELECT preferences::text,updated_at,device_revision FROM "+schema+".push_subscriptions WHERE endpoint=?",r->List.of(r.getString(1),r.getTimestamp(2).toInstant().toString(),Long.toString(r.getLong(3))),legacy.get("endpoint").getAsString()).getFirst());
        store.initialize(owner,clock.now);store.initialize(owner,clock.now);clock.now=clock.now.plusSeconds(72000);
        var request=PushStoreIntegrationTest.test(legacy);request.remove("testId");assertFalse(push.preferences(owner,request).get("plants").getAsBoolean());
        var sends=new AtomicInteger();try(var worker=worker(sends)){worker.tick();worker.tick();}
        assertEquals(0,sends.get());assertEquals(0,count("crop_push_jobs"));assertEquals(0,count("crop_push_deliveries"));
        assertEquals(before,store.transaction(owner,tx->tx.query("SELECT preferences::text,updated_at,device_revision FROM "+schema+".push_subscriptions WHERE endpoint=?",r->List.of(r.getString(1),r.getTimestamp(2).toInstant().toString(),Long.toString(r.getLong(3))),legacy.get("endpoint").getAsString()).getFirst()));
        for(boolean desired:List.of(false,true)){request.addProperty("plants",desired);assertEquals(409,assertThrows(ApiFailure.class,()->push.preferences(owner,request)).status());}
        request.addProperty("deviceRevision",2);request.addProperty("plants",true);assertTrue(push.preferences(owner,request).get("plants").getAsBoolean());assertNull(job().due());
        store.close();store=new JdbcOnlineStore(System.getenv("TEST_DATABASE_URL"),"postgres","",schema);store.initialize(owner,clock.now);game=new OnlineGameService(store,clock,b->b-1,20);push=new PushService(store,clock,(e,p,a,b)->201,"public");
        try(var worker=worker(sends)){worker.tick();assertEquals(0,sends.get());clock.now=clock.now.plusSeconds(1);modify(s->{var tile=s.farm().tile(1,0);tile.plow();tile.plant(CropCatalogLoader.load().get("nabo"),clock.now);tile.water(clock.now);tile.fertilize(clock.now,Duration.ofHours(10));});worker.tick();assertEquals(1,sends.get());}
    }
    @Test void consentCannotBeForgedOrMovedByLegacySubscribeReplay(){
        plantAndWater();var request=PushStoreIntegrationTest.subscription(1,1);var forged=request.deepCopy();forged.getAsJsonObject("preferences").addProperty("plantsConfirmed",true);
        assertEquals(400,assertThrows(ApiFailure.class,()->push.subscribe(owner,forged)).status());push.subscribe(owner,request);
        var explicit=PushStoreIntegrationTest.test(request);explicit.remove("testId");explicit.addProperty("deviceRevision",2);explicit.addProperty("plants",true);push.preferences(owner,explicit);
        assertEquals(409,assertThrows(ApiFailure.class,()->push.subscribe(owner,request)).status());
        request.addProperty("deviceRevision",2);push.subscribe(owner,request);assertTrue(push.preferences(owner,explicit).get("plants").getAsBoolean());
        request.addProperty("deviceRevision",3);push.subscribe(owner,request);explicit.addProperty("deviceRevision",3);explicit.remove("plants");assertFalse(push.preferences(owner,explicit).get("plants").getAsBoolean());assertFalse(job().enabled());
        explicit.addProperty("deviceRevision",4);explicit.addProperty("plants",true);push.preferences(owner,explicit);
        UUID other=UUID.randomUUID();store.initialize(other,clock.now);request.addProperty("deviceRevision",5);request.addProperty("bindingId",PushStoreIntegrationTest.token(22));push.subscribe(other,request);
        explicit=PushStoreIntegrationTest.test(request);explicit.remove("testId");assertFalse(push.preferences(other,explicit).get("plants").getAsBoolean());assertFalse(job().enabled());
    }
    @Test void onlyTheExplicitlyConfirmedDeviceCanReserveAndSendAutomaticEvents(){
        plantAndWater();var first=PushStoreIntegrationTest.subscription(1,1);var second=PushStoreIntegrationTest.subscription(2,1);push.subscribe(owner,first);push.subscribe(owner,second);
        var explicit=PushStoreIntegrationTest.test(first);explicit.remove("testId");explicit.addProperty("deviceRevision",2);explicit.addProperty("plants",true);push.preferences(owner,explicit);
        var reserved=store.transaction(owner,tx->store.cropPushStore().reserveBatch(tx,owner,clock.now,true,false,List.of()));assertEquals(1,reserved.size());assertEquals(first.get("endpoint").getAsString(),reserved.getFirst().subscription().endpoint());
        var legacy=store.transaction(owner,tx->store.pushStore().find(tx,second.get("endpoint").getAsString()));assertFalse(store.transaction(owner,tx->store.cropPushStore().current(tx,new CropPushStore.Delivery(UUID.randomUUID(),owner,legacy,List.of("CROP_READY")))).booleanValue());
        clock.now=clock.now.plusSeconds(72000);var sends=new AtomicInteger();try(var worker=new CropPushService(store,game,clock,(endpoint,p,a,b)->{assertEquals(first.get("endpoint").getAsString(),endpoint);sends.incrementAndGet();return 201;})){worker.tick();}assertEquals(1,sends.get());
    }
    @Test void firstConsentResetsAnOldEnabledJobWithoutDiscardingItsCursors(){
        plantAndWater();var legacy=PushStoreIntegrationTest.subscription(1,1);push.subscribe(owner,legacy);
        var cursors=new com.google.gson.JsonObject();cursors.addProperty("baseline",clock.now.toString());
        store.transaction(owner,tx->{store.cropPushStore().save(tx,owner,new CropPushStore.Job(true,clock.now,null,cursors));return null;});
        clock.now=clock.now.plusSeconds(72000);var request=PushStoreIntegrationTest.test(legacy);request.remove("testId");request.addProperty("plants",true);request.addProperty("deviceRevision",2);push.preferences(owner,request);
        assertEquals(clock.now.toString(),job().cursors().get("baseline").getAsString());assertNull(job().due());
        var sends=new AtomicInteger();try(var worker=worker(sends)){worker.tick();}assertEquals(0,sends.get());
    }
    @Test void offlineMaturityReservesOnceAndSurvivesRestart(){
        plantAndWater();subscribeConsent(PushStoreIntegrationTest.subscription(1,1));
        var sends=new AtomicInteger();
        try(var worker=new CropPushService(store,game,clock,(e,p,a,payload)->{assertTrue(payload.contains("CROP_READY"));sends.incrementAndGet();return 201;})){
            worker.tick();assertEquals(0,sends.get());
            clock.now=clock.now.plusSeconds(72000);worker.tick();worker.tick();assertEquals(1,sends.get());
        }
        try(var worker=new CropPushService(store,game,clock,(e,p,a,b)->{sends.incrementAndGet();return 201;})){worker.tick();}
        assertEquals(1,sends.get());
        assertEquals(1,count("crop_push_deliveries"));assertEquals(0,count("receipts"));
    }
    @Test void activationBaselinesAlreadyMaturePlants(){
        plantAndWater();clock.now=clock.now.plusSeconds(72000);subscribeConsent(PushStoreIntegrationTest.subscription(1,1));
        var sends=new AtomicInteger();try(var worker=new CropPushService(store,game,clock,(e,p,a,b)->{sends.incrementAndGet();return 201;})){worker.tick();}
        assertEquals(0,sends.get());
    }
    @Test void preferencesRequireProofAndRevisionAndKeepKeys(){
        var subscription=PushStoreIntegrationTest.subscription(1,1);push.subscribe(owner,subscription);
        var request=PushStoreIntegrationTest.test(subscription);request.remove("testId");
        assertFalse(push.preferences(owner,request).get("plants").getAsBoolean());
        request.addProperty("plants",false);request.addProperty("deviceRevision",2);
        assertFalse(push.preferences(owner,request).get("plants").getAsBoolean());
        request.addProperty("plants",true);request.addProperty("deviceRevision",3);assertTrue(push.preferences(owner,request).get("plants").getAsBoolean());
        request.addProperty("deviceRevision",4);request.addProperty("plants",false);
        assertFalse(push.preferences(owner,request).get("plants").getAsBoolean());
        var conflict=request.deepCopy();conflict.addProperty("plants",true);
        assertEquals(409,assertThrows(ApiFailure.class,()->push.preferences(owner,conflict)).status());
        var wrong=request.deepCopy();wrong.addProperty("deviceToken",PushStoreIntegrationTest.token(42));
        assertEquals(403,assertThrows(ApiFailure.class,()->push.preferences(owner,wrong)).status());
        var saved=store.transaction(owner,tx->store.pushStore().find(tx,request.get("endpoint").getAsString()));
        assertEquals(subscription.getAsJsonObject("keys").get("p256dh").getAsString(),saved.p256dh());assertEquals(4,saved.revision());
    }
    @Test void neverWateredAndPausedPlantsStopUntilARelevantSave(){
        plantAndWater();modify(s->s.farm().tile(0,0).checkPest(clock.now,true));
        subscribeConsent(PushStoreIntegrationTest.subscription(1,1));
        assertNull(job().due());
        clock.now=clock.now.plusSeconds(72000);
        var sends=new AtomicInteger();try(var worker=worker(sends)){worker.tick();assertEquals(0,sends.get());
            modify(s->s.farm().tile(0,0).removePest(clock.now));assertNotNull(job().due());
            clock.now=clock.now.plusSeconds(72000);worker.tick();assertEquals(1,sends.get());}
    }
    @Test void reactivationSkipsOfflineHistoryAndPreservesEarlierCursors(){
        plantAndWater();var sub=PushStoreIntegrationTest.subscription(1,1);subscribeConsent(sub);
        var pref=PushStoreIntegrationTest.test(sub);pref.remove("testId");pref.addProperty("deviceRevision",3);pref.addProperty("plants",false);push.preferences(owner,pref);
        clock.now=clock.now.plusSeconds(72000);pref.addProperty("deviceRevision",4);pref.addProperty("plants",true);push.preferences(owner,pref);
        var sends=new AtomicInteger();try(var worker=worker(sends)){worker.tick();assertEquals(0,sends.get());}
    }
    @Test void directAndDeferredWritesAndReceiptRollbackKeepQueueAtomic(){
        plantAndWater();subscribeConsent(PushStoreIntegrationTest.subscription(1,1));var before=job().due();clock.now=clock.now.plusSeconds(1);
        assertThrows(IllegalStateException.class,()->store.transaction(owner,tx->{
            tx.lockFarms(owner);var farm=tx.farm(owner);var state=restore(farm);state.farm().tile(0,0).fertilize(clock.now,Duration.ofHours(10));
            tx.collectSaves();tx.save(owner,state.snapshot(),2,clock.now);tx.remember(owner,UUID.randomUUID(),"hash",new com.google.gson.JsonObject(),clock.now.plusSeconds(60));throw new IllegalStateException();
        }));assertEquals(before,job().due());assertEquals(0,count("receipts"));
        store.transaction(owner,tx->{tx.lockFarms(owner);var farm=tx.farm(owner);var state=restore(farm);state.farm().tile(0,0).fertilize(clock.now,Duration.ofHours(10));tx.collectSaves();tx.save(owner,state.snapshot(),2,clock.now);tx.remember(owner,UUID.randomUUID(),"hash",new com.google.gson.JsonObject(),clock.now.plusSeconds(60));return null;});
        assertEquals(clock.now,job().due());
        var sends=new AtomicInteger();try(var worker=worker(sends)){worker.tick();assertEquals(1,sends.get());}
    }
    @Test void twoWorkersReserveTheSameBatchOnlyOnce() throws Exception {
        plantAndWater();subscribeConsent(PushStoreIntegrationTest.subscription(1,1));clock.now=clock.now.plusSeconds(72000);
        var sends=new AtomicInteger();var barrier=new CyclicBarrier(2);
        try(var first=worker(sends);var second=worker(sends);var executor=Executors.newFixedThreadPool(2)){
            var a=executor.submit(()->{barrier.await();first.tick();return 0;});var b=executor.submit(()->{barrier.await();second.tick();return 0;});a.get(10,TimeUnit.SECONDS);b.get(10,TimeUnit.SECONDS);
        }assertEquals(1,sends.get());assertEquals(1,count("crop_push_deliveries"));
    }
    @Test void lateExpiredResponseCannotDisableTransferredSubscription() throws Exception {
        plantAndWater();var sub=PushStoreIntegrationTest.subscription(1,1);subscribeConsent(sub);clock.now=clock.now.plusSeconds(72000);
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);UUID other=UUID.randomUUID();store.initialize(other,clock.now);
        try(var worker=new CropPushService(store,game,clock,(e,p,a,b)->{entered.countDown();assertTrue(release.await(10,TimeUnit.SECONDS));return 410;});var executor=Executors.newSingleThreadExecutor()){
            var future=executor.submit(worker::tick);assertTrue(entered.await(10,TimeUnit.SECONDS));
            try{var transfer=sub.deepCopy();transfer.addProperty("bindingId",PushStoreIntegrationTest.token(33));transfer.addProperty("deviceRevision",3);push.subscribe(other,transfer);}finally{release.countDown();}
            future.get(10,TimeUnit.SECONDS);
        }
        assertTrue(store.transaction(owner,tx->store.pushStore().find(tx,sub.get("endpoint").getAsString()).active()).booleanValue());
    }
    @Test void crashReservationExpiresWithoutReplayAfterReopeningStore(){
        plantAndWater();subscribeConsent(PushStoreIntegrationTest.subscription(1,1));clock.now=clock.now.plusSeconds(72000);
        store.transaction(owner,tx->{tx.lockFarms(owner);var snapshot=game.advanceForCropPush(tx,owner);var job=store.cropPushStore().job(tx,owner);store.cropPushStore().reserveBatch(tx,owner,clock.now,true,false,List.of());store.cropPushStore().observeReady(snapshot,job.cursors(),clock.now);store.cropPushStore().save(tx,owner,new CropPushStore.Job(true,null,clock.now,job.cursors()));return null;});
        store.close();store=new JdbcOnlineStore(System.getenv("TEST_DATABASE_URL"),"postgres","",schema);store.initialize(owner,clock.now);game=new OnlineGameService(store,clock,b->b-1,20);
        clock.now=clock.now.plusSeconds(60);var sends=new AtomicInteger();try(var worker=worker(sends)){worker.tick();}
        assertEquals(0,sends.get());assertEquals("unavailable",store.transaction(owner,tx->tx.query("SELECT status FROM "+schema+".crop_push_deliveries",r->r.getString(1)).getFirst()));
    }
    @Test void workerDoesNotClaimChristmasGiftOrUpdateNotesOrEconomicReceipt(){
        plantAndWater();subscribeConsent(PushStoreIntegrationTest.subscription(1,1));var before=store.transaction(owner,tx->tx.farm(owner).snapshot());
        clock.now=Instant.parse("2026-12-25T12:00:00Z");var sends=new AtomicInteger();try(var worker=worker(sends)){worker.tick();}
        var after=store.transaction(owner,tx->tx.farm(owner).snapshot());assertEquals(before.coins(),after.coins());assertEquals(before.greenCoins(),after.greenCoins());assertEquals(before.christmas2026(),after.christmas2026());assertEquals(0,count("receipts"));assertEquals(0,count("update_note_views"));
    }
    @Test void migrationIsIdempotentAndNewTablesArePrivate(){
        store.initialize(owner,clock.now);
        for(String table:List.of("crop_push_jobs","crop_push_deliveries"))assertTrue(store.transaction(owner,tx->tx.query("SELECT relrowsecurity FROM pg_class JOIN pg_namespace ON pg_namespace.oid=relnamespace WHERE nspname=? AND relname=?",r->r.getBoolean(1),schema,table).getFirst()).booleanValue());
        assertEquals(0,store.transaction(owner,tx->tx.query("SELECT count(*) FROM information_schema.table_privileges WHERE table_schema=? AND table_name LIKE 'crop_push_%' AND grantee IN ('PUBLIC','anon','authenticated')",r->r.getInt(1),schema).getFirst()).intValue());
        assertEquals(2,store.transaction(owner,tx->tx.query("SELECT count(*) FROM pg_indexes WHERE schemaname=? AND indexname IN ('crop_push_due_idx','crop_push_pending_idx')",r->r.getInt(1),schema).getFirst()).intValue());
    }
    @Test void failedFarmBacksOffInsteadOfPoisoningEveryPoll(){
        plantAndWater();subscribeConsent(PushStoreIntegrationTest.subscription(1,1));clock.now=clock.now.plusSeconds(72000);
        store.transaction(owner,tx->{tx.execute("UPDATE "+schema+".farms SET ruleset='future' WHERE owner=?",owner);return null;});
        try(var worker=worker(new AtomicInteger())){worker.tick();}
        assertEquals(clock.now.plusSeconds(60),job().due());
    }
    @Test void plantsCoalescePerMinuteAcrossDevicesWithoutUsingManualTestQuota(){
        plantAndWater();var sub=PushStoreIntegrationTest.subscription(1,1);subscribeConsent(sub);subscribeConsent(PushStoreIntegrationTest.subscription(2,1));var testRequest=PushStoreIntegrationTest.test(sub);testRequest.addProperty("deviceRevision",2);push.test(owner,testRequest);
        clock.now=clock.now.plusSeconds(1);modify(s->s.farm().tile(0,0).fertilize(clock.now,Duration.ofHours(10)));
        var sends=new AtomicInteger();try(var worker=worker(sends)){
            worker.tick();assertEquals(2,sends.get());
            clock.now=clock.now.plusSeconds(1);modify(s->{var tile=s.farm().tile(1,0);tile.plow();tile.plant(CropCatalogLoader.load().get("nabo"),clock.now);tile.water(clock.now);tile.fertilize(clock.now,Duration.ofHours(10));});
            worker.tick();assertEquals(2,sends.get());clock.now=clock.now.plusSeconds(59);worker.tick();assertEquals(4,sends.get());
        }assertEquals(4,count("crop_push_deliveries"));assertEquals(1,count("push_tests"));
    }
    @Test void allSixSeasonsStayDistinctAfterDismissingInternalNotices(){
        var type=CropCatalogLoader.load().get("coco");
        var state=new GameState(clock::instant,clock,CropCatalogLoader.load(),b->b-1);state.startNewFarm();var tile=state.farm().tile(0,0);tile.plow();tile.plant(type,clock.now);tile.water(clock.now);
        store.transaction(owner,tx->{tx.save(owner,state.snapshot(),1,clock.now);return null;});subscribeConsent(PushStoreIntegrationTest.subscription(1,1));
        var sends=new AtomicInteger();try(var worker=worker(sends)){
            for(int season=1;season<=6;season++){
                clock.now=clock.now.plusSeconds(500000);worker.tick();worker.tick();assertEquals(season,sends.get());
                modify(s->{s.notifications().markAllRead();s.notifications().dismissAllRead();});worker.tick();assertEquals(season,sends.get());
                if(season<6)modify(s->{s.farm().tile(0,0).harvest(clock.now);s.farm().tile(0,0).water(clock.now);});
            }
        }assertEquals(6,count("crop_push_deliveries"));assertTrue(job().cursors().size()<=37);
    }
    @Test void automationsReplayExactlyLikeNormalAccessAndBaselineOldMaturity(){
        plantAndWater();modify(s->{s.wallet().credit(br.com.colheitafeliz.domain.economy.Currency.GREEN,new java.math.BigDecimal("1000"));assertTrue(s.buyVip().success());assertTrue(s.buyAutomation("water").success());assertTrue(s.buyAutomation("pests").success());});
        subscribeConsent(PushStoreIntegrationTest.subscription(1,1));var saved=store.transaction(owner,tx->tx.farm(owner));
        clock.now=clock.now.plusSeconds(40000);var expected=GameState.restoreOnline(saved.time(),clock.now,CropCatalogLoader.load(),saved.snapshot(),b->b-1);expected.refresh();expected.protectedPlots();expected.runAutomationsNow();
        var sends=new AtomicInteger();try(var worker=worker(sends)){worker.tick();}
        assertEquals(expected.snapshot(),store.transaction(owner,tx->tx.farm(owner).snapshot()));assertEquals(1,sends.get());
        // Legacy stage-one rows are baselined in bounded startup batches, including replayed care.
        store.transaction(owner,tx->{tx.execute("DELETE FROM "+schema+".crop_push_jobs WHERE owner=?",owner);return null;});
        try(var worker=worker(sends)){worker.tick();}assertEquals(1,sends.get());
    }
    @Test void vipSuspensionLeavesUnwateredPlantDormantUntilRenewal(){
        plantAndWater();modify(s->{s.wallet().credit(br.com.colheitafeliz.domain.economy.Currency.GREEN,new java.math.BigDecimal("1000"));s.buyVip();s.buyAutomation("water");s.buyAutomation("water");});
        clock.now=clock.now.plusSeconds(2592000);modify(s->{var t=s.farm().tile(1,0);t.plow();t.plant(CropCatalogLoader.load().get("nabo"),clock.now);});subscribeConsent(PushStoreIntegrationTest.subscription(1,1));
        assertNull(job().due());
        modify(s->{assertTrue(s.buyVip().success());s.runAutomationsNow();});assertNotNull(job().due());
    }
    @Test void expiredLastDeviceStartsANewBaselineWhenItReturns(){
        plantAndWater();var sub=PushStoreIntegrationTest.subscription(1,1);push.subscribe(owner,sub);
        var expired=new PushService(store,clock,(e,p,a,b)->410,"public");expired.test(owner,PushStoreIntegrationTest.test(sub));
        clock.now=clock.now.plusSeconds(72000);sub.addProperty("deviceRevision",2);push.subscribe(owner,sub);
        var sends=new AtomicInteger();try(var worker=worker(sends)){worker.tick();}assertEquals(0,sends.get());
    }
    @Test void invalidSnapshotDoesNotAbortHealthyFarmBehindIt(){
        plantAndWater();subscribeConsent(PushStoreIntegrationTest.subscription(1,1));UUID healthy=UUID.randomUUID();store.initialize(healthy,clock.now);
        var saved=store.transaction(owner,tx->tx.farm(owner));store.transaction(healthy,tx->{tx.save(healthy,saved.snapshot(),1,clock.now);return null;});subscribeConsent(healthy,PushStoreIntegrationTest.subscription(2,1));
        clock.now=clock.now.plusSeconds(72000);store.transaction(owner,tx->{tx.execute("UPDATE "+schema+".farms SET snapshot=jsonb_set(snapshot,'{tiles,0,grownAtAnchorMillis}','-1'::jsonb) WHERE owner=?",owner);return null;});
        var sends=new AtomicInteger();try(var worker=worker(sends)){worker.tick();}assertEquals(1,sends.get());assertEquals(clock.now.plusSeconds(60),job().due());
    }
    @Test void missingPestArrivalGetsOneObservationThenSchedulesActualRemoval(){
        plantAndWater();modify(s->{s.wallet().credit(br.com.colheitafeliz.domain.economy.Currency.GREEN,new java.math.BigDecimal("1000"));s.buyVip();s.buyAutomation("pests");s.farm().tile(0,0).checkPest(clock.now,true);});
        store.transaction(owner,tx->{tx.execute("UPDATE "+schema+".farms SET snapshot=snapshot #- '{automations,weedsSince,0}' WHERE owner=?",owner);return null;});
        subscribeConsent(PushStoreIntegrationTest.subscription(1,1));assertEquals(clock.now,job().due());
        try(var worker=worker(new AtomicInteger())){worker.tick();assertEquals(clock.now.plusSeconds(900),job().due());clock.now=clock.now.plusSeconds(900);worker.tick();assertTrue(job().due().isAfter(clock.now));}
    }
    @Test void inactivatedPlayerCannotKeepAJobDueForever(){
        plantAndWater();subscribeConsent(PushStoreIntegrationTest.subscription(1,1));clock.now=clock.now.plusSeconds(72000);store.transaction(owner,tx->{tx.setActive(owner,false);return null;});
        try(var worker=worker(new AtomicInteger())){worker.tick();}assertFalse(job().enabled());assertNull(job().due());
        store.transaction(owner,tx->{tx.execute("DELETE FROM "+schema+".crop_push_jobs WHERE owner=?",owner);return null;});
        assertFalse(store.transaction(owner,tx->store.cropPushStore().uninitialized(tx)).contains(owner));
    }
    @Test void bootstrapAndDueWorkShareTheTenFarmBudget(){
        plantAndWater();subscribeConsent(PushStoreIntegrationTest.subscription(1,1));var snapshot=store.transaction(owner,tx->tx.farm(owner).snapshot());
        for(int i=2;i<=11;i++){
            UUID legacy=UUID.randomUUID();store.initialize(legacy,clock.now);store.transaction(legacy,tx->{tx.save(legacy,snapshot,1,clock.now);return null;});subscribeConsent(legacy,PushStoreIntegrationTest.subscription(i,1));
            store.transaction(legacy,tx->{tx.execute("DELETE FROM "+schema+".crop_push_jobs WHERE owner=?",legacy);return null;});
        }
        clock.now=clock.now.plusSeconds(72000);var sends=new AtomicInteger();try(var worker=worker(sends)){worker.tick();assertEquals(0,sends.get());assertEquals(11,count("crop_push_jobs"));worker.tick();assertEquals(1,sends.get());}
    }
    @Test void expiredPestCareIsReplayedBeforePlanningFutureMaturityOnReactivation(){
        historicalAutomation("pests",true,3600);var sub=PushStoreIntegrationTest.subscription(1,1);subscribeConsent(sub);
        var preference=PushStoreIntegrationTest.test(sub);preference.remove("testId");preference.addProperty("plants",false);preference.addProperty("deviceRevision",3);push.preferences(owner,preference);
        clock.now=clock.now.plusSeconds(7200);preference.addProperty("plants",true);preference.addProperty("deviceRevision",4);push.preferences(owner,preference);
        assertEquals(clock.now,job().due());var sends=new AtomicInteger();try(var worker=worker(sends)){
            worker.tick();assertEquals(0,sends.get());assertNotNull(job().due());assertTrue(job().due().isAfter(clock.now));
            assertNull(store.transaction(owner,tx->tx.farm(owner).snapshot().tiles().getFirst().pausedAt()));
            clock.now=clock.now.plusSeconds(72000);worker.tick();assertEquals(1,sends.get());
        }
    }
    @Test void bootstrapReplaysExpiredWaterCareBeforeFutureMaturity(){
        historicalAutomation("water",false,3600);var sub=PushStoreIntegrationTest.subscription(1,1);subscribeConsent(sub);
        store.transaction(owner,tx->{tx.execute("DELETE FROM "+schema+".crop_push_jobs WHERE owner=?",owner);return null;});
        clock.now=clock.now.plusSeconds(7200);var sends=new AtomicInteger();try(var worker=worker(sends)){
            worker.tick();assertEquals(0,sends.get());assertNotNull(job().due());assertTrue(job().due().isAfter(clock.now));
            assertNotNull(store.transaction(owner,tx->tx.farm(owner).snapshot().tiles().getFirst().wateredAt()));
            clock.now=clock.now.plusSeconds(72000);worker.tick();assertEquals(1,sends.get());
        }
    }
    @Test void expiredPestCareDoesNotSendMaturityBeforeTheActivationBaseline(){
        historicalAutomation("pests",true,3600);clock.now=clock.now.plusSeconds(72000);subscribeConsent(PushStoreIntegrationTest.subscription(1,1));
        assertEquals(clock.now,job().due());var sends=new AtomicInteger();try(var worker=worker(sends)){worker.tick();worker.tick();}
        assertEquals(0,sends.get());assertNull(job().due());assertEquals(br.com.colheitafeliz.domain.farm.TileState.READY,store.transaction(owner,tx->tx.farm(owner).snapshot().tiles().getFirst().state()));
    }
    @Test void expiredCareWithNoPossibleProgressSleepsAfterOneReplay(){
        historicalAutomation("pests",true,600);clock.now=clock.now.plusSeconds(7200);subscribeConsent(PushStoreIntegrationTest.subscription(1,1));
        assertEquals(clock.now,job().due());var sends=new AtomicInteger();try(var worker=worker(sends)){worker.tick();assertNull(job().due());worker.tick();}
        assertEquals(0,sends.get());assertNotNull(store.transaction(owner,tx->tx.farm(owner).snapshot().tiles().getFirst().pausedAt()));
    }
    @Test void harvestedCropBeforeReservationCancelsTheScheduledAlert(){
        plantAndWater();subscribeConsent(PushStoreIntegrationTest.subscription(1,1));clock.now=clock.now.plusSeconds(72000);modify(s->s.farm().tile(0,0).harvest(clock.now));
        var sends=new AtomicInteger();try(var worker=worker(sends)){worker.tick();}assertEquals(0,sends.get());assertNull(job().due());assertEquals(0,count("crop_push_deliveries"));
    }
    private void historicalAutomation(String service,boolean paused,long expirySeconds){
        plantAndWater();modify(s->{s.wallet().credit(br.com.colheitafeliz.domain.economy.Currency.GREEN,new java.math.BigDecimal("1000"));s.buyVip();s.buyAutomation(service);if(paused)s.farm().tile(0,0).checkPest(clock.now,true);});
        // Short terms keep the fixture focused on replay after both VIP and care have expired.
        store.transaction(owner,tx->{
            var farm=tx.farm(owner);var json=new com.google.gson.Gson().toJsonTree(farm.snapshot()).getAsJsonObject();
            json.addProperty("vipExpiresAt",clock.now.plusSeconds(expirySeconds).toString());json.getAsJsonObject("automations").getAsJsonObject("expiresAt").addProperty(service,clock.now.plusSeconds(expirySeconds).toString());
            if(!paused){var tile=json.getAsJsonArray("tiles").get(0).getAsJsonObject();tile.add("wateredAt",com.google.gson.JsonNull.INSTANCE);tile.add("growthAnchorAt",com.google.gson.JsonNull.INSTANCE);tile.add("nextPestCheckAt",com.google.gson.JsonNull.INSTANCE);tile.add("nextInsectCheckAt",com.google.gson.JsonNull.INSTANCE);}
            tx.save(owner,new com.google.gson.Gson().fromJson(json,GameSnapshot.class),farm.revision()+1,clock.now);return null;
        });
    }
    private CropPushService worker(AtomicInteger sends){return new CropPushService(store,game,clock,(e,p,a,b)->{sends.incrementAndGet();return 201;});}
    private CropPushStore.Job job(){return store.transaction(owner,tx->store.cropPushStore().job(tx,owner));}
    private GameState restore(JdbcOnlineStore.Farm saved){return GameState.restore(clock::instant,clock,CropCatalogLoader.load(),saved.snapshot(),b->b-1);}
    private void modify(java.util.function.Consumer<GameState> action){store.transaction(owner,tx->{tx.lockFarms(owner);var saved=tx.farm(owner);var state=restore(saved);action.accept(state);tx.save(owner,state.snapshot(),saved.revision()+1,clock.now);return null;});}
    private int count(String table){return store.transaction(owner,tx->tx.query("SELECT count(*) FROM "+schema+"."+table,r->r.getInt(1)).getFirst());}
    private void plantAndWater(){
        var fixed=Clock.fixed(clock.now,ZoneOffset.UTC);var state=new GameState(fixed::instant,fixed,CropCatalogLoader.load(),b->b-1);state.startNewFarm();
        var tile=state.farm().tile(0,0);tile.plow();tile.plant(CropCatalogLoader.load().get("nabo"),clock.now);tile.water(clock.now);
        store.transaction(owner,tx->{tx.lockFarms(owner);tx.save(owner,state.snapshot(),1,clock.now);return null;});
    }
    static final class TestClock extends Clock {
        Instant now=Instant.parse("2026-11-13T12:00:00Z");
        @Override public ZoneId getZone(){return ZoneOffset.UTC;}
        @Override public Clock withZone(ZoneId zone){return this;}
        @Override public Instant instant(){return now;}
    }
}
