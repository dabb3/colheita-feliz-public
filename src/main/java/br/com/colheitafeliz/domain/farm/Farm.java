package br.com.colheitafeliz.domain.farm;

import java.time.Instant;

public final class Farm {
    private final FarmTile[][] tiles;

    public Farm(int columns, int rows) {
        if (columns <= 0 || rows <= 0) throw new IllegalArgumentException("Invalid farm size");
        tiles = new FarmTile[rows][columns];
        for (int row = 0; row < rows; row++)
            for (int col = 0; col < columns; col++) tiles[row][col] = new FarmTile();
    }

    public int columns() { return tiles[0].length; }
    public int rows() { return tiles.length; }
    public FarmTile tile(int column, int row) { return tiles[row][column]; }
    public boolean contains(int column, int row) {
        return column >= 0 && column < columns() && row >= 0 && row < rows();
    }
    public void refresh(Instant now) {
        for (FarmTile[] row : tiles) for (FarmTile tile : row) tile.refresh(now);
    }
}
