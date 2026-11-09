package br.com.colheitafeliz.game;

import br.com.colheitafeliz.domain.animal.AnimalType;
import br.com.colheitafeliz.domain.economy.Currency;
import br.com.colheitafeliz.testsupport.ManualClock;
import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DogReplacementTest {
    @Test void normalDogRetainsExistingAcquisitionAndAllProtectorRules(){
        var clock=new ManualClock();var catalog=CropCatalogLoader.load();
        var game=new GameState(clock::instant,clock,catalog,bound->bound-1);
        while(game.progress().level()<10)game.progress().award(game.progress().xpToNext());
        var dog=game.animalType("cachorro-robo");
        assertEquals("Cachorro",dog.name());assertEquals(AnimalType.Kind.PROTECTOR,dog.kind());
        assertEquals(new BigDecimal("20000"),dog.prices().get(Currency.GOLD));assertEquals(new BigDecimal("15"),dog.prices().get(Currency.GREEN));
        assertEquals(10,dog.requiredLevel());assertEquals(1,dog.max());assertEquals(Duration.ofHours(6),dog.foodDuration());
        game.wallet().credit(20000);assertTrue(game.buyAnimal(dog,Currency.GOLD).success());
        var before=game.snapshot();var restored=GameState.restore(clock::instant,clock,catalog,before,bound->bound-1);
        assertEquals(before,restored.snapshot());
        var owned=restored.barn().get(0);assertEquals("Cachorro",owned.type().name());assertEquals("cachorro-robo",owned.origin().itemId());
        assertEquals(Duration.ofDays(30),Duration.between(owned.acquiredAt(),owned.expiresAt()));
        assertEquals(1,restored.activeProtectors());assertEquals(1,restored.protectedPlots().size());
        assertEquals(5,restored.animalTypes().stream().filter(t->t.kind()==AnimalType.Kind.PROTECTOR).count());
    }
}
