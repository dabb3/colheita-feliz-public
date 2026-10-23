package br.com.colheitafeliz.domain.economy;

import br.com.colheitafeliz.domain.crop.CropType;
import br.com.colheitafeliz.domain.inventory.Inventory;

public final class Market {
    private final Wallet wallet;
    private final Inventory inventory;

    public Market(Wallet wallet, Inventory inventory) {
        this.wallet = wallet;
        this.inventory = inventory;
    }

    public void buySeed(CropType type) {
        Math.addExact(inventory.seeds(type), 1);
        wallet.debit(type.seedPrice());
        inventory.addSeeds(type, 1);
    }

    public void sellProduce(CropType type) {
        sellProduce(type, 1);
    }

    public void sellProduce(CropType type, int amount) {
        if (amount <= 0 || inventory.produce(type) < amount)
            throw new IllegalStateException("No produce to sell");
        int total = Math.multiplyExact(type.salePrice(), amount);
        wallet.credit(total);
        inventory.takeProduce(type, amount);
    }
}
