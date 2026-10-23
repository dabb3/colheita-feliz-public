package br.com.colheitafeliz.ui;

import br.com.colheitafeliz.domain.animal.AnimalType;
import br.com.colheitafeliz.domain.crop.CropCatalog;
import br.com.colheitafeliz.domain.crop.CropType;
import br.com.colheitafeliz.game.ActionResult;
import br.com.colheitafeliz.game.GameState;
import br.com.colheitafeliz.render.GameAssets;
import br.com.colheitafeliz.render.Palette;
import br.com.colheitafeliz.render.UiSkin;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Align;
import java.util.List;

/**
 * Janela da Loja/Depósito no estilo do original: barra de título cinza, "×" no canto, abas cinza e itens
 * soltos no fundo branco. Na aba de compra cada item tem um botão amarelo com o preço; na de venda,
 * "Vender 1" e "Tudo". A aba Animais tem, para cada animal, o animal, a ração e o produto para vender.
 */
public final class MarketPanel {
    public static final float LEFT = 300, BOTTOM = 130, WIDTH = 1000, HEIGHT = 620;
    private static final float BAR = 46, TAB_W = 190, TAB_H = 36, COLS = 5;
    private static final float SHEET_X = LEFT + 18, SHEET_Y = BOTTOM + 18, SHEET_W = WIDTH - 36;
    private static final float SHEET_H = HEIGHT - BAR - 18 - TAB_H - 12;
    private static final float CELL_W = (SHEET_W - 24) / COLS, CELL_H = (SHEET_H - 24) / 2;
    private final List<CropType> crops;
    private boolean open;
    private boolean selling;
    private boolean animals;

    public MarketPanel(CropCatalog catalog) { crops = catalog.all(); }
    public void showShop() { open = true; selling = false; animals = false; }
    public void showSell() { open = true; selling = true; animals = false; }
    public void showAnimals() { open = true; selling = false; animals = true; }
    public boolean animals() { return animals; }
    public void close() { open = false; }
    public boolean isOpen() { return open; }
    public boolean selling() { return selling; }

    /** Enquanto aberta, a janela (e o véu) consomem todos os cliques. */
    public boolean handles(float x, float y) { return open; }

    public static Rectangle closeButton() { return new Rectangle(LEFT + WIDTH - 44, BOTTOM + HEIGHT - 40, 34, 34); }
    public static Rectangle tab(int index) {
        return new Rectangle(LEFT + 24 + index * (TAB_W + 6), SHEET_Y + SHEET_H - 1, TAB_W, TAB_H);
    }
    /** "Vender tudo", à direita das abas, só no depósito. */
    public static Rectangle sellEverythingButton() {
        return new Rectangle(LEFT + WIDTH - 24 - 270, SHEET_Y + SHEET_H + 2, 270, TAB_H - 4);
    }
    public static Rectangle cell(int index) {
        float x = SHEET_X + 12 + (index % COLS) * CELL_W;
        float y = SHEET_Y + SHEET_H - 12 - (index / (int) COLS + 1) * CELL_H;
        return new Rectangle(x, y, CELL_W, CELL_H);
    }
    /** Botão principal do item: "Comprar" na loja, "Vender 1" no depósito. */
    public static Rectangle primaryButton(int index) {
        Rectangle c = cell(index);
        return new Rectangle(c.x + 14, c.y + 14, c.width - 28, 30);
    }
    /** "Vender 1" ocupa a metade esquerda e "Tudo" a direita na aba de venda. */
    public static Rectangle sellOneButton(int index) {
        Rectangle b = primaryButton(index);
        return new Rectangle(b.x, b.y, b.width * .58f - 3, b.height);
    }
    public static Rectangle sellAllButton(int index) {
        Rectangle b = primaryButton(index);
        return new Rectangle(b.x + b.width * .58f + 3, b.y, b.width * .42f - 3, b.height);
    }

    public ActionResult click(float x, float y, GameState state) {
        if (!open) return null;
        if (closeButton().contains(x, y)) { close(); return null; }
        if (x < LEFT || x > LEFT + WIDTH || y < BOTTOM || y > BOTTOM + HEIGHT) { close(); return null; }
        if (tab(0).contains(x, y)) { showShop(); return null; }
        if (tab(1).contains(x, y)) { showSell(); return null; }
        if (tab(2).contains(x, y)) { showAnimals(); return null; }
        if (animals) return clickAnimals(x, y, state);
        if (selling && sellEverythingButton().contains(x, y)) return state.sellEverything();
        for (int i = 0; i < crops.size(); i++) {
            CropType crop = crops.get(i);
            if (!selling) {
                if (cell(i).contains(x, y)) return state.buySeed(crop);
                continue;
            }
            if (sellOneButton(i).contains(x, y)) return state.sellProduce(crop);
            if (sellAllButton(i).contains(x, y)) return state.sellAllProduce(crop);
        }
        return null;
    }

    private static ActionResult clickAnimals(float x, float y, GameState state) {
        List<AnimalType> types = state.animalTypes();
        for (int t = 0; t < types.size(); t++) {
            AnimalType type = types.get(t);
            if (cell(3 * t).contains(x, y)) return state.buyAnimal(type);
            if (cell(3 * t + 1).contains(x, y)) return state.buyFeed(type);
            if (sellOneButton(3 * t + 2).contains(x, y)) return state.sellProduct(type, false);
            if (sellAllButton(3 * t + 2).contains(x, y)) return state.sellProduct(type, true);
        }
        return null;
    }

    public void draw(SpriteBatch batch, UiSkin skin, GameAssets assets, GameState state, float px, float py) {
        if (!open) return;
        skin.rect(batch, Palette.SCRIM, 0, 0, 1600, 900);
        skin.dropShadow(batch, LEFT, BOTTOM, WIDTH, HEIGHT, 6);
        skin.draw(batch, skin.window, LEFT, BOTTOM, WIDTH, HEIGHT);
        skin.draw(batch, skin.windowBar, LEFT + 1, BOTTOM + HEIGHT - BAR, WIDTH - 2, BAR - 1);
        skin.rect(batch, Palette.CHROME_LO, LEFT + 1, BOTTOM + HEIGHT - BAR, WIDTH - 2, 1);
        skin.shadowed(batch, skin.title, selling ? "Depósito" : animals ? "Animais" : "Loja", LEFT + WIDTH / 2f, BOTTOM + HEIGHT - 14,
                Palette.INK, Palette.WINDOW, Align.center);
        Rectangle close = closeButton();
        boolean overClose = close.contains(px, py);
        if (overClose) skin.tinted(batch, skin.chip, Palette.BERRY_LO, close.x, close.y, close.width, close.height);
        skin.text(batch, skin.title, "×", close.x + close.width / 2f, close.y + 27,
                overClose ? Palette.ON_COLOR : Palette.INK_MUTED, Align.center);

        String[] tabs = {"Comprar sementes", "Vender colheita", "Animais"};
        int activeIndex = animals ? 2 : selling ? 1 : 0;
        for (int i = 0; i < tabs.length; i++) {
            Rectangle t = tab(i);
            boolean active = i == activeIndex;
            skin.draw(batch, active ? skin.tabActive : skin.tab, t.x, t.y - (active ? 1 : 0), t.width, t.height + (active ? 1 : 0));
            skin.text(batch, active ? skin.label : skin.body, tabs[i], t.x + t.width / 2f, t.y + 24,
                    active ? Palette.INK : Palette.INK_MUTED, Align.center);
        }
        skin.draw(batch, skin.sheet, SHEET_X, SHEET_Y, SHEET_W, SHEET_H);
        Rectangle activeTab = tab(activeIndex);
        skin.rect(batch, Palette.WINDOW, activeTab.x + 1, SHEET_Y + SHEET_H - 2, activeTab.width - 2, 3);

        if (animals) {
            List<AnimalType> types = state.animalTypes();
            for (int t = 0; t < types.size(); t++) drawAnimalItems(batch, skin, assets, state, types.get(t), 3 * t, px, py);
            return;
        }
        for (int i = 0; i < crops.size(); i++) drawItem(batch, skin, assets, state, i, px, py);
        if (selling) {
            int value = state.depositValue();
            Rectangle all = sellEverythingButton();
            button(batch, skin, value > 0 ? skin.glassBerry : skin.glassDisabled, all,
                    value > 0 ? "Vender tudo · " + value + " moedas" : "Depósito vazio",
                    value > 0 ? Palette.ON_COLOR : Palette.INK_MUTED, value > 0 ? Palette.BERRY_INK : null,
                    value > 0 && all.contains(px, py));
        }
    }

    private void drawItem(SpriteBatch batch, UiSkin skin, GameAssets assets, GameState state, int i, float px, float py) {
        CropType crop = crops.get(i);
        Rectangle c = cell(i);
        boolean unlocked = selling || state.unlocked(crop);
        int owned = selling ? state.inventory().produce(crop) : state.inventory().seeds(crop);
        boolean hover = c.contains(px, py) && unlocked && (!selling || owned > 0);
        if (hover) skin.draw(batch, skin.itemHover, c.x + 4, c.y + 4, c.width - 8, c.height - 8);
        float cx = c.x + c.width / 2f;
        skin.text(batch, skin.label, crop.name(), cx, c.y + c.height - 16, unlocked ? Palette.INK : Palette.INK_MUTED, Align.center);
        if (owned > 0) skin.text(batch, skin.micro, "x" + owned, c.x + c.width - 12, c.y + c.height - 16, Palette.INK_MUTED, Align.right);
        batch.setColor(unlocked ? Color.WHITE : new Color(.6f, .6f, .6f, .5f));
        Toolbar.drawFitted(batch, assets.crop(crop, crop.stages()), cx, c.y + c.height - 98, 104);
        batch.setColor(Color.WHITE);
        if (!unlocked) {
            float w = skin.width(skin.label, "Nível " + crop.requiredLevel()) + 16;
            skin.draw(batch, skin.sheet, cx - w / 2f, c.y + c.height - 112, w, 26);
            skin.text(batch, skin.label, "Nível " + crop.requiredLevel(), cx, c.y + c.height - 93, Palette.INK, Align.center);
        }
        String info = selling ? "cada" : formatTime(crop.growthSeconds());
        int price = selling ? crop.salePrice() : crop.seedPrice();
        float priceW = 20 + 4 + skin.width(skin.label, Integer.toString(price)) + 6 + skin.width(skin.caption, info);
        float left = cx - priceW / 2f;
        Toolbar.drawFitted(batch, assets.coin, left + 10, c.y + 63, 20);
        skin.text(batch, skin.label, Integer.toString(price), left + 24, c.y + 69, Palette.INK, Align.left);
        skin.text(batch, skin.caption, info, left + 24 + skin.width(skin.label, Integer.toString(price)) + 6, c.y + 68,
                Palette.INK_MUTED, Align.left);
        if (!selling) {
            Rectangle b = primaryButton(i);
            button(batch, skin, unlocked ? skin.glassSun : skin.glassDisabled, b,
                    unlocked ? "Comprar" : "Bloqueado", unlocked ? Palette.INK_WARM : Palette.INK_MUTED, null,
                    b.contains(px, py) && unlocked);
        } else {
            boolean any = owned > 0;
            Rectangle one = sellOneButton(i), all = sellAllButton(i);
            button(batch, skin, any ? skin.glassNeutral : skin.glassDisabled, one, "Vender 1",
                    any ? Palette.INK : Palette.INK_MUTED, null, any && one.contains(px, py));
            button(batch, skin, any ? skin.glassBerry : skin.glassDisabled, all, "Tudo",
                    any ? Palette.ON_COLOR : Palette.INK_MUTED, any ? Palette.BERRY_INK : null, any && all.contains(px, py));
        }
    }

    /** Três cartões por animal: comprar o animal, comprar ração e vender o produto. */
    private static void drawAnimalItems(SpriteBatch batch, UiSkin skin, GameAssets assets, GameState state,
                                        AnimalType type, int first, float px, float py) {
        boolean unlocked = state.progress().level() >= type.requiredLevel();
        boolean full = state.barn().full(type);
        card(batch, skin, assets, first, type.name(), state.barn().count(type) + "/" + type.max(), assets.chicken,
                unlocked, type.price(), type.productYield() + " " + type.productName().toLowerCase() + "s a cada "
                        + formatTime(type.productionSeconds()), px, py);
        Rectangle buy = primaryButton(first);
        boolean canBuy = unlocked && !full;
        button(batch, skin, canBuy ? skin.glassSun : skin.glassDisabled, buy,
                !unlocked ? "Nível " + type.requiredLevel() : full ? "Cercado cheio" : "Comprar",
                canBuy ? Palette.INK_WARM : Palette.INK_MUTED, null, canBuy && buy.contains(px, py));

        int feed = state.inventory().feed(type.id());
        card(batch, skin, assets, first + 1, "Ração", feed > 0 ? "x" + feed : "", assets.bag, unlocked,
                type.feedPrice(), "1 por ciclo", px, py);
        Rectangle feedButton = primaryButton(first + 1);
        button(batch, skin, unlocked ? skin.glassSun : skin.glassDisabled, feedButton,
                unlocked ? "Comprar" : "Nível " + type.requiredLevel(), unlocked ? Palette.INK_WARM : Palette.INK_MUTED,
                null, unlocked && feedButton.contains(px, py));

        int owned = state.inventory().product(type.productId());
        card(batch, skin, assets, first + 2, type.productName(), owned > 0 ? "x" + owned : "", assets.nestEggs, true,
                type.productPrice(), "cada", px, py);
        boolean any = owned > 0;
        Rectangle one = sellOneButton(first + 2), all = sellAllButton(first + 2);
        button(batch, skin, any ? skin.glassNeutral : skin.glassDisabled, one, "Vender 1",
                any ? Palette.INK : Palette.INK_MUTED, null, any && one.contains(px, py));
        button(batch, skin, any ? skin.glassBerry : skin.glassDisabled, all, "Tudo",
                any ? Palette.ON_COLOR : Palette.INK_MUTED, any ? Palette.BERRY_INK : null, any && all.contains(px, py));
    }

    /** Fundo, nome, contador, ícone e linha de preço de um cartão; os botões ficam com quem chama. */
    private static void card(SpriteBatch batch, UiSkin skin, GameAssets assets, int index, String name, String count,
                             com.badlogic.gdx.graphics.Texture icon, boolean unlocked, int price, String info,
                             float px, float py) {
        Rectangle c = cell(index);
        if (c.contains(px, py) && unlocked) skin.draw(batch, skin.itemHover, c.x + 4, c.y + 4, c.width - 8, c.height - 8);
        float cx = c.x + c.width / 2f;
        skin.text(batch, skin.label, name, cx, c.y + c.height - 16, unlocked ? Palette.INK : Palette.INK_MUTED, Align.center);
        if (!count.isEmpty()) skin.text(batch, skin.micro, count, c.x + c.width - 12, c.y + c.height - 16, Palette.INK_MUTED, Align.right);
        batch.setColor(unlocked ? Color.WHITE : new Color(.6f, .6f, .6f, .5f));
        Toolbar.drawFitted(batch, icon, cx, c.y + c.height - 98, 96);
        batch.setColor(Color.WHITE);
        float priceW = 20 + 4 + skin.width(skin.label, Integer.toString(price)) + 6 + skin.width(skin.caption, info);
        float left = cx - priceW / 2f;
        Toolbar.drawFitted(batch, assets.coin, left + 10, c.y + 63, 20);
        skin.text(batch, skin.label, Integer.toString(price), left + 24, c.y + 69, Palette.INK, Align.left);
        skin.text(batch, skin.caption, info, left + 24 + skin.width(skin.label, Integer.toString(price)) + 6, c.y + 68,
                Palette.INK_MUTED, Align.left);
    }

    static void button(SpriteBatch batch, UiSkin skin, NinePatch patch, Rectangle r, String text,
                       Color color, Color shadow, boolean hover) {
        if (hover) skin.tinted(batch, patch, new Color(1.08f, 1.08f, 1.08f, 1), r.x, r.y, r.width, r.height);
        else skin.draw(batch, patch, r.x, r.y, r.width, r.height);
        float baseline = r.y + r.height / 2f + 6;
        if (shadow != null) skin.shadowed(batch, skin.button, text, r.x + r.width / 2f, baseline, color, shadow, Align.center);
        else skin.text(batch, skin.button, text, r.x + r.width / 2f, baseline, color, Align.center);
    }

    static String formatTime(long seconds) {
        long minutes = Math.max(1, (seconds + 59) / 60);
        if (minutes < 60) return minutes + " min";
        long hours = minutes / 60, rest = minutes % 60;
        return rest == 0 ? hours + " h" : hours + "h" + String.format("%02d", rest);
    }
}
