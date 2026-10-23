package br.com.colheitafeliz.render;

import com.badlogic.gdx.math.Vector2;

public final class IsoRenderer {
    public static final int TILE_WIDTH = 170;
    public static final int TILE_HEIGHT = 78;
    private static final float ORIGIN_X = 585;
    private static final float ORIGIN_Y = 450;

    public Vector2 gridToScreen(int column, int row) {
        return new Vector2(ORIGIN_X + (column - row) * TILE_WIDTH / 2f,
                ORIGIN_Y - (column + row) * TILE_HEIGHT / 2f);
    }

    public GridPosition screenToGrid(float x, float y) {
        float relativeX = (x - ORIGIN_X) / (TILE_WIDTH / 2f);
        float relativeY = (ORIGIN_Y - y) / (TILE_HEIGHT / 2f);
        int column = Math.round((relativeX + relativeY) / 2f);
        int row = Math.round((relativeY - relativeX) / 2f);
        return new GridPosition(column, row);
    }

    public record GridPosition(int column, int row) {}
}
