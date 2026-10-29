package br.com.colheitafeliz.game;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
/** Duas tarefas por dia UTC; day é também o maior dia já processado. */
public final class DailyMissionBoard {
    public record Mission(String id, String action, String title, int target, int green) {}
    private static final List<Mission> MISSIONS = List.of(
            new Mission("care", "care", "Cuidar da fazenda", 1, 1),
            new Mission("trade", "trade", "Movimentar a fazenda", 1, 1));
    private static final Set<String> CARE = Set.of("plant", "water", "weed", "pesticide", "harvest", "clear", "feed", "collect");
    private static final Set<String> TRADE = Set.of("buy", "purchase", "sell", "animal-sale");
    private LocalDate day;
    private final Map<String, Integer> progress = new HashMap<>();
    private final Set<String> completed = new HashSet<>();
    public DailyMissionBoard(LocalDate today) { day = today; }
    public void resetIfNewDay(LocalDate today) {
        if (!today.isAfter(day)) return;
        day = today;
        progress.clear();
        completed.clear();
    }
    public List<Mission> missions() { return MISSIONS; }
    public List<Mission> record(String action, int amount, LocalDate today) {
        if (today.isBefore(day) || amount <= 0) return List.of();
        resetIfNewDay(today);
        List<Mission> rewards = new ArrayList<>();
        for (Mission mission : MISSIONS) {
            boolean eligible = mission.id().equals("care") ? CARE.contains(action) : TRADE.contains(action);
            if (!eligible || completed.contains(mission.id())) continue;
            progress.put(mission.id(), 1);
            completed.add(mission.id());
            rewards.add(mission);
        }
        return rewards;
    }
    public int progress(Mission mission) { return progress.getOrDefault(mission.id(), 0); }
    public boolean completed(Mission mission) { return completed.contains(mission.id()); }
    public LocalDate day() { return day; }
    public Map<String, Integer> progressSnapshot() { return Map.copyOf(progress); }
    public List<String> completedSnapshot() { return List.copyOf(completed); }
    public void restore(String savedDay, Map<String, Integer> savedProgress, List<String> savedCompleted, LocalDate today) {
        day = LocalDate.parse(savedDay);
        progress.clear();
        completed.clear();
        for (Mission mission : MISSIONS) {
            int value = savedProgress.getOrDefault(mission.id(), 0);
            boolean paid = savedCompleted.contains(mission.id());
            if (value < 0 || value > 1 || paid != (value == 1))
                throw new IllegalArgumentException("Invalid mission payment");
            if (paid) { progress.put(mission.id(), 1); completed.add(mission.id()); }
        }
        resetIfNewDay(today);
    }
}
