package br.com.colheitafeliz.server;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
class PublishedNotesIntegrationTest {
    private final UUID owner=UUID.fromString("00000000-0000-0000-0000-000000000001"),friend=UUID.randomUUID();
    private final TestClock clock=new TestClock();private JdbcOnlineStore store;private OnlineGameService service;private String schema;
    @BeforeEach void setup(){
        var url=System.getenv("TEST_DATABASE_URL");assertNotNull(url);assertTrue(url.startsWith("jdbc:postgresql://127.0.0.1:")&&url.contains("/colheita_test"));
        schema="test_"+UUID.randomUUID().toString().replace("-","");store=new JdbcOnlineStore(url,"postgres","",schema);store.initialize(owner,clock.instant());
        service=new OnlineGameService(store,clock,n->99,20);service.farm(owner);
        var invite=service.createInvitation(owner,id()).get("code").getAsString();service.acceptInvitation(friend,"Amigo",invite,id());
    }
    @AfterEach void close(){if(store!=null)store.close();}
    private UUID id(){return RequestIds.issue(clock.instant());}
    private void publish(String version,int pr){service=new OnlineGameService(store,clock,n->99,20,new PublishedNotes(version,pr,List.of("Ajudantes para sua fazenda.")));}
    @Test void absentPublicationDoesNotShowOrConsumeNotesAndVisitsStayPrivate(){
        assertFalse(service.farm(owner).has("updateNotes"));publish("release-v1",123);
        assertFalse(service.visit(friend,owner).has("updateNotes"));assertFalse(service.friends(friend).get(0).getAsJsonObject().has("updateNotes"));
        assertEquals(0,store.transaction(owner,tx->tx.query("SELECT count(*) FROM "+schema+".update_note_views",r->r.getInt(1)).getFirst()).intValue());
        var note=service.farm(owner).getAsJsonObject("updateNotes");assertEquals(123,note.get("prNumber").getAsInt());assertEquals("2026-11-07",note.get("date").getAsString());
    }
    @Test void samePlayerDoesNotSeeNotesAgainAcrossReentryAndAnotherDeviceButOtherAccountDoes(){
        publish("release-v1",123);assertTrue(service.farm(owner).has("updateNotes"));assertFalse(service.farm(owner).has("updateNotes"));
        store.close();store=new JdbcOnlineStore(System.getenv("TEST_DATABASE_URL"),"postgres","",schema);store.initialize(owner,clock.instant());publish("release-v1",123);
        assertFalse(service.farm(owner).has("updateNotes"));assertTrue(service.farm(friend).has("updateNotes"));assertFalse(service.farm(owner).has("updateNotes"));
    }
    @Test void saoPauloMidnightAndNewVersionOnSameDayRenewNotesWithoutUtcOrClockRollbackDuplication(){
        publish("release-v1",123);assertTrue(service.farm(owner).has("updateNotes"));
        clock.now=Instant.parse("2026-11-08T03:00:00Z");var next=service.farm(owner).getAsJsonObject("updateNotes");assertEquals("2026-11-08",next.get("date").getAsString());
        assertFalse(service.farm(owner).has("updateNotes"));publish("release-v2",124);assertTrue(service.farm(owner).has("updateNotes"));
        assertFalse(service.farm(owner).has("updateNotes"));clock.now=Instant.parse("2026-11-08T02:59:59Z");assertFalse(service.farm(owner).has("updateNotes"));
    }
    @Test void concurrentAccessesClaimOnlyOneDailyPopup() throws Exception {
        publish("release-v1",123);var barrier=new java.util.concurrent.CyclicBarrier(2);
        try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)){
            var first=executor.submit(()->{barrier.await();return service.farm(owner).has("updateNotes")?1:0;});
            var second=executor.submit(()->{barrier.await();return service.farm(owner).has("updateNotes")?1:0;});assertEquals(1,first.get()+second.get());
        }
    }
    @Test void transactionRollbackDoesNotConsumeDailyPopupAndRevokedAccountCannotClaim(){
        publish("release-v1",123);
        assertThrows(IllegalStateException.class,()->store.transaction(owner,tx->{assertTrue(service.farm(tx,owner,null).has("updateNotes"));throw new IllegalStateException("rollback notes");}));
        assertTrue(service.farm(owner).has("updateNotes"));store.transaction(friend,tx->{tx.setActive(friend,false);return null;});
        assertEquals(403,assertThrows(ApiFailure.class,()->service.farm(friend)).status());
        assertEquals(0,store.transaction(owner,tx->tx.query("SELECT count(*) FROM "+schema+".update_note_views WHERE actor=?",r->r.getInt(1),friend).getFirst()).intValue());
    }
    private static final class TestClock extends Clock {
        Instant now=Instant.parse("2026-11-08T02:59:59Z");@Override public Instant instant(){return now;}@Override public ZoneId getZone(){return ZoneOffset.UTC;}@Override public Clock withZone(ZoneId zone){return this;}
    }
}
