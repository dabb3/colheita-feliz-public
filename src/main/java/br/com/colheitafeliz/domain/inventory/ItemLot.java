package br.com.colheitafeliz.domain.inventory;

import br.com.colheitafeliz.domain.economy.Currency;
import br.com.colheitafeliz.domain.economy.CropSale;
import java.math.BigDecimal;

public record ItemLot(String itemId, Currency currency, BigDecimal unitCost, int quantity, boolean vipOnly,
                      CropSale cropSale, Integer economyVersion) {
    /** Missing terms explicitly retain the pre-v1 price rule, including animal products. */
    public ItemLot(String itemId, Currency currency, BigDecimal unitCost, int quantity, boolean vipOnly) {
        this(itemId, currency, unitCost, quantity, vipOnly, null, 0);
    }
    public ItemLot(String itemId, Currency currency, BigDecimal unitCost, int quantity, boolean vipOnly, CropSale cropSale) {
        this(itemId, currency, unitCost, quantity, vipOnly, cropSale, cropSale == null ? 0 : cropSale.economyVersion());
    }
    public ItemLot {
        if (itemId == null || itemId.isBlank() || currency == null || unitCost == null
                || unitCost.signum() <= 0 || quantity <= 0)
            throw new IllegalArgumentException("Invalid item lot");
        unitCost = unitCost.stripTrailingZeros();
        if (unitCost.scale() > 1) throw new IllegalArgumentException("Invalid monetary precision");
        // Draft v1 saves had only the nested version; legacy saves have neither marker.
        if (economyVersion == null) economyVersion = cropSale == null ? 0 : cropSale.economyVersion();
        if (economyVersion != (cropSale == null ? 0 : cropSale.economyVersion()))
            throw new IllegalArgumentException("Inconsistent economy version marker");
        if (vipOnly && cropSale != null && cropSale.cycleVipBonusTenths() != 0)
            throw new IllegalArgumentException("VIP-only crops cannot receive an extra bonus");
    }

    public ItemLot withQuantity(int amount) { return new ItemLot(itemId, currency, unitCost, amount, vipOnly, cropSale); }
    public ItemLot withCropSale(CropSale terms) {
        return new ItemLot(itemId, currency, unitCost, quantity, vipOnly, terms);
    }
    public ItemLot harvested(int season) {
        return cropSale == null ? this : withCropSale(cropSale.forSeason(season));
    }
}
