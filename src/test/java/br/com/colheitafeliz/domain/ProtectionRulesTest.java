package br.com.colheitafeliz.domain;

import br.com.colheitafeliz.domain.animal.ProtectionRules;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProtectionRulesTest {
    @Test void coverageCapsAtOneAndCountsUseCeiling() {
        int[] protectors = {0, 1, 4, 5, 6};
        String[] fractions = {"0", "0.2", "0.8", "1", "1"};
        for (int i = 0; i < protectors.length; i++) assertEquals(0, new BigDecimal(fractions[i]).compareTo(ProtectionRules.coverage(protectors[i])));
        int[] sizes = {5, 6, 7, 30};
        int[] one = {1, 2, 2, 6}; int[] four = {4, 5, 6, 24};
        for (int i = 0; i < sizes.length; i++) {
            assertEquals(one[i], ProtectionRules.protectedCount(sizes[i], 1));
            assertEquals(four[i], ProtectionRules.protectedCount(sizes[i], 4));
            assertEquals(sizes[i], ProtectionRules.protectedCount(sizes[i], 5));
        }
        assertEquals(0, ProtectionRules.protectedCount(0, 5));
        assertEquals(429496730, ProtectionRules.protectedCount(Integer.MAX_VALUE, 1));
    }
    @Test void samplesDistinctUnlockedPlotsAndSkipsRandomWhenNoneOrAll() {
        var ids = IntStream.range(0, 7).boxed().toList();
        var selected = ProtectionRules.selectPlots(ids, 1, bound -> bound - 1);
        assertEquals(java.util.Set.of(5, 6), selected);
        assertEquals(2, selected.size()); assertTrue(ids.containsAll(selected));
        assertTrue(ProtectionRules.selectPlots(ids, 0, bound -> { fail("sem sorteio"); return 0; }).isEmpty());
        assertEquals(java.util.Set.copyOf(ids), ProtectionRules.selectPlots(ids, 6, bound -> { fail("sem sorteio"); return 0; }));
        assertThrows(IllegalArgumentException.class, () -> ProtectionRules.coverage(-1));
        assertThrows(IllegalArgumentException.class, () -> ProtectionRules.protectedCount(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> ProtectionRules.selectPlots(List.of(1, 1), 1, bound -> 0));
        assertThrows(IllegalArgumentException.class, () -> ProtectionRules.selectPlots(List.of(-1), 1, bound -> 0));
    }
}
