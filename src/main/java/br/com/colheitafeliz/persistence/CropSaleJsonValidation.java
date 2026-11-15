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
        for (var seed : snapshot.seeds()) {
            Authority.CATALOG.get(seed.itemId()).validateSaleTerms(seed);
            if (seed.cropSale() != null) throw new IllegalArgumentException("Seed cannot contain crop sale metadata");
        }
        for (var product : snapshot.produce()) validateTerms(product, false);
        for (var tile : snapshot.tiles()) {
            if (tile.cropId() != null && tile.state() != br.com.colheitafeliz.domain.farm.TileState.DEAD) {
                var crop = Authority.CATALOG.get(tile.cropId());
                if (tile.origin() != null && tile.origin().cropSale() != null) {
                    validateTerms(tile.origin(), true);
                    if (!tile.origin().itemId().equals(tile.cropId()) || tile.origin().quantity() != 1
                            || tile.season() < 1 || tile.season() > tile.origin().cropSale().seasons())
                        throw new IllegalArgumentException("Inconsistent plant sale metadata");
                }
                if (crop.acquisitionSource() == br.com.colheitafeliz.domain.inventory.AcquisitionSource.LOGIN_REWARD
                        && (tile.origin() == null || tile.origin().cropSale() == null
                        || tile.origin().cropSale().seasonIndex() != 0 || tile.season() != 1))
                    throw new IllegalArgumentException("Reward crop plant is missing sale metadata");
            }
        }
        for (var animal : snapshot.animals()) {
            if (animal.origin().acquisitionSource() != br.com.colheitafeliz.domain.inventory.AcquisitionSource.SHOP)
                throw new IllegalArgumentException("Animal cannot contain login reward origin");
            if (animal.origin().cropSale() != null) throw new IllegalArgumentException("Animal cannot contain crop sale metadata");
        }
    }

    private static void validateTerms(ItemLot lot, boolean plant) {
        if (lot.cropSale() == null && lot.acquisitionSource()
                == br.com.colheitafeliz.domain.inventory.AcquisitionSource.SHOP) return;
        Authority.CATALOG.get(lot.itemId()).validateSaleTerms(lot);
        if (lot.cropSale() == null) return;
        if ((lot.cropSale().seasonIndex() == 0) != plant)
            throw new IllegalArgumentException("Product/plant sale index mismatch");
    }

    private static final class Authority {
        private static final br.com.colheitafeliz.domain.crop.CropCatalog CATALOG = CropCatalogLoader.load();
    }

    private static void validateLot(JsonObject lot) {
        boolean loginReward = lot.has("acquisitionSource") && lot.get("acquisitionSource").isJsonPrimitive()
                && "LOGIN_REWARD".equals(lot.get("acquisitionSource").getAsString());
        if (lot.has("acquisitionSource")) {
            var source = lot.get("acquisitionSource");
            if (source == null || !source.isJsonPrimitive() || !source.getAsJsonPrimitive().isString()
                    || !java.util.Set.of("SHOP", "LOGIN_REWARD").contains(source.getAsString()))
                throw new IllegalArgumentException("Invalid acquisition source");
        }
        if (loginReward) {
            integer(lot, "quantity");
            var vipOnly = lot.get("vipOnly");
            if (vipOnly == null || !vipOnly.isJsonPrimitive() || !vipOnly.getAsJsonPrimitive().isBoolean()
                    || vipOnly.getAsBoolean()) throw new IllegalArgumentException("Invalid reward VIP marker");
            var currency = lot.get("currency");
            var cost = lot.get("unitCost");
            if (currency == null || !currency.isJsonPrimitive() || !"GREEN".equals(currency.getAsString())
                    || cost == null || !cost.isJsonPrimitive() || !cost.getAsJsonPrimitive().isNumber()
                    || cost.getAsBigDecimal().signum() != 0)
                throw new IllegalArgumentException("Invalid reward lot cost");
            var animalSale = lot.get("animalSale");
            if (animalSale != null && !animalSale.isJsonNull())
                throw new IllegalArgumentException("Login reward cannot contain animal sale terms");
        }
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
