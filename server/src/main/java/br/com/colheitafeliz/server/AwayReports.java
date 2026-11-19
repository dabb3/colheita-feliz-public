package br.com.colheitafeliz.server;

import br.com.colheitafeliz.domain.animal.Animal;
import br.com.colheitafeliz.domain.animal.AnimalType;
import br.com.colheitafeliz.game.GameState;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds the owner's "while you were away" summary from the ledger and the farm as it is now. */
final class AwayReports {
    static final Duration MIN_ABSENCE = Duration.ofHours(3);
    private static final Map<String,String> SERVICE_NAMES = Map.of("water", "Rega automática", "pests", "Controle de pragas", "animals", "Cuidador de animais");
    private AwayReports() {}

    /** One absence the report covers; ready crops and producers are only credited when they became ready inside one. */
    private record Window(Instant since, Instant until) {
        boolean contains(Instant at) { return at != null && at.isAfter(since) && !at.isAfter(until); }
    }

    /** Null when nothing happened in the window. */
    static JsonObject build(GameState game, AwayLedger ledger, Instant now) {
        Instant since = ledger.lastSeenAt();
        var windows = List.of(new Window(since, now));
        var harvests = harvests(game, windows);
        var products = products(game, ledger.collected());
        var readyProducts = products(game, readyProducers(game, windows));
        var thefts = new JsonArray(); var defenses = new JsonArray();
        for (var event : ledger.events()) {
            var item = new JsonObject();
            item.addProperty("at", event.at()); item.addProperty("actor", event.actor());
            if (event.kind().equals("THEFT")) {
                // A crop retired from the catalog is skipped instead of failing the owner's farm.
                var crop = game.catalog().all().stream().filter(type -> type.id().equals(event.itemId())).findFirst().orElse(null);
                if (crop == null) continue;
                item.addProperty("itemId", event.itemId()); item.addProperty("name", crop.name());
                item.addProperty("stem", crop.spriteStem()); item.addProperty("quantity", event.quantity());
                thefts.add(item);
            } else defenses.add(item);
        }
        var pending = pending(game, since, now);
        int care = ledger.waterings() + ledger.pestsRemoved() + ledger.animalCares();
        // Old, still-open pendings ride along with news but never trigger a report on their own.
        if (harvests.isEmpty() && products.isEmpty() && readyProducts.isEmpty() && thefts.isEmpty() && defenses.isEmpty() && care == 0
                && !pending.openedInWindow()) return null;
        var report = new JsonObject();
        report.addProperty("awaySeconds", Duration.between(since, now).getSeconds());
        report.add("harvests", harvests); report.add("products", products); report.add("readyProducts", readyProducts);
        report.add("thefts", thefts); report.add("defenses", defenses);
        var careJson = new JsonObject();
        careJson.addProperty("waterings", ledger.waterings()); careJson.addProperty("pestsRemoved", ledger.pestsRemoved());
        careJson.addProperty("animalCares", ledger.animalCares());
        report.add("care", careJson);
        report.add("pending", pending.items());
        report.add("windows", windowsJson(windows));
        return report;
    }

    /**
     * Joins an unacknowledged report with the next one. What already happened adds up (time away, collected
     * products, care) and visit events keep the newest {@link AwayLedger#MAX_EVENTS} overall. What describes the
     * farm now (ready crops and products, pendings) is read again from {@code game}, limited to the reports' windows.
     */
    static JsonObject merge(JsonObject older, JsonObject newer, GameState game) {
        var windows = new ArrayList<>(windows(older)); windows.addAll(windows(newer));
        var merged = new JsonObject();
        merged.addProperty("awaySeconds", older.get("awaySeconds").getAsLong() + newer.get("awaySeconds").getAsLong());
        merged.add("harvests", harvests(game, windows));
        merged.add("products", sumBy(older, newer, "products", "productId"));
        merged.add("readyProducts", products(game, readyProducers(game, windows)));
        latestEvents(older, newer, merged);
        var care = new JsonObject();
        for (String field : java.util.List.of("waterings", "pestsRemoved", "animalCares"))
            care.addProperty(field, older.getAsJsonObject("care").get(field).getAsInt() + newer.getAsJsonObject("care").get(field).getAsInt());
        merged.add("care", care);
        merged.add("pending", newer.getAsJsonArray("pending").deepCopy());
        merged.add("windows", windowsJson(windows));
        return merged;
    }

    private static List<Window> windows(JsonObject report) {
        var result = new ArrayList<Window>();
        for (var element : list(report, "windows")) {
            var window = element.getAsJsonObject();
            result.add(new Window(Instant.parse(window.get("since").getAsString()), Instant.parse(window.get("until").getAsString())));
        }
        return result;
    }

    private static JsonArray windowsJson(List<Window> windows) {
        var result = new JsonArray();
        for (var window : windows) {
            var item = new JsonObject();
            item.addProperty("since", window.since().toString()); item.addProperty("until", window.until().toString());
            result.add(item);
        }
        return result;
    }

    private static JsonArray list(JsonObject report, String name) {
        return report.has(name) ? report.getAsJsonArray(name) : new JsonArray();
    }

    private static JsonArray sumBy(JsonObject older, JsonObject newer, String name, String key) {
        var byKey = new LinkedHashMap<String,JsonObject>();
        for (var report : java.util.List.of(older, newer))
            for (var element : list(report, name)) {
                var item = element.getAsJsonObject();
                var existing = byKey.get(item.get(key).getAsString());
                if (existing == null) byKey.put(item.get(key).getAsString(), item.deepCopy());
                else existing.addProperty("quantity", existing.get("quantity").getAsInt() + item.get("quantity").getAsInt());
            }
        var result = new JsonArray(); byKey.values().forEach(result::add);
        return result;
    }

    /** Thefts and defenses share one cap, like the ledger: the newest events overall, split back by kind. */
    private static void latestEvents(JsonObject older, JsonObject newer, JsonObject merged) {
        record Tagged(String kind, JsonObject event) {}
        var all = new ArrayList<Tagged>();
        for (var report : List.of(older, newer))
            for (String kind : List.of("thefts", "defenses"))
                list(report, kind).forEach(item -> all.add(new Tagged(kind, item.getAsJsonObject().deepCopy())));
        all.sort(Comparator.comparing((Tagged t) -> Instant.parse(t.event().get("at").getAsString())));
        var thefts = new JsonArray(); var defenses = new JsonArray();
        for (var tagged : all.subList(Math.max(0, all.size() - AwayLedger.MAX_EVENTS), all.size()))
            (tagged.kind().equals("thefts") ? thefts : defenses).add(tagged.event());
        merged.add("thefts", thefts); merged.add("defenses", defenses);
    }

    private static boolean inWindow(Instant at, Instant since, Instant now) { return new Window(since, now).contains(at); }

    private static boolean inAny(List<Window> windows, Instant at) { return windows.stream().anyMatch(window -> window.contains(at)); }

    private static JsonArray harvests(GameState game, List<Window> windows) {
        var byCrop = new LinkedHashMap<String,Integer>();
        for (int row = 0; row < GameState.ROWS; row++) for (int col = 0; col < GameState.COLUMNS; col++) {
            var crop = game.farm().tile(col, row).crop();
            if (crop != null && crop.matureAt(game.now()) && inAny(windows, crop.maturedAt(game.now())))
                byCrop.merge(crop.type().id(), crop.origin().quantity(), Integer::sum);
        }
        var list = new JsonArray();
        byCrop.forEach((id, quantity) -> {
            var type = game.catalog().get(id); var item = new JsonObject();
            item.addProperty("cropId", id); item.addProperty("name", type.name());
            item.addProperty("stem", type.spriteStem()); item.addProperty("quantity", quantity);
            list.add(item);
        });
        return list;
    }

    /** Producers that became ready in a window and still wait in the barn; collected output is counted apart. */
    private static Map<String,Integer> readyProducers(GameState game, List<Window> windows) {
        var byProduct = new LinkedHashMap<String,Integer>();
        for (Animal animal : game.barn().animals()) {
            var type = animal.type();
            if (type.kind() != AnimalType.Kind.PRODUCER || animal.statusAt(game.now()) != Animal.Status.READY) continue;
            // Readiness assumes uninterrupted feeding from the cycle anchor; a hungry pause can only move it later.
            if (inAny(windows, animal.growthAnchorAt().plusMillis(type.requiredMillis() - animal.grownAtAnchorMillis())))
                byProduct.merge(type.productId(), 1, Integer::sum);
        }
        return byProduct;
    }

    private static JsonArray products(GameState game, Map<String,Integer> byProduct) {
        var list = new JsonArray();
        byProduct.forEach((productId, quantity) -> game.animalTypes().stream()
                .filter(t -> t.kind() == AnimalType.Kind.PRODUCER && t.productId().equals(productId)).findFirst().ifPresent(type -> {
                    var item = new JsonObject();
                    item.addProperty("productId", productId); item.addProperty("name", type.productName());
                    item.addProperty("animalId", type.id()); item.addProperty("quantity", quantity);
                    list.add(item);
                }));
        return list;
    }

    private record Pending(JsonArray items, boolean openedInWindow) {}

    /** What still needs the player now, whenever it started; the window only says whether it is news. */
    private static Pending pending(GameState game, Instant since, Instant now) {
        var list = new JsonArray(); boolean fresh = false;
        for (String id : java.util.List.of("water", "pests", "animals")) {
            Instant expiry = game.automationExpiresAt(id);
            if (expiry == null || expiry.isAfter(now)) continue;
            var item = new JsonObject();
            item.addProperty("kind", "AUTOMATION"); item.addProperty("serviceId", id);
            item.addProperty("name", SERVICE_NAMES.get(id)); item.addProperty("expiredAt", expiry.toString());
            list.add(item); fresh |= inWindow(expiry, since, now);
        }
        Instant vipExpiry = game.vip().expiresAt();
        if (vipExpiry != null && !game.vipActive()) {
            var item = new JsonObject();
            item.addProperty("kind", "VIP"); item.addProperty("expiredAt", vipExpiry.toString());
            list.add(item); fresh |= inWindow(vipExpiry, since, now);
        }
        for (Animal animal : game.barn().animals()) {
            if (animal.type().kind() != AnimalType.Kind.PROTECTOR || animal.expiredAt(now) || !animal.hungryAt(game.now())) continue;
            var item = new JsonObject();
            item.addProperty("kind", "FEED"); item.addProperty("animalId", animal.type().id());
            item.addProperty("name", animal.type().name());
            item.addProperty("stock", game.inventory().feed("growth") + game.inventory().feed("producer"));
            list.add(item); fresh |= inWindow(animal.fedUntil(), since, now);
        }
        return new Pending(list, fresh);
    }
}
