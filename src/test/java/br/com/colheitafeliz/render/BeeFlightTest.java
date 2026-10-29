package br.com.colheitafeliz.render;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BeeFlightTest {
    @Test void beesMoveWithinASmallAreaAroundTheHive() {
        for (int bee = 0; bee < 4; bee++) {
            var start = Scenery.beeOffset(bee, 0);
            assertNotEquals(start, Scenery.beeOffset(bee, 1));
            for (int second = 0; second < 600; second++) {
                var offset = Scenery.beeOffset(bee, second);
                assertTrue(Math.abs(offset.x()) <= 28, "Voo horizontal limitado");
                assertTrue(Math.abs(offset.y()) <= 12, "Voo vertical limitado");
            }
        }
        assertNotEquals(Scenery.beeOffset(0, 1), Scenery.beeOffset(1, 1));
    }
}
