package br.com.colheitafeliz.server;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CommandTest {
    @Test void animalProductIdsMatchTheDomainCatalog() {
        assertDoesNotThrow(() -> new Command(UUID.randomUUID(), "1", "SELL_ITEM", "ovo_de_galinha", null, null, null, null));
        assertThrows(ApiFailure.class, () -> new Command(UUID.randomUUID(), "1", "SELL_ITEM", "../secret", null, null, null, null));
    }
}
