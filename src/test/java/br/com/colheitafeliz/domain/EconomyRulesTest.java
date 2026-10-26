package br.com.colheitafeliz.domain;

import br.com.colheitafeliz.domain.economy.Currency;
import br.com.colheitafeliz.domain.economy.Market;
import br.com.colheitafeliz.domain.economy.Wallet;
import br.com.colheitafeliz.domain.inventory.Inventory;
import br.com.colheitafeliz.domain.inventory.ItemLot;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EconomyRulesTest {
    private static BigDecimal decimal(String value) { return new BigDecimal(value); }

    @Test void saleRoundsOnlyOperationTotal() {
        Wallet wallet = new Wallet(0);
        Inventory inventory = new Inventory();
        inventory.addProduceLot(new ItemLot("ovo", Currency.GREEN, decimal("9"), 2, false));
        Market market = new Market(wallet, inventory);
        assertEquals(0, decimal("20.7").compareTo(market.quoteEverything(false).get(Currency.GREEN)));
        assertEquals(1, inventory.produceLots().size());
        assertEquals(0, BigDecimal.ZERO.compareTo(wallet.balance(Currency.GREEN)));
        var receipt = market.sellEverything(false);
        assertEquals(0, decimal("20.7").compareTo(receipt.get(Currency.GREEN)));
        assertEquals(0, decimal("20.7").compareTo(wallet.balance(Currency.GREEN)));
        assertTrue(inventory.produceLots().isEmpty());
    }

    @Test void mixedOriginsAndBlockedVipRemainSeparate() {
        Wallet wallet = new Wallet(0);
        Inventory inventory = new Inventory();
        inventory.addProduceLot(new ItemLot("ovo", Currency.GREEN, decimal("9"), 1, false));
        inventory.addProduceLot(new ItemLot("ovo", Currency.GOLD, decimal("8000"), 1, false));
        ItemLot blocked = new ItemLot("batata-doce", Currency.GREEN, decimal("4"), 1, true);
        inventory.addProduceLot(blocked);
        var receipt = new Market(wallet, inventory).sellEverything(false);
        assertEquals(0, decimal("10.4").compareTo(receipt.get(Currency.GREEN)));
        assertEquals(0, decimal("9200").compareTo(receipt.get(Currency.GOLD)));
        assertEquals(java.util.List.of(blocked), inventory.produceLots());
    }

    @Test void failedTradeDoesNotMutateAndSeedsKeepOrigin() {
        Wallet wallet = new Wallet(0);
        Inventory inventory = new Inventory();
        ItemLot seed = new ItemLot("nabo", Currency.GOLD, decimal("300"), 1, false);
        inventory.addSeedLot(seed);
        assertThrows(IllegalStateException.class, () -> wallet.debit(Currency.GOLD, decimal("1")));
        assertEquals(seed, inventory.takeSeed("nabo"));
        assertThrows(IllegalStateException.class, () -> inventory.takeSeed("nabo"));
        assertThrows(IllegalStateException.class,
                () -> new Market(wallet, inventory).sellProduce("nabo", 1, false));
        assertEquals(0, BigDecimal.ZERO.compareTo(wallet.balance(Currency.GOLD)));
    }

    @Test void vipUsesCurrentProfileAndRoundingIsPerCurrency() {
        Wallet wallet = new Wallet(0);
        Inventory inventory = new Inventory();
        inventory.addProduceLot(new ItemLot("melancia", Currency.GOLD, decimal("1975"), 2, false));
        inventory.addProduceLot(new ItemLot("batata-doce", Currency.GREEN, decimal("4"), 1, true));
        var receipt = new Market(wallet, inventory).sellEverything(true);
        assertEquals(0, decimal("4937.5").compareTo(receipt.get(Currency.GOLD)));
        assertEquals(0, decimal("5").compareTo(receipt.get(Currency.GREEN)));
    }

    @Test void sellingEverythingHasNoCombinedIntegerQuantityLimit() {
        Inventory inventory = new Inventory();
        inventory.addProduceLot(new ItemLot("nabo", Currency.GOLD, decimal("1"), Integer.MAX_VALUE, false));
        inventory.addProduceLot(new ItemLot("milho", Currency.GREEN, decimal("1"), Integer.MAX_VALUE, false));
        var receipt = new Market(new Wallet(0), inventory).sellEverything(false);
        assertEquals(2, receipt.size());
        assertTrue(inventory.produceLots().isEmpty());
    }

    @Test void invalidLotsAndMoneyAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ItemLot("nabo", Currency.GOLD, decimal("0"), 1, false));
        assertThrows(IllegalArgumentException.class, () -> new ItemLot("nabo", Currency.GOLD, decimal("300"), 0, false));
        assertThrows(IllegalArgumentException.class, () -> new Wallet(-1));
        assertThrows(IllegalArgumentException.class, () -> new Wallet(0).credit(Currency.GREEN, decimal("0.01")));
    }

    @Test void overflowAndExcessSalePreserveInventoryAndBalances() {
        Inventory inventory = new Inventory();
        inventory.addSeedLot(new ItemLot("nabo", Currency.GOLD, decimal("300"), Integer.MAX_VALUE, false));
        assertThrows(ArithmeticException.class, () -> inventory.addSeedLot(new ItemLot("nabo", Currency.GOLD, decimal("300"), 1, false)));
        assertEquals(Integer.MAX_VALUE, inventory.seedLots().getFirst().quantity());
        ItemLot lot = new ItemLot("ovo", Currency.GREEN, decimal("9"), 1, false);
        inventory.addProduceLot(lot);
        Wallet wallet = new Wallet(0);
        assertThrows(IllegalStateException.class, () -> new Market(wallet, inventory).sellProduce("ovo", 2, false));
        assertEquals(java.util.List.of(lot), inventory.produceLots());
        assertEquals(0, wallet.balance(Currency.GREEN).signum());
    }
}
