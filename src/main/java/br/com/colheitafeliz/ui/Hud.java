package br.com.colheitafeliz.ui;

import br.com.colheitafeliz.domain.animal.AnimalType;
import br.com.colheitafeliz.domain.animal.Animal;
import br.com.colheitafeliz.domain.crop.Crop;
import br.com.colheitafeliz.domain.crop.CropType;
import br.com.colheitafeliz.domain.farm.FarmTile;
import br.com.colheitafeliz.domain.farm.PestState;
import br.com.colheitafeliz.domain.farm.TileState;
import br.com.colheitafeliz.game.DailyMissionBoard;
import br.com.colheitafeliz.game.GameState;
import br.com.colheitafeliz.game.GameText;
import br.com.colheitafeliz.game.Tutorial;
import br.com.colheitafeliz.game.LandExpansion;
import br.com.colheitafeliz.render.GameAssets;
import br.com.colheitafeliz.render.Palette;
import br.com.colheitafeliz.render.UiSkin;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Align;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

/**
 * Camada do HUD sobre a fazenda: cartão do perfil, ícones de atalho, avisos (toasts), números que sobem
 * (floaters), banner de subir de nível e o balão de status do canteiro.
 */
public final class Hud {
    public enum Shortcut { ALERTS, MISSIONS, SHOP, DEPOSIT, SETTINGS }
    /** Ações do painel de Ajustes, executadas por quem trata a entrada. */
    public enum Setting { MUSIC, EFFECTS, SPEED, DEBUG, SAVE }
    private enum Panel { NONE, ALERTS, SETTINGS }
    public enum ToastKind { NORMAL, REWARD, ALERT }
    public enum FloaterStyle { COIN, XP, ITEM }

    private record Toast(Texture icon, String bold, String rest, ToastKind kind) {}
    private record Floater(float x, float y, Texture icon, String text, FloaterStyle style, long start) {}

    private static final float CARD_X = 16, CARD_W = 330, CARD_H = 92, CARD_Y = 900 - 14 - CARD_H;
    private static final float ICON_W = 78, ICON_TOP = 890;
    private static final long TOAST_NANOS = 3_200_000_000L, TOAST_FADE = 250_000_000L;
    private static final long FLOATER_NANOS = 1_100_000_000L, BANNER_NANOS = 2_600_000_000L, BUMP_NANOS = 360_000_000L;

    private final Deque<Toast> toasts = new ArrayDeque<>();
    private long toastStarted;
    private final List<Floater> floaters = new ArrayList<>();
    private String bannerTitle, bannerSub;
    private long bannerStarted;
    private java.math.BigDecimal lastGold;
    private Panel panel = Panel.NONE;
    private long bumpStarted;

    // ---------- eventos ----------

    public void toast(Texture icon, String bold, String rest, ToastKind kind) {
        for (Toast t : toasts) if (t.bold().equals(bold) && t.rest().equals(rest)) return;
        if (toasts.isEmpty()) toastStarted = System.nanoTime();
        while (toasts.size() >= 3) toasts.removeLast();
        toasts.addLast(new Toast(icon, bold, rest, kind));
    }

    public void floater(float x, float y, Texture icon, String text, FloaterStyle style, long delayNanos) {
        floaters.add(new Floater(x, y, icon, text, style, System.nanoTime() + delayNanos));
    }

    public void levelUp(int level, List<CropType> unlocked) {
        bannerTitle = "Nível " + level + "!";
        bannerSub = unlocked.isEmpty() ? "Continue colhendo para liberar mais culturas"
                : joinNames(unlocked) + (unlocked.size() == 1 ? " liberado na Loja" : " liberados na Loja");
        bannerStarted = System.nanoTime();
    }

    public boolean bannerVisible() { return bannerTitle != null; }

    /** Limpa avisos, floaters e banner (usado pela revisão visual entre cenas). */
    public void reset() { toasts.clear(); floaters.clear(); bannerTitle = null; lastGold = null; panel = Panel.NONE; }
    public void dismissBanner() { bannerTitle = null; }

    // ---------- atalhos ----------

    public static Rectangle shortcutBounds(Shortcut shortcut) {
        int fromRight = Shortcut.values().length - 1 - shortcut.ordinal();
        float cx = 1600 - 18 - ICON_W / 2f - fromRight * (ICON_W + 6);
        return new Rectangle(cx - ICON_W / 2f, ICON_TOP - 88, ICON_W, 88);
    }

    public Shortcut shortcutHit(float x, float y) {
        for (Shortcut s : Shortcut.values()) if (shortcutBounds(s).contains(x, y)) return s;
        return null;
    }

    // ---------- painéis do sininho e de ajustes ----------

    private static final float PANEL_W = 340, ROW = 40;
    private static final Setting[] SETTINGS = Setting.values();

    public void toggleAlerts() { panel = panel == Panel.ALERTS ? Panel.NONE : Panel.ALERTS; }
    public void toggleSettings() { panel = panel == Panel.SETTINGS ? Panel.NONE : Panel.SETTINGS; }
    public boolean panelOpen() { return panel != Panel.NONE; }
    public void closePanel() { panel = Panel.NONE; }

    public static Rectangle settingRow(int index) {
        Rectangle anchor = shortcutBounds(Shortcut.SETTINGS);
        float x = anchor.x + anchor.width - PANEL_W, top = anchor.y - 8 - 12;
        return new Rectangle(x + 10, top - (index + 1) * ROW, PANEL_W - 20, ROW - 4);
    }

    /** Clique com um painel aberto: devolve o ajuste escolhido, ou nulo; clique fora fecha o painel. */
    public Setting panelClick(float x, float y) {
        if (panel == Panel.SETTINGS)
            for (int i = 0; i < SETTINGS.length; i++) if (settingRow(i).contains(x, y)) return SETTINGS[i];
        panel = Panel.NONE;
        return null;
    }

    // ---------- desenho ----------

    public void drawTop(SpriteBatch batch, UiSkin skin, GameAssets assets, GameState state, float px, float py,
                        double speed, boolean debug) {
        drawProfile(batch, skin, assets, state);
        Toolbar.drawFitted(batch, assets.gift, CARD_X + 32, GIFT_Y + GIFT / 2f, GIFT);
        if (state.tutorial().active()) drawTutorial(batch, skin, assets, state.tutorial());
        drawShortcuts(batch, skin, assets, state, px, py);
        if (panel == Panel.ALERTS) drawAlerts(batch, skin, state.alerts());
        if (panel == Panel.SETTINGS) drawSettings(batch, skin, state, speed, debug, px, py);
    }

    private static final float GIFT = 50, GIFT_Y = CARD_Y - 8 - GIFT;

    private void drawAlerts(SpriteBatch batch, UiSkin skin, List<GameState.Alert> alerts) {
        Rectangle anchor = shortcutBounds(Shortcut.ALERTS);
        int shown = Math.min(alerts.size(), 8);
        float h = 50 + Math.max(1, shown) * 30 + (alerts.size() > shown ? 26 : 0);
        float x = Math.min(anchor.x, 1600 - 12 - PANEL_W), y = anchor.y - 8 - h;
        skin.dropShadow(batch, x, y, PANEL_W, h, 4);
        skin.draw(batch, skin.window, x, y, PANEL_W, h);
        skin.text(batch, skin.label, "Avisos da fazenda", x + 16, y + h - 16, Palette.INK, Align.left);
        if (alerts.isEmpty())
            skin.text(batch, skin.body, "Tudo certo por aqui.", x + 16, y + h - 52, Palette.INK_MUTED, Align.left);
        for (int i = 0; i < shown; i++) {
            float ry = y + h - 52 - i * 30;
            skin.text(batch, skin.label, alerts.get(i).title(), x + 16, ry, Palette.BERRY_INK, Align.left);
            skin.text(batch, skin.body, alerts.get(i).detail(), x + PANEL_W - 16, ry, Palette.INK_MUTED, Align.right);
        }
        if (alerts.size() > shown)
            skin.text(batch, skin.caption, "e mais " + (alerts.size() - shown), x + 16, y + 18, Palette.INK_MUTED, Align.left);
    }

    private void drawSettings(SpriteBatch batch, UiSkin skin, GameState state, double speed, boolean debug,
                              float px, float py) {
        Rectangle first = settingRow(0), last = settingRow(SETTINGS.length - 1);
        float x = first.x - 10, y = last.y - 12, h = first.y + first.height + 54 - y;
        skin.dropShadow(batch, x, y, PANEL_W, h, 4);
        skin.draw(batch, skin.window, x, y, PANEL_W, h);
        skin.text(batch, skin.label, "Ajustes", x + 16, y + h - 16, Palette.INK, Align.left);
        for (int i = 0; i < SETTINGS.length; i++) {
            Rectangle r = settingRow(i);
            if (r.contains(px, py)) skin.draw(batch, skin.itemHover, r.x, r.y, r.width, r.height);
            String label = switch (SETTINGS[i]) {
                case MUSIC -> "Música (M)";
                case EFFECTS -> "Efeitos sonoros (N)";
                case SPEED -> "Tempo acelerado (T)";
                case DEBUG -> "Overlay técnico (F3)";
                case SAVE -> "Salvar agora (S)";
            };
            String value = switch (SETTINGS[i]) {
                case MUSIC -> state.musicEnabled() ? "Ligada" : "Desligada";
                case EFFECTS -> state.effectsEnabled() ? "Ligados" : "Desligados";
                case SPEED -> speed > 1 ? "60×" : "1×";
                case DEBUG -> debug ? "Ligado" : "Desligado";
                case SAVE -> "";
            };
            skin.text(batch, skin.body, label, r.x + 10, r.y + 26, Palette.INK, Align.left);
            skin.text(batch, skin.label, value, r.x + r.width - 10, r.y + 26, Palette.SKY_INK, Align.right);
        }
    }

    /** Card dos primeiros passos, logo abaixo do perfil: tarefa atual, dica e recompensa. */
    private void drawTutorial(SpriteBatch batch, UiSkin skin, GameAssets assets, Tutorial tutorial) {
        Tutorial.Step step = tutorial.current();
        float w = 420, h = 96, x = CARD_X, y = GIFT_Y - 8 - h;
        skin.dropShadow(batch, x, y, w, h, 2);
        skin.draw(batch, skin.hudCard, x, y, w, h);
        skin.text(batch, skin.caption, "Primeiros passos · " + (tutorial.stepIndex() + 1) + "/" + Tutorial.STEPS.size(),
                x + 16, y + h - 14, Palette.INK_MUTED, Align.left);
        skin.text(batch, skin.label, step.title(), x + 16, y + h - 40, Palette.INK, Align.left);
        skin.text(batch, skin.caption, step.hint(), x + 16, y + 22, Palette.INK_MUTED, Align.left);
        float rx = x + w - 16;
        String xp = "+" + step.xp(), coins = "+" + step.coins();
        float xpW = skin.width(skin.label, xp);
        skin.text(batch, skin.label, xp, rx, y + h - 40, Palette.SKY_INK, Align.right);
        Toolbar.drawFitted(batch, assets.star, rx - xpW - 14, y + h - 46, 22);
        float coinsRight = rx - xpW - 34;
        skin.text(batch, skin.label, coins, coinsRight, y + h - 40, Palette.COIN_INK, Align.right);
        Toolbar.drawFitted(batch, assets.coin, coinsRight - skin.width(skin.label, coins) - 14, y + h - 46, 22);
    }

    private void drawProfile(SpriteBatch batch, UiSkin skin, GameAssets assets, GameState state) {
        var coins = state.wallet().coins();
        long now = System.nanoTime();
        if (lastGold != null && coins.compareTo(lastGold) > 0) bumpStarted = now;
        lastGold = coins;
        skin.dropShadow(batch, CARD_X, CARD_Y, CARD_W, CARD_H, 2);
        skin.draw(batch, skin.hudCard, CARD_X, CARD_Y, CARD_W, CARD_H);
        skin.draw(batch, skin.sheet, CARD_X + 8, CARD_Y + 10, 72, 72);
        batch.draw(assets.avatar, CARD_X + 11, CARD_Y + 13, 66, 66);
        float tx = CARD_X + 92;
        String name = "Minha fazenda";
        skin.text(batch, skin.label, name, tx, CARD_Y + CARD_H - 14, Palette.INK, Align.left);
        float starX = tx + skin.width(skin.label, name) + 26;
        Toolbar.drawFitted(batch, assets.star, starX - 4, CARD_Y + CARD_H - 21, 28);
        skin.label.getData().setScale(.8f);
        skin.outlined(batch, skin.label, Integer.toString(state.progress().level()), starX - 4, CARD_Y + CARD_H - 15,
                Palette.ON_COLOR, Palette.SKY_INK, 1f, Align.center);
        skin.label.getData().setScale(1);
        skin.micro.getData().setScale(.85f);
        skin.bar(batch, skin.barSky, tx, CARD_Y + 42, 220, 14,
                state.progress().currentXp().floatValue() / state.progress().xpToNext().floatValue(),
                GameText.xp(state.progress().currentXp()) + " / " + GameText.xp(state.progress().xpToNext()) + " XP");
        skin.micro.getData().setScale(1);
        float bump = 1;
        float t = (now - bumpStarted) / (float) BUMP_NANOS;
        if (t < 1) bump = 1 + .35f * (float) Math.sin(t * Math.PI);
        Toolbar.drawFitted(batch, assets.coin, tx + 11, CARD_Y + 22, 24 * bump);
        String gold = GameText.money(state.wallet().coins());
        skin.text(batch, skin.number, gold, tx + 28, CARD_Y + 30, Palette.INK, Align.left);
        float greenX = tx + 28 + skin.width(skin.number, gold) + 24;
        Toolbar.drawFitted(batch, assets.greenCoin, greenX, CARD_Y + 22, 24);
        skin.text(batch, skin.number, GameText.money(state.greenCoins()), greenX + 17, CARD_Y + 30, Palette.LEAF_INK, Align.left);
    }

    private void drawShortcuts(SpriteBatch batch, UiSkin skin, GameAssets assets, GameState state, float px, float py) {
        int pending = 0;
        if (state.tutorial().active()) pending = 1;
        else for (DailyMissionBoard.Mission mission : state.dailyMissions().missions())
            if (!state.dailyMissions().completed(mission)) pending++;
        boolean produce = !state.depositTotals().isEmpty();
        int alerts = state.alerts().size();
        for (Shortcut s : Shortcut.values()) {
            Rectangle r = shortcutBounds(s);
            boolean hover = r.contains(px, py);
            float cx = r.x + r.width / 2f, cy = r.y + 56;
            if (hover) skin.glow(batch, cx, cy, 100, 1f);
            Texture icon = switch (s) {
                case ALERTS -> assets.bell; case MISSIONS -> assets.clipboard; case SHOP -> assets.shop;
                case DEPOSIT -> assets.deposit; case SETTINGS -> assets.gear;
            };
            batch.setColor(0, 0, 0, .25f);
            Toolbar.drawFitted(batch, icon, cx + 1, cy - 3 + (hover ? 2 : 0), 56);
            batch.setColor(Color.WHITE);
            Toolbar.drawFitted(batch, icon, cx, cy + (hover ? 2 : 0), hover ? 60 : 56);
            String label = switch (s) {
                case ALERTS -> "Avisos"; case MISSIONS -> "Missões"; case SHOP -> "Loja"; case DEPOSIT -> "Depósito";
                case SETTINGS -> "Ajustes";
            };
            skin.onScene(batch, skin.label, label, cx, r.y + 14, Align.center);
            if (s == Shortcut.ALERTS && alerts > 0) badge(batch, skin, Integer.toString(alerts), cx + 24, cy + 22);
            if (s == Shortcut.MISSIONS && pending > 0) badge(batch, skin, Integer.toString(pending), cx + 24, cy + 22);
            if (s == Shortcut.DEPOSIT && produce) badge(batch, skin, "", cx + 22, cy + 22);
        }
    }

    private static void badge(SpriteBatch batch, UiSkin skin, String text, float cx, float cy) {
        // O badge tem cantos de 11 px: menor que 22 px ele vira um quadrado.
        float h = 22;
        float w = text.isEmpty() ? 22 : Math.max(22, skin.width(skin.micro, text) + 12);
        skin.draw(batch, skin.badge, cx - w / 2f, cy - h / 2f, w, h);
        if (!text.isEmpty()) skin.text(batch, skin.micro, text, cx, cy + 4.5f, Palette.ON_COLOR, Align.center);
    }

    /** Avisos, floaters e banner: desenhados por último, sobre janelas e toolbar. */
    public void drawOverlay(SpriteBatch batch, UiSkin skin) {
        drawFloaters(batch, skin);
        drawToast(batch, skin);
        drawBanner(batch, skin);
    }

    private void drawToast(SpriteBatch batch, UiSkin skin) {
        if (toasts.isEmpty()) return;
        long now = System.nanoTime();
        long age = now - toastStarted;
        if (age >= TOAST_NANOS) {
            toasts.removeFirst();
            toastStarted = now;
            if (toasts.isEmpty()) return;
            age = 0;
        }
        Toast toast = toasts.peekFirst();
        float in = Math.min(1, age / (float) TOAST_FADE);
        float out = Math.min(1, (TOAST_NANOS - age) / (float) TOAST_FADE);
        float alpha = Math.min(in, out);
        float boldW = toast.bold().isEmpty() ? 0 : skin.width(skin.label, toast.bold()) + 5;
        float textW = boldW + skin.width(skin.body, toast.rest());
        float w = textW + (toast.icon() != null ? 64 : 34), h = 50;
        float x = 800 - w / 2f, y = 900 - 112 - h + (1 - Interpolation.pow2Out.apply(in)) * 16;
        Color previous = batch.getColor().cpy();
        batch.setColor(1, 1, 1, alpha);
        skin.dropShadow(batch, x, y, w, h, 4);
        NinePatch patch = switch (toast.kind()) { case REWARD -> skin.toastReward; case ALERT -> skin.toastAlert; default -> skin.toast; };
        patch.setColor(new Color(1, 1, 1, alpha));
        skin.draw(batch, patch, x, y, w, h);
        patch.setColor(Color.WHITE);
        if (toast.kind() == ToastKind.ALERT) skin.rect(batch, withAlpha(Palette.BERRY, alpha), x + 1, y + 8, 3, h - 16);
        float tx = x + 18;
        if (toast.icon() != null) {
            batch.setColor(1, 1, 1, alpha);
            Toolbar.drawFitted(batch, toast.icon(), x + 30, y + h / 2f, 36);
            tx = x + 54;
        }
        batch.setColor(previous);
        if (!toast.bold().isEmpty())
            skin.text(batch, skin.label, toast.bold(), tx, y + 31, withAlpha(Palette.INK, alpha), Align.left);
        skin.text(batch, skin.body, toast.rest(), tx + boldW, y + 31, withAlpha(Palette.INK, alpha), Align.left);
    }

    private void drawFloaters(SpriteBatch batch, UiSkin skin) {
        long now = System.nanoTime();
        for (Iterator<Floater> it = floaters.iterator(); it.hasNext(); ) {
            Floater f = it.next();
            long age = now - f.start();
            if (age < 0) continue;
            if (age >= FLOATER_NANOS) { it.remove(); continue; }
            float t = age / (float) FLOATER_NANOS;
            float scale = t < .18f ? Interpolation.swingOut.apply(t / .18f) : 1;
            float alpha = t < .75f ? 1 : 1 - (t - .75f) / .25f;
            float y = f.y() + Interpolation.pow2Out.apply(t) * 46;
            Color fill = switch (f.style()) { case COIN -> Palette.COIN; case XP -> Palette.SKY_HI; case ITEM -> Palette.ON_COLOR; };
            Color line = switch (f.style()) { case COIN -> Palette.COIN_INK; case XP -> Palette.SKY_INK; case ITEM -> Palette.LEAF_INK; };
            BitmapFont font = skin.floater;
            font.getData().setScale(Math.max(.01f, scale));
            float textW = skin.width(font, f.text());
            float iconSize = 28 * scale;
            float total = (f.icon() != null ? iconSize + 4 : 0) + textW;
            float x = f.x() - total / 2f;
            if (f.icon() != null) {
                batch.setColor(1, 1, 1, alpha);
                Toolbar.drawFitted(batch, f.icon(), x + iconSize / 2f, y + 10 * scale, iconSize);
                batch.setColor(Color.WHITE);
                x += iconSize + 4;
            }
            skin.outlined(batch, font, f.text(), x, y + 21 * scale, withAlpha(fill, alpha), withAlpha(line, alpha), 1.6f, Align.left);
            font.getData().setScale(1);
        }
    }

    private void drawBanner(SpriteBatch batch, UiSkin skin) {
        if (bannerTitle == null) return;
        long age = System.nanoTime() - bannerStarted;
        if (age >= BANNER_NANOS) { bannerTitle = null; return; }
        float t = Math.min(1, age / 450_000_000f);
        float scale = Interpolation.swingOut.apply(t) * .6f + .4f * Math.min(1, t * 2);
        float alpha = Math.min(1, (BANNER_NANOS - age) / 300_000_000f);
        float w = Math.max(420, skin.width(skin.body, bannerSub) + 80) * scale, h = 128 * scale;
        float x = 800 - w / 2f, y = 470 - h / 2f;
        batch.setColor(1, 1, 1, alpha);
        skin.glow(batch, 800, 470, 560 * scale, .7f * alpha);
        skin.dropShadow(batch, x, y, w, h, 6);
        skin.banner.setColor(new Color(1, 1, 1, alpha));
        skin.draw(batch, skin.banner, x, y, w, h);
        skin.banner.setColor(Color.WHITE);
        batch.setColor(Color.WHITE);
        skin.banner48.getData().setScale(Math.max(.01f, scale));
        skin.outlined(batch, skin.banner48, bannerTitle, 800, y + h - 26 * scale,
                withAlpha(Palette.SUN, alpha), withAlpha(Palette.SUN_INK, alpha), 2.2f * scale, Align.center);
        skin.banner48.getData().setScale(1);
        skin.text(batch, skin.body, bannerSub, 800, y + 30 * scale, withAlpha(Palette.INK, alpha), Align.center);
    }

    // ---------- balão de status ----------

    public void drawTooltip(SpriteBatch batch, UiSkin skin, GameAssets assets, GameState state,
                            int col, int row, float tileX, float tileY) {
        Instant now = state.now();
        FarmTile tile = state.farm().tile(col, row);
        if (!state.land().isUnlocked(col, row)) {
            LandExpansion.Plot plot = state.land().at(col, row);
            LandExpansion.Plot next = state.land().next();
            if (next != null && next.id() == plot.id()) {
                boolean levelOk = state.progress().level() >= plot.requiredLevel();
                simpleTip(batch, skin, tileX, tileY, "Lote à venda",
                        !levelOk ? "Libera no nível " + plot.requiredLevel() + " · " + plot.price() + " moedas"
                                : state.wallet().coins().compareTo(java.math.BigDecimal.valueOf(plot.price())) >= 0 ? "Clique para comprar por " + plot.price() + " moedas"
                                : "Custa " + plot.price() + " moedas");
            } else simpleTip(batch, skin, tileX, tileY, "Lote bloqueado", "Os lotes são comprados em ordem");
            return;
        }
        if (tile.crop() == null) {
            if (tile.state() == TileState.GRASS) simpleTip(batch, skin, tileX, tileY, "Grama", "Use a pá (2) para arar");
            else if (tile.state() == TileState.DEAD)
                simpleTip(batch, skin, tileX, tileY, tile.withered().name() + " morto", "Use a pá (2) para retirar");
            else simpleTip(batch, skin, tileX, tileY, "Terra arada", "Escolha uma semente (3) e plante");
            return;
        }
        Crop crop = tile.crop();
        CropType type = crop.type();
        float w = 330, h = 134;
        float x = clamp(tileX - w / 2f, 12, 1600 - w - 12), y = Math.min(900 - 140 - h, tileY + 92);
        tipBox(batch, skin, x, y, w, h, tileX);
        List<String[]> chips = chips(tile, now);
        skin.text(batch, skin.label, type.name(), x + 14, y + h - 14, Palette.INK, Align.left);
        float cx = x + w - 12;
        for (int i = chips.size() - 1; i >= 0; i--) {
            String text = chips.get(i)[0];
            float cw = skin.width(skin.micro, text) + 12;
            skin.tinted(batch, skin.chip, Color.valueOf(chips.get(i)[1]), cx - cw, y + h - 30, cw, 18);
            skin.text(batch, skin.micro, text, cx - cw / 2f, y + h - 17, Palette.ON_COLOR, Align.center);
            cx -= cw + 4;
        }
        Toolbar.drawFitted(batch, assets.coin, x + 26, y + 78, 26);
        skin.text(batch, skin.caption, "Temporada", x + 48, y + 95, Palette.INK_MUTED, Align.left);
        skin.bar(batch, skin.barSun, x + 48, y + 66, w - 62, 17, crop.season() / (float) type.seasons(),
                seasonLine(crop, state.vipActive()));
        Toolbar.drawFitted(batch, assets.crop(type, Math.max(1, crop.stageAt(now))), x + 26, y + 34, 32);
        skin.text(batch, skin.caption, "Crescimento", x + 48, y + 51, Palette.INK_MUTED, Align.left);
        float fraction = crop.grownMillisAt(now) / (float) crop.currentDuration().toMillis();
        boolean paused = crop.paused();
        skin.bar(batch, paused || !crop.watered() ? skin.barGray : skin.barLeaf, x + 48, y + 22, w - 62, 17, fraction,
                growthLine(tile, now));
    }

    /** Temporada atual e o que a colheita rende: sempre uma unidade, ou o bloqueio de venda sem VIP. */
    static String seasonLine(Crop crop, boolean vipActive) {
        return crop.season() + " / " + crop.type().seasons() + " · "
                + (crop.type().vipOnly() && !vipActive ? "venda bloqueada sem VIP" : "1 unidade");
    }

    /** Estado do crescimento: rega pendente, pronta, pausa pelas pragas ou tempo restante (e a seca a 50%). */
    static String growthLine(FarmTile tile, Instant now) {
        Crop crop = tile.crop();
        boolean weeds = tile.pest() == PestState.WEEDS, insects = tile.insects() > 0;
        if (!crop.watered()) return "Regue para começar";
        if (crop.matureAt(now)) return "Pronta! Use a luvinha";
        if (crop.paused()) return weeds && insects ? "Pausado por erva e insetos"
                : weeds ? "Pausado pela erva daninha" : "Pausado pelos insetos";
        return formatRemaining(crop.remainingAt(now)) + (crop.dryAt(now) ? " · seca: cresce a 50%" : "");
    }

    private static List<String[]> chips(FarmTile tile, Instant now) {
        List<String[]> chips = new ArrayList<>();
        Crop crop = tile.crop();
        if (crop.matureAt(now)) chips.add(new String[]{"PRONTA!", "3a8a14"});
        if (tile.pest() == PestState.WEEDS) chips.add(new String[]{"PRAGA", "4f7d1f"});
        if (tile.insects() > 0) chips.add(new String[]{tile.insects() == 1 ? "VERME" : tile.insects() + " VERMES", "c62a20"});
        if (!crop.matureAt(now) && crop.dryAt(now)) chips.add(new String[]{crop.watered() ? "SECA" : "SEM ÁGUA", "b07a3a"});
        if (chips.isEmpty()) chips.add(new String[]{"SAUDÁVEL", "3a8a14"});
        return chips.size() > 2 ? chips.subList(0, 2) : chips;
    }

    /** Balão sobre o ninho: o que a galinha está fazendo e o que o clique faz. */
    public void animalTip(SpriteBatch batch, UiSkin skin, Animal animal, Instant now, float x, float y, GameState state) {
        AnimalType type = animal.type();
        String line;
        if (type.kind() == AnimalType.Kind.PROTECTOR) {
            line = animal.activeAt(state.realNow(), now) ? "Proteção ativa" : "Proteção pausada · com fome";
            line += " · até " + GameText.vipExpiry(animal.expiresAt(), java.time.ZoneId.systemDefault());
            if (animal.hungryAt(now)) line += " · clique para alimentar";
        } else if (animal.statusAt(now) == Animal.Status.READY) {
            line = type.kind() == AnimalType.Kind.GROWING ? "Adulto · clique para vender" : "1 " + type.productName() + " · clique para coletar";
        } else if (animal.hungryAt(now)) line = "Com fome · clique para dar ração · compre na Loja";
        else line = (type.kind() == AnimalType.Kind.GROWING ? "Crescendo · " + switch (animal.stageAt(now)) {
            case BABY -> "bebê"; case YOUNG -> "jovem"; case ADULT -> "adulto";
        } : type.productName()) + " · " + MarketPanel.formatTime(animal.remainingAt(now).toSeconds()) + " restantes";
        simpleTip(batch, skin, x, y, type.name(), line);
    }

    private void simpleTip(SpriteBatch batch, UiSkin skin, float tileX, float tileY, String title, String line) {
        float w = Math.max(skin.width(skin.label, title), skin.width(skin.body, line)) + 30, h = 60;
        float x = clamp(tileX - w / 2f, 12, 1600 - w - 12), y = tileY + 58;
        tipBox(batch, skin, x, y, w, h, tileX);
        skin.text(batch, skin.label, title, x + 15, y + h - 14, Palette.INK, Align.left);
        skin.text(batch, skin.body, line, x + 15, y + 22, Palette.INK_MUTED, Align.left);
    }

    private static void tipBox(SpriteBatch batch, UiSkin skin, float x, float y, float w, float h, float tailX) {
        skin.dropShadow(batch, x, y, w, h, 4);
        float tx = clamp(tailX, x + 18, x + w - 18);
        batch.setColor(Palette.TOOLTIP_LINE);
        batch.draw(skin.white, tx - 8, y - 8, 8, 8, 16, 16, 1, 1, 45, 0, 0, 2, 2, false, false);
        batch.setColor(Color.WHITE);
        skin.draw(batch, skin.tooltip, x, y, w, h);
        batch.setColor(Palette.TOOLTIP);
        batch.draw(skin.white, tx - 7, y - 6, 7, 7, 14, 14, 1, 1, 45, 0, 0, 2, 2, false, false);
        batch.setColor(Color.WHITE);
    }

    // ---------- utilidades ----------

    private static String joinNames(List<CropType> crops) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < crops.size(); i++) {
            if (i > 0) out.append(i == crops.size() - 1 ? " e " : ", ");
            out.append(crops.get(i).name());
        }
        return out.toString();
    }

    static String formatRemaining(java.time.Duration remaining) {
        long minutes = Math.max(1, (remaining.toSeconds() + 59) / 60);
        long hours = minutes / 60;
        if (hours >= 24) return hours / 24 + "d " + hours % 24 + "h";
        if (hours > 0) return hours + "h" + String.format("%02d", minutes % 60);
        return minutes + " min";
    }

    public static String formatInt(int value) {
        return String.format(java.util.Locale.forLanguageTag("pt-BR"), "%,d", value);
    }

    private static Color withAlpha(Color c, float a) { return new Color(c.r, c.g, c.b, c.a * a); }
    private static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }
}
