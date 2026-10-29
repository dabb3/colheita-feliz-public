package br.com.colheitafeliz.domain.player;

import java.math.BigDecimal;
import java.util.Objects;

public final class PlayerProgress {
    private final ProgressionRules rules;
    private int level;
    private BigDecimal currentXp;

    public PlayerProgress(ProgressionRules rules) { this(rules, 1, BigDecimal.ZERO); }

    public PlayerProgress(ProgressionRules rules, int level, BigDecimal currentXp) {
        this.rules = Objects.requireNonNull(rules);
        if (level < 1 || currentXp == null || currentXp.signum() < 0 || currentXp.compareTo(xpToNext(level)) >= 0)
            throw new IllegalArgumentException("Invalid progress");
        this.level = level;
        this.currentXp = currentXp;
    }

    public int level() { return level; }
    public BigDecimal currentXp() { return currentXp; }
    public BigDecimal xpToNext() { return xpToNext(level); }
    public ProgressionRules rules() { return rules; }

    public int award(int xp) {
        return award(BigDecimal.valueOf(xp));
    }
    public int award(BigDecimal xp) {
        if (xp == null || xp.signum() < 0) throw new IllegalArgumentException("Negative XP");
        currentXp = currentXp.add(xp);
        int levels = 0;
        while (currentXp.compareTo(xpToNext()) >= 0) {
            currentXp = currentXp.subtract(xpToNext());
            level = Math.incrementExact(level);
            levels++;
        }
        return levels;
    }

    private BigDecimal xpToNext(int level) {
        return rules.xpToNext(level);
    }
}
