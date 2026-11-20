package br.com.colheitafeliz.server;

import br.com.colheitafeliz.game.CropCatalogLoader;
import br.com.colheitafeliz.game.GameState;
import br.com.colheitafeliz.domain.economy.Currency;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AchievementsTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-20T12:00:00Z"), ZoneOffset.UTC);

    @Test void catalogMatchesConquistasMdExactlyIncludingUnicode() throws Exception {
        Path file = Path.of("conquistas.md");
        if (!Files.exists(file)) file = Path.of("..", "conquistas.md");
        var rows = new ArrayList<String>();
        var row = Pattern.compile("^\\| ([^|]+) \\| (.+) \\|$");
        var number = Pattern.compile("(\\d[\\d.]*)");
        for (String line : Files.readAllLines(file)) {
            var match = row.matcher(line);
            if (!match.matches() || match.group(1).equals("Meta")) continue;
            var target = number.matcher(match.group(1));
            assertTrue(target.find(), line);
            rows.add(target.group(1).replace(".", "") + "=" + match.group(2));
        }
        assertEquals(40, rows.size());
        assertEquals(rows, Achievements.ALL.stream().map(item -> item.target() + "=" + item.title()).toList());
        assertEquals(40, Achievements.ALL.stream().map(Achievements.Achievement::id).distinct().count());
        assertEquals(9, Achievements.categories().size());
    }

    @Test void everyCumulativeCategoryUnlocksExactlyAtItsTarget() {
        var game = new GameState(clock::instant, clock, CropCatalogLoader.load(), bound -> 0);
        for (var category : List.of(Achievements.Category.CLEAN, Achievements.Category.PLANT, Achievements.Category.HARVEST,
                Achievements.Category.SPREAD, Achievements.Category.STEAL, Achievements.Category.GREEN)) {
            for (var achievement : Achievements.ALL.stream().filter(item -> item.category() == category).toList()) {
                for (long offset : new long[] {-1, 0, 1}) {
                    long value = achievement.target() + offset;
                    var progress = progress(category, value);
                    assertEquals(offset >= 0, Achievements.unlocked(progress, game).contains(achievement.id()), achievement.id() + " " + value);
                }
            }
        }
    }

    @Test void levelPlotsAndAnimalsAreReadFromTheCurrentFarmAndStayUnlockedAfterwards() {
        var game = new GameState(clock::instant, clock, CropCatalogLoader.load(), bound -> 0);
        while (game.progress().level() < 40) game.progress().award(game.progress().xpToNext());
        var unlocked = Achievements.unlocked(Achievements.Progress.empty(), game);
        assertTrue(unlocked.containsAll(Set.of("level-30", "level-40")));
        assertFalse(unlocked.contains("level-50"));
        assertFalse(unlocked.contains("plots-10"), "a fazenda nova não tem 10 terrenos");

        game.wallet().credit(Currency.GOLD, new BigDecimal("100000000"));
        int bought = 0;
        for (var type : game.animalTypes()) if (bought < 5 && game.buyAnimal(type, Currency.GOLD).success()) bought++;
        assertEquals(5, bought);
        unlocked = Achievements.unlocked(Achievements.Progress.empty(), game);
        assertTrue(unlocked.contains("animals-5"));
        var kept = new Achievements.Progress(0, 0, 0, 0, 0, BigDecimal.ZERO, unlocked, "animals-5");
        var fresh = new GameState(clock::instant, clock, CropCatalogLoader.load(), bound -> 0);
        assertTrue(Achievements.unlocked(kept, fresh).contains("animals-5"), "título liberado nunca é revogado");
    }

    @Test void equippedTitleMustBeUnlockedAndKnown() {
        assertThrows(IllegalArgumentException.class, () -> new Achievements.Progress(0, 0, 0, 0, 0, BigDecimal.ZERO, Set.of(), "level-30"));
        assertThrows(IllegalArgumentException.class, () -> new Achievements.Progress(0, 0, 0, 0, 0, BigDecimal.ZERO, Set.of("level-99"), null));
        assertNull(Achievements.equippedTitle("<script>"));
        assertEquals("♚ 𝕽𝖊𝖎 𝖉𝖔 𝕬𝖌𝖗𝖔 ♚", Achievements.equippedTitle("plant-5000"));
    }

    private static Achievements.Progress progress(Achievements.Category category, long value) {
        return new Achievements.Progress(
                category == Achievements.Category.CLEAN ? value : 0, category == Achievements.Category.PLANT ? value : 0,
                category == Achievements.Category.HARVEST ? value : 0, category == Achievements.Category.SPREAD ? value : 0,
                category == Achievements.Category.STEAL ? value : 0,
                category == Achievements.Category.GREEN ? BigDecimal.valueOf(value) : BigDecimal.ZERO, Set.of(), null);
    }
}
