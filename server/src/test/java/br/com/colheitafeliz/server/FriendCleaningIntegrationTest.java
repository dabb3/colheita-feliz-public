package br.com.colheitafeliz.server;

import br.com.colheitafeliz.game.*;
import br.com.colheitafeliz.domain.animal.*;
import br.com.colheitafeliz.domain.crop.Crop;
import br.com.colheitafeliz.domain.economy.Currency;
import br.com.colheitafeliz.domain.farm.*;
import br.com.colheitafeliz.domain.inventory.ItemLot;
import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
class FriendCleaningIntegrationTest {
    private final UUID owner=UUID.randomUUID(),visitor=UUID.randomUUID();
    private final ProtectorBiteIntegrationTest.TestClock clock=new ProtectorBiteIntegrationTest.TestClock();
    private JdbcOnlineStore store;private OnlineGameService service;
    @BeforeEach void setup(){
        String url=System.getenv("TEST_DATABASE_URL");assertNotNull(url);assertTrue(url.startsWith("jdbc:postgresql://127.0.0.1:")&&url.contains("/colheita_test"));
        store=new JdbcOnlineStore(url,"postgres","","test_"+UUID.randomUUID().toString().replace("-",""));store.initialize(owner,clock.instant());
        service=new OnlineGameService(store,clock,bound->bound-1,20);
        farm(owner);var invite=service.createInvitation(owner,id()).get("code").getAsString();service.acceptInvitation(visitor,"Visitante",invite,id());farm(visitor);
    }
    @AfterEach void close(){if(store!=null)store.close();}
    private JsonObject farm(UUID player){var view=service.farm(player);view.remove("requestLease");return view;}
    private UUID id(){return RequestIds.issue(clock.instant());}
    private void edit(UUID player,java.util.function.Consumer<GameState> change){store.transaction(player,tx->{tx.lockFarms(player);var saved=tx.farm(player);var game=GameState.restoreOnline(saved.time(),clock.instant(),CropCatalogLoader.load(),saved.snapshot(),bound->bound-1);change.accept(game);tx.save(player,game.snapshot(),saved.revision()+1,clock.instant());return null;});}
    /** Planta crescendo, regada, com ervas e insetos espalhados pelo domínio e chegada observada para a automação VIP. */
    private void infest(int col,int row){
        edit(owner,game->{
            var type=game.catalog().get("cenoura");var origin=type.plantingOrigin(new ItemLot(type.id(),Currency.GOLD,type.seedPrices().get(Currency.GOLD),1,false));
            var tile=game.farm().tile(col,row);var crop=new Crop(type,clock.instant(),origin).water(clock.instant());
            tile.restore(TileState.GROWING,crop,PestState.NONE,clock.instant().plusSeconds(3600),0,clock.instant().plusSeconds(3600),null,false,tile.harvestRevision()+1);
            var helper=GameState.newOnline(clock,game.catalog(),bound->bound-1);var visits=new VisitService();
            assertTrue(visits.spreadWeeds(game,helper,col,row,tile.harvestRevision()).result().success());
            assertTrue(visits.spreadInsects(game,helper,col,row,tile.harvestRevision()).result().success());
            tile.observePestArrivals(clock.instant());
        });
    }
    private JsonObject tile(JsonObject farm,int col,int row){for(var t:farm.getAsJsonArray("tiles")){var o=t.getAsJsonObject();if(o.get("column").getAsInt()==col&&o.get("row").getAsInt()==row)return o;}throw new AssertionError();}
    private Command clean(String action,int col,int row){var farm=service.visit(visitor,owner);return new Command(id(),farm.get("revision").getAsString(),action,null,null,col,row,tile(farm,col,row).get("harvestRevision").getAsString());}
    private JsonObject act(Command command){return service.visitAction(visitor,owner,command);}
    private boolean success(JsonObject reply){return reply.getAsJsonObject("result").get("success").getAsBoolean();}
    private String message(JsonObject reply){return reply.getAsJsonObject("result").get("message").getAsString();}
    private BigDecimal xp(){return new BigDecimal(farm(visitor).get("currentXp").getAsString());}
    private JsonObject nature(JsonObject farm){for(var m:farm.getAsJsonArray("missions"))if(m.getAsJsonObject().get("id").getAsString().equals("nature"))return m.getAsJsonObject();throw new AssertionError();}
    private GameSnapshot.AutomationSnapshot arrivals(){return store.transaction(owner,tx->tx.farm(owner).snapshot().automations());}

    @Test void cleaningHelpsOwnerRewardsVisitorAndReplayIsIdempotent(){
        infest(0,0);assertTrue(arrivals().weedsSince().containsKey("0"));assertTrue(arrivals().insectsSince().containsKey("0"));
        var ownerBefore=farm(owner);var xp=xp();var green=new BigDecimal(farm(visitor).get("greenCoins").getAsString());
        var command=clean("CLEAN_WEEDS",0,0);var reply=act(command);
        assertTrue(success(reply),message(reply));assertTrue(message(reply).contains("+2 XP"),message(reply));
        assertFalse(tile(farm(owner),0,0).get("weeds").getAsBoolean());assertFalse(arrivals().weedsSince().containsKey("0"));assertTrue(arrivals().insectsSince().containsKey("0"));
        assertEquals(0,xp.add(BigDecimal.TWO).compareTo(xp()));assertEquals(1,nature(farm(visitor)).get("progress").getAsInt());
        assertEquals(reply,act(command));assertEquals(0,xp.add(BigDecimal.TWO).compareTo(xp()));assertEquals(1,nature(farm(visitor)).get("progress").getAsInt());
        var again=act(clean("CLEAN_WEEDS",0,0));assertFalse(success(again));assertEquals(0,xp.add(BigDecimal.TWO).compareTo(xp()));assertEquals(1,nature(farm(visitor)).get("progress").getAsInt());
        var insects=act(clean("CLEAN_INSECTS",0,0));assertTrue(success(insects),message(insects));
        var after=farm(visitor);assertEquals(0,xp.add(BigDecimal.valueOf(4)).compareTo(xp()));assertTrue(nature(after).get("done").getAsBoolean());assertEquals(2,nature(after).get("target").getAsInt());
        assertFalse(after.getAsJsonObject("cooperativeBonus").get("done").getAsBoolean());assertEquals(0,green.compareTo(new BigDecimal(after.get("greenCoins").getAsString())));
        var ownerAfter=farm(owner);assertEquals(0,tile(ownerAfter,0,0).get("insects").getAsInt());assertTrue(arrivals().insectsSince().isEmpty());
        for(String key:List.of("currentXp","greenCoins","missions","coins","fertilizers"))assertEquals(ownerBefore.get(key),ownerAfter.get(key),key);
        infest(0,0);assertTrue(success(act(clean("CLEAN_WEEDS",0,0))));
        assertEquals(0,green.compareTo(new BigDecimal(farm(visitor).get("greenCoins").getAsString())));assertEquals(2,nature(farm(visitor)).get("progress").getAsInt());
    }
    @Test void dailyCooperativeXpCapStillCleansAndCountsMission(){
        infest(0,0);store.transaction(visitor,tx->{tx.addCooperativeXp(visitor,LocalDate.of(2026,11,9),19);return null;});var xp=xp();
        assertTrue(success(act(clean("CLEAN_WEEDS",0,0))));assertEquals(0,xp.add(BigDecimal.ONE).compareTo(xp()));
        var capped=act(clean("CLEAN_INSECTS",0,0));assertTrue(success(capped));assertTrue(message(capped).contains("sem XP"),message(capped));
        assertEquals(0,xp.add(BigDecimal.ONE).compareTo(xp()));assertEquals(20,(int)store.transaction(visitor,tx->tx.cooperativeXp(visitor,LocalDate.of(2026,11,9))));
        assertTrue(nature(farm(visitor)).get("done").getAsBoolean());
        clock.advance(Duration.ofHours(13).toSeconds());assertEquals(0,(int)store.transaction(visitor,tx->tx.cooperativeXp(visitor,LocalDate.of(2026,11,10))));
        infest(0,0);var next=xp();assertTrue(success(act(clean("CLEAN_WEEDS",0,0))));assertEquals(0,next.add(BigDecimal.TWO).compareTo(xp()));
        assertEquals(2,(int)store.transaction(visitor,tx->tx.cooperativeXp(visitor,LocalDate.of(2026,11,10))));
    }
    @Test void strangerAndStaleRevisionAreRejectedWithoutRewards(){
        infest(0,0);var stranger=UUID.randomUUID();var invite=service.createInvitation(owner,id()).get("code").getAsString();service.acceptInvitation(stranger,"Outra pessoa",invite,id());
        var c=clean("CLEAN_WEEDS",0,0);var before=farm(visitor);
        assertEquals(404,assertThrows(ApiFailure.class,()->service.visitAction(stranger,visitor,c)).status());
        assertEquals(409,assertThrows(ApiFailure.class,()->act(new Command(id(),"0","CLEAN_WEEDS",null,null,0,0,c.harvestRevision()))).status());
        assertFalse(success(act(new Command(id(),c.expectedRevision(),"CLEAN_WEEDS",null,null,0,0,Long.toString(Long.parseLong(c.harvestRevision())+1)))));
        assertEquals(before,farm(visitor));assertTrue(tile(farm(owner),0,0).get("weeds").getAsBoolean());
        assertEquals(400,assertThrows(ApiFailure.class,()->service.command(visitor,new Command(id(),farm(visitor).get("revision").getAsString(),"CLEAN_WEEDS",null,null,0,0,"0"))).status());
    }
    @Test void protectedPlotsCanBeCleanedAndRefusalsDoNotRevealProtection(){
        for(var t:farm(owner).getAsJsonArray("tiles")){var o=t.getAsJsonObject();if(o.get("unlocked").getAsBoolean())infest(o.get("column").getAsInt(),o.get("row").getAsInt());}
        edit(owner,game->game.animalTypes().stream().filter(t->t.kind()==AnimalType.Kind.PROTECTOR).limit(1).forEach(t->
                game.barn().add(Animal.acquire(t,new ItemLot(t.id(),Currency.GOLD,t.prices().get(Currency.GOLD),1,false),clock.instant(),clock.instant()))));
        JsonObject guarded=null,open=null;
        for(var t:farm(owner).getAsJsonArray("tiles")){var o=t.getAsJsonObject();if(!o.get("unlocked").getAsBoolean())continue;if(o.get("protected").getAsBoolean()){if(guarded==null)guarded=o;}else if(open==null)open=o;}
        assertNotNull(guarded);assertNotNull(open);
        int gc=guarded.get("column").getAsInt(),gr=guarded.get("row").getAsInt(),oc=open.get("column").getAsInt(),or=open.get("row").getAsInt();
        var g=clean("CLEAN_WEEDS",gc,gr);var o=clean("CLEAN_WEEDS",oc,or);
        assertEquals(message(act(new Command(id(),o.expectedRevision(),"CLEAN_WEEDS",null,null,oc,or,"999"))),message(act(new Command(id(),g.expectedRevision(),"CLEAN_WEEDS",null,null,gc,gr,"999"))));
        for(String action:List.of("CLEAN_WEEDS","CLEAN_INSECTS")){assertTrue(success(act(clean(action,gc,gr))));assertTrue(success(act(clean(action,oc,or))));}
        for(String action:List.of("CLEAN_WEEDS","CLEAN_INSECTS"))assertEquals(message(act(clean(action,oc,or))),message(act(clean(action,gc,gr))));
        var tile=tile(farm(owner),gc,gr);assertFalse(tile.get("weeds").getAsBoolean());assertEquals(0,tile.get("insects").getAsInt());assertTrue(tile.get("protected").getAsBoolean());
    }
}
