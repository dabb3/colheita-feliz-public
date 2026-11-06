package br.com.colheitafeliz.game;

import br.com.colheitafeliz.domain.economy.Currency;
import br.com.colheitafeliz.domain.economy.Market;
import br.com.colheitafeliz.domain.inventory.ItemLot;
import br.com.colheitafeliz.testsupport.ManualClock;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AnimalSettlementTest {
    @Test void legacyAdultsKeepOriginalCurrencyCostAndProfile() {
        for(String id:new String[]{"pombo","coelho","porco","burro"})for(var currency:Currency.values())for(boolean vip:new boolean[]{false,true}){
            var clock=new ManualClock();var g=AnimalEconomyV2Test.rich(clock);if(vip)assertTrue(g.buyVip().success());
            var type=g.animalType(id);var origin=new ItemLot(id,currency,new BigDecimal("3.3"),1,false);
            g.barn().add(new br.com.colheitafeliz.domain.animal.Animal(type,origin,clock.instant(),clock.instant(),type.requiredMillis(),clock.instant()));
            g.dailyMissions().restore("2026-01-01",Map.of("care",1,"trade",1),java.util.List.of("care","trade"),java.time.LocalDate.of(2026,1,1));
            var before=g.wallet().balance(currency);
            assertTrue(g.sellAnimal(0).success());
            var expected=new BigDecimal("3.3").multiply(new BigDecimal(vip?"1.25":"1.15")).setScale(1,java.math.RoundingMode.CEILING);
            assertEquals(0,expected.compareTo(g.wallet().balance(currency).subtract(before)));
        }
    }
    private ItemLot egg(GameState g,Currency currency,int quantity) {
        var hen=g.animalType("galinha");
        return new ItemLot("ovo",currency,hen.prices().get(currency),quantity,false).withAnimalSale(hen.saleTerms(currency));
    }
    @Test void newGroupedAndIndividualSalesHaveExactlyTheSamePriceAndCannotRepeat() {
        for(var currency:Currency.values())for(boolean vip:new boolean[]{false,true}){
            var g=AnimalEconomyV2Test.rich(new ManualClock());
            var one=egg(g,currency,2);g.inventory().addProduceLot(one);
            var market=new Market(g.wallet(),g.inventory());
            var quote=market.quoteEverything(vip);var expected=one.animalSale().unitRevenue(vip).multiply(new BigDecimal("2"));
            assertEquals(0,expected.compareTo(quote.get(currency)));
            var first=market.sellProduce("ovo",1,vip).get(currency);
            assertNotNull(g.inventory().produceLots().getFirst().animalSale());
            var second=market.sellEverything(vip).get(currency);
            assertEquals(0,expected.compareTo(first.add(second)));
            assertThrows(IllegalStateException.class,()->market.sellEverything(vip));
        }
    }
    @Test void mixedV0V1V2SellTogetherWithoutChangingHistoricalRoundingAndSkipBlockedVip() {
        var g=AnimalEconomyV2Test.rich(new ManualClock());var catalog=g.catalog();
        g.inventory().addProduceLot(new ItemLot("ovo",Currency.GREEN,new BigDecimal("9"),2,false));
        g.inventory().addProduceLot(egg(g,Currency.GREEN,2));
        var tomato=catalog.get("tomate");g.inventory().addProduceLot(tomato.plantingOrigin(new ItemLot("tomate",Currency.GOLD,new BigDecimal("950"),1,false)).harvested(1));
        var coconut=catalog.get("coco");g.inventory().addProduceLot(coconut.plantingOrigin(new ItemLot("coco",Currency.GREEN,new BigDecimal("20"),1,true)).harvested(1));
        var market=new Market(g.wallet(),g.inventory());
        assertEquals(Map.of(Currency.GREEN,new BigDecimal("39.1"),Currency.GOLD,new BigDecimal("679.0")),market.quoteEverything(false));
        assertEquals(market.quoteEverything(false),market.sellEverything(false));
        assertEquals(1,g.inventory().produceLots().size());assertEquals("coco",g.inventory().produceLots().getFirst().itemId());
        assertFalse(market.sellEverything(true).isEmpty());
    }
    @Test void expiryChangesOnlyTheSaleProfileAndNeverTheStoredTerms() {
        var clock=new ManualClock();var g=AnimalEconomyV2Test.rich(clock);assertTrue(g.buyVip().success());
        var lot=egg(g,Currency.GREEN,1);g.inventory().addProduceLot(lot);
        assertEquals(0,new BigDecimal("9.4").compareTo(Market.unitRevenue(lot,g.vipActive())));
        clock.advanceSeconds(30*86400);g.refresh();assertFalse(g.vipActive());
        assertEquals(0,new BigDecimal("9.2").compareTo(g.quoteItem("ovo").get(Currency.GREEN)));
        assertEquals(lot,g.inventory().produceLots().getFirst());
    }
}
