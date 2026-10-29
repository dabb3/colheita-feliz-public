package br.com.colheitafeliz.domain.animal;

import br.com.colheitafeliz.domain.economy.Currency;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;

public record AnimalType(String id, String name, Kind kind, Map<Currency, BigDecimal> prices,
                         int requiredLevel, int max, long productionSeconds, long babySeconds,
                         long youngSeconds, String productId, String productName, int collectXp) {
    public enum Kind { PRODUCER, GROWING, PROTECTOR }
    public AnimalType {
        if (id == null || id.isBlank() || name == null || name.isBlank() || kind == null
                || prices == null || prices.isEmpty() || requiredLevel < 1 || max < 1 || collectXp < 0
                || productionSeconds < 0 || babySeconds < 0 || youngSeconds < 0
                || productId == null || productName == null)
            throw new IllegalArgumentException("Invalid animal definition");
        prices = Map.copyOf(prices);
        if (prices.values().stream().anyMatch(p -> p == null || p.signum() <= 0 || p.stripTrailingZeros().scale() > 1)
                || (kind == Kind.PRODUCER && (productionSeconds == 0 || babySeconds != 0 || youngSeconds != 0 || productId.isBlank()))
                || (kind == Kind.GROWING && (productionSeconds != 0 || babySeconds == 0 || youngSeconds == 0))
                || (kind == Kind.PROTECTOR && (productionSeconds != 0 || babySeconds != 0 || youngSeconds != 0 || collectXp != 0)))
            throw new IllegalArgumentException("Invalid animal timings or prices");
    }
    public int price() { return prices.get(Currency.GOLD).intValueExact(); }
    public String feedId() { return kind == Kind.PRODUCER ? "producer" : "growth"; }
    public int feedPrice() { return kind == Kind.PRODUCER ? 120 : 80; }
    public int productYield() { return kind == Kind.PRODUCER ? 1 : 0; }
    public Duration productionTime() { return Duration.ofSeconds(productionSeconds); }
    public Duration foodDuration() { return Duration.ofHours(kind == Kind.PRODUCER ? 8 : kind == Kind.GROWING ? 12 : 6); }
    public long requiredMillis() { return Math.multiplyExact(kind == Kind.GROWING ? Math.addExact(babySeconds, youngSeconds) : productionSeconds, 1000); }
}
