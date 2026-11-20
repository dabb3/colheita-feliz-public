package br.com.colheitafeliz.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

/** Tarefas semanais: viram domingo 00:00 em America/Sao_Paulo e pagam 100 XP uma vez por semana cada. */
final class WeeklyTasks {
    static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    static final int REWARD_XP = 100;

    enum Task {
        HARVEST("harvest", "Colha 12 plantas", 12),
        BUY_ANIMAL("buy-animals", "Compre 5 animais", 5),
        LOVE("love-friends", "Adube 30 plantas de amigos com fertilizante do amor", 30);

        final String id; final String title; final int target;
        Task(String id, String title, int target) { this.id = id; this.title = title; this.target = target; }
    }

    /** Contadores da semana; cada um para na meta, então chegar nela é o único momento de pagar. */
    record Progress(LocalDate weekStart, int harvests, int animals, int love) {
        Progress {
            if (weekStart.getDayOfWeek() != DayOfWeek.SUNDAY) throw new IllegalArgumentException("Week must start on Sunday");
            if (harvests < 0 || animals < 0 || love < 0) throw new IllegalArgumentException("Negative weekly progress");
        }
        static Progress empty(LocalDate weekStart) { return new Progress(weekStart, 0, 0, 0); }
        int value(Task task) {
            return switch (task) { case HARVEST -> harvests; case BUY_ANIMAL -> animals; case LOVE -> love; };
        }
        Progress plusOne(Task task) {
            if (value(task) >= task.target) return this;
            return switch (task) {
                case HARVEST -> new Progress(weekStart, harvests + 1, animals, love);
                case BUY_ANIMAL -> new Progress(weekStart, harvests, animals + 1, love);
                case LOVE -> new Progress(weekStart, harvests, animals, love + 1);
            };
        }
    }

    private WeeklyTasks() {}

    static LocalDate weekStart(Instant now) {
        return LocalDate.ofInstant(now, ZONE).with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
    }

    /** A semana guardada vale até a próxima virada; um relógio atrasado nunca reabre a semana anterior. */
    static Progress current(Progress stored, Instant now) {
        LocalDate week = weekStart(now);
        if (stored == null || stored.weekStart().isBefore(week)) return Progress.empty(week);
        return stored;
    }

    static JsonObject view(Progress progress) {
        var tasks = new JsonArray();
        for (Task task : Task.values()) {
            var item = new JsonObject();
            item.addProperty("id", task.id);
            item.addProperty("title", task.title);
            item.addProperty("progress", progress.value(task));
            item.addProperty("target", task.target);
            item.addProperty("rewardXp", REWARD_XP);
            item.addProperty("done", progress.value(task) >= task.target);
            tasks.add(item);
        }
        var view = new JsonObject();
        view.addProperty("weekStartsAt", progress.weekStart().atStartOfDay(ZONE).toInstant().toString());
        view.addProperty("nextResetAt", progress.weekStart().plusWeeks(1).atStartOfDay(ZONE).toInstant().toString());
        view.add("tasks", tasks);
        return view;
    }

    static List<Task> all() { return List.of(Task.values()); }
}
