package br.com.colheitafeliz.ui;

import br.com.colheitafeliz.domain.animal.AnimalType;
import br.com.colheitafeliz.domain.crop.CropCatalog;
import br.com.colheitafeliz.domain.crop.CropType;
import br.com.colheitafeliz.game.ActionResult;
import br.com.colheitafeliz.game.GameState;
import br.com.colheitafeliz.game.GameText;
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
    private static final float BAR = 46, TAB_W = 140, TAB_H = 36, COLS = 5;
    private static final float SHEET_X = LEFT + 18, SHEET_Y = BOTTOM + 18, SHEET_W = WIDTH - 36;
    private static final float SHEET_H = HEIGHT - BAR - 18 - TAB_H - 12;
    private static final float CELL_W = (SHEET_W - 24) / COLS, CELL_H = (SHEET_H - 24) / 2;
    private final List<CropType> crops;
    private boolean open;
    private boolean selling;
    private boolean animals;
    private boolean vip;
    /** Página da grade de culturas (sementes e colheitas), com {@link #PAGE_SIZE} entradas cada. */
    private int page;
    public static final int PAGE_SIZE = 10;

    public MarketPanel(CropCatalog catalog) { crops = catalog.all(); }
    public void showShop() { open = true; selling = false; animals = false; vip = false; page = 0; }
    public void showSell() { open = true; selling = true; animals = false; vip = false; page = 0; }
    public void showAnimals() { open = true; selling = false; animals = true; vip = false; page = 0; }
    public void showVip() { open = true; selling = false; animals = false; vip = true; page = 0; }
    public int page() { return page; }
    public int pageCount() { return (crops.size() + PAGE_SIZE - 1) / PAGE_SIZE; }
    public void nextPage() { page = Math.min(pageCount() - 1, page + 1); }
    public void previousPage() { page = Math.max(0, page - 1); }
    /** Entradas da página atual, a partir de {@code page * PAGE_SIZE}. */
    private int visibleCrops() { return Math.min(PAGE_SIZE, crops.size() - page * PAGE_SIZE); }
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
        return new Rectangle(LEFT + WIDTH - 24 - 370, SHEET_Y + SHEET_H + 2, 370, TAB_H - 4);
    }
    /** "Comprar adubo", no mesmo lugar do Vender tudo, só na aba de sementes. */
    public static Rectangle fertilizerButton() { return sellEverythingButton(); }
    public static Rectangle vipButton() { return new Rectangle(LEFT + WIDTH / 2 - 150, BOTTOM + 110, 300, 40); }
    /** Setas de página na barra de título, à esquerda, dentro da janela. */
    public static Rectangle previousPageButton() { return new Rectangle(LEFT + 14, BOTTOM + HEIGHT - BAR + 7, 34, 32); }
    public static Rectangle nextPageButton() { return new Rectangle(LEFT + 14 + 34 + 64, BOTTOM + HEIGHT - BAR + 7, 34, 32); }
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
        if (tab(3).contains(x, y)) { showVip(); return null; }
        if (vip) return vipButton().contains(x, y) ? state.buyVip() : null;
        if (animals) return clickAnimals(x, y, state);
        if (selling && sellEverythingButton().contains(x, y)) return state.sellEverything();
        if (!selling && fertilizerButton().contains(x, y)) return state.buyFertilizer();
        if (previousPageButton().contains(x, y)) { previousPage(); return null; }
        if (nextPageButton().contains(x, y)) { nextPage(); return null; }
        for (int i = 0; i < visibleCrops(); i++) {
            CropType crop = crops.get(page * PAGE_SIZE + i);
            if (!selling) {
                if (cell(i).contains(x, y)) return state.buySeed(crop);
                continue;
            }
            if (sellOneButton(i).contains(x, y)) return state.sellProduce(crop);
            if (sellAllButton(i).contains(x, y)) return state.sellAllProduce(crop);
        }
        return null;
    }

    /** Linha de um animal na aba Animais: até 4 linhas, de cima para baixo. */
    public static Rectangle animalRow(int index) {
        float h = (SHEET_H - 24) / 4f;
        return new Rectangle(SHEET_X + 12, SHEET_Y + SHEET_H - 12 - (index + 1) * h, SHEET_W - 24, h);
    }
    public static Rectangle buyAnimalButton(int index) { return rowButton(index, 316, 170); }
    public static Rectangle buyFeedButton(int index) { return rowButton(index, 590, 120); }
    public static Rectangle sellProductOneButton(int index) { return rowButton(index, 790, 82); }
    public static Rectangle sellProductAllButton(int index) { return rowButton(index, 878, 58); }
    private static Rectangle rowButton(int index, float x, float width) {
        Rectangle r = animalRow(index);
        return new Rectangle(r.x + x, r.y + r.height / 2f - 34, width, 30);
    }

    private static ActionResult clickAnimals(float x, float y, GameState state) {
        List<AnimalType> types = state.animalTypes();
        for (int t = 0; t < types.size(); t++) {
            AnimalType type = types.get(t);
            if (buyAnimalButton(t).contains(x, y)) return state.buyAnimal(type);
            if (buyFeedButton(t).contains(x, y)) return state.buyFeed(type);
            if (sellProductOneButton(t).contains(x, y)) return state.sellProduct(type, false);
            if (sellProductAllButton(t).contains(x, y)) return state.sellProduct(type, true);
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

        String[] tabs = {"Sementes", "Colheitas", "Animais", "VIP"};
        int activeIndex = vip ? 3 : animals ? 2 : selling ? 1 : 0;
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

        if (vip) {
            float center = LEFT + WIDTH / 2;
            Toolbar.drawFitted(batch, assets.vipCard, center, BOTTOM + 355, 130);
            skin.text(batch, skin.label, "Cartão VIP · 30 dias · 100 verdes", center, BOTTOM + 265, Palette.INK, Align.center);
            skin.text(batch, skin.body, "Venda com acréscimo de 25%. Renovação acumula mais 30 dias.",
                    center, BOTTOM + 225, Palette.INK_MUTED, Align.center);
            skin.text(batch, skin.body, state.vipActive() ? "Ativo até " + GameText.vipExpiry(state.vip().expiresAt(), java.time.ZoneId.systemDefault()) : "VIP inativo",
                    center, BOTTOM + 195, Palette.INK_MUTED, Align.center);
            Rectangle buy = vipButton();
            button(batch, skin, skin.glassSun, buy, "Comprar cartão VIP", Palette.INK_WARM, null, buy.contains(px, py));
            return;
        }
        if (animals) {
            List<AnimalType> types = state.animalTypes();
            for (int t = 0; t < types.size(); t++) drawAnimalRow(batch, skin, assets, state, types.get(t), t, px, py);
            return;
        }
        for (int i = 0; i < visibleCrops(); i++) drawItem(batch, skin, assets, state, i, px, py);
        drawPager(batch, skin, px, py);
        if (!selling) {
            boolean unlocked = state.progress().level() >= GameState.FERTILIZER_LEVEL;
            Rectangle fert = fertilizerButton();
            button(batch, skin, unlocked ? skin.glassSun : skin.glassDisabled, fert,
                    unlocked ? "Adubo · " + GameState.FERTILIZER_PRICE + " moedas (tem " + state.inventory().fertilizer() + ")"
                            : "Adubo · nível " + GameState.FERTILIZER_LEVEL,
                    unlocked ? Palette.INK_WARM : Palette.INK_MUTED, null, unlocked && fert.contains(px, py));
        }
        if (selling) {
            var totals = state.depositTotals();
            String value = GameState.formatTotals(totals);
            Rectangle all = sellEverythingButton();
            button(batch, skin, !totals.isEmpty() ? skin.glassBerry : skin.glassDisabled, all,
                    !totals.isEmpty() ? "Vender tudo · " + value : "Depósito vazio",
                    !totals.isEmpty() ? Palette.ON_COLOR : Palette.INK_MUTED, !totals.isEmpty() ? Palette.BERRY_INK : null,
                    !totals.isEmpty() && all.contains(px, py));
        }
    }

    private void drawItem(SpriteBatch batch, UiSkin skin, GameAssets assets, GameState state, int i, float px, float py) {
        CropType crop = crops.get(page * PAGE_SIZE + i);
        Rectangle c = cell(i);
        int owned = selling ? state.inventory().produce(crop) : state.inventory().seeds(crop);
        var quote = selling ? state.quoteItem(crop.id()) : java.util.Map.<br.com.colheitafeliz.domain.economy.Currency, java.math.BigDecimal>of();
        boolean vipBlocked = selling && owned > 0 && quote.isEmpty();
        boolean unlocked = selling ? !vipBlocked : state.unlocked(crop);
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
            skin.text(batch, skin.label, vipBlocked ? "VIP necessário" : "Nível " + crop.requiredLevel(), cx, c.y + c.height - 93, Palette.INK, Align.center);
        }
        String info = selling ? "cada" : formatTime(crop.growthSeconds());
        var seedPrice = crop.seedPrice();
        String price = GameText.money(seedPrice.getValue().multiply(new java.math.BigDecimal(selling ? (state.vipActive() ? "1.25" : "1.15") : "1")).setScale(1, java.math.RoundingMode.CEILING));
        boolean green = seedPrice.getKey() == br.com.colheitafeliz.domain.economy.Currency.GREEN;
        if (vipBlocked) {
            var origin = state.inventory().produceLots().stream().filter(lot -> lot.itemId().equals(crop.id())).findFirst().orElseThrow();
            price = GameText.money(origin.unitCost().multiply(new java.math.BigDecimal("1.15")).setScale(1, java.math.RoundingMode.CEILING));
            green = origin.currency() == br.com.colheitafeliz.domain.economy.Currency.GREEN;
        }
        if (!quote.isEmpty()) {
            var first = quote.entrySet().iterator().next();
            price = GameText.money(first.getValue());
            green = first.getKey() == br.com.colheitafeliz.domain.economy.Currency.GREEN;
        }
        float priceW = 20 + 4 + skin.width(skin.label, price) + 6 + skin.width(skin.caption, info);
        float left = cx - priceW / 2f;
        Toolbar.drawFitted(batch, green ? assets.greenCoin : assets.coin, left + 10, c.y + 63, 20);
        skin.text(batch, skin.label, price, left + 24, c.y + 69, Palette.INK, Align.left);
        skin.text(batch, skin.caption, info, left + 24 + skin.width(skin.label, price) + 6, c.y + 68,
                Palette.INK_MUTED, Align.left);
        if (!selling) {
            Rectangle b = primaryButton(i);
            // Semente VIP liberada pelo nível ainda exige o cartão ativo para compra (seção 9.2).
            boolean buyable = unlocked && (!crop.vipOnly() || state.vipActive());
            button(batch, skin, buyable ? skin.glassSun : skin.glassDisabled, b,
                    buyable ? "Comprar" : unlocked ? "Só com VIP" : "Bloqueado", buyable ? Palette.INK_WARM : Palette.INK_MUTED, null,
                    b.contains(px, py) && buyable);
        } else {
            boolean any = owned > 0 && !vipBlocked;
            Rectangle one = sellOneButton(i), all = sellAllButton(i);
            button(batch, skin, any ? skin.glassNeutral : skin.glassDisabled, one, "Vender 1",
                    any ? Palette.INK : Palette.INK_MUTED, null, any && one.contains(px, py));
            button(batch, skin, any ? skin.glassBerry : skin.glassDisabled, all, "Tudo",
                    any ? Palette.ON_COLOR : Palette.INK_MUTED, any ? Palette.BERRY_INK : null, any && all.contains(px, py));
        }
    }

    /** Uma linha por animal: o animal e seu preço, a ração e o produto para vender. */
    private static void drawAnimalRow(SpriteBatch batch, UiSkin skin, GameAssets assets, GameState state,
                                      AnimalType type, int index, float px, float py) {
        Rectangle r = animalRow(index);
        boolean unlocked = state.progress().level() >= type.requiredLevel();
        boolean full = state.barn().full(type);
        float mid = r.y + r.height / 2f;
        if (index > 0) skin.rect(batch, Palette.CHROME_LO, r.x + 8, r.y + r.height, r.width - 16, 1);
        batch.setColor(unlocked ? Color.WHITE : new Color(.6f, .6f, .6f, .5f));
        Toolbar.drawFitted(batch, assets.animal(type.id()), r.x + 52, mid, 84);
        batch.setColor(Color.WHITE);
        skin.text(batch, skin.label, type.name() + "  " + state.barn().count(type) + "/" + type.max(), r.x + 108, mid + 24,
                unlocked ? Palette.INK : Palette.INK_MUTED, Align.left);
        skin.text(batch, skin.caption, type.productYield() + "× " + type.productName() + " a cada "
                + formatTime(type.productionSeconds()), r.x + 108, mid + 2, Palette.INK_MUTED, Align.left);
        skin.text(batch, skin.caption, "+" + type.collectXp() + " XP por coleta", r.x + 108, mid - 18, Palette.SKY_INK, Align.left);
        Rectangle buy = buyAnimalButton(index);
        boolean canBuy = unlocked && !full;
        button(batch, skin, canBuy ? skin.glassSun : skin.glassDisabled, buy,
                !unlocked ? "Nível " + type.requiredLevel() : full ? "Cercado cheio" : "Comprar · " + type.price(),
                canBuy ? Palette.INK_WARM : Palette.INK_MUTED, null, canBuy && buy.contains(px, py));

        int feed = state.inventory().feed(type.id());
        Toolbar.drawFitted(batch, assets.bag, r.x + 540, mid + 2, 64);
        skin.text(batch, skin.label, "Ração x" + feed, r.x + 590, mid + 24, Palette.INK, Align.left);
        Rectangle feedButton = buyFeedButton(index);
        button(batch, skin, unlocked ? skin.glassSun : skin.glassDisabled, feedButton,
                unlocked ? "Comprar · " + type.feedPrice() : "Nível " + type.requiredLevel(),
                unlocked ? Palette.INK_WARM : Palette.INK_MUTED, null, unlocked && feedButton.contains(px, py));

        int owned = state.inventory().product(type.productId());
        Toolbar.drawFitted(batch, assets.product(type.id()), r.x + 750, mid + 4, 50);
        skin.text(batch, skin.label, type.productName() + " x" + owned + " · " + GameState.formatTotals(state.quoteItem(type.productId())), r.x + 790, mid + 24,
                Palette.INK, Align.left);
        boolean any = owned > 0;
        Rectangle one = sellProductOneButton(index), all = sellProductAllButton(index);
        button(batch, skin, any ? skin.glassNeutral : skin.glassDisabled, one, "Vender 1",
                any ? Palette.INK : Palette.INK_MUTED, null, any && one.contains(px, py));
        button(batch, skin, any ? skin.glassBerry : skin.glassDisabled, all, "Tudo",
                any ? Palette.ON_COLOR : Palette.INK_MUTED, any ? Palette.BERRY_INK : null, any && all.contains(px, py));
    }

    /** "‹ 2/4 ›" na barra de título; a seta da ponta fica desabilitada. */
    private void drawPager(SpriteBatch batch, UiSkin skin, float px, float py) {
        Rectangle previous = previousPageButton(), next = nextPageButton();
        boolean hasPrevious = page > 0, hasNext = page < pageCount() - 1;
        button(batch, skin, hasPrevious ? skin.glassNeutral : skin.glassDisabled, previous, "<",
                hasPrevious ? Palette.INK : Palette.INK_MUTED, null, hasPrevious && previous.contains(px, py));
        skin.text(batch, skin.label, (page + 1) + "/" + pageCount(), (previous.x + previous.width + next.x) / 2f,
                previous.y + previous.height / 2f + 7, Palette.INK, Align.center);
        button(batch, skin, hasNext ? skin.glassNeutral : skin.glassDisabled, next, ">",
                hasNext ? Palette.INK : Palette.INK_MUTED, null, hasNext && next.contains(px, py));
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
