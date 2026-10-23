package br.com.colheitafeliz.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.utils.Align;
import java.util.ArrayList;
import java.util.List;

/**
 * Peças da interface "Flash 2010 remasterizado" geradas em tempo de execução: caixas arredondadas com
 * borda de 1px, vidro cortado ao meio, sombras suaves e as fontes do design system. Tudo é desenhado
 * com {@link SpriteBatch}, sem ShapeRenderer, para ter anti-aliasing nas curvas.
 */
public final class UiSkin implements AutoCloseable {
    /** Parada de gradiente vertical: {@code at} vai de 0 (topo) a 1 (base). */
    private record Stop(float at, Color color) {}

    private final List<Texture> textures = new ArrayList<>();
    private final List<BitmapFont> fonts = new ArrayList<>();
    private final GlyphLayout layout = new GlyphLayout();

    public final NinePatch glassLeaf, glassSun, glassSky, glassBerry, glassNeutral, glassDisabled;
    public final NinePatch window, windowBar, tab, tabActive, sheet;
    public final NinePatch tray, hudCard, tooltip, toast, toastReward, toastAlert, banner;
    public final NinePatch barTrack, barSky, barSun, barLeaf, barGray;
    public final NinePatch badge, chip, signBoard, itemHover, missionDone, mission;
    public final NinePatch shadow;
    public final Texture glow, white, groundShadow;

    public final BitmapFont title, number, button, floater, banner48, label, body, caption, micro;

    public UiSkin() {
        glassLeaf = glass(Palette.LEAF_HI, Palette.LEAF, Palette.LEAF_LO, Palette.LEAF_INK);
        glassSun = glass(Palette.SUN_HI, Color.valueOf("ffd84a"), Palette.SUN, Palette.SUN_INK, Palette.SUN_LO);
        glassSky = glass(Palette.SKY_HI, Palette.SKY, Palette.SKY_LO, Palette.SKY_INK);
        glassBerry = glass(Palette.BERRY_HI, Palette.BERRY, Palette.BERRY_LO, Palette.BERRY_INK);
        glassNeutral = glass(Palette.WINDOW, Palette.CHROME_HI, Palette.CHROME, Palette.CHROME_LINE, Palette.CHROME_LO);
        glassDisabled = glass(Palette.WINDOW_2, Palette.CHROME, Palette.CHROME_LO, Palette.CHROME_LINE);

        window = box(64, 64, 10, 1, Palette.CHROME_LINE, true,
                new Stop(0, Palette.WINDOW), new Stop(.6f, Palette.WINDOW), new Stop(1, Palette.WINDOW_2));
        windowBar = box(64, 44, 10, 0, Palette.CHROME_LINE, false,
                new Stop(0, Palette.CHROME_HI), new Stop(1, Palette.CHROME));
        tab = box(48, 34, 6, 1, Palette.CHROME_LINE, true,
                new Stop(0, Palette.CHROME_HI), new Stop(.5f, Palette.CHROME), new Stop(1, Palette.CHROME_LO));
        tabActive = box(48, 34, 6, 1, Palette.CHROME_LINE, false, new Stop(0, Palette.WINDOW), new Stop(1, Palette.WINDOW));
        sheet = box(48, 48, 6, 1, Palette.CHROME_LINE, false, new Stop(0, Palette.WINDOW), new Stop(1, Palette.WINDOW));
        tray = box(48, 48, 10, 1, Palette.TRAY_LINE, false, new Stop(0, Palette.TRAY), new Stop(1, Palette.TRAY));
        hudCard = box(48, 48, 10, 1, Palette.CHROME_LINE, false, new Stop(0, Palette.HUD_CARD), new Stop(1, Palette.HUD_CARD));
        tooltip = box(48, 48, 10, 1, Palette.TOOLTIP_LINE, false, new Stop(0, Palette.TOOLTIP), new Stop(1, Palette.TOOLTIP));
        toast = box(48, 48, 10, 1, Palette.CHROME_LINE, false, new Stop(0, Palette.WINDOW), new Stop(1, Palette.WINDOW_2));
        toastReward = box(48, 48, 10, 1, Palette.REWARD_LINE, false, new Stop(0, Palette.REWARD_HI), new Stop(1, Palette.REWARD));
        toastAlert = box(48, 48, 10, 1, Palette.BERRY_LO, false, new Stop(0, Palette.WINDOW), new Stop(1, Palette.WINDOW_2));
        banner = box(64, 64, 10, 1, Palette.REWARD_LINE, false,
                new Stop(0, withAlpha(Palette.WINDOW, .92f)), new Stop(1, withAlpha(Palette.TOOLTIP, .92f)));
        barTrack = box(32, 18, 9, 1, Palette.CHROME_LINE, false, new Stop(0, Palette.WINDOW_2), new Stop(1, Palette.WINDOW));
        barSky = pill(Palette.SKY_HI, Palette.SKY, Palette.SKY_LO);
        barSun = pill(Palette.SUN_HI, Color.valueOf("ffd84a"), Palette.SUN_LO);
        barLeaf = pill(Palette.LEAF_HI, Palette.LEAF, Palette.LEAF_LO);
        barGray = pill(Palette.CHROME_HI, Palette.CHROME_LO, Palette.INK_MUTED);
        badge = box(24, 22, 11, 1.5f, Palette.ON_COLOR, false,
                new Stop(0, Color.valueOf("ff7468")), new Stop(.48f, Color.valueOf("ff7468")),
                new Stop(.52f, Palette.BERRY), new Stop(1, Palette.BERRY_LO));
        chip = box(16, 16, 4, 0, Palette.WINDOW, false, new Stop(0, Color.WHITE), new Stop(1, Color.WHITE));
        signBoard = box(32, 40, 3, 1, Palette.WOOD_INK, true, new Stop(0, Palette.WOOD_HI), new Stop(1, Palette.WOOD));
        itemHover = box(32, 32, 6, 1, Palette.REWARD_LINE, false, new Stop(0, Palette.REWARD_HI), new Stop(1, Palette.REWARD));
        mission = box(32, 32, 6, 1, Palette.CHROME_LO, false, new Stop(0, Palette.WINDOW), new Stop(1, Palette.WINDOW));
        missionDone = box(32, 32, 6, 1, Palette.REWARD_LINE, false, new Stop(0, Palette.REWARD_HI), new Stop(1, Palette.REWARD));
        shadow = softShadow(14, 14);
        glow = radial(128);
        groundShadow = groundShadow();
        white = solid();

        FreeTypeFontGenerator display = new FreeTypeFontGenerator(Gdx.files.internal("fonts/MPLUSRounded1c-ExtraBold.ttf"));
        FreeTypeFontGenerator text800 = new FreeTypeFontGenerator(Gdx.files.internal("fonts/Nunito-ExtraBold.ttf"));
        FreeTypeFontGenerator text700 = new FreeTypeFontGenerator(Gdx.files.internal("fonts/Nunito-Bold.ttf"));
        title = font(display, 22);
        number = font(display, 21);
        button = font(display, 17);
        floater = font(display, 27);
        banner48 = font(display, 50);
        label = font(text800, 16);
        caption = font(text700, 13);
        body = font(text700, 15);
        micro = font(text800, 11);
        display.dispose();
        text800.dispose();
        text700.dispose();
    }

    // ---------- desenho ----------

    public void draw(SpriteBatch batch, NinePatch patch, float x, float y, float width, float height) {
        patch.draw(batch, x, y, width, height);
    }

    public void tinted(SpriteBatch batch, NinePatch patch, Color tint, float x, float y, float width, float height) {
        Color previous = patch.getColor().cpy();
        patch.setColor(tint);
        patch.draw(batch, x, y, width, height);
        patch.setColor(previous);
    }

    /** Sombra suave atrás de uma caixa, deslocada para baixo. */
    public void dropShadow(SpriteBatch batch, float x, float y, float width, float height, float offset) {
        shadow.draw(batch, x - 10, y - 10 - offset, width + 20, height + 20);
    }

    public void glow(SpriteBatch batch, float cx, float cy, float size, float alpha) {
        Color previous = batch.getColor().cpy();
        batch.setColor(Palette.GLOW.r, Palette.GLOW.g, Palette.GLOW.b, Palette.GLOW.a * alpha);
        batch.draw(glow, cx - size / 2f, cy - size / 2f, size, size);
        batch.setColor(previous);
    }

    public void rect(SpriteBatch batch, Color color, float x, float y, float width, float height) {
        Color previous = batch.getColor().cpy();
        batch.setColor(color);
        batch.draw(white, x, y, width, height);
        batch.setColor(previous);
    }

    /** Barra de vidro: trilho claro rebaixado e preenchimento com brilho; {@code fraction} de 0 a 1. */
    public void bar(SpriteBatch batch, NinePatch fill, float x, float y, float width, float height,
                    float fraction, String text) {
        barTrack.draw(batch, x, y, width, height);
        float filled = Math.max(0, Math.min(1, fraction)) * (width - 2);
        if (filled > 0) fill.draw(batch, x + 1, y + 1, Math.max(height - 2, filled), height - 2);
        if (text != null && !text.isEmpty()) {
            text(batch, micro, text, x + width / 2f + .5f, y + height / 2f + 4f, Color.valueOf("ffffffbf"), Align.center);
            text(batch, micro, text, x + width / 2f, y + height / 2f + 4.5f, Palette.INK, Align.center);
        }
    }

    public float width(BitmapFont font, String text) {
        layout.setText(font, text);
        return layout.width;
    }

    /** Desenha texto com a linha de base em {@code y}; {@code align} é um valor de {@link Align}. */
    public void text(SpriteBatch batch, BitmapFont font, String text, float x, float y, Color color, int align) {
        font.setColor(color);
        layout.setText(font, text);
        float left = Align.isCenterHorizontal(align) ? x - layout.width / 2f
                : Align.isRight(align) ? x - layout.width : x;
        font.draw(batch, text, left, y);
    }

    /** Texto com sombra curta de 1px, como nos botões e legendas do Flash. */
    public void shadowed(SpriteBatch batch, BitmapFont font, String text, float x, float y,
                         Color color, Color shadowColor, int align) {
        text(batch, font, text, x, y - 1.2f, shadowColor, align);
        text(batch, font, text, x, y, color, align);
    }

    /** Legenda branca sobre a cena: sombra escura difusa em volta. */
    public void onScene(SpriteBatch batch, BitmapFont font, String text, float x, float y, int align) {
        Color shade = new Color(0, 0, 0, .45f);
        text(batch, font, text, x + 1, y - 1, shade, align);
        text(batch, font, text, x - 1, y - 1, shade, align);
        text(batch, font, text, x, y - 1.5f, new Color(0, 0, 0, .6f), align);
        text(batch, font, text, x, y, Palette.ON_COLOR, align);
    }

    /** Contorno em 8 direções: floaters e banner de nível. */
    public void outlined(SpriteBatch batch, BitmapFont font, String text, float x, float y,
                         Color color, Color outline, float thickness, int align) {
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++)
                if (dx != 0 || dy != 0) text(batch, font, text, x + dx * thickness, y + dy * thickness, outline, align);
        text(batch, font, text, x, y - thickness * 1.6f, outline, align);
        text(batch, font, text, x, y, color, align);
    }

    // ---------- geração das texturas ----------

    private NinePatch glass(Color hi, Color mid, Color lo, Color ink) { return glass(hi, mid, lo, ink, lo); }

    /** Vidro: metade de cima de {@code hi} a {@code mid}, corte seco em 50%, metade de baixo em {@code lo}. */
    private NinePatch glass(Color hi, Color mid, Color lo, Color ink, Color bottom) {
        return box(48, 40, 6, 1, ink, true,
                new Stop(0, hi), new Stop(.49f, mid), new Stop(.51f, lo), new Stop(1, bottom));
    }

    private NinePatch pill(Color hi, Color mid, Color lo) {
        return box(32, 16, 8, 0, lo, true, new Stop(0, hi), new Stop(.48f, mid), new Stop(.52f, lo), new Stop(1, lo));
    }

    /**
     * Caixa arredondada com anti-aliasing por distância. A borda tem {@code border} px; {@code gloss} põe
     * o fio branco de 1px logo abaixo da borda superior.
     */
    private NinePatch box(int width, int height, float radius, float border, Color borderColor,
                          boolean gloss, Stop... stops) {
        Pixmap pixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        pixmap.setBlending(Pixmap.Blending.None);
        float halfW = width / 2f, halfH = height / 2f;
        for (int py = 0; py < height; py++) {
            float t = height == 1 ? 0 : py / (float) (height - 1);
            Color fill = sample(stops, t);
            for (int px = 0; px < width; px++) {
                float d = roundedDistance(px + .5f - halfW, py + .5f - halfH, halfW, halfH, radius);
                float outer = clamp(.5f - d);
                if (outer <= 0) { pixmap.drawPixel(px, py, 0); continue; }
                float inner = border <= 0 ? 1 : clamp(.5f - (d + border));
                float r = lerp(borderColor.r, fill.r, inner);
                float g = lerp(borderColor.g, fill.g, inner);
                float b = lerp(borderColor.b, fill.b, inner);
                float a = lerp(borderColor.a, fill.a, inner) * outer;
                if (gloss && py >= Math.ceil(border) && py < Math.ceil(border) + 1 && inner > .5f) {
                    r = lerp(r, 1, .65f); g = lerp(g, 1, .65f); b = lerp(b, 1, .65f);
                }
                pixmap.drawPixel(px, py, Color.rgba8888(r, g, b, a));
            }
        }
        Texture texture = track(new Texture(pixmap));
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        pixmap.dispose();
        int split = (int) Math.ceil(radius + border) + 1;
        int vertical = Math.min(split, height / 2 - 1);
        return new NinePatch(texture, split, split, vertical, vertical);
    }

    private NinePatch softShadow(int radius, int blur) {
        int size = (radius + blur) * 2 + 8;
        Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        pixmap.setBlending(Pixmap.Blending.None);
        float half = size / 2f;
        float inset = blur;
        for (int py = 0; py < size; py++)
            for (int px = 0; px < size; px++) {
                float d = roundedDistance(px + .5f - half, py + .5f - half, half - inset, half - inset, radius);
                float a = d <= 0 ? 1 : Math.max(0, 1 - d / blur);
                a = a * a * (3 - 2 * a) * Palette.SHADOW.a;
                pixmap.drawPixel(px, py, Color.rgba8888(0, 0, 0, a));
            }
        Texture texture = track(new Texture(pixmap));
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        pixmap.dispose();
        int split = radius + blur;
        return new NinePatch(texture, split, split, split, split);
    }

    private Texture radial(int size) {
        Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        pixmap.setBlending(Pixmap.Blending.None);
        float half = size / 2f;
        for (int py = 0; py < size; py++)
            for (int px = 0; px < size; px++) {
                float d = (float) Math.hypot(px + .5f - half, py + .5f - half) / half;
                float a = d < .3f ? 1 : Math.max(0, 1 - (d - .3f) / .7f);
                pixmap.drawPixel(px, py, Color.rgba8888(1, 1, 1, a * a));
            }
        Texture texture = track(new Texture(pixmap));
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        pixmap.dispose();
        return texture;
    }

    /** Disco sem margem transparente; o SpriteBatch o estica em uma elipse nos pés. */
    private Texture groundShadow() {
        int size = 128;
        Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        pixmap.setBlending(Pixmap.Blending.None);
        float half = size / 2f;
        for (int y = 0; y < size; y++)
            for (int x = 0; x < size; x++) {
                float distance = (float) Math.hypot(x + .5f - half, y + .5f - half);
                float alpha = clamp(half - distance);
                pixmap.drawPixel(x, y, Color.rgba8888(1, 1, 1, alpha));
            }
        Texture texture = track(new Texture(pixmap));
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        pixmap.dispose();
        return texture;
    }

    private Texture solid() {
        Pixmap pixmap = new Pixmap(2, 2, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        Texture texture = track(new Texture(pixmap));
        pixmap.dispose();
        return texture;
    }

    private BitmapFont font(FreeTypeFontGenerator generator, int size) {
        FreeTypeFontGenerator.FreeTypeFontParameter parameter = new FreeTypeFontGenerator.FreeTypeFontParameter();
        parameter.size = size;
        parameter.characters = FreeTypeFontGenerator.DEFAULT_CHARS
                + "áàâãéêíóôõúüçÁÀÂÃÉÊÍÓÔÕÚÜÇ•–…×·";
        parameter.minFilter = Texture.TextureFilter.Linear;
        parameter.magFilter = Texture.TextureFilter.Linear;
        parameter.kerning = true;
        BitmapFont font = generator.generateFont(parameter);
        font.setUseIntegerPositions(false);
        fonts.add(font);
        return font;
    }

    private Texture track(Texture texture) { textures.add(texture); return texture; }

    private static Color sample(Stop[] stops, float t) {
        if (t <= stops[0].at()) return stops[0].color();
        for (int i = 1; i < stops.length; i++) {
            if (t <= stops[i].at()) {
                Stop a = stops[i - 1], b = stops[i];
                float local = (t - a.at()) / Math.max(1e-4f, b.at() - a.at());
                return a.color().cpy().lerp(b.color(), local);
            }
        }
        return stops[stops.length - 1].color();
    }

    private static float roundedDistance(float x, float y, float halfW, float halfH, float radius) {
        float qx = Math.abs(x) - (halfW - radius);
        float qy = Math.abs(y) - (halfH - radius);
        float outside = (float) Math.hypot(Math.max(qx, 0), Math.max(qy, 0));
        return outside + Math.min(Math.max(qx, qy), 0) - radius;
    }

    private static Color withAlpha(Color color, float alpha) { Color c = color.cpy(); c.a = alpha; return c; }
    private static float clamp(float v) { return Math.max(0, Math.min(1, v)); }
    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    @Override public void close() {
        for (Texture texture : textures) texture.dispose();
        for (BitmapFont font : fonts) font.dispose();
    }
}
