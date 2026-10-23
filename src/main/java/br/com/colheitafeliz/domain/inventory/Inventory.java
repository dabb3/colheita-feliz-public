package br.com.colheitafeliz.domain.inventory;

import br.com.colheitafeliz.domain.crop.CropType;
import java.util.HashMap;
import java.util.Map;

public final class Inventory {
    private final Map<String, Integer> seeds = new HashMap<>();
    private final Map<String, Integer> produce = new HashMap<>();
    /** Produtos de animais (ovo, leite...) e ração, ambos pelo id. A ração é do animal: "galinha". */
    private final Map<String, Integer> products = new HashMap<>();
    private final Map<String, Integer> feed = new HashMap<>();
    private int fertilizer;

    public int seeds(CropType type) { return seeds.getOrDefault(type.id(), 0); }
    public int produce(CropType type) { return produce.getOrDefault(type.id(), 0); }
    public Map<String, Integer> seedSnapshot() { return Map.copyOf(seeds); }
    public Map<String, Integer> produceSnapshot() { return Map.copyOf(produce); }
    public int product(String id) { return products.getOrDefault(id, 0); }
    public int feed(String animalId) { return feed.getOrDefault(animalId, 0); }
    public Map<String, Integer> productSnapshot() { return Map.copyOf(products); }
    public Map<String, Integer> feedSnapshot() { return Map.copyOf(feed); }
    public void addProduct(String id, int amount) { add(products, id, amount); }
    public void takeProduct(String id, int amount) { take(products, id, amount); }
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

    public void addSeeds(CropType type, int amount) { add(seeds, type.id(), amount); }
    public void addProduce(CropType type, int amount) { add(produce, type.id(), amount); }
    public void useSeed(CropType type) { take(seeds, type.id(), 1); }
    public void takeProduce(CropType type, int amount) { take(produce, type.id(), amount); }

    public void restore(Map<String, Integer> seedCounts, Map<String, Integer> produceCounts) {
        seeds.clear();
        produce.clear();
        seedCounts.forEach((id, count) -> add(seeds, id, count));
        produceCounts.forEach((id, count) -> add(produce, id, count));
    }

    public void restoreFertilizer(int amount) {
        fertilizer = 0;
        addFertilizer(amount);
    }

    public void restoreAnimalItems(Map<String, Integer> productCounts, Map<String, Integer> feedCounts) {
        products.clear();
        feed.clear();
        if (productCounts != null) productCounts.forEach((id, count) -> add(products, id, count));
        if (feedCounts != null) feedCounts.forEach((id, count) -> add(feed, id, count));
    }

    private static void add(Map<String, Integer> items, String id, int amount) {
        if (amount < 0) throw new IllegalArgumentException("Negative inventory amount");
        items.put(id, Math.addExact(items.getOrDefault(id, 0), amount));
    }

    private static void take(Map<String, Integer> items, String id, int amount) {
        if (amount <= 0 || items.getOrDefault(id, 0) < amount)
            throw new IllegalStateException("Insufficient inventory: " + id);
        items.put(id, items.get(id) - amount);
    }
}
