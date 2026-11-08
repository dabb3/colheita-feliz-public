package br.com.colheitafeliz.persistence;

import com.google.gson.JsonElement;
import java.time.Instant;
import java.util.Set;

/** Validate service metadata before Gson can coerce numbers or booleans into strings. */
public final class AutomationJsonValidation {
    private AutomationJsonValidation() {}
    public static void validate(JsonElement snapshot) {
        if (snapshot == null || !snapshot.isJsonObject()) return;
        var object = snapshot.getAsJsonObject();
        var value = object.get("automations");
        if (value == null || value.isJsonNull()) return;
        if (!value.isJsonObject() || !value.getAsJsonObject().keySet().equals(Set.of("cursor","expiresAt","weedsSince","insectsSince")))
            throw new IllegalArgumentException("Incomplete automation metadata");
        var metadata = value.getAsJsonObject();
        timestamp(metadata.get("cursor"));
        for (String field : Set.of("expiresAt","weedsSince","insectsSince")) {
            var entries = metadata.get(field);
            if (!entries.isJsonObject()) throw new IllegalArgumentException("Invalid automation map");
            for (var entry : entries.getAsJsonObject().entrySet()) {
                if (field.equals("expiresAt")) {
                    if (!Set.of("water","pests","animals").contains(entry.getKey())) throw new IllegalArgumentException("Unknown automation service");
                } else {
                    int id = Integer.parseInt(entry.getKey());
                    if (id < 0 || id >= 30 || !Integer.toString(id).equals(entry.getKey())) throw new IllegalArgumentException("Invalid pest tile");
                }
                timestamp(entry.getValue());
            }
        }
    }
    private static void timestamp(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
            throw new IllegalArgumentException("Invalid automation timestamp");
        Instant.parse(value.getAsString());
    }
}
