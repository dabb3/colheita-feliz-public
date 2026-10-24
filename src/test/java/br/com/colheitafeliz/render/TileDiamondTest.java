package br.com.colheitafeliz.render;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TileDiamondTest {
    /** Losango torto (topo puxado para a direita) dentro de uma imagem 100 × 60 com sobra transparente. */
    @Test void findsTheFourCornersOfASkewedDiamondWithMargins() {
        int[][] corners = {{10, 30}, {40, 50}, {90, 30}, {60, 10}};
        float[] uv = TileDiamond.corners(100, 60, (x, y) -> inside(corners, x + .5f, y + .5f) ? 255 : 0);
        float[] expected = {.10f, .50f, .40f, .84f, .90f, .50f, .60f, .16f};
        for (int i = 0; i < 8; i++) assertEquals(expected[i], uv[i], .04f, "coordenada " + i);
    }

    @Test void rejectsAnEmptyImage() {
        assertThrows(IllegalArgumentException.class, () -> TileDiamond.corners(8, 8, (x, y) -> 0));
    }

    private static boolean inside(int[][] p, float x, float y) {
        boolean in = false;
        for (int i = 0, j = p.length - 1; i < p.length; j = i++)
            if ((p[i][1] > y) != (p[j][1] > y)
                    && x < (p[j][0] - p[i][0]) * (y - p[i][1]) / (float) (p[j][1] - p[i][1]) + p[i][0]) in = !in;
        return in;
    }
}
