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
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
class ProtectorBiteIntegrationTest {
    private final UUID owner=UUID.randomUUID(),visitor=UUID.randomUUID();
    private final TestClock clock=new TestClock();
    private final AtomicInteger rolls=new AtomicInteger();
    private int roll=0,fine=40;
    private JdbcOnlineStore store;private OnlineGameService service;
    @BeforeEach void setup(){
        String url=System.getenv("TEST_DATABASE_URL");assertNotNull(url);assertTrue(url.startsWith("jdbc:postgresql://127.0.0.1:")&&url.contains("/colheita_test"));
        store=new JdbcOnlineStore(url,"postgres","","test_"+UUID.randomUUID().toString().replace("-",""));store.initialize(owner,clock.instant());
        service=new OnlineGameService(store,clock,bound->{if(bound==100){rolls.incrementAndGet();return roll;}return bound==41?fine:bound-1;},20);
        farm(owner);var invite=service.createInvitation(owner,id()).get("code").getAsString();service.acceptInvitation(visitor,"Visitante",invite,id());farm(visitor);
        prepare(5,600,false);
    }
    @AfterEach void close(){if(store!=null)store.close();}
    private JsonObject farm(UUID player){var view=service.farm(player);view.remove("requestLease");return view;}
    private UUID id(){return RequestIds.issue(clock.instant());}
    private void edit(UUID player,java.util.function.Consumer<GameState> change){store.transaction(player,tx->{tx.lockFarms(player);var saved=tx.farm(player);var game=GameState.restore(clock::instant,clock,CropCatalogLoader.load(),saved.snapshot(),bound->bound-1);change.accept(game);tx.save(player,game.snapshot(),saved.revision()+1,clock.instant());return null;});}
    private void prepare(int guards,int matureSecondsAgo,boolean expired){
        edit(owner,game->{
            var type=game.catalog().get("cenoura");var origin=type.plantingOrigin(new ItemLot(type.id(),Currency.GOLD,type.seedPrices().get(Currency.GOLD),1,false));
            var at=clock.instant().minusSeconds(matureSecondsAgo);
            var crop=new Crop(type,at,origin).water(at).advance(at,type.growthTime(1).toMillis());
            var tile=game.farm().tile(0,0);tile.restore(TileState.READY,crop,PestState.NONE,null,0,null,null,false,tile.harvestRevision()+1);
            game.barn().restore(List.of());
            game.animalTypes().stream().filter(t->t.kind()==AnimalType.Kind.PROTECTOR).limit(guards).forEach(t->{
                var lot=new ItemLot(t.id(),Currency.GOLD,t.prices().get(Currency.GOLD),1,false);
                var animal=Animal.acquire(t,lot,expired?clock.instant().minus(Duration.ofDays(31)):at,clock.instant());
                game.barn().add(animal);
            });
        });
        farm(owner);rolls.set(0);
    }
    private Command request(){var farm=service.visit(visitor,owner);return new Command(id(),farm.get("revision").getAsString(),"STEAL",null,null,0,0,farm.getAsJsonArray("tiles").get(0).getAsJsonObject().get("harvestRevision").getAsString());}
    private String kind(JsonObject reply){return reply.getAsJsonObject("theftOutcome").get("kind").getAsString();}
    private void unchangedRewards(JsonObject before,JsonObject after){for(String key:List.of("greenCoins","currentXp","level","produceLots","missions","seedCounts","fertilizers"))assertEquals(before.get(key),after.get(key),key);}

    @Test void bittenAttemptBurnsOnlyGoldAndReplayReturnsExactReceipt(){
        var command=request();var ownerBefore=farm(owner);var before=farm(visitor);
        var reply=service.visitAction(visitor,owner,command);
        assertEquals("BITTEN",kind(reply));assertFalse(reply.getAsJsonObject("result").get("success").getAsBoolean());
        assertEquals("50",reply.getAsJsonObject("theftOutcome").get("goldDebited").getAsString());
        var after=farm(visitor);assertEquals(0,new BigDecimal(before.get("coins").getAsString()).subtract(new BigDecimal("50")).compareTo(new BigDecimal(after.get("coins").getAsString())));
        unchangedRewards(before,after);assertEquals(ownerBefore,farm(owner));assertEquals(clock.instant(),store.transaction(visitor,tx->tx.lastTheft(visitor)));
        assertEquals(reply,service.visitAction(visitor,owner,command));assertEquals(1,rolls.get());assertEquals(after,farm(visitor));
    }
    @Test void noBiteStillConsumesGlobalAttemptWithoutWalletOrRewardsChanges(){
        roll=30;var command=request();var before=farm(visitor);var ownerBefore=farm(owner);
        var reply=service.visitAction(visitor,owner,command);assertEquals("PROTECTED",kind(reply));assertEquals("0",reply.getAsJsonObject("theftOutcome").get("goldDebited").getAsString());
        assertEquals(before,farm(visitor));assertEquals(ownerBefore,farm(owner));assertEquals(clock.instant(),store.transaction(visitor,tx->tx.lastTheft(visitor)));
        assertEquals("BLOCKED",kind(service.visitAction(visitor,owner,request())));assertEquals(1,rolls.get());
        clock.advance(86399);assertEquals("BLOCKED",kind(service.visitAction(visitor,owner,request())));assertEquals(1,rolls.get());
        clock.advance(1);assertEquals("STOLEN",kind(service.visitAction(visitor,owner,request()))); // Guards are hungry after 24h.
    }
    @Test void exclusiveWindowPrecedesProtectionAndUsesExactFiveMinutes(){
        prepare(5,299,false);var before=farm(visitor);
        assertEquals("BLOCKED",kind(service.visitAction(visitor,owner,request())));assertEquals(0,rolls.get());assertNull(store.transaction(visitor,tx->tx.lastTheft(visitor)));assertEquals(before,farm(visitor));
        clock.advance(1);assertEquals("BITTEN",kind(service.visitAction(visitor,owner,request())));assertEquals(1,rolls.get());
    }
    @Test void hungryAndExpiredAnimalsDoNotTriggerBite(){
        clock.advance(21600);assertEquals("STOLEN",kind(service.visitAction(visitor,owner,request())));assertEquals(0,rolls.get());
    }
    @Test void expiredButFedAnimalDoesNotTriggerBite(){
        prepare(5,600,true);assertEquals("STOLEN",kind(service.visitAction(visitor,owner,request())));assertEquals(0,rolls.get());
    }
    @Test void staleSeasonFarmRevisionAndInvalidCoordinatesCannotSpendOrRoll(){
        var c=request();
        assertEquals("BLOCKED",kind(service.visitAction(visitor,owner,new Command(id(),c.expectedRevision(),"STEAL",null,null,0,0,Long.toString(Long.parseLong(c.harvestRevision())+1)))));
        assertEquals(409,assertThrows(ApiFailure.class,()->service.visitAction(visitor,owner,new Command(id(),"0","STEAL",null,null,0,0,c.harvestRevision()))).status());
        assertEquals(400,assertThrows(ApiFailure.class,()->new Command(id(),c.expectedRevision(),"STEAL",null,null,6,0,c.harvestRevision())).status());
        assertEquals(0,rolls.get());assertNull(store.transaction(visitor,tx->tx.lastTheft(visitor)));
    }
    @Test void growingProtectedCropDoesNotSpendOrRoll(){
        edit(owner,game->{var tile=game.farm().tile(0,0);var crop=new Crop(game.catalog().get("cenoura"),clock.instant(),tile.crop().origin()).water(clock.instant());tile.restore(TileState.GROWING,crop,PestState.NONE,clock.instant().plusSeconds(3600),0,clock.instant().plusSeconds(3600),null,false,tile.harvestRevision()+1);});
        assertEquals("BLOCKED",kind(service.visitAction(visitor,owner,request())));assertEquals(0,rolls.get());assertNull(store.transaction(visitor,tx->tx.lastTheft(visitor)));
    }
    @Test void missingFriendshipIsRejectedBeforeRoll(){
        var stranger=UUID.randomUUID();var invite=service.createInvitation(owner,id()).get("code").getAsString();service.acceptInvitation(stranger,"Outra pessoa",invite,id());
        var c=request();assertEquals(404,assertThrows(ApiFailure.class,()->service.visitAction(stranger,visitor,c)).status());assertEquals(0,rolls.get());
    }
    @Test void concurrentAttemptsOnlyRollAndChargeOnce()throws Exception {
        var first=request();var second=request();var start=new java.util.concurrent.CyclicBarrier(2);
        try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)){
            var a=executor.submit(()->{start.await();return service.visitAction(visitor,owner,first);});
            var b=executor.submit(()->{start.await();return service.visitAction(visitor,owner,second);});
            assertEquals(Set.of("BITTEN","BLOCKED"),Set.of(kind(a.get()),kind(b.get())));
        }
        assertEquals(1,rolls.get());assertEquals("450",farm(visitor).get("coins").getAsString());
    }
    @Test void transactionFailureRollsBackFineCooldownAndReceipt(){
        var command=request();var before=farm(visitor);var ownerBefore=farm(owner);
        assertThrows(IllegalStateException.class,()->store.transaction(visitor,tx->{assertEquals("BITTEN",kind(service.visitAction(tx,visitor,owner,command)));throw new IllegalStateException("Injected rollback");}));
        assertNull(store.transaction(visitor,tx->tx.lastTheft(visitor)));assertEquals(before,farm(visitor));assertEquals(ownerBefore,farm(owner));
        assertEquals("BITTEN",kind(service.visitAction(visitor,owner,command)));assertEquals("450",farm(visitor).get("coins").getAsString());
    }
    @Test void nonStealActionsNeverReturnBiteOutcome(){
        var c=request();var reply=service.visitAction(visitor,owner,new Command(id(),c.expectedRevision(),"SPREAD_INSECTS",null,null,0,0,c.harvestRevision()));
        assertFalse(reply.has("theftOutcome"));assertEquals(0,rolls.get());assertNull(store.transaction(visitor,tx->tx.lastTheft(visitor)));
    }
    static final class TestClock extends Clock {
        private Instant now=Instant.parse("2026-11-09T12:00:00Z");
        void advance(long seconds){now=now.plusSeconds(seconds);}
        @Override public Instant instant(){return now;}
        @Override public ZoneId getZone(){return ZoneOffset.UTC;}
        @Override public Clock withZone(ZoneId zone){return this;}
    }
}
