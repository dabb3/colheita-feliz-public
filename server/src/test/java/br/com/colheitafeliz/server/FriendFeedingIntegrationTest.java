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
class FriendFeedingIntegrationTest {
    private final UUID owner=UUID.randomUUID(),visitor=UUID.randomUUID();
    private final ProtectorBiteIntegrationTest.TestClock clock=new ProtectorBiteIntegrationTest.TestClock();
    private final String schema="test_"+UUID.randomUUID().toString().replace("-","");
    private JdbcOnlineStore store;private OnlineGameService service;
    @BeforeEach void setup(){
        String url=System.getenv("TEST_DATABASE_URL");assertNotNull(url);assertTrue(url.startsWith("jdbc:postgresql://127.0.0.1:")&&url.contains("/colheita_test"));
        store=new JdbcOnlineStore(url,"postgres","",schema);store.initialize(owner,clock.instant());
        service=new OnlineGameService(store,clock,bound->bound-1,20);
        farm(owner);var invite=service.createInvitation(owner,id()).get("code").getAsString();service.acceptInvitation(visitor,"Visitante",invite,id());farm(visitor);
    }
    @AfterEach void close(){if(store!=null)store.close();}
    private JsonObject farm(UUID player){var view=service.farm(player);view.remove("requestLease");return view;}
    private UUID id(){return RequestIds.issue(clock.instant());}
    private void edit(UUID player,java.util.function.Consumer<GameState> change){store.transaction(player,tx->{tx.lockFarms(player);var saved=tx.farm(player);var game=GameState.restoreOnline(saved.time(),clock.instant(),CropCatalogLoader.load(),saved.snapshot(),bound->bound-1);change.accept(game);tx.save(player,game.snapshot(),saved.revision()+1,clock.instant());return null;});}
    /** Adiciona ao dono um animal faminto do tipo indicado e devolve o id do tipo. */
    private String hungry(AnimalType.Kind kind){
        String[] id=new String[1];
        edit(owner,game->{var type=game.animalTypes().stream().filter(t->t.kind()==kind&&!game.barn().full(t)).findFirst().orElseThrow();var price=type.prices().entrySet().iterator().next();
            game.barn().add(new Animal(type,new ItemLot(type.id(),price.getKey(),price.getValue(),1,false),clock.instant(),clock.instant(),0,clock.instant()));id[0]=type.id();});
        return id[0];
    }
    private void feed(String feedId,int amount){edit(visitor,game->game.inventory().addFeed(feedId,amount));}
    private int stock(UUID player,String feedId){var feed=farm(player).getAsJsonObject("feed");return feed.has(feedId)?feed.get(feedId).getAsInt():0;}
    private Command feedCommand(String type,String feedId){var farm=service.visit(visitor,owner);return new Command(id(),farm.get("revision").getAsString(),"FEED_ANIMAL",type,null,null,null,null,null,feedId);}
    private JsonObject act(Command command){return service.visitAction(visitor,owner,command);}
    private boolean success(JsonObject reply){return reply.getAsJsonObject("result").get("success").getAsBoolean();}
    private String message(JsonObject reply){return reply.getAsJsonObject("result").get("message").getAsString();}
    private BigDecimal xp(){return new BigDecimal(farm(visitor).get("currentXp").getAsString());}
    private BigDecimal green(){return new BigDecimal(farm(visitor).get("greenCoins").getAsString());}
    private JsonObject mission(JsonObject farm,String id){for(var m:farm.getAsJsonArray("missions"))if(m.getAsJsonObject().get("id").getAsString().equals(id))return m.getAsJsonObject();throw new AssertionError();}
    private String status(String type){for(var a:farm(owner).getAsJsonArray("animals"))if(a.getAsJsonObject().get("id").getAsString().equals(type))return a.getAsJsonObject().get("status").getAsString();throw new AssertionError();}
    private void infest(){
        edit(owner,game->{
            var type=game.catalog().get("cenoura");var origin=type.plantingOrigin(new ItemLot(type.id(),Currency.GOLD,type.seedPrices().get(Currency.GOLD),1,false));
            var tile=game.farm().tile(0,0);var crop=new Crop(type,clock.instant(),origin).water(clock.instant());
            tile.restore(TileState.GROWING,crop,PestState.NONE,clock.instant().plusSeconds(3600),0,clock.instant().plusSeconds(3600),null,false,tile.harvestRevision()+1);
            var helper=GameState.newOnline(clock,game.catalog(),bound->bound-1);var visits=new VisitService();
            assertTrue(visits.spreadWeeds(game,helper,0,0,tile.harvestRevision()).result().success());
            assertTrue(visits.spreadInsects(game,helper,0,0,tile.harvestRevision()).result().success());
            tile.observePestArrivals(clock.instant());
        });
    }
    private Command clean(String action){var farm=service.visit(visitor,owner);String harvest=null;
        for(var t:farm.getAsJsonArray("tiles")){var o=t.getAsJsonObject();if(o.get("column").getAsInt()==0&&o.get("row").getAsInt()==0)harvest=o.get("harvestRevision").getAsString();}
        return new Command(id(),farm.get("revision").getAsString(),action,null,null,0,0,harvest);}

    @Test void feedingUsesVisitorFeedRewardsVisitorAndReplayIsIdempotent(){
        var type=hungry(AnimalType.Kind.PRODUCER);feed("producer",2);feed("growth",1);
        var ownerBefore=farm(owner);var xp=xp();
        assertEquals("HUNGRY",status(type));
        assertFalse(success(act(feedCommand(type,"growth"))));assertEquals(1,stock(visitor,"growth"));
        var command=feedCommand(type,"producer");var reply=act(command);
        assertTrue(success(reply),message(reply));assertTrue(message(reply).contains("+2 XP"),message(reply));
        assertEquals("PRODUCING",status(type));assertEquals(1,stock(visitor,"producer"));
        assertEquals(0,xp.add(BigDecimal.TWO).compareTo(xp()));assertTrue(mission(farm(visitor),"animals").get("done").getAsBoolean());
        assertEquals(reply,act(command));assertEquals(1,stock(visitor,"producer"));assertEquals(0,xp.add(BigDecimal.TWO).compareTo(xp()));
        var again=act(feedCommand(type,"producer"));assertFalse(success(again));assertEquals(1,stock(visitor,"producer"));assertEquals(0,xp.add(BigDecimal.TWO).compareTo(xp()));
        var ownerAfter=farm(owner);
        for(String key:List.of("currentXp","greenCoins","missions","coins","feed"))assertEquals(ownerBefore.get(key),ownerAfter.get(key),key);
        assertEquals(400,assertThrows(ApiFailure.class,()->service.command(visitor,new Command(id(),farm(visitor).get("revision").getAsString(),"FEED_ANIMAL",type,null,null,null,null,null,"producer"))).status());
    }
    @Test void missingFeedStrangerRemovedFriendshipAndStaleRevisionChangeNothing(){
        var type=hungry(AnimalType.Kind.PRODUCER);var before=farm(visitor);
        var empty=act(feedCommand(type,"producer"));assertFalse(success(empty));assertEquals("Sem ração. Compre na Loja, aba Animais.",message(empty));
        assertEquals(before.get("currentXp"),farm(visitor).get("currentXp"));assertEquals("HUNGRY",status(type));
        feed("producer",1);
        assertEquals(400,assertThrows(ApiFailure.class,()->act(new Command(id(),"1","FEED_ANIMAL",null,null,null,null,null,null,"producer"))).status());
        assertEquals(409,assertThrows(ApiFailure.class,()->act(new Command(id(),"0","FEED_ANIMAL",type,null,null,null,null,null,"producer"))).status());
        var stranger=UUID.randomUUID();var invite=service.createInvitation(owner,id()).get("code").getAsString();service.acceptInvitation(stranger,"Outra pessoa",invite,id());
        var c=feedCommand(type,"producer");
        assertEquals(404,assertThrows(ApiFailure.class,()->service.visitAction(stranger,visitor,c)).status());
        store.transaction(visitor,tx->{tx.execute("DELETE FROM "+schema+".friendships");return null;});
        assertEquals(404,assertThrows(ApiFailure.class,()->act(c)).status());
        assertEquals(1,stock(visitor,"producer"));assertEquals("HUNGRY",status(type));
    }
    @Test void concurrentFeedsOnlyFeedAndChargeOnce()throws Exception{
        var type=hungry(AnimalType.Kind.PRODUCER);feed("producer",2);var xp=xp();
        var first=feedCommand(type,"producer");var second=feedCommand(type,"producer");var start=new java.util.concurrent.CyclicBarrier(2);
        int fed=0;
        try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)){
            var a=executor.submit(()->{start.await();return act(first);});var b=executor.submit(()->{start.await();return act(second);});
            for(var f:List.of(a,b))try{if(success(f.get()))fed++;}catch(java.util.concurrent.ExecutionException e){assertEquals(409,((ApiFailure)e.getCause()).status());}
        }
        assertEquals(1,fed);assertEquals(1,stock(visitor,"producer"));assertEquals(0,xp.add(BigDecimal.TWO).compareTo(xp()));
    }
    @Test void cooperativeXpCapIsSharedWithCleaningAndBonusNeedsBothMissionsOnce(){
        store.transaction(visitor,tx->{tx.addCooperativeXp(visitor,LocalDate.of(2026,11,9),17);return null;});
        var xp=xp();var green=green();
        infest();assertTrue(success(act(clean("CLEAN_WEEDS"))));assertTrue(success(act(clean("CLEAN_INSECTS"))));
        assertEquals(0,xp.add(BigDecimal.valueOf(3)).compareTo(xp()));
        assertTrue(mission(farm(visitor),"nature").get("done").getAsBoolean());
        assertFalse(farm(visitor).getAsJsonObject("cooperativeBonus").get("done").getAsBoolean());assertEquals(0,green.compareTo(green()));
        var producer=hungry(AnimalType.Kind.PRODUCER);feed("producer",2);
        var capped=act(feedCommand(producer,"producer"));assertTrue(success(capped),message(capped));assertTrue(message(capped).contains("sem XP"),message(capped));
        assertEquals(0,xp.add(BigDecimal.valueOf(3)).compareTo(xp()));
        assertEquals(20,(int)store.transaction(visitor,tx->tx.cooperativeXp(visitor,LocalDate.of(2026,11,9))));
        var after=farm(visitor);assertTrue(mission(after,"animals").get("done").getAsBoolean());
        assertTrue(after.getAsJsonObject("cooperativeBonus").get("done").getAsBoolean());assertEquals(0,green.add(BigDecimal.ONE).compareTo(green()));
        var other=hungry(AnimalType.Kind.PRODUCER);
        assertTrue(success(act(feedCommand(other,"producer"))));assertEquals(0,green.add(BigDecimal.ONE).compareTo(green()));
    }
}
