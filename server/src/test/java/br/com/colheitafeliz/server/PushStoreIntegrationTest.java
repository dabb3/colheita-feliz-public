package br.com.colheitafeliz.server;

import com.google.gson.JsonObject;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
class PushStoreIntegrationTest {
    private final UUID owner=UUID.randomUUID(),other=UUID.randomUUID();
    private final TestClock clock=new TestClock();
    private final AtomicInteger sends=new AtomicInteger();
    private JdbcOnlineStore store;private PushService service;private String schema;
    @BeforeEach void setup(){
        String url=System.getenv("TEST_DATABASE_URL");assertNotNull(url);assertTrue(url.startsWith("jdbc:postgresql://127.0.0.1:")&&url.contains("/colheita_test"));
        schema="test_"+UUID.randomUUID().toString().replace("-","");store=new JdbcOnlineStore(url,"postgres","",schema);store.initialize(owner,clock.instant());store.initialize(other,clock.instant());
        service=new PushService(store,clock,(e,p,a,b)->{sends.incrementAndGet();return 201;},"public");
    }
    @AfterEach void close(){if(store!=null)store.close();}
    static String token(int value){byte[] bytes=new byte[32];Arrays.fill(bytes,(byte)value);return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);}
    static JsonObject subscription(int device,long revision){
        var r=new JsonObject();r.addProperty("endpoint","https://fcm.googleapis.com/fcm/send/device"+device);r.addProperty("deviceToken",token(device));r.addProperty("bindingId",token(device+1));r.addProperty("deviceRevision",revision);
        var k=new JsonObject();byte[] point=HexFormat.of().parseHex("046b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c2964fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f5");k.addProperty("p256dh",Base64.getUrlEncoder().withoutPadding().encodeToString(point));k.addProperty("auth",Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[16]));r.add("keys",k);
        var p=new JsonObject();for(String key:List.of("plants","animals","thefts","pests"))p.addProperty(key,true);r.add("preferences",p);return r;
    }
    static JsonObject test(JsonObject subscription){var r=subscription.deepCopy();r.remove("keys");r.remove("preferences");r.addProperty("testId",UUID.randomUUID().toString());return r;}
    private static void failure(int status,Runnable action){assertEquals(status,assertThrows(ApiFailure.class,action::run).status());}
    @Test void atomicTransferRequiresProofAndRevisionAndDoesNotCreateFarm(){
        var request=subscription(1,1);service.subscribe(owner,request);service.subscribe(owner,request);
        var stolen=request.deepCopy();stolen.addProperty("deviceRevision",2);stolen.addProperty("deviceToken",token(9));failure(403,()->service.subscribe(other,stolen));
        var transfer=request.deepCopy();transfer.addProperty("deviceRevision",2);transfer.addProperty("bindingId",token(10));service.subscribe(other,transfer);
        failure(409,()->service.subscribe(owner,request));failure(403,()->service.test(owner,test(request)));
        assertEquals("accepted",service.test(other,test(transfer)).get("status").getAsString());
        assertEquals(0,store.transaction(owner,tx->tx.query("SELECT count(*) FROM "+schema+".farms",r->r.getInt(1)).getFirst()).intValue());
        String proof=store.transaction(owner,tx->tx.query("SELECT proof FROM "+schema+".push_subscriptions",r->r.getString(1)).getFirst());assertEquals(PushService.hash(token(1)),proof);
    }
    @Test void revokeKeepsProofAndRevisionAndIsAvailableWhileDisabled(){
        var request=subscription(1,1);service.subscribe(owner,request);var revoked=request.deepCopy();revoked.addProperty("deviceRevision",2);
        var disabled=new PushService(store,clock,(e,p,a,b)->201,null);assertFalse(disabled.config(owner).get("enabled").getAsBoolean());failure(503,()->disabled.subscribe(owner,request));failure(503,()->disabled.test(owner,test(request)));
        disabled.revoke(other,revoked);disabled.revoke(other,revoked);failure(409,()->service.subscribe(owner,request));failure(409,()->service.subscribe(owner,revoked));
        revoked.addProperty("deviceRevision",3);service.subscribe(other,revoked);
    }
    @Test void revokeBeforeDelayedRegistrationLeavesTombstone(){var late=subscription(1,1);var revoke=late.deepCopy();revoke.addProperty("deviceRevision",2);service.revoke(owner,revoke);failure(409,()->service.subscribe(owner,late));service.revoke(other,revoke);}
    @Test void retriesNeverResendAndCooldownCoversActorAndEndpoint(){
        var request=subscription(1,1);service.subscribe(owner,request);var test=test(request);assertEquals("accepted",service.test(owner,test).get("status").getAsString());assertEquals("accepted",service.test(owner,test).get("status").getAsString());assertEquals(1,sends.get());
        failure(429,()->service.test(owner,test(request)));var second=subscription(2,1);service.subscribe(owner,second);failure(429,()->service.test(owner,test(second)));
        clock.now=clock.now.plusSeconds(60);assertEquals("accepted",service.test(owner,test(request)).get("status").getAsString());assertEquals(2,sends.get());
    }
    @Test void exceptionIsSanitizedAndReservationSurvivesReopen(){
        var request=subscription(1,1);service.subscribe(owner,request);service=new PushService(store,clock,(e,p,a,b)->{sends.incrementAndGet();throw new IllegalStateException(e+p+a);},"public");var test=test(request);assertEquals("unavailable",service.test(owner,test).get("status").getAsString());
        store.close();store=new JdbcOnlineStore(System.getenv("TEST_DATABASE_URL"),"postgres","",schema);store.initialize(owner,clock.instant());service=new PushService(store,clock,(e,p,a,b)->{sends.incrementAndGet();return 201;},"public");assertEquals("unavailable",service.test(owner,test).get("status").getAsString());assertEquals(1,sends.get());
    }
    @Test void migrationIsPrivateIdempotentAndTransactionsRollback(){
        store.initialize(owner,clock.instant());
        for(String table:List.of("push_subscriptions","push_tests"))assertTrue(store.transaction(owner,tx->tx.query("SELECT relrowsecurity FROM pg_class JOIN pg_namespace ON pg_namespace.oid=relnamespace WHERE nspname=? AND relname=?",r->r.getBoolean(1),schema,table).getFirst()).booleanValue());
        assertEquals(0,store.transaction(owner,tx->tx.query("SELECT count(*) FROM information_schema.table_privileges WHERE table_schema=? AND table_name IN ('push_subscriptions','push_tests') AND grantee IN ('PUBLIC','anon','authenticated')",r->r.getInt(1),schema).getFirst()).intValue());
        var push=store.pushStore();assertThrows(IllegalStateException.class,()->store.transaction(owner,tx->{push.save(tx,new PushStore.Subscription(owner,"endpoint","proof","binding",1,"key","auth","{}",true),clock.instant());throw new IllegalStateException();}));
        assertNull(store.transaction(owner,tx->push.find(tx,"endpoint")));
        store.transaction(owner,tx->{tx.setActive(other,false);return null;});failure(403,()->service.config(other));failure(403,()->service.subscribe(other,subscription(1,1)));failure(403,()->service.revoke(other,subscription(1,2)));failure(403,()->service.test(other,test(subscription(1,1))));
    }
    @Test void eventMigrationPreservesLegacyRowsAndIsRepeatable(){
        UUID request=UUID.randomUUID();
        store.transaction(owner,tx->{
            tx.execute("DROP TABLE "+schema+".push_events");
            tx.execute("CREATE TABLE "+schema+".push_events (id uuid PRIMARY KEY,owner uuid NOT NULL,actor uuid NOT NULL,type text NOT NULL,metadata jsonb NOT NULL,created_at timestamptz NOT NULL)");
            tx.execute("INSERT INTO "+schema+".push_events VALUES(?,?,?,'THEFT','{}'::jsonb,?)",request,owner,other,java.sql.Timestamp.from(clock.instant()));
            return null;
        });
        store.initialize(owner,clock.instant());store.initialize(owner,clock.instant());
        assertEquals(request,store.transaction(owner,tx->tx.query("SELECT request_id FROM "+schema+".push_events",r->r.getObject(1,UUID.class)).getFirst()));
        assertEventPrimaryKey();
    }
    @Test void emptyEventSchemaInitializesTwice(){
        store.initialize(owner,clock.instant());store.initialize(owner,clock.instant());
        assertEventPrimaryKey();
        assertEquals(0,store.transaction(owner,tx->tx.query("SELECT count(*) FROM "+schema+".push_events",r->r.getInt(1)).getFirst()).intValue());
    }
    private void assertEventPrimaryKey(){
        var definitions=store.transaction(owner,tx->tx.query("SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conrelid=?::regclass AND contype='p'",r->r.getString(1),schema+".push_events"));
        assertEquals(List.of("PRIMARY KEY (actor, request_id)"),definitions);
    }
    @Test void eventDeduplicationIsScopedToActorAndRequest(){
        UUID request=UUID.randomUUID();
        store.transaction(owner,tx->{store.pushStore().recordEvent(tx,request,owner,owner,"THEFT","{\"column\":0}",clock.instant());store.pushStore().recordEvent(tx,request,owner,owner,"THEFT","{\"column\":1}",clock.instant());return null;});
        store.transaction(other,tx->{store.pushStore().recordEvent(tx,request,other,other,"PEST","{\"column\":0}",clock.instant());return null;});
        assertEquals(2,store.transaction(owner,tx->tx.query("SELECT count(*) FROM "+schema+".push_events",r->r.getInt(1)).getFirst()).intValue());
        assertEventPrimaryKey();
    }
    @Test void concurrentClaimsSerializeAndQuotaIsAtomic() throws Exception {
        for(int i=1;i<=4;i++)service.subscribe(owner,subscription(i,1));
        var barrier=new CyclicBarrier(2);try(var executor=Executors.newFixedThreadPool(2)){
            Callable<Integer> a=()->{barrier.await();try{service.subscribe(owner,subscription(5,1));return 1;}catch(ApiFailure e){assertEquals(409,e.status());return 0;}};
            Callable<Integer> b=()->{barrier.await();try{service.subscribe(owner,subscription(6,1));return 1;}catch(ApiFailure e){assertEquals(409,e.status());return 0;}};
            var first=executor.submit(a);var second=executor.submit(b);assertEquals(1,first.get()+second.get());
        }
        assertEquals(5,store.transaction(owner,tx->store.pushStore().activeCount(tx,owner)).intValue());
    }
    @Test void pendingReplayAndExpirationCannotDisableNewBinding() throws Exception {
        var request=subscription(1,1);service.subscribe(owner,request);var test=test(request);
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        var blocked=new PushService(store,clock,(e,p,a,b)->{sends.incrementAndGet();entered.countDown();assertTrue(release.await(10,TimeUnit.SECONDS));return 410;},"public");
        try(var executor=Executors.newSingleThreadExecutor()){
            var future=executor.submit(()->blocked.test(owner,test));assertTrue(entered.await(10,TimeUnit.SECONDS));
            try{
                assertEquals("pending",blocked.test(owner,test).get("status").getAsString());
                var replacement=request.deepCopy();replacement.addProperty("deviceRevision",2);replacement.addProperty("bindingId",token(12));service.subscribe(other,replacement);
                release.countDown();assertEquals("expired",future.get(10,TimeUnit.SECONDS).get("status").getAsString());
                assertTrue(store.transaction(other,tx->store.pushStore().find(tx,request.get("endpoint").getAsString()).active()).booleanValue());assertEquals(1,sends.get());
            } finally {release.countDown();}
        }
    }
    @Test void concurrentRevokeAndTransferCannotRestoreStaleBinding() throws Exception {
        var request=subscription(1,1);service.subscribe(owner,request);
        var transfer=request.deepCopy();transfer.addProperty("deviceRevision",2);transfer.addProperty("bindingId",token(13));
        var revoke=request.deepCopy();revoke.addProperty("deviceRevision",3);
        var barrier=new CyclicBarrier(2);
        try(var executor=Executors.newFixedThreadPool(2)){
            var first=executor.submit(()->{barrier.await();try{service.subscribe(other,transfer);}catch(ApiFailure e){assertEquals(409,e.status());}return 0;});
            var second=executor.submit(()->{barrier.await();service.revoke(owner,revoke);return 0;});first.get(10,TimeUnit.SECONDS);second.get(10,TimeUnit.SECONDS);
        }
        var row=store.transaction(owner,tx->store.pushStore().find(tx,request.get("endpoint").getAsString()));assertFalse(row.active());assertEquals(3,row.revision());
        failure(409,()->service.subscribe(other,transfer));
    }
    @Test void sameRevisionRequiresExactBindingKeysAndPreferencesAndTestIdCannotChangeRequest(){
        var request=subscription(1,1);service.subscribe(owner,request);
        var modified=request.deepCopy();modified.getAsJsonObject("preferences").addProperty("plants",false);failure(409,()->service.subscribe(owner,modified));failure(409,()->service.subscribe(other,request));
        var test=test(request);service.test(owner,test);var second=subscription(2,1);service.subscribe(owner,second);var collision=test(second);collision.add("testId",test.get("testId"));failure(409,()->service.test(owner,collision));
    }
    @Test void expiredSubscriptionIsDeactivatedAndItsReplayNeverResends(){
        var request=subscription(1,1);service.subscribe(owner,request);var test=test(request);
        var expired=new PushService(store,clock,(e,p,a,b)->{sends.incrementAndGet();return 404;},"public");
        assertEquals("expired",expired.test(owner,test).get("status").getAsString());assertEquals("expired",expired.test(owner,test).get("status").getAsString());assertEquals(1,sends.get());
        assertFalse(store.transaction(owner,tx->store.pushStore().find(tx,request.get("endpoint").getAsString()).active()).booleanValue());
        failure(409,()->service.subscribe(owner,request));
    }
    @Test void transferKeepsEndpointCooldownAcrossAccounts(){
        var request=subscription(1,1);service.subscribe(owner,request);service.test(owner,test(request));
        var next=request.deepCopy();next.addProperty("deviceRevision",2);next.addProperty("bindingId",token(15));service.subscribe(other,next);
        failure(429,()->service.test(other,test(next)));clock.now=clock.now.plusSeconds(60);service.test(other,test(next));assertEquals(2,sends.get());
    }
    @Test void totalQuotaIncludesTombstonesAndExistingRevocationRemainsAvailable(){
        var disabled=new PushService(store,clock,(e,p,a,b)->201,null);
        for(int i=1;i<=50;i++)disabled.revoke(owner,subscription(i,1));
        failure(409,()->disabled.revoke(owner,subscription(51,1)));
        failure(409,()->service.subscribe(owner,subscription(51,1)));
        var existing=subscription(1,2);service.subscribe(owner,existing);
        existing.addProperty("deviceRevision",3);assertEquals("revoked",disabled.revoke(owner,existing).get("status").getAsString());
        assertEquals(50,store.transaction(owner,tx->tx.query("SELECT count(*) FROM "+schema+".push_subscriptions WHERE owner=?",r->r.getInt(1),owner).getFirst()).intValue());
    }
    @Test void transferIntoFullAccountIsRejectedButOwnEndpointCanBeReactivated(){
        for(int i=1;i<=50;i++)service.revoke(owner,subscription(i,1));
        var external=subscription(51,1);service.subscribe(other,external);external.addProperty("deviceRevision",2);
        failure(409,()->service.subscribe(owner,external));
        assertEquals(other,store.transaction(owner,tx->store.pushStore().find(tx,external.get("endpoint").getAsString()).owner()));
        var own=subscription(1,2);assertEquals("subscribed",service.subscribe(owner,own).get("status").getAsString());
    }
    @Test void registrationQuotaRenewsAtThirtyDaysWithoutErasingReplayProtection(){
        for(int i=1;i<=50;i++)service.revoke(owner,subscription(i,2));
        Instant created=clock.instant();
        clock.now=created.plus(Duration.ofDays(30)).minusSeconds(1);
        failure(409,()->service.subscribe(owner,subscription(51,1)));
        failure(409,()->service.revoke(owner,subscription(51,1)));
        service.subscribe(owner,subscription(1,3));
        clock.now=created.plus(Duration.ofDays(30));
        assertEquals("subscribed",service.subscribe(owner,subscription(51,1)).get("status").getAsString());
        assertEquals("revoked",service.revoke(owner,subscription(52,1)).get("status").getAsString());
        failure(409,()->service.subscribe(owner,subscription(2,1)));
        var retained=store.transaction(owner,tx->store.pushStore().find(tx,subscription(2,1).get("endpoint").getAsString()));
        assertFalse(retained.active());assertEquals(2,retained.revision());assertEquals(PushService.hash(token(2)),retained.proof());
        assertEquals(created,store.transaction(owner,tx->tx.query("SELECT created_at FROM "+schema+".push_subscriptions WHERE endpoint=?",r->r.getTimestamp(1).toInstant(),subscription(1,1).get("endpoint").getAsString()).getFirst()));
        assertEquals(52,store.transaction(owner,tx->tx.query("SELECT count(*) FROM "+schema+".push_subscriptions",r->r.getInt(1)).getFirst()).intValue());
    }
    @Test void abandonedPendingBecomesDurablyUnavailableAfterRestart(){
        var request=subscription(1,1);service.subscribe(owner,request);var test=test(request);
        UUID id=UUID.fromString(test.get("testId").getAsString());String endpoint=request.get("endpoint").getAsString(),binding=request.get("bindingId").getAsString();
        String requestHash=PushService.hash(owner+"\n"+endpoint+"\n"+PushService.hash(token(1))+"\n"+binding+"\n1");
        store.transaction(owner,tx->{tx.execute("INSERT INTO "+schema+".push_tests(id,actor,endpoint,binding,device_revision,request_hash,status,reserved_at) VALUES(?,?,?,?,?,?,?,?)",id,owner,endpoint,binding,1L,requestHash,"pending",java.sql.Timestamp.from(clock.instant()));return null;});
        store.close();store=new JdbcOnlineStore(System.getenv("TEST_DATABASE_URL"),"postgres","",schema);store.initialize(owner,clock.instant());
        service=new PushService(store,clock,(e,p,a,b)->{sends.incrementAndGet();return 201;},"public");
        clock.now=clock.now.plusSeconds(59);assertEquals("pending",service.test(owner,test).get("status").getAsString());
        var second=subscription(2,1);service.subscribe(owner,second);failure(429,()->service.test(owner,test(second)));
        clock.now=clock.now.plusSeconds(1);assertEquals("unavailable",service.test(owner,test).get("status").getAsString());
        assertEquals("unavailable",service.test(owner,test).get("status").getAsString());assertEquals(0,sends.get());
        assertEquals("unavailable",store.transaction(owner,tx->store.pushStore().attempt(tx,id).status()));
        assertEquals("accepted",service.test(owner,test(second)).get("status").getAsString());assertEquals(1,sends.get());
    }
    @Test void transferAfterQuotaWindowPreservesOwnershipAndCreationTime(){
        for(int i=1;i<=50;i++)service.revoke(owner,subscription(i,1));
        var external=subscription(51,1);service.subscribe(other,external);Instant created=clock.instant();
        var transfer=external.deepCopy();transfer.addProperty("deviceRevision",2);transfer.addProperty("bindingId",token(90));
        failure(409,()->service.subscribe(owner,transfer));
        clock.now=clock.now.plus(Duration.ofDays(30));
        assertEquals("subscribed",service.subscribe(owner,transfer).get("status").getAsString());
        var row=store.transaction(owner,tx->store.pushStore().find(tx,external.get("endpoint").getAsString()));
        assertEquals(owner,row.owner());assertEquals(2,row.revision());
        failure(409,()->service.subscribe(other,external));
        assertEquals(created,store.transaction(owner,tx->tx.query("SELECT created_at FROM "+schema+".push_subscriptions WHERE endpoint=?",r->r.getTimestamp(1).toInstant(),external.get("endpoint").getAsString()).getFirst()));
    }
    @Test void lateSenderCannotOverwriteExpiredPendingOrDeactivateSubscription() throws Exception {
        var request=subscription(1,1);service.subscribe(owner,request);var test=test(request);
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        var blocked=new PushService(store,clock,(e,p,a,b)->{sends.incrementAndGet();entered.countDown();assertTrue(release.await(10,TimeUnit.SECONDS));return 410;},"public");
        try(var executor=Executors.newSingleThreadExecutor()){
            var future=executor.submit(()->blocked.test(owner,test));assertTrue(entered.await(10,TimeUnit.SECONDS));
            try{
                clock.now=clock.now.plusSeconds(60);
                assertEquals("unavailable",service.test(owner,test).get("status").getAsString());
                release.countDown();assertEquals("unavailable",future.get(10,TimeUnit.SECONDS).get("status").getAsString());
                assertEquals("unavailable",service.test(owner,test).get("status").getAsString());assertEquals(1,sends.get());
                assertTrue(store.transaction(owner,tx->store.pushStore().find(tx,request.get("endpoint").getAsString()).active()).booleanValue());
            }finally{release.countDown();}
        }
    }
    private static final class TestClock extends Clock {volatile Instant now=Instant.parse("2026-11-13T12:00:00Z");@Override public Instant instant(){return now;}@Override public ZoneId getZone(){return ZoneOffset.UTC;}@Override public Clock withZone(ZoneId zone){return this;}}
}





