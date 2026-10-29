package br.com.colheitafeliz.render;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpriteFootprintTest {
    @Test void sleepingPoseUsesItsOwnVisibleBaseInsteadOfTheSharedCanvasBottom() {
        var awake = SpriteFootprint.measure(10, 10, (x, y) -> x >= 2 && x <= 7 && y >= 1 && y <= 8 ? 255 : 0);
        var asleep = SpriteFootprint.measure(10, 10, (x, y) -> x >= 1 && x <= 8 && y >= 2 && y <= 4 ? 255 : 0);
        assertEquals(.1f, awake.bottom(), .001f);
        assertEquals(.5f, asleep.bottom(), .001f);
        assertEquals(.3f, asleep.height(), .001f);
        assertEquals(.8f, asleep.width(), .001f);
        assertEquals(.5f, asleep.centerX(), .001f);
        assertEquals(0, -100 * asleep.bottom() + 100 * .5f, .001f, "A base visível toca o chão");
    }

    @Test void feetDetermineHorizontalSupportAndInvisibleResidueIsIgnored() {
        var footprint = SpriteFootprint.measure(10, 10, (x, y) -> y == 9 ? 8 : y == 7 && x >= 6 && x <= 8 ? 255 : 0);
        assertEquals(.2f, footprint.bottom(), .001f);
        assertEquals(.75f, footprint.centerX(), .001f);
        var empty = SpriteFootprint.measure(10, 10, (x, y) -> 0);
        assertEquals(.5f, empty.centerX());
        assertEquals(0, empty.bottom());
    }
}
