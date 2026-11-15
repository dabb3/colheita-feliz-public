package br.com.colheitafeliz.domain.inventory;

import br.com.colheitafeliz.domain.economy.Currency;
import br.com.colheitafeliz.domain.economy.CropSale;
import br.com.colheitafeliz.domain.economy.AnimalSale;
import java.math.BigDecimal;

public record ItemLot(String itemId, Currency currency, BigDecimal unitCost, int quantity, boolean vipOnly,
                      CropSale cropSale, Integer economyVersion, AnimalSale animalSale,
                      AcquisitionSource acquisitionSource) {
    /** Missing terms explicitly retain the pre-v1 price rule, including animal products. */
    public ItemLot(String itemId, Currency currency, BigDecimal unitCost, int quantity, boolean vipOnly) {
        this(itemId, currency, unitCost, quantity, vipOnly, null, 0, null, AcquisitionSource.SHOP);
    }
    public ItemLot(String itemId, Currency currency, BigDecimal unitCost, int quantity, boolean vipOnly, CropSale cropSale) {
        this(itemId, currency, unitCost, quantity, vipOnly, cropSale, cropSale == null ? 0 : cropSale.economyVersion(), null, AcquisitionSource.SHOP);
    }
    public ItemLot(String itemId, Currency currency, BigDecimal unitCost, int quantity, boolean vipOnly,
                   CropSale cropSale, Integer economyVersion) {
        this(itemId, currency, unitCost, quantity, vipOnly, cropSale, economyVersion, null, AcquisitionSource.SHOP);
    }
    public ItemLot(String itemId, Currency currency, BigDecimal unitCost, int quantity, boolean vipOnly,
                   CropSale cropSale, Integer economyVersion, AnimalSale animalSale) {
        this(itemId, currency, unitCost, quantity, vipOnly, cropSale, economyVersion, animalSale, AcquisitionSource.SHOP);
    }
    public ItemLot(String itemId, Currency currency, BigDecimal unitCost, int quantity, boolean vipOnly,
                   AcquisitionSource acquisitionSource) {
        this(itemId, currency, unitCost, quantity, vipOnly, null, 0, null, acquisitionSource);
    }
    public ItemLot {
        if (acquisitionSource == null) acquisitionSource = AcquisitionSource.SHOP;
        if (itemId == null || itemId.isBlank() || currency == null || unitCost == null || quantity <= 0
                || (acquisitionSource == AcquisitionSource.SHOP
                ? unitCost.signum() <= 0 : unitCost.signum() != 0 || currency != Currency.GREEN || vipOnly))
            throw new IllegalArgumentException("Invalid item lot");
        unitCost = unitCost.stripTrailingZeros();
        if (unitCost.scale() > 1) throw new IllegalArgumentException("Invalid monetary precision");
        // Draft v1 saves had only the nested version; legacy saves have neither marker.
        if (cropSale != null && animalSale != null) throw new IllegalArgumentException("Mixed sale terms");
        if (acquisitionSource == AcquisitionSource.LOGIN_REWARD && animalSale != null)
            throw new IllegalArgumentException("Login reward cannot contain animal sale terms");
        int expected = animalSale != null ? animalSale.economyVersion() : cropSale == null ? 0 : cropSale.economyVersion();
        if (economyVersion == null) economyVersion = expected;
        if (economyVersion != expected || (animalSale != null && vipOnly))
            throw new IllegalArgumentException("Inconsistent economy version marker");
        if (vipOnly && cropSale != null && cropSale.cycleVipBonusTenths() != 0)
            throw new IllegalArgumentException("VIP-only crops cannot receive an extra bonus");
    }

    public ItemLot withQuantity(int amount) { return new ItemLot(itemId, currency, unitCost, amount, vipOnly, cropSale, economyVersion, animalSale, acquisitionSource); }
    public ItemLot withAnimalSale(AnimalSale terms) {
        return new ItemLot(itemId, currency, unitCost, quantity, vipOnly, null, terms == null ? 0 : 2, terms, acquisitionSource);
    }
    public ItemLot withCropSale(CropSale terms) {
        return new ItemLot(itemId, currency, unitCost, quantity, vipOnly, terms, terms == null ? 0 : terms.economyVersion(), null, acquisitionSource);
    }
    public ItemLot harvested(int season) {
        return cropSale == null ? this : withCropSale(cropSale.forSeason(season));
    }
}
