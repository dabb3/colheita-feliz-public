package br.com.colheitafeliz.domain.crop;

import br.com.colheitafeliz.domain.economy.Currency;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

/**
 * Fertilizante da seção 5: reduz o tempo restante da temporada atual em {@code reductionSeconds}. O do amor
 * ({@code friendsOnly}) só vale na fazenda de amigos.
 */
public record FertilizerType(String id, String name, Map<Currency, BigDecimal> prices, int requiredLevel,
                             long reductionSeconds, boolean friendsOnly) {
    public FertilizerType {
        if (id == null || id.isBlank() || name == null || name.isBlank() || requiredLevel < 1 || reductionSeconds <= 0
                || prices == null || prices.isEmpty() || prices.values().stream().anyMatch(price -> price == null || price.signum() <= 0))
            throw new IllegalArgumentException("Invalid fertilizer: " + id);
        prices = Map.copyOf(new EnumMap<>(prices));
    }

    public Duration reduction() { return Duration.ofSeconds(reductionSeconds); }
}
