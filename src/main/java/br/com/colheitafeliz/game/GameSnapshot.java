package br.com.colheitafeliz.game;

import br.com.colheitafeliz.domain.farm.TileState;
import java.util.List;
import java.util.Map;
import java.math.BigDecimal;

public record GameSnapshot(BigDecimal coins, List<br.com.colheitafeliz.domain.inventory.ItemLot> seeds, List<br.com.colheitafeliz.domain.inventory.ItemLot> produce,
                           List<TileSnapshot> tiles, String selectedSeedId, Tool tool,
                           int level, BigDecimal currentXp,
                           boolean musicEnabled, boolean effectsEnabled,
                           String dailyMissionDay, Map<String, Integer> dailyMissionProgress,
                           List<String> completedDailyMissions, List<Integer> unlockedPlotIds,
                           Integer tutorialStep, List<AnimalSnapshot> animals,
                           Map<String, Integer> feed, Map<String, Integer> fertilizers, String selectedFertilizerId,
                           BigDecimal greenCoins, String vipExpiresAt, ProtectionSnapshot protection,
                           br.com.colheitafeliz.domain.notification.NotificationInbox.Snapshot notifications,
                           ChristmasState christmas2026) {
    public record ChristmasState(String registeredAt,boolean giftGranted) {
        public ChristmasState {
            var registration=java.time.Instant.parse(registeredAt);
            if(giftGranted&&!registration.isBefore(Christmas2026.REGISTRATION_CUTOFF))
                throw new IllegalArgumentException("Christmas gift outside registration window");
        }
    }
    public GameSnapshot {
        if(notifications==null)notifications=new br.com.colheitafeliz.domain.notification.NotificationInbox().snapshot();
        var giftCursor=notifications.observed().get("christmas-gift-2026");
        if((giftCursor!=null&&!giftCursor.equals("2026"))
                || (christmas2026!=null&&christmas2026.giftGranted())!="2026".equals(giftCursor))
            throw new IllegalArgumentException("Inconsistent Christmas gift receipt");
        if (coins == null || greenCoins == null || coins.signum() < 0 || greenCoins.signum() < 0
                || coins.stripTrailingZeros().scale() > 1 || greenCoins.stripTrailingZeros().scale() > 1
                || currentXp == null || currentXp.signum() < 0 || level < 1
                || selectedSeedId == null || selectedSeedId.isBlank() || tool == null
                || tutorialStep == null || dailyMissionDay == null || unlockedPlotIds == null)
            throw new IllegalArgumentException("Invalid farm snapshot");
        seeds = List.copyOf(seeds);
        produce = List.copyOf(produce);
        tiles = List.copyOf(tiles);
        if (tiles.size() != GameState.COLUMNS * GameState.ROWS)
            throw new IllegalArgumentException("Invalid farm dimensions");
        dailyMissionProgress = Map.copyOf(dailyMissionProgress);
        completedDailyMissions = List.copyOf(completedDailyMissions);
        unlockedPlotIds = List.copyOf(unlockedPlotIds);
        if (protection != null && !protection.unlockedIds().equals(unlockedPlotIds))
            throw new IllegalArgumentException("Protection does not match unlocked plots");
        animals = List.copyOf(animals);
        feed = Map.copyOf(feed);
        // Null só chega de um save v20 sem o campo; o v16 já é recusado na carga e o adubo antigo não é convertido (seção 10).
        fertilizers = fertilizers == null ? Map.of() : Map.copyOf(fertilizers);
    }
    public record ProtectionSnapshot(List<Integer> unlockedIds, List<String> activeProtectors, List<Integer> protectedIds) {
        public ProtectionSnapshot {
            unlockedIds = List.copyOf(unlockedIds);
            activeProtectors = List.copyOf(activeProtectors);
            protectedIds = List.copyOf(protectedIds);
            if (unlockedIds.stream().anyMatch(id -> id < 0 || id >= GameState.COLUMNS * GameState.ROWS)
                    || new java.util.HashSet<>(unlockedIds).size() != unlockedIds.size()
                    || new java.util.HashSet<>(activeProtectors).size() != activeProtectors.size()
                    || new java.util.HashSet<>(protectedIds).size() != protectedIds.size()
                    || !unlockedIds.containsAll(protectedIds)
                    || protectedIds.size() != br.com.colheitafeliz.domain.animal.ProtectionRules.protectedCount(unlockedIds.size(), activeProtectors.size()))
                throw new IllegalArgumentException("Invalid protection snapshot");
        }
    }
    /** Estado de aquisição e progresso, sem recalcular origem nem reiniciar alimento na carga. */
    public record AnimalSnapshot(String typeId, br.com.colheitafeliz.domain.inventory.ItemLot origin,
                                 String acquiredAt, String growthAnchorAt, long grownAtAnchorMillis, String fedUntil) {}
    public record TileSnapshot(TileState state, String cropId, String plantedAt, String wateredAt,
                               String pest, String pausedAt, String nextPestCheckAt,
                               String growthAnchorAt, long grownAtAnchorMillis,
                               int insects, String nextInsectCheckAt, boolean fertilized,
                               br.com.colheitafeliz.domain.inventory.ItemLot origin, int season, long harvestRevision) {
        public TileSnapshot {
            if (harvestRevision < 0) throw new IllegalArgumentException("Negative tile revision");
        }
    }
}
