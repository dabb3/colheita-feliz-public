package br.com.colheitafeliz.server;

import br.com.colheitafeliz.game.AwayTally;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** What happened to a farm since its owner last opened it; persisted beside the farm in the same transaction. */
record AwayLedger(Instant lastSeenAt, int waterings, int pestsRemoved, int animalCares,
                  Map<String,Integer> collected, List<Event> events) {
    static final int MAX_EVENTS = 20;
    private static final Set<String> KINDS = Set.of("THEFT", "DEFENDED");

    record Event(String kind, String at, String actor, String itemId, int quantity, String protectorId) {
        Event {
            if (!KINDS.contains(kind) || actor == null || actor.isBlank() || quantity < 0) throw new IllegalArgumentException("Invalid away event");
            Instant.parse(at);
        }
    }

    AwayLedger {
        if (lastSeenAt == null || waterings < 0 || pestsRemoved < 0 || animalCares < 0) throw new IllegalArgumentException("Invalid away ledger");
        collected = Map.copyOf(collected);
        events = List.copyOf(events);
    }

    static AwayLedger empty(Instant lastSeenAt) { return new AwayLedger(lastSeenAt, 0, 0, 0, Map.of(), List.of()); }

    AwayLedger plus(AwayTally tally) { return plus(tally.waterings(), tally.pestsRemoved(), tally.animalCares(), tally.collected()); }

    AwayLedger plus(int moreWaterings, int morePests, int moreCares, Map<String,Integer> moreCollected) {
        var sum = new LinkedHashMap<>(collected);
        moreCollected.forEach((id, qty) -> sum.merge(id, qty, Integer::sum));
        return new AwayLedger(lastSeenAt, Math.addExact(waterings, moreWaterings), Math.addExact(pestsRemoved, morePests),
                Math.addExact(animalCares, moreCares), sum, events);
    }

    AwayLedger withEvent(Event event) {
        var all = new ArrayList<>(events); all.add(event);
        all.sort(Comparator.comparing((Event e) -> Instant.parse(e.at())));
        return new AwayLedger(lastSeenAt, waterings, pestsRemoved, animalCares, collected,
                all.subList(Math.max(0, all.size() - MAX_EVENTS), all.size()));
    }

    boolean hasActivity() { return waterings + pestsRemoved + animalCares > 0 || !collected.isEmpty() || !events.isEmpty(); }

    JsonObject toJson() {
        var json = new JsonObject();
        json.addProperty("waterings", waterings); json.addProperty("pestsRemoved", pestsRemoved); json.addProperty("animalCares", animalCares);
        var products = new JsonObject(); collected.forEach(products::addProperty); json.add("collected", products);
        var list = new JsonArray();
        for (var e : events) {
            var item = new JsonObject();
            item.addProperty("kind", e.kind()); item.addProperty("at", e.at()); item.addProperty("actor", e.actor());
            if (e.itemId() != null) item.addProperty("itemId", e.itemId());
            item.addProperty("quantity", e.quantity());
            if (e.protectorId() != null) item.addProperty("protectorId", e.protectorId());
            list.add(item);
        }
        json.add("events", list);
        return json;
    }

    /** Unreadable data never blocks the farm: it is treated as an empty ledger. */
    static AwayLedger fromJson(Instant lastSeenAt, JsonObject json) {
        try {
            var products = new LinkedHashMap<String,Integer>();
            for (var entry : json.getAsJsonObject("collected").entrySet()) products.put(entry.getKey(), integer(entry.getValue()));
            var events = new ArrayList<Event>();
            for (var element : json.getAsJsonArray("events")) {
                var e = element.getAsJsonObject();
                events.add(new Event(e.get("kind").getAsString(), e.get("at").getAsString(), e.get("actor").getAsString(),
                        e.has("itemId") ? e.get("itemId").getAsString() : null, integer(e.get("quantity")),
                        e.has("protectorId") ? e.get("protectorId").getAsString() : null));
            }
            return new AwayLedger(lastSeenAt, integer(json.get("waterings")), integer(json.get("pestsRemoved")),
                    integer(json.get("animalCares")), products, events);
        } catch (RuntimeException e) {
            return empty(lastSeenAt);
        }
    }

    private static int integer(JsonElement value) {
        var primitive = value.getAsJsonPrimitive();
        if (!primitive.isNumber()) throw new IllegalArgumentException("Not a number");
        return primitive.getAsBigInteger().intValueExact();
    }
}
