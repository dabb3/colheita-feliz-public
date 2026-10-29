package br.com.colheitafeliz.render;

import com.badlogic.gdx.math.Vector2;
import br.com.colheitafeliz.game.GameState;
import br.com.colheitafeliz.ui.Toolbar;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IsoRendererTest {
    @Test void fullFarmClearsToolbarAndTileEdgesRemainClickable() {
        IsoRenderer iso = new IsoRenderer();
        for (int row = 0; row < GameState.ROWS; row++) {
            for (int col = 0; col < GameState.COLUMNS; col++) {
                Vector2 center = iso.gridToScreen(col, row);
                assertTrue(center.y - IsoRenderer.TILE_HEIGHT / 2f >= Toolbar.Y + Toolbar.HEIGHT + 24,
                        "Terreno " + col + "," + row + " sobrepõe a barra inferior");
                var expected = new IsoRenderer.GridPosition(col, row);
                for (float direction : new float[] {-1, 1}) {
                    assertEquals(expected, iso.screenToGrid(center.x + direction * (IsoRenderer.TILE_WIDTH / 2f - 2), center.y));
                    assertEquals(expected, iso.screenToGrid(center.x, center.y + direction * (IsoRenderer.TILE_HEIGHT / 2f - 2)));
                }
            }
        }
    }

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
