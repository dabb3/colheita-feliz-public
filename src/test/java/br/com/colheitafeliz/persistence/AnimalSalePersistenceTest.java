package br.com.colheitafeliz.persistence;

import br.com.colheitafeliz.game.*;
import br.com.colheitafeliz.domain.animal.Animal;
import br.com.colheitafeliz.domain.economy.Currency;
import br.com.colheitafeliz.domain.economy.Market;
import br.com.colheitafeliz.domain.inventory.ItemLot;
import br.com.colheitafeliz.testsupport.ManualClock;
import com.google.gson.JsonParser;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class AnimalSalePersistenceTest {
    @TempDir Path dir;
    private GameState game(ManualClock clock) {
        var g = new GameState(clock::instant,clock,CropCatalogLoader.load(),bound->99);
        while(g.progress().level()<60)g.progress().award(g.progress().xpToNext());
        g.wallet().credit(Currency.GOLD,new BigDecimal("1000000"));
        g.wallet().credit(Currency.GREEN,new BigDecimal("10000"));
        return g;
    }
    @ParameterizedTest
    @ValueSource(strings={"fraction","string","overflow","missing","wrongPrice","wrongId","wrongCurrency","removedTerms","version","mixed","seed","tile","productId","quantity","vip"})
    void corruptTermsAreRejectedWithoutOverwriting(String corruption) throws Exception {
        var clock=new ManualClock();var g=game(clock);
        assertTrue(g.buyAnimal(g.animalType("galinha"),Currency.GOLD).success());
        var file=dir.resolve("save.json");var repo=new JsonSaveRepository(file);
        repo.save(g.snapshot(),clock.instant(),clock.instant());
        var json=JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        var snapshot=json.getAsJsonObject("snapshot");
        var lot=snapshot.getAsJsonArray("animals").get(0).getAsJsonObject().getAsJsonObject("origin");
        var terms=lot.getAsJsonObject("animalSale");
        switch(corruption){
            case "fraction" -> terms.addProperty("commonRevenueTenths",new BigDecimal("84800.9"));
            case "string" -> terms.addProperty("vipRevenueTenths","86000");
            case "overflow" -> terms.addProperty("vipRevenueTenths",new BigDecimal("9223372036854775808"));
            case "missing" -> terms.remove("vipRevenueTenths");
            case "wrongPrice" -> terms.addProperty("vipRevenueTenths",86001);
            case "wrongId" -> terms.addProperty("animalId","vaca");
            case "wrongCurrency" -> lot.addProperty("currency","GREEN");
            case "removedTerms" -> lot.remove("animalSale");
            case "version" -> terms.addProperty("economyVersion",3);
            case "mixed" -> {var crop=new com.google.gson.JsonObject();crop.addProperty("economyVersion",1);lot.add("cropSale",crop);}
            case "seed" -> {lot.addProperty("itemId","nabo");snapshot.getAsJsonArray("seeds").add(lot.deepCopy());}
            case "tile" -> snapshot.getAsJsonArray("tiles").get(0).getAsJsonObject().add("origin",lot.deepCopy());
            case "productId" -> {lot.addProperty("itemId","mel");snapshot.getAsJsonArray("produce").add(lot.deepCopy());}
            case "quantity" -> lot.addProperty("quantity",new BigDecimal("1.1"));
            case "vip" -> lot.addProperty("vipOnly","false");
            default -> throw new AssertionError(corruption);
        }
        Files.writeString(file,json.toString());var bytes=Files.readAllBytes(file);
        assertThrows(java.io.IOException.class,repo::load,corruption);
        assertThrows(java.io.IOException.class,()->repo.save(g.snapshot(),clock.instant(),clock.instant()));
        assertArrayEquals(bytes,Files.readAllBytes(file));
    }
    @Test void newAnimalAndCollectedProductRoundTripWithoutRepricing() throws Exception {
        var clock=new ManualClock();var g=game(clock);
        assertTrue(g.buyAnimal(g.animalType("galinha"),Currency.GREEN).success());
        var origin=g.barn().get(0).origin();
        assertEquals(2,origin.economyVersion());assertNotNull(origin.animalSale());
        var file=dir.resolve("new.json");var repo=new JsonSaveRepository(file);repo.save(g.snapshot(),clock.instant(),clock.instant());
        var restored=GameState.restore(clock::instant,clock,CropCatalogLoader.load(),repo.load().orElseThrow().snapshot(),bound->99);
        assertEquals(origin,restored.barn().get(0).origin());
        clock.advanceSeconds(8*3600);assertTrue(restored.tendAnimal("galinha").success());
        var product=restored.inventory().produceLots().getFirst();assertEquals(origin.animalSale(),product.animalSale());
        repo.save(restored.snapshot(),clock.instant(),clock.instant());
        assertEquals(product,repo.load().orElseThrow().snapshot().produce().getFirst());
    }
    @Test void legacyAcquisitionsAndProductsKeepTheirTermsAfterUpgradeAndCollection() throws Exception {
        var clock=new ManualClock();var g=game(clock);var type=g.animalType("galinha");
        var origin=new ItemLot(type.id(),Currency.GREEN,new BigDecimal("9"),1,false);
        g.barn().add(new Animal(type,origin,clock.instant(),clock.instant(),type.requiredMillis(),clock.instant()));
        g.inventory().addProduceLot(new ItemLot("ovo",Currency.GREEN,new BigDecimal("9"),1,false));
        var file=dir.resolve("old.json");var repo=new JsonSaveRepository(file);
        repo.save(g.snapshot(),clock.instant(),clock.instant());
        var json=JsonParser.parseString(Files.readString(file)).getAsJsonObject();json.addProperty("ruleset","economy-2026-11-plants-v1");
        // Exact historical representation had no animalSale field.
        json.getAsJsonObject("snapshot").getAsJsonArray("animals").get(0).getAsJsonObject().getAsJsonObject("origin").remove("animalSale");
        Files.writeString(file,json.toString());
        var restored=GameState.restore(clock::instant,clock,CropCatalogLoader.load(),repo.load().orElseThrow().snapshot(),bound->99);
        assertTrue(restored.tendAnimal("galinha").success());
        for(var lot:restored.inventory().produceLots())assertEquals(0,new BigDecimal("11.25").compareTo(Market.unitRevenue(lot,true)));
        assertEquals(0,new BigDecimal("20.7").compareTo(restored.depositTotals().get(Currency.GREEN)));
    }
}
