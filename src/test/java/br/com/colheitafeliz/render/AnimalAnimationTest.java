package br.com.colheitafeliz.render;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AnimalAnimationTest {
    @Test void pigeonHiveAndPeacockNeverRoam() {
        for (String id : new String[]{"pombo", "abelha", "pavao"}) assertFalse(Scenery.roams(id));
        assertTrue(Scenery.roams("cavalo"));
    }

    @Test void spritesFaceTheDirectionOfTravelRegardlessOfTheirOriginalDirection() {
        assertFalse(Scenery.flipSprite("cavalo", true));
        assertTrue(Scenery.flipSprite("cavalo", false));
        assertFalse(Scenery.flipSprite("cachorro-robo", true));
        assertFalse(Scenery.flipSprite("cabra", true));
        assertFalse(Scenery.flipSprite("vaca", false));
        assertTrue(Scenery.flipSprite("vaca", true));
    }

    @Test void footstepsDependOnDistanceAndPeacockAlternatesInPlace() {
        assertFalse(Scenery.alternatePose("cavalo", false, 8, 10));
        assertEquals(Scenery.alternatePose("cavalo", true, 8, 0), Scenery.alternatePose("cavalo", true, 8, 99));
        assertNotEquals(Scenery.alternatePose("cavalo", true, 0, 0), Scenery.alternatePose("cavalo", true, 6, 0));
        assertFalse(Scenery.alternatePose("pombo", true, 6, 9));
        assertFalse(Scenery.alternatePose("pavao", false, 0, 0));
        assertTrue(Scenery.alternatePose("pavao", false, 0, 4));
        assertFalse(Scenery.alternatePose("pavao", false, 0, 8));
        assertTrue(GameAssets.animalPath("pavao", br.com.colheitafeliz.domain.animal.Animal.Stage.ADULT,
                GameAssets.AnimalPose.WALKING).endsWith("pavao-aberto.png"));
    }
}
