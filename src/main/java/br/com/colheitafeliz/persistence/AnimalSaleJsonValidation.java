package br.com.colheitafeliz.persistence;

import com.google.gson.JsonElement;
import java.util.Set;

/** Reject Gson numeric coercion before deserializing immutable economic terms. */
public final class AnimalSaleJsonValidation {
    private static final Set<String> FIELDS = Set.of("economyVersion","animalId","commonRevenueTenths","vipRevenueTenths");
    private AnimalSaleJsonValidation() {}
    private static final class Authority {
        private static final java.util.List<br.com.colheitafeliz.domain.animal.AnimalType> TYPES = br.com.colheitafeliz.game.AnimalCatalogLoader.load();
    }
    public static void validateTerms(br.com.colheitafeliz.game.GameSnapshot snapshot) {
        br.com.colheitafeliz.game.AnimalSaleValidation.validate(snapshot,Authority.TYPES);
    }
    public static void validate(JsonElement element) {
        if (element == null || element.isJsonNull()) return;
        if (element.isJsonArray()) {
            for (var child : element.getAsJsonArray()) validate(child);
        } else if (element.isJsonObject()) {
            var object = element.getAsJsonObject();
            var terms = object.get("animalSale");
            if (terms != null && !terms.isJsonNull()) {
                if (!terms.isJsonObject() || !terms.getAsJsonObject().keySet().equals(FIELDS))
                    throw new IllegalArgumentException("Incomplete animal sale metadata");
                var data = terms.getAsJsonObject();
                for (var field : FIELDS) {
                    var value = data.get(field);
                    if (value == null || !value.isJsonPrimitive()) throw new IllegalArgumentException("Invalid animal term");
                    if (field.equals("animalId")) {
                        if (!value.getAsJsonPrimitive().isString() || value.getAsString().isBlank())
                            throw new IllegalArgumentException("Invalid animal id");
                    } else {
                        if (!value.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException("Non-numeric animal term");
                        if (field.equals("economyVersion")) value.getAsBigDecimal().intValueExact();
                        else value.getAsBigDecimal().longValueExact();
                    }
                }
                var quantity = object.get("quantity");
                if (quantity == null || !quantity.isJsonPrimitive() || !quantity.getAsJsonPrimitive().isNumber())
                    throw new IllegalArgumentException("Invalid animal lot quantity");
                quantity.getAsBigDecimal().intValueExact();
                var vip = object.get("vipOnly");
                if (vip == null || !vip.isJsonPrimitive() || !vip.getAsJsonPrimitive().isBoolean())
                    throw new IllegalArgumentException("Invalid animal VIP restriction");
            }
            for (var entry : object.entrySet()) validate(entry.getValue());
        }
    }
}
