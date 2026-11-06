package br.com.colheitafeliz.domain.animal;

import br.com.colheitafeliz.domain.economy.Currency;
import br.com.colheitafeliz.domain.economy.AnimalSale;
import br.com.colheitafeliz.domain.inventory.ItemLot;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;

public record AnimalType(String id, String name, Kind kind, Map<Currency, BigDecimal> prices,
                         int requiredLevel, int max, long productionSeconds, long babySeconds,
                         long youngSeconds, String productId, String productName, int collectXp,
                         Map<Currency, SalePrices> salePrices) {
    public record SalePrices(long commonTenths, long vipTenths) {
        public SalePrices {
            if (commonTenths <= 0 || vipTenths < commonTenths) throw new IllegalArgumentException("Invalid animal prices");
        }
    }
    public enum Kind { PRODUCER, GROWING, PROTECTOR }
    public AnimalType {
        if (id == null || id.isBlank() || name == null || name.isBlank() || kind == null
                || prices == null || prices.isEmpty() || requiredLevel < 1 || max < 1 || collectXp < 0
                || productionSeconds < 0 || babySeconds < 0 || youngSeconds < 0
                || productId == null || productName == null)
            throw new IllegalArgumentException("Invalid animal definition");
        prices = Map.copyOf(prices);
        salePrices = salePrices == null ? Map.of() : Map.copyOf(salePrices);
        if (kind == Kind.PROTECTOR ? !salePrices.isEmpty() : !salePrices.keySet().equals(prices.keySet()))
            throw new IllegalArgumentException("Missing or unexpected animal sale prices");
        if (prices.values().stream().anyMatch(p -> p == null || p.signum() <= 0 || p.stripTrailingZeros().scale() > 1)
                || (kind == Kind.PRODUCER && (productionSeconds == 0 || babySeconds != 0 || youngSeconds != 0 || productId.isBlank()))
                || (kind == Kind.GROWING && (productionSeconds != 0 || babySeconds == 0 || youngSeconds == 0))
                || (kind == Kind.PROTECTOR && (productionSeconds != 0 || babySeconds != 0 || youngSeconds != 0 || collectXp != 0)))
            throw new IllegalArgumentException("Invalid animal timings or prices");
    }
    public AnimalSale saleTerms(Currency currency) {
        if (kind == Kind.PROTECTOR) return null;
        var price = salePrices.get(currency);
        if (price == null) throw new IllegalArgumentException("Unsupported animal currency");
        return new AnimalSale(2, id, price.commonTenths(), price.vipTenths());
    }
    public void validateSaleTerms(ItemLot lot, boolean product) {
        if (lot.cropSale() != null) throw new IllegalArgumentException("Animal cannot have crop terms");
        if (lot.animalSale() == null) return; // Historical acquisition/product, never reprice.
        if (kind == Kind.PROTECTOR || (product && kind != Kind.PRODUCER)
                || !(product ? productId : id).equals(lot.itemId()) || lot.vipOnly()
                || !prices.containsKey(lot.currency()) || prices.get(lot.currency()).compareTo(lot.unitCost()) != 0
                || !saleTerms(lot.currency()).equals(lot.animalSale()))
            throw new IllegalArgumentException("Inconsistent animal sale metadata");
    }
    public int price() { return prices.get(Currency.GOLD).intValueExact(); }
    public String feedId() { return kind == Kind.PRODUCER ? "producer" : "growth"; }
    public int feedPrice() { return kind == Kind.PRODUCER ? 120 : 80; }
    public int productYield() { return kind == Kind.PRODUCER ? 1 : 0; }
    public Duration productionTime() { return Duration.ofSeconds(productionSeconds); }
    public Duration foodDuration() { return Duration.ofHours(kind == Kind.PRODUCER ? 8 : kind == Kind.GROWING ? 12 : 6); }
    public long requiredMillis() { return Math.multiplyExact(kind == Kind.GROWING ? Math.addExact(babySeconds, youngSeconds) : productionSeconds, 1000); }
}
