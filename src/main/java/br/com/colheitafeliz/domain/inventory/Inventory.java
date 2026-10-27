package br.com.colheitafeliz.domain.inventory;

import br.com.colheitafeliz.domain.crop.CropType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class Inventory {
    private final List<ItemLot> seedLots = new ArrayList<>();
    private final List<ItemLot> produceLots = new ArrayList<>();
    /** Produtos de animais (ovo, leite...) e ração, ambos pelo id. A ração é do animal: "galinha". */
    private final Map<String, Integer> feed = new HashMap<>();
    private int fertilizer;

    public void addSeedLot(ItemLot lot) { addLot(seedLots, lot); }
    public void addProduceLot(ItemLot lot) { addLot(produceLots, lot); }
    public List<ItemLot> seedLots() { return List.copyOf(seedLots); }
    public List<ItemLot> produceLots() { return List.copyOf(produceLots); }
    public ItemLot takeSeed(String id) {
        for (int i = 0; i < seedLots.size(); i++) {
            ItemLot lot = seedLots.get(i);
            if (!lot.itemId().equals(id)) continue;
            if (lot.quantity() == 1) seedLots.remove(i);
            else seedLots.set(i, lot.withQuantity(lot.quantity() - 1));
            return lot.withQuantity(1);
        }
        throw new IllegalStateException("Insufficient inventory: " + id);
    }
    public void replaceProduceLots(List<ItemLot> remaining) {
        List<ItemLot> checked = List.copyOf(remaining);
        quantities(checked);
        produceLots.clear();
        produceLots.addAll(checked);
    }
    public void restoreLots(List<ItemLot> seeds, List<ItemLot> produce) {
        List<ItemLot> checkedSeeds = List.copyOf(seeds);
        List<ItemLot> checkedProduce = List.copyOf(produce);
        quantities(checkedSeeds);
        quantities(checkedProduce);
        seedLots.clear();
        seedLots.addAll(checkedSeeds);
        produceLots.clear();
        produceLots.addAll(checkedProduce);
    }
    private static void addLot(List<ItemLot> lots, ItemLot lot) {
        Objects.requireNonNull(lot);
        int current = quantity(lots, lot.itemId());
        if (lot.quantity() > Integer.MAX_VALUE - current) throw new ArithmeticException("Inventory quantity overflow");
        lots.add(lot);
    }
    private static int quantity(List<ItemLot> lots, String id) {
        Objects.requireNonNull(id);
        int count = 0;
        for (ItemLot lot : lots) if (lot.itemId().equals(id)) count = Math.addExact(count, lot.quantity());
        return count;
    }
    private static Map<String, Integer> quantities(List<ItemLot> lots) {
        Map<String, Integer> counts = new HashMap<>();
        for (ItemLot lot : lots) counts.merge(lot.itemId(), lot.quantity(), Math::addExact);
        return Map.copyOf(counts);
    }
    public int seeds(CropType type) { return quantity(seedLots, type.id()); }
    public int produce(CropType type) { return quantity(produceLots, type.id()); }
    public Map<String, Integer> seedSnapshot() { return quantities(seedLots); }
    public Map<String, Integer> produceSnapshot() { return quantities(produceLots); }
    public void useSeed(CropType type) { takeSeed(type.id()); }
    public void takeProduce(CropType type, int amount) { takeProduct(type.id(), amount); }
    public void takeProduct(String id, int amount) {
        if (amount <= 0 || amount > product(id)) throw new IllegalStateException("Insufficient inventory: " + id);
        List<ItemLot> remaining = new ArrayList<>();
        int left = amount;
        for (ItemLot lot : produceLots) {
            int take = lot.itemId().equals(id) ? Math.min(left, lot.quantity()) : 0;
            left -= take;
            if (take < lot.quantity()) remaining.add(lot.withQuantity(lot.quantity() - take));
        }
        replaceProduceLots(remaining);
    }
    public int product(String id) { return quantity(produceLots, id); }
    public int feed(String animalId) { return feed.getOrDefault(animalId, 0); }
    public Map<String, Integer> feedSnapshot() { return Map.copyOf(feed); }
    public void addFeed(String animalId, int amount) { add(feed, animalId, amount); }
    public void useFeed(String animalId) { take(feed, animalId, 1); }
    public int fertilizer() { return fertilizer; }
    public void addFertilizer(int amount) {
        if (amount < 0) throw new IllegalArgumentException("Negative inventory amount");
        fertilizer = Math.addExact(fertilizer, amount);
    }
    public void useFertilizer() {
        if (fertilizer == 0) throw new IllegalStateException("Insufficient inventory: fertilizer");
        fertilizer--;
    }
    public void restoreFertilizer(int amount) {
        if (amount < 0) throw new IllegalArgumentException("Negative inventory amount");
        fertilizer = amount;
    }
    public void restoreFeed(Map<String, Integer> feedCounts) {
        Map<String, Integer> checkedFeed = checkedCounts(feedCounts);
        feed.clear();
        feed.putAll(checkedFeed);
    }
    private static Map<String, Integer> checkedCounts(Map<String, Integer> counts) {
        Map<String, Integer> checked = new HashMap<>();
        counts.forEach((id, count) -> add(checked, id, count));
        return checked;
    }
    private static void add(Map<String, Integer> items, String id, int amount) {
        if (id == null || id.isBlank() || amount < 0) throw new IllegalArgumentException("Invalid inventory amount");
        if (amount > 0) items.put(id, Math.addExact(items.getOrDefault(id, 0), amount));
    }
    private static void take(Map<String, Integer> items, String id, int amount) {
        if (amount <= 0 || items.getOrDefault(id, 0) < amount)
            throw new IllegalStateException("Insufficient inventory: " + id);
        items.put(id, items.get(id) - amount);
    }
}
