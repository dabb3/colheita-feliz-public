package br.com.colheitafeliz.domain.economy;

public final class Wallet {
    private int coins;
    public Wallet(int coins) {
        if (coins < 0) throw new IllegalArgumentException("Negative balance");
        this.coins = coins;
    }
    public int coins() { return coins; }
    public void credit(int amount) {
        if (amount <= 0) throw new IllegalArgumentException("Credit must be positive");
        coins = Math.addExact(coins, amount);
    }
    public void debit(int amount) {
        if (amount <= 0 || amount > coins) throw new IllegalStateException("Insufficient coins");
        coins -= amount;
    }
}
