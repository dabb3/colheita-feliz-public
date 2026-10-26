package br.com.colheitafeliz.domain.crop;

import br.com.colheitafeliz.domain.economy.Currency;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

/**
 * Uma cultura do catálogo. A primeira temporada dura {@code growthSeconds}; as seguintes, {@code regrowthSeconds}.
 * O XP de cada colheita sai da fórmula da regra (horas da primeira temporada × 1,5, metade a cada temporada).
 */
public record CropType(String id, String name, int seedPrice, int salePrice,
                       long growthSeconds, int stages, String spriteStem, int requiredLevel,
                       int harvestYield, int harvestXp, long regrowthSeconds, int seasons, boolean vipOnly,
                       Map<Currency, BigDecimal> seedPrices) {
    private static final BigDecimal SECONDS_PER_HOUR = BigDecimal.valueOf(3600);
    private static final BigDecimal XP_PER_HOUR = new BigDecimal("1.5");

    public CropType {
        if (id == null || id.isBlank() || name == null || name.isBlank()
                || spriteStem == null || spriteStem.isBlank()
                || seedPrice <= 0 || salePrice <= 0 || growthSeconds <= 0 || stages < 2
                || requiredLevel < 1 || harvestYield < 1 || harvestXp < 0
                || seasons < 1 || (seasons == 1) != (regrowthSeconds == 0) || regrowthSeconds < 0)
            throw new IllegalArgumentException("Invalid crop definition");
        if (seedPrices == null || seedPrices.isEmpty() || seedPrices.values().stream()
                .anyMatch(price -> price == null || price.signum() <= 0))
            throw new IllegalArgumentException("Invalid seed prices: " + id);
        seedPrices = Map.copyOf(new EnumMap<>(seedPrices));
        try { firstHarvestXp(growthSeconds); }
        catch (ArithmeticException e) { throw new IllegalArgumentException("Growth time without exact XP: " + id, e); }
    }

    private static BigDecimal firstHarvestXp(long growthSeconds) {
        return BigDecimal.valueOf(growthSeconds).multiply(XP_PER_HOUR).divide(SECONDS_PER_HOUR);
    }

    public Duration growthTime(int season) {
        requireSeason(season);
        return Duration.ofSeconds(season == 1 ? growthSeconds : regrowthSeconds);
    }

    /** XP exato da colheita da temporada, sem arredondar. */
    public BigDecimal harvestXp(int season) {
        requireSeason(season);
        return firstHarvestXp(growthSeconds).divide(BigDecimal.valueOf(2).pow(season - 1));
    }

    private void requireSeason(int season) {
        if (season < 1 || season > seasons) throw new IllegalArgumentException("Invalid season " + season + " for " + id);
    }
}
