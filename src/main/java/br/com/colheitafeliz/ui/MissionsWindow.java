package br.com.colheitafeliz.ui;

import br.com.colheitafeliz.game.DailyMissionBoard;
import br.com.colheitafeliz.game.GameState;
import br.com.colheitafeliz.game.Tool;
import br.com.colheitafeliz.game.Tutorial;
import br.com.colheitafeliz.render.GameAssets;
import br.com.colheitafeliz.render.Palette;
import br.com.colheitafeliz.render.UiSkin;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Align;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.Instant;

/** Janela das missões diárias: as duas tarefas fixas, com barra de progresso e a recompensa de 1 verde cada. */
public final class MissionsWindow {
    public static final float LEFT = 430, BOTTOM = 190, WIDTH = 740, HEIGHT = 520;
    private static final float BAR = 46, ROW_H = 104;
    private boolean open;

    public boolean isOpen() { return open; }
    public void toggle() { open = !open; }
    public void close() { open = false; }
    public boolean handles(float x, float y) { return open; }

    public static Rectangle closeButton() { return new Rectangle(LEFT + WIDTH - 44, BOTTOM + HEIGHT - 40, 34, 34); }

    /** Fecha no "×" ou com clique fora da janela; cliques dentro não fazem nada. */
    public void click(float x, float y) {
        if (closeButton().contains(x, y) || x < LEFT || x > LEFT + WIDTH || y < BOTTOM || y > BOTTOM + HEIGHT) close();
    }

    public void draw(SpriteBatch batch, UiSkin skin, GameAssets assets, GameState state, float px, float py) {
        if (!open) return;
        skin.rect(batch, Palette.SCRIM, 0, 0, 1600, 900);
        skin.dropShadow(batch, LEFT, BOTTOM, WIDTH, HEIGHT, 6);
        skin.draw(batch, skin.window, LEFT, BOTTOM, WIDTH, HEIGHT);
        skin.draw(batch, skin.windowBar, LEFT + 1, BOTTOM + HEIGHT - BAR, WIDTH - 2, BAR - 1);
        skin.rect(batch, Palette.CHROME_LO, LEFT + 1, BOTTOM + HEIGHT - BAR, WIDTH - 2, 1);
        boolean tutorial = state.tutorial().active();
        skin.shadowed(batch, skin.title, tutorial ? "Primeiros passos" : "Missões do dia", LEFT + WIDTH / 2f, BOTTOM + HEIGHT - 14,
                Palette.INK, Palette.WINDOW, Align.center);
        Rectangle close = closeButton();
        boolean overClose = close.contains(px, py);
        if (overClose) skin.tinted(batch, skin.chip, Palette.BERRY_LO, close.x, close.y, close.width, close.height);
        skin.text(batch, skin.title, "×", close.x + close.width / 2f, close.y + 27,
                overClose ? Palette.ON_COLOR : Palette.INK_MUTED, Align.center);

        if (tutorial) drawTutorial(batch, skin, assets, state.tutorial());
        int index = tutorial ? 1 : 0;
        for (DailyMissionBoard.Mission mission : state.dailyMissions().missions()) {
            float x = LEFT + 20, y = BOTTOM + HEIGHT - BAR - 20 - (index + 1) * ROW_H + 8, w = WIDTH - 40, h = ROW_H - 12;
            boolean done = state.dailyMissions().completed(mission);
            int amount = state.dailyMissions().progress(mission);
            skin.draw(batch, done ? skin.missionDone : skin.mission, x, y, w, h);
            Toolbar.drawFitted(batch, icon(assets, mission), x + 44, y + h / 2f, 60);
            skin.text(batch, skin.label, mission.title(), x + 88, y + h - 22, Palette.INK, Align.left);
            skin.bar(batch, done ? skin.barSun : skin.barLeaf, x + 88, y + 18, 300, 20,
                    amount / (float) mission.target(), amount + " / " + mission.target());
            float rx = x + w - 20;
            skin.text(batch, skin.label, "+1 verde", rx, y + h - 22, Palette.LEAF_INK, Align.right);
            Toolbar.drawFitted(batch, assets.greenCoin, rx - 90, y + h - 28, 22);
            skin.text(batch, skin.caption, done ? "Concluída · recompensa recebida" : "Em andamento",
                    rx, y + 32, done ? Palette.LEAF_INK : Palette.INK_MUTED, Align.right);
            index++;
        }
        skin.text(batch, skin.caption, "Novas missões em " + untilMidnight(state.dailyMissions().day(), state.realNow())
                + " (meia-noite UTC)  ·  [Q] fecha", LEFT + WIDTH / 2f, BOTTOM + 26, Palette.INK_MUTED, Align.center);
    }

    /** Durante os primeiros passos, a primeira linha mostra a tarefa atual e o progresso do tutorial; as tarefas diárias, que já contam, ficam abaixo. */
    private static void drawTutorial(SpriteBatch batch, UiSkin skin, GameAssets assets, Tutorial tutorial) {
        Tutorial.Step step = tutorial.current();
        float x = LEFT + 20, y = BOTTOM + HEIGHT - BAR - 20 - ROW_H + 8, w = WIDTH - 40, h = ROW_H - 12;
        skin.draw(batch, skin.mission, x, y, w, h);
        Toolbar.drawFitted(batch, icon(assets, step.action()), x + 44, y + h / 2f, 60);
        skin.text(batch, skin.label, step.title(), x + 88, y + h - 22, Palette.INK, Align.left);
        skin.text(batch, skin.caption, step.hint(), x + 88, y + h - 46, Palette.INK_MUTED, Align.left);
        skin.bar(batch, skin.barLeaf, x + 88, y + 8, 360, 16,
                tutorial.stepIndex() / (float) Tutorial.STEPS.size(), tutorial.stepIndex() + " / " + Tutorial.STEPS.size());

    }

    private static Texture icon(GameAssets assets, DailyMissionBoard.Mission mission) { return icon(assets, mission.action()); }

    private static Texture icon(GameAssets assets, String action) {
        return switch (action) {
            case "plant" -> assets.bag;
            case "water" -> assets.water;
            case "harvest" -> assets.tool(Tool.HAND);
            case "sell" -> assets.coin;
            case "plow" -> assets.hoe;
            case "buy" -> assets.shop;
            case "collect" -> assets.nestEggs;
            case "care" -> assets.water;
            default -> assets.shop;
        };
    }

    private static String untilMidnight(LocalDate day, Instant now) {
        Duration left = Duration.between(now, day.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant());
        if (left.isNegative()) left = Duration.ZERO;
        long minutes = left.toMinutes();
        return minutes >= 60 ? minutes / 60 + "h" + String.format("%02d", minutes % 60) : minutes + " min";
    }
}
