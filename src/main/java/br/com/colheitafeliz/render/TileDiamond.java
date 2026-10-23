package br.com.colheitafeliz.render;

import java.util.function.IntBinaryOperator;

/**
 * Os 4 vértices do losango desenhado numa imagem de chão. As artes vêm com sobra transparente em volta e o
 * losango torto; desenhar cada vértice no vértice da célula corrige os dois.
 */
final class TileDiamond {
    private static final int OPAQUE = 200, BAND = 2;

    private TileDiamond() {}

    /**
     * Vértices em coordenadas de textura (0 a 1, v para baixo): esquerda, baixo, direita e cima, nessa ordem,
     * como pares {u, v}. {@code alpha} devolve a opacidade (0–255) do pixel (x, y).
     */
    static float[] corners(int width, int height, IntBinaryOperator alpha) {
        int minX = width, maxX = -1, minY = height, maxY = -1;
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++)
                if (alpha.applyAsInt(x, y) > OPAQUE) {
                    minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                }
        if (maxX < 0) throw new IllegalArgumentException("Imagem de chão sem pixels opacos");
        long[] sum = new long[4];
        int[] count = new int[4];
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++) {
                if (alpha.applyAsInt(x, y) <= OPAQUE) continue;
                if (x <= minX + BAND) { sum[0] += y; count[0]++; }
                if (y >= maxY - BAND) { sum[1] += x; count[1]++; }
                if (x >= maxX - BAND) { sum[2] += y; count[2]++; }
                if (y <= minY + BAND) { sum[3] += x; count[3]++; }
            }
        return new float[]{
                minX / (float) width, sum[0] / (float) count[0] / height,
                sum[1] / (float) count[1] / width, (maxY + 1) / (float) height,
                (maxX + 1) / (float) width, sum[2] / (float) count[2] / height,
                sum[3] / (float) count[3] / width, minY / (float) height};
    }
}
