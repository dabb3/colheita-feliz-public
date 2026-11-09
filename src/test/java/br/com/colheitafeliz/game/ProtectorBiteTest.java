package br.com.colheitafeliz.game;

import br.com.colheitafeliz.domain.animal.Animal;
import br.com.colheitafeliz.domain.animal.AnimalType;
import br.com.colheitafeliz.domain.economy.Currency;
import br.com.colheitafeliz.domain.farm.PestState;
import br.com.colheitafeliz.domain.farm.TileState;
import br.com.colheitafeliz.domain.inventory.ItemLot;
import br.com.colheitafeliz.testsupport.ManualClock;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class ProtectorBiteTest {
    private final ManualClock clock=new ManualClock();
    private final GameState owner=state(),visitor=state();
    private final VisitService visits=new VisitService();
    private GameState state(){return new GameState(clock::instant,clock,CropCatalogLoader.load(),bound->bound-1);}
    private void ready(int row){
        var type=owner.catalog().get("cenoura");var tile=owner.farm().tile(0,row);tile.plow();
        var origin=type.plantingOrigin(new ItemLot(type.id(),Currency.GOLD,type.seedPrices().get(Currency.GOLD),1,false));
        var crop=new br.com.colheitafeliz.domain.crop.Crop(type,clock.instant(),origin).water(clock.instant()).advance(clock.instant(),type.growthTime(1).toMillis());
        tile.restore(TileState.READY,crop,PestState.NONE,null,0,null,null,false,1);
    }
    private void protect(int count){
        owner.animalTypes().stream().filter(t->t.kind()==AnimalType.Kind.PROTECTOR).limit(count).forEach(type->{
            var origin=new ItemLot(type.id(),Currency.GOLD,type.prices().get(Currency.GOLD),1,false);
            owner.barn().add(Animal.acquire(type,origin,clock.instant()));
        });owner.protectedPlots();
    }
    private void gold(String value){var old=visitor.wallet().coins();if(old.signum()>0)visitor.wallet().debit(Currency.GOLD,old);var amount=new BigDecimal(value);if(amount.signum()>0)visitor.wallet().credit(Currency.GOLD,amount);}
    @ParameterizedTest @CsvSource({"0,0,500,10.0,BITTEN","29,40,500,50.0,BITTEN","30,0,500,0,PROTECTED","99,0,500,0,PROTECTED","0,40,7.5,7.5,BITTEN","0,40,0,0,BITTEN"})
    void probabilityBoundsAndActualDebit(int roll,int fine,String balance,String debit,String kind){
        ready(0);protect(5);gold(balance);var beforeOwner=owner.snapshot();var before=visitor.snapshot();
        var calls=new java.util.ArrayList<Integer>();
        var result=visits.attemptSteal(owner,visitor,0,0,1,"Visitante",bound->{calls.add(bound);return bound==100?roll:fine;});
        assertEquals(kind,result.kind().name());assertFalse(result.result().success());assertTrue(result.consumesCooldown());
        assertEquals(0,new BigDecimal(debit).compareTo(result.goldDebited()));
        assertEquals(0,new BigDecimal(balance).subtract(new BigDecimal(debit)).compareTo(visitor.wallet().coins()));
        assertEquals(before.greenCoins(),visitor.greenCoins());assertEquals(before.produce(),visitor.snapshot().produce());
        assertEquals(before.currentXp(),visitor.snapshot().currentXp());assertEquals(before.dailyMissionProgress(),visitor.snapshot().dailyMissionProgress());
        assertEquals(beforeOwner,owner.snapshot());assertEquals(roll<30?java.util.List.of(100,41):java.util.List.of(100),calls);
    }
    @Test void unprotectedPlotStealsWithoutRolling(){
        ready(0);var result=visits.attemptSteal(owner,visitor,0,0,1,"Visitante",bound->{throw new AssertionError("Unexpected bite roll");});
        assertEquals("STOLEN",result.kind().name());assertTrue(result.result().success());assertTrue(result.consumesCooldown());
    }
    @Test void invalidTargetsNeverRollOrConsumeAttempt(){
        ready(0);protect(5);
        for(int[] target:new int[][]{{-1,0,1},{1,0,0},{0,1,0},{0,0,0}}){
            var result=visits.attemptSteal(owner,visitor,target[0],target[1],target[2],"Visitor",bound->{throw new AssertionError("Invalid target rolled");});
            assertEquals("BLOCKED",result.kind().name());assertFalse(result.consumesCooldown());
        }
    }
    @Test void growingProtectedPlantNeverRolls(){
        var tile=owner.farm().tile(0,0);tile.plow();tile.plant(owner.catalog().get("cenoura"),clock.instant());tile.water(clock.instant());protect(5);
        assertEquals("BLOCKED",visits.attemptSteal(owner,visitor,0,0,tile.harvestRevision(),"Visitor",bound->{throw new AssertionError("Growing target rolled");}).kind().name());
    }
    @Test void hungryAndExpiredProtectorsDoNotBite(){
        ready(0);protect(5);clock.advanceSeconds(21600);
        assertEquals("STOLEN",visits.attemptSteal(owner,visitor,0,0,1,"Visitor",bound->{throw new AssertionError("Hungry guard rolled");}).kind().name());
        ready(1);clock.advanceSeconds(30*86400L);
        assertEquals("STOLEN",visits.attemptSteal(owner,visitor,0,1,1,"Visitor",bound->{throw new AssertionError("Expired guard rolled");}).kind().name());
    }
    @Test void oneProtectorOnlyBlocksItsSelectedCoverage(){
        for(int row=0;row<5;row++)ready(row);protect(1);var protectedIds=owner.protectedPlots();assertEquals(1,protectedIds.size());
        int protectedRow=protectedIds.iterator().next();
        assertEquals("PROTECTED",visits.attemptSteal(owner,visitor,0,protectedRow,1,"Visitor",bound->99).kind().name());
        int openRow=(protectedRow+1)%5;
        assertEquals("STOLEN",visits.attemptSteal(owner,visitor,0,openRow,1,"Visitor",bound->{throw new AssertionError("Uncovered plot rolled");}).kind().name());
    }
}
