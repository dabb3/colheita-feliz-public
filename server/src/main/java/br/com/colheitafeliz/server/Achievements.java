package br.com.colheitafeliz.server;

import br.com.colheitafeliz.game.GameState;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** As 40 conquistas de conquistas.md. A recompensa é só o título; liberado uma vez, nunca é revogado. */
final class Achievements {
    enum Category {
        CLEAN("clean", "🐛 Limpeza de pragas"),
        PLANT("plant", "🌱 Plantio de sementes"),
        HARVEST("harvest", "🌾 Colheitas realizadas"),
        SPREAD("spread", "😈 Pragas jogadas nos amigos"),
        STEAL("steal", "🥷 Colheitas roubadas"),
        LEVEL("level", "⭐ Níveis"),
        GREEN("green", "💸 Moedas verdes"),
        PLOTS("plots", "🏡 Todos os terrenos (30/30)"),
        ANIMALS("animals", "🐮 Todos os animais (15/15)");

        final String id; final String label;
        Category(String id, String label) { this.id = id; this.label = label; }
    }

    record Achievement(String id, Category category, long target, String title) {}

    static final List<Achievement> ALL = List.of(
            a("clean-100", Category.CLEAN, 100, "★ Mata-Mosquito ★"),
            a("clean-250", Category.CLEAN, 250, "۞ Caçador de Pragas ۞"),
            a("clean-500", Category.CLEAN, 500, "•° Dedetizador VIP °•"),
            a("clean-1000", Category.CLEAN, 1_000, "♛ Senhor dos Insetos ♛"),
            a("clean-5000", Category.CLEAN, 5_000, "мєgα єxтєямιиα∂σя"),
            a("plant-100", Category.PLANT, 100, "•° Camponês •°"),
            a("plant-250", Category.PLANT, 250, "★ Playboy Rural ★"),
            a("plant-500", Category.PLANT, 500, "♥ Mãe Natureza ♥"),
            a("plant-1000", Category.PLANT, 1_000, "♛ Senhor das Sementes ♛"),
            a("plant-5000", Category.PLANT, 5_000, "♚ 𝕽𝖊𝖎 𝖉𝖔 𝕬𝖌𝖗𝖔 ♚"),
            a("harvest-500", Category.HARVEST, 500, "★ Mão na Enxada ★"),
            a("harvest-1000", Category.HARVEST, 1_000, "•° Mestre da Colheita °•"),
            a("harvest-2500", Category.HARVEST, 2_500, "♛ Magnata Rural ♛"),
            a("harvest-5000", Category.HARVEST, 5_000, "۞ O Poderoso Fazendeiro ۞"),
            a("harvest-10000", Category.HARVEST, 10_000, "۞ LENDÁЯIO ۞"),
            a("spread-100", Category.SPREAD, 100, "•° Praga Amarga •°"),
            a("spread-250", Category.SPREAD, 250, "☠ O Pestinha ☠"),
            a("spread-500", Category.SPREAD, 500, "☠ Capeta da Roça ☠"),
            a("spread-1000", Category.SPREAD, 1_000, "♛ 171 Online ♛"),
            a("spread-5000", Category.SPREAD, 5_000, "мαℓ∂α∂є єм ρєѕѕσα"),
            a("steal-100", Category.STEAL, 100, "•° Ladrão de Galinha °•"),
            a("steal-250", Category.STEAL, 250, "☠ Ladrãozinho VIP ☠"),
            a("steal-500", Category.STEAL, 500, "♛ Don Corleone ♛"),
            a("steal-1000", Category.STEAL, 1_000, "♛ Rei do Furto ♛"),
            a("steal-5000", Category.STEAL, 5_000, "★ αѕѕαℓтσ à ƒαzєи∂α ¢єитяαℓ ★"),
            a("level-30", Category.LEVEL, 30, "★ Veterano VIP ★"),
            a("level-40", Category.LEVEL, 40, "♛ Elite da Fazenda ♛"),
            a("level-50", Category.LEVEL, 50, "۞ Lenda do Orkut ۞"),
            a("level-60", Category.LEVEL, 60, "♛ Patrão Online ♛"),
            a("green-1000", Category.GREEN, 1_000, "★ Rico de Berço ★"),
            a("green-2000", Category.GREEN, 2_000, "$ Dono da Verdinha $"),
            a("green-3000", Category.GREEN, 3_000, "۞ Patrão da Fazenda ۞"),
            a("green-4000", Category.GREEN, 4_000, "♛ Rei do Malote ♛"),
            a("green-5000", Category.GREEN, 5_000, "★ Império Rural ★"),
            a("plots-10", Category.PLOTS, 10, "•° Dono do Pedaço °•"),
            a("plots-20", Category.PLOTS, 20, "★ Fã de Carteirinha ★"),
            a("plots-30", Category.PLOTS, 30, "♛ Rei do Gado ♛"),
            a("animals-5", Category.ANIMALS, 5, "★ Rei dos Bichinhos ★"),
            a("animals-10", Category.ANIMALS, 10, "♛ Dono do Zoológico ♛"),
            a("animals-15", Category.ANIMALS, 15, "♥ Nσé ∂σ Cσℓнєιтα ♥"));

    private static final Map<String, Achievement> BY_ID = ALL.stream().collect(Collectors.toUnmodifiableMap(Achievement::id, Function.identity()));

    private static Achievement a(String id, Category category, long target, String title) { return new Achievement(id, category, target, title); }

    /** Contadores cumulativos contam a partir da implantação; nível, terrenos e animais vêm do estado atual. */
    record Progress(long clean, long plant, long harvest, long spread, long steal, BigDecimal green,
                    Set<String> unlocked, String equipped) {
        Progress {
            if (clean < 0 || plant < 0 || harvest < 0 || spread < 0 || steal < 0 || green.signum() < 0)
                throw new IllegalArgumentException("Negative achievement counter");
            unlocked = Set.copyOf(unlocked);
            if (!unlocked.stream().allMatch(BY_ID::containsKey)) throw new IllegalArgumentException("Unknown achievement");
            if (equipped != null && !unlocked.contains(equipped)) throw new IllegalArgumentException("Equipped title is locked");
        }
        static Progress empty() { return new Progress(0, 0, 0, 0, 0, BigDecimal.ZERO, Set.of(), null); }
    }

    /** O que uma ação confirmada acrescenta aos contadores cumulativos. */
    record Delta(long clean, long plant, long harvest, long spread, long steal, BigDecimal green) {
        static final Delta NONE = new Delta(0, 0, 0, 0, 0, BigDecimal.ZERO);
        static Delta of(Category category) {
            return new Delta(category == Category.CLEAN ? 1 : 0, category == Category.PLANT ? 1 : 0,
                    category == Category.HARVEST ? 1 : 0, category == Category.SPREAD ? 1 : 0,
                    category == Category.STEAL ? 1 : 0, BigDecimal.ZERO);
        }
        Delta withGreen(BigDecimal spent) { return new Delta(clean, plant, harvest, spread, steal, green.add(spent)); }
        boolean empty() { return clean == 0 && plant == 0 && harvest == 0 && spread == 0 && steal == 0 && green.signum() == 0; }
    }

    private Achievements() {}

    static Achievement find(String id) { return id == null ? null : BY_ID.get(id); }

    static long value(Progress progress, GameState game, Category category) {
        return switch (category) {
            case CLEAN -> progress.clean();
            case PLANT -> progress.plant();
            case HARVEST -> progress.harvest();
            case SPREAD -> progress.spread();
            case STEAL -> progress.steal();
            case LEVEL -> game.progress().level();
            case GREEN -> progress.green().longValue();
            case PLOTS -> game.land().snapshot().size();
            case ANIMALS -> game.barn().animals().stream().map(animal -> animal.type().id()).distinct().count();
        };
    }

    /** Soma o que já foi liberado com o que o estado atual alcança; nada sai do conjunto. */
    static Set<String> unlocked(Progress progress, GameState game) {
        var result = new LinkedHashSet<>(progress.unlocked());
        for (var achievement : ALL)
            if (value(progress, game, achievement.category()) >= achievement.target()) result.add(achievement.id());
        return Set.copyOf(result);
    }

    static String equippedTitle(String id) {
        var achievement = find(id);
        return achievement == null ? null : achievement.title();
    }

    static JsonObject view(Progress progress, GameState game) {
        var list = new JsonArray();
        for (var achievement : ALL) {
            var item = new JsonObject();
            item.addProperty("id", achievement.id());
            item.addProperty("category", achievement.category().id);
            item.addProperty("categoryLabel", achievement.category().label);
            item.addProperty("title", achievement.title());
            item.addProperty("target", achievement.target());
            item.addProperty("progress", Math.min(achievement.target(), value(progress, game, achievement.category())));
            item.addProperty("unlocked", progress.unlocked().contains(achievement.id()));
            list.add(item);
        }
        var view = new JsonObject();
        view.add("achievements", list);
        view.addProperty("equippedTitleId", progress.equipped());
        view.addProperty("equippedTitle", equippedTitle(progress.equipped()));
        return view;
    }

    static Collection<Category> categories() { return List.of(Category.values()); }
}
