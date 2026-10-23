package br.com.colheitafeliz.game;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Keep the left starting column, then purchase the remaining field left to right and top to bottom. */
public final class LandExpansion {
    public record Plot(int id, int column, int row, int requiredLevel, int price) {}

    private static final List<int[]> COORDINATES = List.of(
            new int[]{0, 0}, new int[]{0, 1}, new int[]{0, 2}, new int[]{0, 3}, new int[]{0, 4},
            new int[]{1, 0}, new int[]{2, 0}, new int[]{3, 0}, new int[]{4, 0}, new int[]{5, 0},
            new int[]{1, 1}, new int[]{2, 1}, new int[]{3, 1}, new int[]{4, 1}, new int[]{5, 1},
            new int[]{1, 2}, new int[]{2, 2}, new int[]{3, 2}, new int[]{4, 2}, new int[]{5, 2},
            new int[]{1, 3}, new int[]{2, 3}, new int[]{3, 3}, new int[]{4, 3}, new int[]{5, 3},
            new int[]{1, 4}, new int[]{2, 4}, new int[]{3, 4}, new int[]{4, 4}, new int[]{5, 4}
    );

    private final Set<Integer> unlocked = new TreeSet<>();

    public LandExpansion() {
        for (int id = 0; id < 5; id++) unlocked.add(id);
    }

    public boolean isUnlocked(int column, int row) {
        Plot plot = at(column, row);
        return plot != null && unlocked.contains(plot.id());
    }

    public Plot at(int column, int row) {
        for (int id = 0; id < COORDINATES.size(); id++) {
            int[] coordinate = COORDINATES.get(id);
            if (coordinate[0] == column && coordinate[1] == row) return describe(id);
        }
        return null;
    }

    public Plot next() {
        for (int id = 0; id < COORDINATES.size(); id++)
            if (!unlocked.contains(id)) return describe(id);
        return null;
    }

    public void purchase(int plotId) {
        Plot next = next();
        if (next == null || next.id() != plotId) throw new IllegalStateException("Plots must be purchased in order");
        unlocked.add(plotId);
    }

    public List<Integer> snapshot() { return List.copyOf(unlocked); }

    public void restore(List<Integer> plotIds) {
        if (plotIds == null) {
            unlocked.clear();
            for (int id = 0; id < COORDINATES.size(); id++) unlocked.add(id);
            return;
        }
        TreeSet<Integer> restored = new TreeSet<>(plotIds);
        if (restored.size() < 5 || restored.size() > COORDINATES.size()
                || restored.first() != 0 || restored.last() >= COORDINATES.size())
            throw new IllegalArgumentException("Invalid unlocked plot list");
        for (int id = 0; id < restored.size(); id++)
            if (!restored.contains(id)) throw new IllegalArgumentException("Plot unlocks are not sequential");
        unlocked.clear();
        unlocked.addAll(restored);
    }

    private static Plot describe(int id) {
        int[] coordinate = COORDINATES.get(id);
        int level = id < 5 ? 1 : id < 10 ? 2 : id < 15 ? 3 : id < 20 ? 4 : 5;
        int price = id < 5 ? 0 : id < 10 ? 75 : id < 15 ? 200 : id < 20 ? 500 : 1000;
        return new Plot(id, coordinate[0], coordinate[1], level, price);
    }
}
