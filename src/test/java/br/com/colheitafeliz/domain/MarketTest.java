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
        Wallet wallet = new Wallet(300);
        Inventory inventory = new Inventory();
        Market market = new Market(wallet, inventory);
        market.buySeed(turnip, br.com.colheitafeliz.domain.economy.Currency.GOLD);
        assertEquals(0, java.math.BigDecimal.valueOf(0).compareTo(wallet.coins()));
        assertEquals(1, inventory.seeds(turnip));
        inventory.useSeed(turnip);
        assertEquals(0, inventory.seeds(turnip));
        inventory.addProduceLot(new br.com.colheitafeliz.domain.inventory.ItemLot(turnip.id(), br.com.colheitafeliz.domain.economy.Currency.GOLD, turnip.seedPrice().getValue(), 1, false));
        market.sellProduce(turnip.id(), 1, false);
        assertEquals(0, new java.math.BigDecimal("345").compareTo(wallet.coins()));
        assertEquals(0, inventory.produce(turnip));
    }

    @Test void insufficientMoneyAndMissingItemsDoNotMutateState() {
        Wallet wallet = new Wallet(299);
        Inventory inventory = new Inventory();
        Market market = new Market(wallet, inventory);
        assertThrows(IllegalStateException.class, () -> market.buySeed(turnip, br.com.colheitafeliz.domain.economy.Currency.GOLD));
        assertThrows(IllegalArgumentException.class, () -> market.buySeed(turnip, br.com.colheitafeliz.domain.economy.Currency.GREEN));
        assertEquals(0, java.math.BigDecimal.valueOf(299).compareTo(wallet.coins()));
        assertEquals(0, inventory.seeds(turnip));
        assertThrows(IllegalStateException.class, () -> inventory.useSeed(turnip));
        assertThrows(IllegalStateException.class, () -> market.sellProduce(turnip.id(), 1, false));
        assertEquals(0, java.math.BigDecimal.valueOf(299).compareTo(wallet.coins()));
        assertThrows(IllegalArgumentException.class, () -> inventory.addProduceLot(new br.com.colheitafeliz.domain.inventory.ItemLot(turnip.id(), br.com.colheitafeliz.domain.economy.Currency.GOLD, turnip.seedPrice().getValue(), -1, false)));
        assertThrows(IllegalArgumentException.class, () -> new Wallet(-1));
    }

    @Test void sellingAllProduceCreditsTheWholeValue() {
        Wallet wallet = new Wallet(0);
        Inventory inventory = new Inventory();
        Market market = new Market(wallet, inventory);
        inventory.addProduceLot(new br.com.colheitafeliz.domain.inventory.ItemLot(turnip.id(), br.com.colheitafeliz.domain.economy.Currency.GOLD, turnip.seedPrice().getValue(), 4, false));
        market.sellProduce(turnip.id(), inventory.produce(turnip), false);
        assertEquals(0, java.math.BigDecimal.valueOf(1380).compareTo(wallet.coins()));
        assertEquals(0, inventory.produce(turnip));
        assertThrows(IllegalStateException.class, () -> market.sellProduce(turnip.id(), 1, false));
    }
}
