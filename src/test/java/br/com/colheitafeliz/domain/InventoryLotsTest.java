package br.com.colheitafeliz.domain;

import br.com.colheitafeliz.domain.inventory.Inventory;
import br.com.colheitafeliz.domain.inventory.ItemLot;
import br.com.colheitafeliz.domain.economy.Currency;
import br.com.colheitafeliz.game.CropCatalogLoader;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InventoryLotsTest {
    @Test void countsAggregateIdsWithoutMixingOriginsAndSnapshotsAreImmutable() {
        var inventory = new Inventory();
        var turnip = CropCatalogLoader.load().get("nabo");
        var gold = new ItemLot("nabo", Currency.GOLD, new BigDecimal("10"), 2, false);
        var green = new ItemLot("nabo", Currency.GREEN, new BigDecimal("9"), 3, true);
        inventory.addSeedLot(gold);
        inventory.addSeedLot(green);
        inventory.addProduceLot(gold);
        inventory.addProduceLot(green);
        inventory.addProduceLot(new ItemLot("ovo", Currency.GOLD, new BigDecimal("45"), 7, false));
        assertEquals(5, inventory.seeds(turnip));
        assertEquals(5, inventory.produce(turnip));
        assertEquals(7, inventory.product("ovo"));
        var snapshot = inventory.produceSnapshot();
        assertThrows(UnsupportedOperationException.class, () -> snapshot.put("ovo", 0));
        assertThrows(UnsupportedOperationException.class, () -> inventory.produceLots().clear());
        inventory.takeProduce(turnip, 3);
        assertEquals(2, inventory.produce(turnip));
        assertEquals(5, snapshot.get("nabo"));
        assertEquals(green.withQuantity(2), inventory.produceLots().getFirst());
        assertEquals(7, inventory.product("ovo"));
        assertEquals(gold.withQuantity(1), inventory.takeSeed("nabo"));
        assertEquals(4, inventory.seeds(turnip));
        assertEquals(0, inventory.product("missing"));
    }
}
