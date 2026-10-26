package br.com.colheitafeliz.domain.economy;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public final class Wallet {
    private final Map<Currency, BigDecimal> balances = new EnumMap<>(Currency.class);
    public Wallet(int coins) {
        this(BigDecimal.valueOf(coins), BigDecimal.ZERO);
    }
    public Wallet(BigDecimal gold, BigDecimal green) {
        balances.put(Currency.GOLD, validate(gold));
        balances.put(Currency.GREEN, validate(green));
    }
    public BigDecimal balance(Currency currency) { return balances.get(Objects.requireNonNull(currency)); }
    public void credit(Currency currency, BigDecimal amount) {
        BigDecimal valid = validate(amount);
        if (valid.signum() == 0) throw new IllegalArgumentException("Credit must be positive");
        balances.put(currency, balance(currency).add(valid));
    }
    public void debit(Currency currency, BigDecimal amount) {
        BigDecimal valid = validate(amount);
        if (valid.signum() == 0 || valid.compareTo(balance(currency)) > 0)
            throw new IllegalStateException("Insufficient coins");
        balances.put(currency, balance(currency).subtract(valid));
    }
    private static BigDecimal validate(BigDecimal amount) {
        if (amount == null || amount.signum() < 0 || amount.stripTrailingZeros().scale() > 1)
            throw new IllegalArgumentException("Invalid balance or precision");
        return amount.setScale(1);
    }
    public BigDecimal coins() { return balance(Currency.GOLD); }
    public void credit(int amount) {
        credit(Currency.GOLD, BigDecimal.valueOf(amount));
    }
    public void debit(int amount) {
        debit(Currency.GOLD, BigDecimal.valueOf(amount));
    }
}
