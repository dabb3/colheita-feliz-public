package br.com.colheitafeliz.server;

import br.com.colheitafeliz.game.Christmas2026;
import com.google.gson.JsonObject;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
class UsabilityIntegrationTest {
    private final UUID owner=UUID.fromString("00000000-0000-0000-0000-000000000001"),friend=UUID.randomUUID();
    private final TestClock clock=new TestClock();
    private JdbcOnlineStore store; private OnlineGameService service; private String schema;
    @BeforeEach void setup(){
        var url=System.getenv("TEST_DATABASE_URL");assertNotNull(url);
        assertTrue(url.startsWith("jdbc:postgresql://127.0.0.1:")&&url.contains("/colheita_test"));
        schema="test_"+UUID.randomUUID().toString().replace("-","");
        store=new JdbcOnlineStore(url,"postgres","",schema);store.initialize(owner,clock.instant());
        service=new OnlineGameService(store,clock,bound->99,20);service.farm(owner);
        var invite=service.createInvitation(owner,request()).get("code").getAsString();service.acceptInvitation(friend,"Amigo",invite,request());service.farm(friend);
    }
    @AfterEach void close(){if(store!=null)store.close();}
    private UUID request(){return RequestIds.issue(clock.instant());}
    private Command command(String action,String item){return new Command(request(),service.farm(owner).get("revision").getAsString(),action,item,null,null,null,null);}
    private JsonObject act(String action,String item){return service.command(owner,command(action,item));}
    private Command avatar(String action,String id,String photo){
        var data=OnlineViews.JSON.toJsonTree(command(action,id)).getAsJsonObject();if(photo!=null)data.addProperty("photoUrl",photo);return OnlineViews.JSON.fromJson(data,Command.class);
    }
    private void reopen(){store.close();store=new JdbcOnlineStore(System.getenv("TEST_DATABASE_URL"),"postgres","",schema);service=new OnlineGameService(store,clock,bound->99,20);}
    @Test void friendListReturnsPersistedPublicAvatarWithoutLoadingOrExposingTheFarm(){
        assertEquals("classic",service.friends(friend).get(0).getAsJsonObject().getAsJsonObject("avatar").get("id").getAsString());
        act("SET_AVATAR","female-2");reopen();
        var profile=service.friends(friend).get(0).getAsJsonObject();
        assertEquals(java.util.Set.of("id","name","active","avatar"),profile.keySet());
        assertEquals("female-2",profile.getAsJsonObject("avatar").get("id").getAsString());
        service.command(owner,avatar("SET_AVATAR","google","https://lh3.googleusercontent.com/a/owner-photo"));
        clock.now=Christmas2026.GIFT_START;
        assertEquals("https://lh3.googleusercontent.com/a/owner-photo",service.friends(friend).get(0).getAsJsonObject().getAsJsonObject("avatar").get("photoUrl").getAsString());
        store.transaction(owner,tx->{assertFalse(tx.farm(owner).snapshot().christmas2026().giftGranted());return null;});
        store.transaction(owner,tx->{tx.setActive(owner,false);return null;});assertEquals(0,service.friends(friend).size());
    }
    @Test void publicProfileBelongsToOwnerWhileInventoryAndNotificationsRemainPrivate(){
        var own=service.farm(owner);assertFalse(own.get("avatarSelected").getAsBoolean());
        var choose=command("SET_AVATAR","female-2");var response=service.command(owner,choose);assertEquals(response,service.command(owner,choose));
        reopen();var visit=service.visit(friend,owner);
        assertEquals("female-2",visit.getAsJsonObject("avatar").get("id").getAsString());
        for(var key:new String[]{"name","level","currentXp","xpToNext","coins","greenCoins"})assertEquals(service.farm(owner).get(key),visit.get(key));
        for(var key:new String[]{"notifications","unreadNotifications","seedCounts","produceLots","feed","fertilizers","missions","dailyAllowance","vipExpiresAt","christmas2026"})assertFalse(visit.has(key),key);
        assertEquals("classic",service.farm(friend).getAsJsonObject("avatar").get("id").getAsString());
        act("IMPORT_AVATAR","male-2");assertEquals("female-2",service.farm(owner).getAsJsonObject("avatar").get("id").getAsString());
        assertThrows(ApiFailure.class,()->service.visitAction(friend,owner,new Command(request(),visit.get("revision").getAsString(),"SET_AVATAR","classic",null,0,0,"0")));
    }
    @Test void googlePhotoIsValidatedAndSurvivesReentryWithoutPrivateAuthData(){
        String photo="https://lh3.googleusercontent.com/a/test-photo=s96-c";
        service.command(owner,avatar("SET_AVATAR","google",photo));reopen();
        assertEquals(photo,service.visit(friend,owner).getAsJsonObject("avatar").get("photoUrl").getAsString());
        for(var value:new String[]{"http://lh3.googleusercontent.com/a/x","https://evil.example/a/x","https://lh3.googleusercontent.com.evil.example/a/x","https://user@lh3.googleusercontent.com/a/x","https://lh3.googleusercontent.com:443/a/x","https://lh3.googleusercontent.com/a/x?token=x","https://lh3.googleusercontent.com/a/x#x"})
            assertThrows(IllegalArgumentException.class,()->service.command(owner,avatar("SET_AVATAR","google",value)),value);
        assertThrows(IllegalArgumentException.class,()->service.command(owner,avatar("SET_AVATAR","google",null)));
        assertThrows(IllegalArgumentException.class,()->service.command(owner,avatar("SET_AVATAR","unknown",null)));
        assertThrows(IllegalArgumentException.class,()->service.command(owner,avatar("SET_AVATAR","classic",photo)));
        assertEquals(photo,service.farm(owner).getAsJsonObject("avatar").get("photoUrl").getAsString());
    }
    @Test void concurrentImportsCannotOverwriteExplicitChoiceAndRollbackLeavesNoPreference() throws Exception {
        var initial=service.farm(owner);var importOne=command("IMPORT_AVATAR","female-1");var importTwo=command("IMPORT_AVATAR","male-3");
        try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)){
            var a=executor.submit(()->confirmed(importOne));var b=executor.submit(()->confirmed(importTwo));assertEquals(1,a.get()+b.get());
        }
        var chosen=service.farm(owner).getAsJsonObject("avatar");assertNotEquals(initial.getAsJsonObject("avatar"),chosen);
        act("SET_AVATAR","classic");act("IMPORT_AVATAR","female-3");assertEquals("classic",service.farm(owner).getAsJsonObject("avatar").get("id").getAsString());
        var change=command("SET_AVATAR","male-2");
        assertThrows(IllegalStateException.class,()->store.transaction(owner,tx->{service.command(tx,owner,change);throw new IllegalStateException("rollback preference");}));
        reopen();assertEquals("classic",service.farm(owner).getAsJsonObject("avatar").get("id").getAsString());
        assertTrue(service.command(owner,change).getAsJsonObject("result").get("success").getAsBoolean());
    }
    private int confirmed(Command c){try{return service.command(owner,c).getAsJsonObject("result").get("success").getAsBoolean()?1:0;}catch(ApiFailure e){assertEquals(409,e.status());return 0;}}
    @Test void dismissReadChristmasNoticePreservesReceiptAcrossReplayReentryAndRestore(){
        clock.now=Christmas2026.GIFT_START;var farm=service.farm(owner);var note=farm.getAsJsonArray("notifications").get(0).getAsJsonObject();String id=note.get("id").getAsString();
        assertFalse(act("DISMISS_NOTIFICATION",id).getAsJsonObject("result").get("success").getAsBoolean());
        act("READ_NOTIFICATION",id);var dismiss=command("DISMISS_NOTIFICATION",id);var result=service.command(owner,dismiss);assertEquals(result,service.command(owner,dismiss));
        reopen();farm=service.farm(owner);assertEquals(0,farm.getAsJsonArray("notifications").size());assertEquals(1,farm.getAsJsonObject("seedCounts").get("mini-bis").getAsInt());
        store.transaction(owner,tx->{var snapshot=tx.farm(owner).snapshot();assertTrue(snapshot.christmas2026().giftGranted());assertEquals("2026",snapshot.notifications().observed().get("christmas-gift-2026"));return null;});
    }
    @Test void clearingReadNoticesIsConcurrentSafeAndDoesNotRemoveUnread() throws Exception {
        clock.now=Christmas2026.GIFT_START;var farm=service.farm(owner);String id=farm.getAsJsonArray("notifications").get(0).getAsJsonObject().get("id").getAsString();
        act("DISMISS_READ_NOTIFICATIONS",null);assertEquals(1,service.farm(owner).getAsJsonArray("notifications").size());
        act("READ_NOTIFICATION",id);var clear=command("DISMISS_READ_NOTIFICATIONS",null);
        try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)){
            var first=executor.submit(()->service.command(owner,clear));var second=executor.submit(()->service.command(owner,clear));assertEquals(first.get(),second.get());
        }
        assertEquals(0,service.farm(owner).getAsJsonArray("notifications").size());
        assertFalse(service.visit(friend,owner).has("notifications"));
    }
    @Test void plotQuoteIsAuthoritativeAndPurchaseChecksFundsAndReplaysOnlyOnce(){
        var farm=service.farm(owner);var tile=farm.getAsJsonArray("tiles").get(1).getAsJsonObject();
        assertEquals("GREEN",tile.get("currency").getAsString());assertEquals(50,tile.get("price").getAsInt());assertEquals(2,tile.get("requiredLevel").getAsInt());
        assertFalse(service.command(owner,new Command(request(),farm.get("revision").getAsString(),"BUY_PLOT",null,null,1,0,null)).getAsJsonObject("result").get("success").getAsBoolean());
        assertEquals("5",service.farm(owner).get("greenCoins").getAsString());
    }
    @Test void confirmedPlotPurchaseRechecksFundsAndConcurrentReplayCannotBuyTwice() throws Exception {
        act("BUY_SEED","nabo");
        store.transaction(owner,tx->{var saved=tx.farm(owner);var game=br.com.colheitafeliz.game.GameState.restore(clock::instant,clock,br.com.colheitafeliz.game.CropCatalogLoader.load(),saved.snapshot(),bound->99);
            game.progress().award(game.progress().xpToNext());tx.save(owner,game.snapshot(),saved.revision()+1,clock.instant());return null;
        });
        var poor=service.farm(owner);var refused=service.command(owner,new Command(request(),poor.get("revision").getAsString(),"BUY_PLOT",null,null,1,0,null));
        assertFalse(refused.getAsJsonObject("result").get("success").getAsBoolean());assertEquals(poor.get("greenCoins"),refused.getAsJsonObject("farm").get("greenCoins"));
        store.transaction(owner,tx->{var saved=tx.farm(owner);var game=br.com.colheitafeliz.game.GameState.restore(clock::instant,clock,br.com.colheitafeliz.game.CropCatalogLoader.load(),saved.snapshot(),bound->99);
            game.wallet().credit(br.com.colheitafeliz.domain.economy.Currency.GREEN,new java.math.BigDecimal("100"));tx.save(owner,game.snapshot(),saved.revision()+1,clock.instant());return null;
        });
        var before=service.farm(owner);var buy=new Command(request(),before.get("revision").getAsString(),"BUY_PLOT",null,"GOLD",1,0,null);
        try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)){
            var first=executor.submit(()->service.command(owner,buy));var second=executor.submit(()->service.command(owner,buy));
            assertEquals(first.get(),second.get());assertTrue(first.get().getAsJsonObject("result").get("success").getAsBoolean());
        }
        var after=service.farm(owner);assertEquals(before.get("coins"),after.get("coins"));
        assertEquals(0,new java.math.BigDecimal(before.get("greenCoins").getAsString()).subtract(new java.math.BigDecimal(after.get("greenCoins").getAsString())).compareTo(new java.math.BigDecimal("50")));
        assertTrue(after.getAsJsonArray("tiles").get(1).getAsJsonObject().get("unlocked").getAsBoolean());
    }
    private static final class TestClock extends Clock {
        Instant now=Instant.parse("2026-11-07T12:00:00Z");@Override public Instant instant(){return now;}@Override public ZoneId getZone(){return ZoneOffset.UTC;}@Override public Clock withZone(ZoneId zone){return this;}
    }
}
