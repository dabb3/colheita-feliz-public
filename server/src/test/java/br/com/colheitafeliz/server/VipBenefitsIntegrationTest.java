package br.com.colheitafeliz.server;

import br.com.colheitafeliz.game.*;
import br.com.colheitafeliz.domain.economy.Currency;
import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
class VipBenefitsIntegrationTest {
    private final UUID owner=UUID.fromString("00000000-0000-0000-0000-000000000001"),friend=UUID.randomUUID();
    private final TestClock clock=new TestClock();private JdbcOnlineStore store;private OnlineGameService service;private String schema;
    @BeforeEach void setup(){
        var url=System.getenv("TEST_DATABASE_URL");assertNotNull(url);assertTrue(url.startsWith("jdbc:postgresql://127.0.0.1:")&&url.contains("/colheita_test"));
        schema="test_"+UUID.randomUUID().toString().replace("-","");store=new JdbcOnlineStore(url,"postgres","",schema);store.initialize(owner,clock.instant());
        service=new OnlineGameService(store,clock,n->99,20);service.farm(owner);
        var invite=service.createInvitation(owner,id()).get("code").getAsString();service.acceptInvitation(friend,"Amigo",invite,id());service.farm(friend);
        edit(game->{game.wallet().credit(Currency.GREEN,new BigDecimal("5000"));game.wallet().credit(500000);while(game.progress().level()<60)game.progress().award(game.progress().xpToNext());});
        act("BUY_SEED","nabo");
    }
    @AfterEach void close(){if(store!=null)store.close();}
    private UUID id(){return RequestIds.issue(clock.instant());}
    private Command command(String action,String item,Integer col,Integer row){return new Command(id(),service.farm(owner).get("revision").getAsString(),action,item,null,col,row,null);}
    private JsonObject act(String action,String item){return service.command(owner,command(action,item,null,null));}
    private JsonObject tile(String action,String item){return service.command(owner,command(action,item,0,0));}
    private void edit(java.util.function.Consumer<GameState> change){store.transaction(owner,tx->{var saved=tx.farm(owner);var game=GameState.restoreOnline(saved.time(),clock.instant(),CropCatalogLoader.load(),saved.snapshot(),n->99);change.accept(game);tx.save(owner,game.snapshot(),saved.revision()+1,clock.instant());return null;});}
    private void reopen(){store.close();store=new JdbcOnlineStore(System.getenv("TEST_DATABASE_URL"),"postgres","",schema);service=new OnlineGameService(store,clock,n->99,20);}
    private JsonObject benefit(JsonObject farm,String type){return farm.getAsJsonArray("automations").asList().stream().map(e->e.getAsJsonObject()).filter(e->e.get("id").getAsString().equals(type)).findFirst().orElseThrow();}
    @Test void totalPriceIsFourHundredAndReplayCannotRenewOrChargeTwice() throws Exception {
        var before=service.farm(owner);assertFalse(act("BUY_AUTOMATION","water").getAsJsonObject("result").get("success").getAsBoolean());
        assertEquals(before.get("greenCoins"),service.farm(owner).get("greenCoins"));act("BUY_VIP",null);
        var buy=command("BUY_AUTOMATION","water",null,null);
        try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)){
            var first=executor.submit(()->service.command(owner,buy));var second=executor.submit(()->service.command(owner,buy));assertEquals(first.get(),second.get());
        }
        act("BUY_AUTOMATION","pests");act("BUY_AUTOMATION","animals");var after=service.farm(owner);
        assertEquals(0,new BigDecimal(before.get("greenCoins").getAsString()).subtract(new BigDecimal(after.get("greenCoins").getAsString())).compareTo(new BigDecimal("400")));
        for(String type:List.of("water","pests","animals")){var offer=benefit(after,type);assertEquals("ACTIVE",offer.get("status").getAsString());assertEquals(clock.instant().plus(Duration.ofDays(30)).toString(),offer.get("expiresAt").getAsString());}
        reopen();assertEquals(benefit(after,"water"),benefit(service.farm(owner),"water"));
    }
    @Test void laterServiceSurvivesVipSuspensionAndResumesWithoutExtendingItsDeadline(){
        act("BUY_VIP",null);clock.now=clock.now.plus(Duration.ofDays(10));act("BUY_AUTOMATION","water");
        var until=benefit(service.farm(owner),"water").get("expiresAt");clock.now=clock.now.plus(Duration.ofDays(20));
        assertEquals("SUSPENDED",benefit(service.farm(owner),"water").get("status").getAsString());
        assertFalse(act("BUY_AUTOMATION","pests").getAsJsonObject("result").get("success").getAsBoolean());
        clock.now=clock.now.plus(Duration.ofDays(5));act("BUY_VIP",null);assertEquals(until,benefit(service.farm(owner),"water").get("expiresAt"));
        assertEquals("ACTIVE",benefit(service.farm(owner),"water").get("status").getAsString());
        clock.now=Instant.parse(until.getAsString());assertEquals("EXPIRED",benefit(service.farm(owner),"water").get("status").getAsString());
    }
    @Test void vipCrownIsPublicButExpiryAndServiceTermsRemainPrivate(){
        assertFalse(service.friends(friend).get(0).getAsJsonObject().get("vipActive").getAsBoolean());act("BUY_VIP",null);
        assertTrue(service.friends(friend).get(0).getAsJsonObject().get("vipActive").getAsBoolean());var visit=service.visit(friend,owner);
        assertTrue(visit.get("vipActive").getAsBoolean());assertFalse(visit.has("vipExpiresAt"));assertFalse(visit.has("automations"));
        clock.now=clock.now.plus(Duration.ofDays(30));assertFalse(service.friends(friend).get(0).getAsJsonObject().get("vipActive").getAsBoolean());assertFalse(service.visit(friend,owner).get("vipActive").getAsBoolean());
    }
    @Test void replayBookkeepingAloneDoesNotInvalidateOwnersRevision(){
        act("BUY_VIP",null);act("BUY_AUTOMATION","water");var before=service.farm(owner);
        clock.now=clock.now.plusSeconds(1);var visit=service.visit(friend,owner);
        assertEquals(before.get("revision"),visit.get("revision"));
        store.transaction(owner,tx->{assertEquals(clock.instant().toString(),tx.farm(owner).snapshot().automations().cursor());return null;});
        assertTrue(service.command(owner,new Command(id(),before.get("revision").getAsString(),"BUY_SEED","nabo",null,null,null,null)).getAsJsonObject("result").get("success").getAsBoolean());
    }
    @Test void offlineCareRunsOnVisitAndCommitsExactlyOnceAcrossConcurrencyAndRestart() throws Exception {
        act("BUY_VIP",null);act("BUY_AUTOMATION","water");act("BUY_AUTOMATION","animals");
        act("BUY_FEED","producer");act("BUY_ANIMAL","galinha");tile("PLOW",null);tile("PLANT","nabo");
        var before=service.farm(owner);assertTrue(before.getAsJsonArray("tiles").get(0).getAsJsonObject().get("watered").getAsBoolean());
        clock.now=clock.now.plus(Duration.ofHours(12));var visit=service.visit(friend,owner);assertFalse(visit.has("produceLots"));assertEquals("READY",visit.getAsJsonArray("tiles").get(0).getAsJsonObject().get("state").getAsString());
        try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)){
            var first=executor.submit(()->service.farm(owner));var second=executor.submit(()->service.farm(owner));assertEquals(first.get().get("produceLots"),second.get().get("produceLots"));
        }
        var after=service.farm(owner);assertEquals(1,after.getAsJsonArray("produceLots").asList().stream().filter(e->e.getAsJsonObject().get("itemId").getAsString().equals("ovo")).mapToInt(e->e.getAsJsonObject().get("quantity").getAsInt()).sum());
        assertEquals(before.get("coins"),after.get("coins"));reopen();assertEquals(after.get("produceLots"),service.farm(owner).get("produceLots"));
    }
    @Test void failedOfflineTransactionRollsBackCareAndRetryPaysOnlyManualRewards(){
        act("BUY_VIP",null);act("BUY_AUTOMATION","animals");act("BUY_ANIMAL","galinha");clock.now=clock.now.plus(Duration.ofHours(12));
        assertThrows(IllegalStateException.class,()->store.transaction(owner,tx->{service.farm(tx,owner,null);throw new IllegalStateException("rollback care");}));
        store.transaction(owner,tx->{assertEquals(1,tx.farm(owner).snapshot().animals().size());assertTrue(tx.farm(owner).snapshot().produce().isEmpty());return null;});
        assertEquals(1,service.farm(owner).getAsJsonArray("produceLots").size());reopen();assertEquals(1,service.farm(owner).getAsJsonArray("produceLots").size());
    }
    @Test void socialPestStillWaitsFifteenMinutesAndCleanupDoesNotRenewVisitorsCooldown(){
        act("BUY_VIP",null);act("BUY_AUTOMATION","pests");tile("PLOW",null);tile("PLANT","nabo");tile("WATER",null);
        var visit=service.visit(friend,owner);var tile=visit.getAsJsonArray("tiles").get(0).getAsJsonObject();
        var attack=new Command(id(),visit.get("revision").getAsString(),"SPREAD_INSECTS",null,null,0,0,tile.get("harvestRevision").getAsString());
        assertTrue(service.visitAction(friend,owner,attack).getAsJsonObject("result").get("success").getAsBoolean());var arrival=clock.instant();
        clock.now=clock.now.plusSeconds(899);assertEquals(1,service.farm(owner).getAsJsonArray("tiles").get(0).getAsJsonObject().get("insects").getAsInt());
        clock.now=clock.now.plusSeconds(1);assertEquals(0,service.farm(owner).getAsJsonArray("tiles").get(0).getAsJsonObject().get("insects").getAsInt());
        assertEquals(arrival,store.transaction(friend,tx->tx.lastPest(friend,"SPREAD_INSECTS")));
    }
    private static final class TestClock extends Clock {
        Instant now=Instant.parse("2026-11-08T02:59:59Z");@Override public Instant instant(){return now;}@Override public ZoneId getZone(){return ZoneOffset.UTC;}@Override public Clock withZone(ZoneId zone){return this;}
    }
}
