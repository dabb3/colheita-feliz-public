package br.com.colheitafeliz.domain.farm;

import java.time.Instant;
import java.util.Objects;

/**
 * Sorteia as duas pragas, de forma independente. Ervas daninhas aparecem do estágio 3 em diante; insetos, do
 * estágio 2 em diante, e se multiplicam a cada intervalo. As duas pausam o crescimento sem reduzir a colheita.
 * Cada praga tem cerca de 25% de chance ao longo da sua janela vulnerável, medida na temporada atual.
 */
public final class PestSystem {
    public record Outbreak(boolean weeds, boolean insects) {
        public boolean any() { return weeds || insects; }
    }

    private final RandomSource random;

    public PestSystem(RandomSource random) { this.random = Objects.requireNonNull(random); }

    public Outbreak update(Farm farm, Instant now) {
        boolean weeds = false, insects = false;
        for (int row = 0; row < farm.rows(); row++) for (int col = 0; col < farm.columns(); col++) {
            FarmTile tile = farm.tile(col, row);
            if (tile.state() != TileState.GROWING || tile.crop() == null || !tile.crop().watered()) continue;
            long interval = FarmTile.pestCheckInterval(tile.crop()).toSeconds();
            long season = tile.crop().currentDuration().toSeconds();
            int stage = tile.crop().stageAt(now);
            if (tile.pest() == PestState.NONE && stage >= 3 && tile.nextPestCheckAt() != null
                    && !now.isBefore(tile.nextPestCheckAt())) {
                boolean infected = roll() < chance(interval, season / 2.0);
                tile.checkPest(now, infected);
                weeds |= infected;
            }
            if (tile.nextInsectCheckAt() != null && !now.isBefore(tile.nextInsectCheckAt())) {
                if (tile.insects() > 0) tile.worsenInsects(now);
                else if (stage >= 2) {
                    boolean infected = roll() < chance(interval, season * .75);
                    tile.checkInsects(now, infected);
                    insects |= infected;
                }
            }
        }
        return new Outbreak(weeds, insects);
    }

    private int roll() {
        int roll = random.nextInt(100);
        if (roll < 0 || roll >= 100) throw new IllegalStateException("Invalid random value");
        return roll;
    }

    private static double chance(long interval, double vulnerableSeconds) {
        return (1 - Math.pow(0.75, interval / vulnerableSeconds)) * 100;
    }
}
