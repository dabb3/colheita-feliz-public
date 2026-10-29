package br.com.colheitafeliz.domain.player;

import java.math.BigDecimal;
import java.util.Map;

public record ProgressionRules(int levelBaseXp, int levelQuadraticXp, int plantXp,
                               int waterXp, int removePestXp, int clearDeadXp, Map<Integer, Reward> levelRewards) {
    public ProgressionRules {
        if (levelBaseXp <= 0 || levelQuadraticXp < 0 || plantXp < 0 || waterXp < 0 || removePestXp < 0 || clearDeadXp < 0)
            throw new IllegalArgumentException("Invalid progression rules");
        if (levelRewards == null || levelRewards.keySet().stream().anyMatch(level -> level < 2))
            throw new IllegalArgumentException("Invalid level rewards");
        levelRewards = Map.copyOf(levelRewards);
    }
    public BigDecimal xpToNext(int level) {
        if (level < 1) throw new IllegalArgumentException("Invalid level");
        BigDecimal previous = BigDecimal.valueOf(level - 1L);
        return BigDecimal.valueOf(levelBaseXp).add(previous.pow(2).multiply(BigDecimal.valueOf(levelQuadraticXp)));
    }
    public record Reward(BigDecimal gold, BigDecimal green) {
        public Reward {
            if (gold == null || green == null || gold.signum() < 0 || green.signum() < 0
                    || gold.stripTrailingZeros().scale() > 1 || green.stripTrailingZeros().scale() > 1)
                throw new IllegalArgumentException("Invalid reward");
        }
    }
    public Reward rewardFor(int level) {
        return levelRewards.getOrDefault(level, new Reward(BigDecimal.ZERO, BigDecimal.ZERO));
    }
    @Override public Map<Integer, Reward> levelRewards() { return Map.copyOf(levelRewards); }
}
