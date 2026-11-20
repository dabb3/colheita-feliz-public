package br.com.colheitafeliz.server;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CommandTest {
    @Test void animalProductIdsMatchTheDomainCatalog() {
        assertDoesNotThrow(() -> new Command(UUID.randomUUID(), "1", "SELL_ITEM", "ovo_de_pata", null, null, null, null));
        assertThrows(ApiFailure.class, () -> new Command(UUID.randomUUID(), "1", "SELL_ITEM", "../secret", null, null, null, null));
    }

    @Test void purchaseQuantityIsOnlyForSeedsAndFertilizersFromOneToNinetyNine() {
        assertEquals(1, buy("BUY_SEED", null).purchaseQuantity());
        assertEquals(1, buy("BUY_SEED", 1).purchaseQuantity());
        assertEquals(99, buy("BUY_FERTILIZER", 99).purchaseQuantity());
        for (Integer invalid : new Integer[] {0, -1, 100}) {
            var failure = assertThrows(ApiFailure.class, () -> buy("BUY_SEED", invalid));
            assertEquals("Escolha de 1 a 99 unidades.", failure.getMessage());
        }
        for (String action : new String[] {"BUY_FEED", "BUY_ANIMAL", "SELL_ITEM", "PLANT"})
            assertEquals("Quantidade não permitida nesta ação.", assertThrows(ApiFailure.class, () -> buy(action, 2)).getMessage());
    }

    private static Command buy(String action, Integer quantity) {
        return new Command(UUID.randomUUID(), "1", action, "nabo", "GOLD", null, null, null, null, null, quantity);
    }
}
