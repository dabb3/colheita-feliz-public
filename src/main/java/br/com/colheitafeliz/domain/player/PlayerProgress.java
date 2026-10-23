package br.com.colheitafeliz.domain.player;

public final class PlayerProgress {
    private final ProgressionRules rules;
    private int level;
    private int currentXp;

    public PlayerProgress(ProgressionRules rules) { this(rules, 1, 0); }

    public PlayerProgress(ProgressionRules rules, int level, int currentXp) {
        this.rules = rules;
        if (level < 1 || currentXp < 0 || currentXp >= xpToNext(level))
            throw new IllegalArgumentException("Invalid progress");
        this.level = level;
        this.currentXp = currentXp;
    }

    public int level() { return level; }
    public int currentXp() { return currentXp; }
    public int xpToNext() { return xpToNext(level); }
    public ProgressionRules rules() { return rules; }

    public int award(int xp) {
        if (xp < 0) throw new IllegalArgumentException("Negative XP");
        currentXp = Math.addExact(currentXp, xp);
        int levels = 0;
        while (currentXp >= xpToNext()) {
            currentXp -= xpToNext();
            level++;
            levels++;
        }
        return levels;
    }

    private int xpToNext(int level) {
        return Math.addExact(rules.levelBaseXp(), Math.multiplyExact(level - 1, rules.levelStepXp()));
    }
}
