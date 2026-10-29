package br.com.colheitafeliz.render;

import java.util.function.IntBinaryOperator;

/** Limites visíveis e apoio no chão, normalizados para o canvas de uma pose. */
record SpriteFootprint(float left, float bottom, float width, float height, float centerX) {
    static final SpriteFootprint FULL = new SpriteFootprint(0, 0, 1, 1, .5f);

    static SpriteFootprint measure(int width, int height, IntBinaryOperator alpha) {
        int left = width, right = -1, top = height, bottom = -1;
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++)
                if (alpha.applyAsInt(x, y) > 12) {
                    left = Math.min(left, x); right = Math.max(right, x);
                    top = Math.min(top, y); bottom = Math.max(bottom, y);
                }
        if (right < left) return FULL;
        int footLeft = width, footRight = -1;
        int band = Math.max(1, Math.round((bottom - top + 1) * .12f));
        for (int y = bottom - band + 1; y <= bottom; y++)
            for (int x = left; x <= right; x++)
                if (alpha.applyAsInt(x, y) > 12) {
                    footLeft = Math.min(footLeft, x); footRight = Math.max(footRight, x);
                }
        return new SpriteFootprint(left / (float) width, (height - bottom - 1f) / height,
                (right - left + 1f) / width, (bottom - top + 1f) / height,
                (footLeft + footRight + 1f) / (2f * width));
    }
}
