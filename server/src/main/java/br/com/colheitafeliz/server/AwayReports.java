package br.com.colheitafeliz.server;

import br.com.colheitafeliz.domain.animal.Animal;
import br.com.colheitafeliz.domain.animal.AnimalType;
import br.com.colheitafeliz.game.GameState;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Builds the owner's "while you were away" summary from the ledger and the farm as it is now. */
final class AwayReports {
    static final Duration MIN_ABSENCE = Duration.ofHours(3);
    private static final Map<String,String> SERVICE_NAMES = Map.of("water", "Rega automática", "pests", "Controle de pragas", "animals", "Cuidador de animais");
    private AwayReports() {}

    /** Null when nothing happened in the window. */
    static JsonObject build(GameState game, AwayLedger ledger, Instant now) {
        Instant since = ledger.lastSeenAt();
        var harvests = harvests(game, since, now);
        var products = products(game, ledger, since, now);
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
            } else {
                item.addProperty("protectorId", event.protectorId());
                item.addProperty("protectorName", game.animalTypes().stream().filter(type -> type.id().equals(event.protectorId()))
                        .map(type -> type.name()).findFirst().orElse("Protetor"));
                defenses.add(item);
            }
        }
        var pending = pending(game, since, now);
        int care = ledger.waterings() + ledger.pestsRemoved() + ledger.animalCares();
        if (harvests.isEmpty() && products.isEmpty() && thefts.isEmpty() && defenses.isEmpty() && care == 0 && pending.isEmpty()) return null;
        var report = new JsonObject();
        report.addProperty("awaySeconds", Duration.between(since, now).getSeconds());
        report.add("harvests", harvests); report.add("products", products);
        report.add("thefts", thefts); report.add("defenses", defenses);
        var careJson = new JsonObject();
        careJson.addProperty("waterings", ledger.waterings()); careJson.addProperty("pestsRemoved", ledger.pestsRemoved());
        careJson.addProperty("animalCares", ledger.animalCares());
        report.add("care", careJson);
        report.add("pending", pending);
        return report;
    }

    private static boolean inWindow(Instant at, Instant since, Instant now) { return at != null && at.isAfter(since) && !at.isAfter(now); }

    private static JsonArray harvests(GameState game, Instant since, Instant now) {
        var byCrop = new LinkedHashMap<String,Integer>();
        for (int row = 0; row < GameState.ROWS; row++) for (int col = 0; col < GameState.COLUMNS; col++) {
            var crop = game.farm().tile(col, row).crop();
            if (crop != null && crop.matureAt(game.now()) && inWindow(crop.maturedAt(game.now()), since, now))
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

    private static JsonArray products(GameState game, AwayLedger ledger, Instant since, Instant now) {
        var byProduct = new LinkedHashMap<>(ledger.collected());
        for (Animal animal : game.barn().animals()) {
            var type = animal.type();
            if (type.kind() != AnimalType.Kind.PRODUCER || animal.statusAt(game.now()) != Animal.Status.READY) continue;
            // Readiness assumes uninterrupted feeding from the cycle anchor; a hungry pause can only move it later.
            if (inWindow(animal.growthAnchorAt().plusMillis(type.requiredMillis() - animal.grownAtAnchorMillis()), since, now))
                byProduct.merge(type.productId(), 1, Integer::sum);
        }
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

    private static JsonArray pending(GameState game, Instant since, Instant now) {
        var list = new JsonArray();
        for (String id : java.util.List.of("water", "pests", "animals")) {
            Instant expiry = game.automationExpiresAt(id);
            if (inWindow(expiry, since, now)) {
                var item = new JsonObject();
                item.addProperty("kind", "AUTOMATION"); item.addProperty("serviceId", id);
                item.addProperty("name", SERVICE_NAMES.get(id)); item.addProperty("expiredAt", expiry.toString());
                list.add(item);
            }
        }
        if (inWindow(game.vip().expiresAt(), since, now) && !game.vipActive()) {
            var item = new JsonObject();
            item.addProperty("kind", "VIP"); item.addProperty("expiredAt", game.vip().expiresAt().toString());
            list.add(item);
        }
        for (Animal animal : game.barn().animals()) {
            if (animal.type().kind() != AnimalType.Kind.PROTECTOR || animal.expiredAt(now) || !animal.hungryAt(game.now())
                    || !inWindow(animal.fedUntil(), since, now)) continue;
            var item = new JsonObject();
            item.addProperty("kind", "FEED"); item.addProperty("animalId", animal.type().id());
            item.addProperty("name", animal.type().name());
            item.addProperty("stock", game.inventory().feed("growth") + game.inventory().feed("producer"));
            list.add(item);
        }
        return list;
    }
}
