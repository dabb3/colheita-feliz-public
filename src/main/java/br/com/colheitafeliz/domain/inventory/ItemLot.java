package br.com.colheitafeliz.domain.inventory;

import br.com.colheitafeliz.domain.economy.Currency;
import java.math.BigDecimal;

public record ItemLot(String itemId, Currency currency, BigDecimal unitCost, int quantity, boolean vipOnly) {
    public ItemLot {
        if (itemId == null || itemId.isBlank() || currency == null || unitCost == null
                || unitCost.signum() <= 0 || quantity <= 0)
            throw new IllegalArgumentException("Invalid item lot");
        unitCost = unitCost.stripTrailingZeros();
        if (unitCost.scale() > 1) throw new IllegalArgumentException("Invalid monetary precision");
    }

    public ItemLot withQuantity(int amount) { return new ItemLot(itemId, currency, unitCost, amount, vipOnly); }
}
