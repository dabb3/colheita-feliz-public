package br.com.colheitafeliz.render;

import com.badlogic.gdx.math.Vector2;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IsoRendererTest {
    @Test void mapsTileCentersAndDiamondEdges() {
        IsoRenderer iso = new IsoRenderer();
        for (int row = 0; row < 5; row++) {
            for (int col = 0; col < 6; col++) {
                Vector2 center = iso.gridToScreen(col, row);
                assertEquals(new IsoRenderer.GridPosition(col, row), iso.screenToGrid(center.x, center.y));
                assertEquals(new IsoRenderer.GridPosition(col, row), iso.screenToGrid(center.x + 15, center.y + 5));
            }
        }
    }
}
