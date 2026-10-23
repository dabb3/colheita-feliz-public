package br.com.colheitafeliz.domain;

import br.com.colheitafeliz.domain.crop.CropType;
import br.com.colheitafeliz.domain.economy.Market;
import br.com.colheitafeliz.domain.economy.Wallet;
import br.com.colheitafeliz.domain.inventory.Inventory;
import br.com.colheitafeliz.game.CropCatalogLoader;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MarketTest {
    private final CropType turnip = CropCatalogLoader.load().get("nabo");

    @Test void buyingAndSellingMoveOnlyTheExpectedBalances() {
        Wallet wallet = new Wallet(10);
        Inventory inventory = new Inventory();
        Market market = new Market(wallet, inventory);
        market.buySeed(turnip);
        assertEquals(0, wallet.coins());
        assertEquals(1, inventory.seeds(turnip));
        inventory.useSeed(turnip);
        assertEquals(0, inventory.seeds(turnip));
        inventory.addProduce(turnip, 1);
        market.sellProduce(turnip);
        assertEquals(20, wallet.coins());
        assertEquals(0, inventory.produce(turnip));
    }

    @Test void insufficientMoneyAndMissingItemsDoNotMutateState() {
        Wallet wallet = new Wallet(9);
        Inventory inventory = new Inventory();
        Market market = new Market(wallet, inventory);
        assertThrows(IllegalStateException.class, () -> market.buySeed(turnip));
        assertEquals(9, wallet.coins());
        assertEquals(0, inventory.seeds(turnip));
        assertThrows(IllegalStateException.class, () -> inventory.useSeed(turnip));
        assertThrows(IllegalStateException.class, () -> market.sellProduce(turnip));
        assertEquals(9, wallet.coins());
        assertThrows(IllegalArgumentException.class, () -> inventory.addProduce(turnip, -1));
        assertThrows(IllegalArgumentException.class, () -> new Wallet(-1));
    }

    @Test void sellingAllProduceCreditsTheWholeValue() {
        Wallet wallet = new Wallet(0);
        Inventory inventory = new Inventory();
        Market market = new Market(wallet, inventory);
        inventory.addProduce(turnip, 4);
        market.sellProduce(turnip, inventory.produce(turnip));
        assertEquals(80, wallet.coins());
        assertEquals(0, inventory.produce(turnip));
        assertThrows(IllegalStateException.class, () -> market.sellProduce(turnip, 1));
    }
}
