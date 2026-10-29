package br.com.colheitafeliz.render;

import br.com.colheitafeliz.game.AnimalCatalogLoader;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AnimalLayoutTest {
    @Test void onlyTheFiveFarmAnimalsBelongInsideThePen() {
        Set<String> inside = Set.of("porco", "ovelha", "vaca", "galinha", "pato");
        Pen pen = new Pen(null, null, null);
        for (var type : AnimalCatalogLoader.load()) {
            var placement = AnimalLayout.forType(type.id());
            assertEquals(inside.contains(type.id()), placement.insidePen(), type.id());
            assertEquals(inside.contains(type.id()), pen.inside(placement.x(), placement.y(), placement.width() * .45f), type.id());
            assertTrue(placement.x() > placement.width() / 2 && placement.x() < 1600 - placement.width() / 2, type.id());
            assertTrue(placement.y() >= 0 && placement.y() < 900, type.id());
        }
    }

    @Test void pigeonIsAboveTheLargerHiveAndHousesKeepTheirAnimalsNearby() {
        var hive = AnimalLayout.forType("abelha");
        var pigeon = AnimalLayout.forType("pombo");
        assertTrue(hive.width() >= 150);
        assertTrue(pigeon.y() > hive.y() + 100);
        assertTrue(pigeon.x() > hive.x());
        assertTrue(AnimalLayout.forType("burro").x() < 500);
        assertTrue(AnimalLayout.forType("cabra").x() < 500);
        assertTrue(AnimalLayout.forType("cachorro-robo").y() > 400);
        assertThrows(IllegalArgumentException.class, () -> AnimalLayout.forType("cachorro"));
    }
}
