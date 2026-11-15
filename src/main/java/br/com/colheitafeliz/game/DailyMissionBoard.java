package br.com.colheitafeliz.game;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
/**
 * Tarefas por dia UTC; day é também o maior dia já processado. Missões cooperativas existem só no online
 * (o desktop não tem amigos) e não pagam verdes isoladamente: {@link #COOPERATIVE_BONUS} paga 1 verde, uma vez
 * por dia, quando todas as missões de {@code COOPERATIVE} estiverem concluídas. Novas missões cooperativas
 * entram nessa lista e passam a ser exigidas pelo bônus.
 */
public final class DailyMissionBoard {
    public record Mission(String id, String action, String title, int target, int green) {}
    private static final List<Mission> SOLO = List.of(
            new Mission("care", "care", "Cuidar da fazenda", 1, 1),
            new Mission("trade", "trade", "Movimentar a fazenda", 1, 1));
    private static final List<Mission> COOPERATIVE = List.of(
            new Mission("nature", "friend-pest", "Amigo da Natureza", 2, 0));
    /** Pseudo-missão gravada em completedDailyMissions quando o bônus cooperativo do dia é pago. */
    public static final Mission COOPERATIVE_BONUS = new Mission("cooperative-bonus", "cooperative", "Bônus cooperativo", 1, 1);
    private static final Map<String, Set<String>> ACTIONS = Map.of(
            "care", Set.of("plant", "water", "weed", "pesticide", "harvest", "clear", "feed", "collect"),
            "trade", Set.of("buy", "purchase", "sell", "animal-sale"),
            "nature", Set.of("friend-pest"));
    private final List<Mission> missions;
    private final boolean cooperative;
    private LocalDate day;
    private final Map<String, Integer> progress = new HashMap<>();
    private final Set<String> completed = new HashSet<>();
    public DailyMissionBoard(LocalDate today) { this(today, false); }
    public DailyMissionBoard(LocalDate today, boolean cooperative) {
        day = today;
        this.cooperative = cooperative;
        missions = cooperative ? Stream.concat(SOLO.stream(), COOPERATIVE.stream()).toList() : SOLO;
    }
    public void resetIfNewDay(LocalDate today) {
        if (!today.isAfter(day)) return;
        day = today;
        progress.clear();
        completed.clear();
    }
    public List<Mission> missions() { return List.copyOf(missions); }
    public boolean cooperative() { return cooperative; }
    public boolean cooperativeBonusPaid() { return completed.contains(COOPERATIVE_BONUS.id()); }
    /** Missões concluídas por esta ação, mais {@link #COOPERATIVE_BONUS} quando o bônus é pago agora. */
    public List<Mission> record(String action, int amount, LocalDate today) {
        if (today.isBefore(day) || amount <= 0) return List.of();
        resetIfNewDay(today);
        List<Mission> rewards = new ArrayList<>();
        for (Mission mission : missions) {
            if (!ACTIONS.get(mission.id()).contains(action) || completed.contains(mission.id())) continue;
            int value = Math.min(mission.target(), progress(mission) + amount);
            progress.put(mission.id(), value);
            if (value < mission.target()) continue;
            completed.add(mission.id());
            rewards.add(mission);
        }
        if (cooperative && !cooperativeBonusPaid() && cooperativeDone()) {
            completed.add(COOPERATIVE_BONUS.id());
            rewards.add(COOPERATIVE_BONUS);
        }
        return rewards;
    }
    public int progress(Mission mission) { return progress.getOrDefault(mission.id(), 0); }
    public boolean completed(Mission mission) { return completed.contains(mission.id()); }
    public LocalDate day() { return day; }
    public Map<String, Integer> progressSnapshot() { return Map.copyOf(progress); }
    public List<String> completedSnapshot() { return List.copyOf(completed); }
    private boolean cooperativeDone() { return COOPERATIVE.stream().allMatch(mission -> completed.contains(mission.id())); }
    /**
     * Saves sem as chaves novas começam com progresso zero. Bônus pago exige ao menos uma missão cooperativa
     * concluída (não todas, para aceitar o dia em que outra missão cooperativa for publicada); todas concluídas
     * exigem o bônus pago. O desktop recusa chaves cooperativas.
     */
    public void restore(String savedDay, Map<String, Integer> savedProgress, List<String> savedCompleted, LocalDate today) {
        day = LocalDate.parse(savedDay);
        progress.clear();
        completed.clear();
        Set<String> known = new HashSet<>();
        for (Mission mission : missions) known.add(mission.id());
        boolean bonus = savedCompleted.contains(COOPERATIVE_BONUS.id());
        if (!known.containsAll(savedProgress.keySet()) || (bonus && !cooperative)
                || savedCompleted.stream().anyMatch(id -> !known.contains(id) && !id.equals(COOPERATIVE_BONUS.id())))
            throw new IllegalArgumentException("Unknown daily mission");
        for (Mission mission : missions) {
            int value = savedProgress.getOrDefault(mission.id(), 0);
            boolean paid = savedCompleted.contains(mission.id());
            if (value < 0 || value > mission.target() || paid != (value == mission.target()))
                throw new IllegalArgumentException("Invalid mission payment");
            if (value > 0) progress.put(mission.id(), value);
            if (paid) completed.add(mission.id());
        }
        if (cooperative && (bonus ? COOPERATIVE.stream().noneMatch(mission -> completed.contains(mission.id())) : cooperativeDone()))
            throw new IllegalArgumentException("Invalid cooperative bonus");
        if (bonus) completed.add(COOPERATIVE_BONUS.id());
        resetIfNewDay(today);
    }
}
