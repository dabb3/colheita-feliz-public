package br.com.colheitafeliz.game;

import br.com.colheitafeliz.domain.farm.TileState;
import java.util.List;
import java.util.Map;

public record GameSnapshot(int coins, Map<String, Integer> seeds, Map<String, Integer> produce,
                           List<TileSnapshot> tiles, String selectedSeedId, Tool tool,
                           int level, int currentXp, List<String> unlockedCropIds,
                           boolean musicEnabled, boolean effectsEnabled,
                           String dailyMissionDay, Map<String, Integer> dailyMissionProgress,
                           List<String> completedDailyMissions, List<Integer> unlockedPlotIds,
                           Integer tutorialStep) {
    public record TileSnapshot(TileState state, String cropId, String plantedAt, String wateredAt,
                               String pest, String pestAt, String nextPestCheckAt,
                               String growthAnchorAt, long grownAtAnchorMillis,
                               int insects, String nextInsectCheckAt) {}
}
