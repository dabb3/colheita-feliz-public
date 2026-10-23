package br.com.colheitafeliz.ui;

import br.com.colheitafeliz.domain.crop.CropType;
import br.com.colheitafeliz.game.GameState;
import br.com.colheitafeliz.game.Tool;
import br.com.colheitafeliz.render.GameAssets;
import br.com.colheitafeliz.render.Palette;
import br.com.colheitafeliz.render.UiSkin;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Align;
import java.util.List;

/**
 * Bandeja escura translúcida com as sete ferramentas (atalhos 1–7). Com a ferramenta de sementes ativa, uma
 * segunda bandeja acima lista as sementes do catálogo.
 */
public final class Toolbar {
    public static final float SLOT = 72, SLOT_H = 64, GAP = 6, PAD = 10;
    public static final float WIDTH = Tool.BAR.size() * SLOT + (Tool.BAR.size() - 1) * GAP + PAD * 2;
    public static final float X = (1600 - WIDTH) / 2f, Y = 16, HEIGHT = SLOT_H + PAD * 2;
    private static final float SEED = 62, SEED_GAP = 4, SEED_Y = Y + HEIGHT + 12;

    public Tool hit(float x, float y) {
        if (y < Y + PAD || y > Y + PAD + SLOT_H) return null;
        for (int i = 0; i < Tool.BAR.size(); i++) {
            float left = slotX(i);
            if (x >= left && x <= left + SLOT) return Tool.BAR.get(i);
        }
        return null;
    }

    public static float slotX(int index) { return X + PAD + index * (SLOT + GAP); }

    public CropType seedHit(float x, float y, List<CropType> crops) {
        if (y < SEED_Y + PAD || y > SEED_Y + PAD + SEED) return null;
        float left = seedTrayX(crops.size()) + PAD;
        for (int i = 0; i < crops.size(); i++) {
            float sx = left + i * (SEED + SEED_GAP);
            if (x >= sx && x <= sx + SEED) return crops.get(i);
        }
        return null;
    }

    private static float seedTrayWidth(int count) { return count * SEED + (count - 1) * SEED_GAP + PAD * 2; }
    private static float seedTrayX(int count) { return (1600 - seedTrayWidth(count)) / 2f; }

    public void draw(SpriteBatch batch, UiSkin skin, GameAssets assets, GameState state, float pointerX, float pointerY) {
        skin.dropShadow(batch, X, Y, WIDTH, HEIGHT, 2);
        skin.draw(batch, skin.tray, X, Y, WIDTH, HEIGHT);
        Tool hovered = hit(pointerX, pointerY);
        for (int i = 0; i < Tool.BAR.size(); i++) {
            Tool tool = Tool.BAR.get(i);
            float x = slotX(i), y = Y + PAD;
            boolean active = state.tool() == tool;
            float cx = x + SLOT / 2f, cy = y + SLOT_H / 2f;
            if (active) skin.glow(batch, cx, cy, 104, 1f);
            else if (tool == hovered) skin.glow(batch, cx, cy, 96, .5f);
            float size = active ? 58 : 52;
            float lift = !active && tool == hovered ? 2 : 0;
            if (tool == Tool.FERTILIZER) batch.setColor(Palette.FERTILIZER);
            drawFitted(batch, assets.tool(tool), cx, cy + lift, size);
            batch.setColor(Color.WHITE);
            if (tool == Tool.FERTILIZER) {
                int bags = state.inventory().fertilizer();
                skin.outlined(batch, skin.label, "x" + bags, x + SLOT - 4, y + 15,
                        bags == 0 ? Palette.BERRY_HI : Palette.ON_COLOR, new Color(0, 0, 0, .7f), 1f, Align.right);
            }
            skin.text(batch, skin.micro, Integer.toString(i + 1), x + 4, y + SLOT_H - 3,
                    new Color(1, 1, 1, .72f), Align.left);
            if (tool == Tool.SEED) {
                int seeds = state.inventory().seeds(state.seed());
                drawFitted(batch, assets.seed(state.seed()), x + SLOT - 14, y + SLOT_H - 14, 24);
                skin.outlined(batch, skin.label, "x" + seeds, x + SLOT - 4, y + 15,
                        seeds == 0 ? Palette.BERRY_HI : Palette.ON_COLOR, new Color(0, 0, 0, .7f), 1f, Align.right);
            }
        }
        if (hovered != null && state.tool() != Tool.SEED) {
            int index = Tool.BAR.indexOf(hovered);
            skin.onScene(batch, skin.caption, hovered.label(), slotX(index) + SLOT / 2f, Y + HEIGHT + 18, Align.center);
        }
        if (state.tool() == Tool.SEED) drawSeeds(batch, skin, assets, state, pointerX, pointerY);
    }

    private void drawSeeds(SpriteBatch batch, UiSkin skin, GameAssets assets, GameState state, float px, float py) {
        List<CropType> crops = state.catalog().all();
        float width = seedTrayWidth(crops.size()), x = seedTrayX(crops.size());
        skin.dropShadow(batch, x, SEED_Y, width, SEED + PAD * 2, 2);
        skin.draw(batch, skin.tray, x, SEED_Y, width, SEED + PAD * 2);
        CropType hovered = seedHit(px, py, crops);
        for (int i = 0; i < crops.size(); i++) {
            CropType crop = crops.get(i);
            float sx = x + PAD + i * (SEED + SEED_GAP), sy = SEED_Y + PAD;
            boolean unlocked = state.unlocked(crop);
            boolean selected = crop.equals(state.seed());
            if (selected) skin.glow(batch, sx + SEED / 2f, sy + SEED / 2f, 92, 1f);
            else if (crop == hovered && unlocked) skin.glow(batch, sx + SEED / 2f, sy + SEED / 2f, 84, .45f);
            batch.setColor(unlocked ? Color.WHITE : new Color(.55f, .55f, .55f, .6f));
            drawFitted(batch, assets.seed(crop), sx + SEED / 2f, sy + SEED / 2f + 4, selected ? 48 : 42);
            batch.setColor(Color.WHITE);
            String count = unlocked ? "x" + state.inventory().seeds(crop) : "Nv " + crop.requiredLevel();
            skin.outlined(batch, skin.micro, count, sx + SEED - 3, sy + 12,
                    unlocked ? Palette.ON_COLOR : Palette.CHROME, new Color(0, 0, 0, .75f), 1f, Align.right);
        }
        CropType shown = hovered != null ? hovered : state.seed();
        String caption = shown.name() + (state.unlocked(shown)
                ? " · " + state.inventory().seeds(shown) + " sementes" : " · libera no nível " + shown.requiredLevel());
        skin.onScene(batch, skin.label, caption, 800, SEED_Y + SEED + PAD * 2 + 22, Align.center);
    }

    /** Desenha a textura cabendo num quadrado de {@code size}, centrada em (cx, cy), sem distorcer. */
    public static void drawFitted(SpriteBatch batch, Texture texture, float cx, float cy, float size) {
        float ratio = texture.getWidth() / (float) texture.getHeight();
        float w = ratio >= 1 ? size : size * ratio;
        float h = ratio >= 1 ? size / ratio : size;
        batch.draw(texture, cx - w / 2f, cy - h / 2f, w, h);
    }
}
