package br.com.colheitafeliz.game;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DailyMissionBoard {
    public record Mission(String id, String action, String title, int target, int coins, int xp) {}

    private static final List<Mission> ROTATION = List.of(
            new Mission("plant", "plant", "Plante 3 culturas", 3, 30, 10),
            new Mission("water", "water", "Regue 3 culturas", 3, 25, 10),
            new Mission("harvest", "harvest", "Colha 2 culturas", 2, 40, 15),
            new Mission("sell", "sell", "Venda produtos por 100 moedas", 100, 35, 10),
            new Mission("plow", "plow", "Are 4 terrenos", 4, 25, 10),
            new Mission("buy", "buy", "Compre 3 sementes", 3, 25, 10)
    );
    /** Missão extra do dia, só para quem tem animais: as três da rotação continuam as mesmas. */
    public static final Mission COLLECT = new Mission("collect", "collect", "Colete 2 produtos de animais", 2, 40, 15);

    private LocalDate day;
    private final Map<String, Integer> progress = new HashMap<>();
    private final Set<String> completed = new HashSet<>();
    private boolean animals;

    public DailyMissionBoard(LocalDate today) { day = today; }

    public void resetIfNewDay(LocalDate today) {
        if (today.equals(day)) return;
        day = today;
        progress.clear();
        completed.clear();
    }

    /** Liga a missão de coleta quando o jogador tem pelo menos um animal. */
    public void animalsAvailable(boolean available) { animals = available; }

    public List<Mission> missions() {
        int start = Math.floorMod(day.toEpochDay(), ROTATION.size());
        List<Mission> today = List.of(ROTATION.get(start), ROTATION.get((start + 1) % ROTATION.size()),
                ROTATION.get((start + 2) % ROTATION.size()));
        if (!animals) return today;
        List<Mission> withCollect = new ArrayList<>(today);
        withCollect.add(COLLECT);
        return List.copyOf(withCollect);
    }

    public List<Mission> record(String action, int amount, LocalDate today) {
        resetIfNewDay(today);
        List<Mission> rewards = new ArrayList<>();
        for (Mission mission : missions()) {
            if (!mission.action().equals(action) || completed.contains(mission.id())) continue;
            int current = Math.min(mission.target(), Math.addExact(progress.getOrDefault(mission.id(), 0), amount));
            progress.put(mission.id(), current);
            if (current >= mission.target()) {
                completed.add(mission.id());
                rewards.add(mission);
            }
        }
        return rewards;
    }

    public int progress(Mission mission) { return progress.getOrDefault(mission.id(), 0); }
    public boolean completed(Mission mission) { return completed.contains(mission.id()); }
    public LocalDate day() { return day; }
    public Map<String, Integer> progressSnapshot() { return Map.copyOf(progress); }
    public List<String> completedSnapshot() { return List.copyOf(completed); }

    public void restore(String savedDay, Map<String, Integer> savedProgress, List<String> savedCompleted,
                        LocalDate today) {
        if (savedDay == null || !LocalDate.parse(savedDay).equals(today)) {
            day = today;
            return;
        }
        day = today;
        for (Mission mission : missions()) {
            if (savedProgress != null) {
                int value = savedProgress.getOrDefault(mission.id(), 0);
                if (value < 0 || value > mission.target()) throw new IllegalArgumentException("Invalid mission progress");
                if (value > 0) progress.put(mission.id(), value);
            }
            if (savedCompleted != null && savedCompleted.contains(mission.id())) completed.add(mission.id());
            if (completed.contains(mission.id()) && progress(mission) < mission.target())
                throw new IllegalArgumentException("Completed mission has incomplete progress");
        }
    }
}
