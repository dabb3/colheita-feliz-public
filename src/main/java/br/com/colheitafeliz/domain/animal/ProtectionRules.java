package br.com.colheitafeliz.domain.animal;

import br.com.colheitafeliz.domain.farm.RandomSource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Cobertura de terrenos, nunca probabilidade independente por tentativa. */
public final class ProtectionRules {
    private ProtectionRules() {}
    public static BigDecimal coverage(int activeProtectors) {
        if (activeProtectors < 0) throw new IllegalArgumentException("Negative protector count");
        return BigDecimal.valueOf(Math.min(5, activeProtectors)).divide(BigDecimal.valueOf(5));
    }
    public static int protectedCount(int unlockedPlots, int activeProtectors) {
        if (unlockedPlots < 0 || activeProtectors < 0) throw new IllegalArgumentException("Negative count");
        return (int) (((long) unlockedPlots * Math.min(5, activeProtectors) + 4) / 5);
    }
    public static Set<Integer> selectPlots(List<Integer> unlockedIds, int activeProtectors, RandomSource random) {
        Objects.requireNonNull(random);
        var remaining = new ArrayList<>(unlockedIds);
        if (new HashSet<>(remaining).size() != remaining.size() || remaining.stream().anyMatch(id -> id == null || id < 0))
            throw new IllegalArgumentException("Invalid unlocked IDs");
        int count = protectedCount(remaining.size(), activeProtectors);
        if (count == remaining.size()) return Set.copyOf(remaining);
        var selected = new HashSet<Integer>();
        for (int i = 0; i < count; i++) selected.add(remaining.remove(random.nextInt(remaining.size())));
        return Set.copyOf(selected);
    }
}
