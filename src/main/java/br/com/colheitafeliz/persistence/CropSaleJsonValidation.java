package br.com.colheitafeliz.persistence;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import br.com.colheitafeliz.game.GameSnapshot;
import br.com.colheitafeliz.game.CropCatalogLoader;
import br.com.colheitafeliz.domain.inventory.ItemLot;
import java.util.Set;

/** Validate exact numbers before Gson can coerce or truncate record fields. Shared by file and JDBC readers. */
public final class CropSaleJsonValidation {
    private static final Set<String> TERMS = Set.of("economyVersion", "seasonIndex", "seasons",
            "cycleRevenueTenths", "cycleVipBonusTenths");
    private CropSaleJsonValidation() {}

    public static void validate(JsonElement element) {
        if (element == null || element.isJsonNull()) return;
        if (element.isJsonArray()) {
            for (var child : element.getAsJsonArray()) validate(child);
        } else if (element.isJsonObject()) {
            var object = element.getAsJsonObject();
            if (object.has("itemId") && object.has("unitCost")) validateLot(object);
            if (object.has("cropId") && object.has("origin") && object.get("origin").isJsonObject()
                    && object.getAsJsonObject("origin").has("cropSale")) integer(object, "season");
            for (var entry : object.entrySet()) validate(entry.getValue());
        }
    }

    public static void validateTerms(GameSnapshot snapshot) {
        for (var seed : snapshot.seeds()) if (seed.cropSale() != null)
            throw new IllegalArgumentException("Seed cannot contain crop sale metadata");
        for (var product : snapshot.produce()) validateTerms(product, false);
        for (var tile : snapshot.tiles()) if (tile.origin() != null && tile.origin().cropSale() != null) {
            validateTerms(tile.origin(), true);
            if (!tile.origin().itemId().equals(tile.cropId()) || tile.origin().quantity() != 1
                    || tile.season() < 1 || tile.season() > tile.origin().cropSale().seasons())
                throw new IllegalArgumentException("Inconsistent plant sale metadata");
        }
        for (var animal : snapshot.animals()) if (animal.origin().cropSale() != null)
            throw new IllegalArgumentException("Animal cannot contain crop sale metadata");
    }

    private static void validateTerms(ItemLot lot, boolean plant) {
        if (lot.cropSale() == null) return;
        Authority.CATALOG.get(lot.itemId()).validateSaleTerms(lot);
        if ((lot.cropSale().seasonIndex() == 0) != plant)
            throw new IllegalArgumentException("Product/plant sale index mismatch");
    }

    private static final class Authority {
        private static final br.com.colheitafeliz.domain.crop.CropCatalog CATALOG = CropCatalogLoader.load();
    }

    private static void validateLot(JsonObject lot) {
        if (lot.has("economyVersion")) integer(lot, "economyVersion");
        var terms = lot.get("cropSale");
        if (terms == null || terms.isJsonNull()) return; // ItemLot checks the explicit version marker.
        if (!terms.isJsonObject() || !terms.getAsJsonObject().keySet().equals(TERMS))
            throw new IllegalArgumentException("Incomplete crop sale metadata");
        for (String field : TERMS) integer(terms.getAsJsonObject(), field);
        integer(lot, "quantity");
        var vip = lot.get("vipOnly");
        if (vip == null || !vip.isJsonPrimitive() || !vip.getAsJsonPrimitive().isBoolean())
            throw new IllegalArgumentException("Invalid VIP sale restriction");
    }

    private static void integer(JsonObject object, String field) {
        var value = object.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException("Missing or non-numeric integer: " + field);
        if (field.startsWith("cycle")) value.getAsBigDecimal().longValueExact();
        else value.getAsBigDecimal().intValueExact();
    }
}
