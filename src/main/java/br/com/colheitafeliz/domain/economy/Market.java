package br.com.colheitafeliz.domain.economy;

import br.com.colheitafeliz.domain.crop.CropType;
import br.com.colheitafeliz.domain.inventory.Inventory;
import br.com.colheitafeliz.domain.inventory.ItemLot;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.EnumMap;
import java.util.List;
import java.util.ArrayList;

public final class Market {
    private final Wallet wallet;
    private final Inventory inventory;

    public Market(Wallet wallet, Inventory inventory) {
        this.wallet = wallet;
        this.inventory = inventory;
    }

    public Map<Currency, BigDecimal> sellEverything(boolean vipActive) {
        return sellLots(null, Integer.MAX_VALUE, vipActive, true);
    }

    public Map<Currency, BigDecimal> sellProduce(String id, int quantity, boolean vipActive) {
        if (id == null || id.isBlank() || quantity <= 0) throw new IllegalArgumentException("Invalid sale");
        return sellLots(id, quantity, vipActive, true);
    }

    public Map<Currency, BigDecimal> quoteEverything(boolean vipActive) {
        return sellLots(null, Integer.MAX_VALUE, vipActive, false);
    }
    public Map<Currency, BigDecimal> quoteProduce(String id, int quantity, boolean vipActive) {
        if (id == null || id.isBlank() || quantity <= 0) throw new IllegalArgumentException("Invalid sale");
        return sellLots(id, quantity, vipActive, false);
    }

    private Map<Currency, BigDecimal> sellLots(String id, int quantity, boolean vipActive, boolean apply) {
        Map<Currency, BigDecimal> totals = new EnumMap<>(Currency.class);
        List<ItemLot> remaining = new ArrayList<>();
        int left = quantity;
        boolean sold = false;
        BigDecimal multiplier = new BigDecimal(vipActive ? "1.25" : "1.15");
        for (ItemLot lot : inventory.produceLots()) {
            if ((id != null && left == 0) || (id != null && !id.equals(lot.itemId())) || (lot.vipOnly() && !vipActive)) {
                remaining.add(lot);
                continue;
            }
            int count = id == null ? lot.quantity() : Math.min(left, lot.quantity());
            BigDecimal total = lot.unitCost().multiply(multiplier).multiply(BigDecimal.valueOf(count));
            totals.merge(lot.currency(), total, BigDecimal::add);
            if (id != null) left -= count;
            sold = true;
            if (count < lot.quantity()) remaining.add(lot.withQuantity(lot.quantity() - count));
        }
        if (apply && (!sold || (id != null && left != 0))) throw new IllegalStateException("No eligible produce to sell");
        totals.replaceAll((currency, total) -> total.setScale(1, RoundingMode.CEILING));
        if (apply) {
            totals.forEach(wallet::credit);
            inventory.replaceProduceLots(remaining);
        }
        return Map.copyOf(totals);
    }

    public void buySeed(CropType type) {
        Math.addExact(inventory.seeds(type), 1);
        wallet.debit(type.seedPrice());
        inventory.addSeeds(type, 1);
    }

    public void sellProduce(CropType type) {
        sellProduce(type, 1);
    }

    public void sellProduce(CropType type, int amount) {
        sellProduce(type.id(), amount, false);
    }
}
