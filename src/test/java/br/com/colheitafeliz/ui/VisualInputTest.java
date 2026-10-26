package br.com.colheitafeliz.ui;

import br.com.colheitafeliz.domain.crop.CropCatalog;
import br.com.colheitafeliz.domain.crop.CropType;
import br.com.colheitafeliz.game.CropCatalogLoader;
import br.com.colheitafeliz.game.GameState;
import br.com.colheitafeliz.game.Tool;
import br.com.colheitafeliz.testsupport.ManualClock;
import com.badlogic.gdx.math.Rectangle;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VisualInputTest {
    @Test void vipTabBuysWithGreensAndDoesNotClickThrough() {
        var clock = new ManualClock();
        var game = new GameState(clock::instant, clock, CropCatalogLoader.load(), bound -> 99);
        var panel = new MarketPanel(game.catalog());
        panel.showShop();
        panel.click(cx(MarketPanel.tab(3)), cy(MarketPanel.tab(3)), game);
        assertFalse(panel.click(cx(MarketPanel.vipButton()), cy(MarketPanel.vipButton()), game).success());
        game.wallet().credit(br.com.colheitafeliz.domain.economy.Currency.GREEN, new java.math.BigDecimal("95"));
        assertTrue(panel.click(cx(MarketPanel.vipButton()), cy(MarketPanel.vipButton()), game).success());
        assertTrue(game.vipActive());
        assertEquals(0, java.math.BigDecimal.ZERO.compareTo(game.greenCoins()));
        assertEquals(0, new java.math.BigDecimal("500").compareTo(game.wallet().coins()));
        assertTrue(panel.handles(0, 0));
    }

    @Test void marketPagesThroughTheWholeCatalogInsideTheWindow() {
        CropCatalog catalog = CropCatalogLoader.load();
        ManualClock clock = new ManualClock();
        GameState game = new GameState(clock::instant, clock, catalog, bound -> 99);
        game.progress().award(new java.math.BigDecimal("10000000"));
        game.wallet().credit(100_000);
        game.wallet().credit(br.com.colheitafeliz.domain.economy.Currency.GREEN, new java.math.BigDecimal("200"));
        assertTrue(game.buyVip().success());
        MarketPanel modal = new MarketPanel(catalog);
        modal.showShop();
        assertEquals(0, modal.page());
        assertEquals(4, modal.pageCount(), "33 culturas, 10 por página");
        for (Rectangle button : new Rectangle[]{MarketPanel.previousPageButton(), MarketPanel.nextPageButton()})
            assertTrue(button.x >= MarketPanel.LEFT && button.x + button.width <= MarketPanel.LEFT + MarketPanel.WIDTH
                    && button.y >= MarketPanel.BOTTOM && button.y + button.height <= MarketPanel.BOTTOM + MarketPanel.HEIGHT,
                    "a navegação fica dentro da janela");
        assertNull(modal.click(cx(MarketPanel.previousPageButton()), cy(MarketPanel.previousPageButton()), game));
        assertEquals(0, modal.page());
        for (int i = 0; i < 4; i++) modal.click(cx(MarketPanel.nextPageButton()), cy(MarketPanel.nextPageButton()), game);
        assertEquals(3, modal.page(), "não passa da última página");
        assertTrue(modal.isOpen());

        assertTrue(modal.click(cx(MarketPanel.cell(2)), cy(MarketPanel.cell(2)), game).success());
        assertEquals(1, game.inventory().seeds(catalog.get("coco")), "última entrada do catálogo");
        assertNull(modal.click(cx(MarketPanel.cell(3)), cy(MarketPanel.cell(3)), game), "posição vazia depois da última");
        modal.click(cx(MarketPanel.previousPageButton()), cy(MarketPanel.previousPageButton()), game);
        assertEquals(2, modal.page());
        assertTrue(modal.click(cx(MarketPanel.cell(0)), cy(MarketPanel.cell(0)), game).success());
        assertEquals(1, game.inventory().seeds(catalog.all().get(20)));

        assertNull(modal.click(MarketPanel.LEFT + MarketPanel.WIDTH / 2f, MarketPanel.BOTTOM + MarketPanel.HEIGHT - 20, game));
        assertTrue(modal.isOpen(), "clique fora da grade não fecha nem vaza");
        assertTrue(modal.handles(10, 10));
        modal.click(cx(MarketPanel.tab(1)), cy(MarketPanel.tab(1)), game);
        assertEquals(0, modal.page(), "trocar de aba volta à primeira página");
        modal.click(cx(MarketPanel.nextPageButton()), cy(MarketPanel.nextPageButton()), game);
        modal.click(cx(MarketPanel.tab(0)), cy(MarketPanel.tab(0)), game);
        assertEquals(0, modal.page());
    }

    @Test void cropTooltipShowsSeasonDryHalfSpeedBothPestsAndVipBlock() {
        CropType strawberry = br.com.colheitafeliz.testsupport.TestCrops.strawberry();
        java.time.Instant start = java.time.Instant.parse("2026-01-01T12:00:00Z");
        br.com.colheitafeliz.domain.farm.FarmTile tile = new br.com.colheitafeliz.domain.farm.FarmTile();
        tile.plow();
        tile.plant(strawberry, start, br.com.colheitafeliz.testsupport.TestCrops.origin(strawberry));
        assertEquals("1 / 3 · 1 unidade", Hud.seasonLine(tile.crop(), false));
        java.time.Instant ready = br.com.colheitafeliz.testsupport.TestCrops.growUntilReady(tile, start);
        tile.harvest(ready);
        assertEquals("2 / 3 · 1 unidade", Hud.seasonLine(tile.crop(), false));
        assertEquals("Regue para começar", Hud.growthLine(tile, ready));
        tile.water(ready);
        java.time.Instant dry = ready.plus(tile.crop().moistDuration());
        assertTrue(Hud.growthLine(tile, dry).endsWith("seca: cresce a 50%"), Hud.growthLine(tile, dry));
        tile.checkPest(dry, true);
        assertEquals("Pausado pela erva daninha", Hud.growthLine(tile, dry));
        tile.checkInsects(dry, true);
        assertEquals("Pausado por erva e insetos", Hud.growthLine(tile, dry));

        CropType sweetPotato = CropCatalogLoader.load().get("batata-doce");
        br.com.colheitafeliz.domain.farm.FarmTile vipTile = new br.com.colheitafeliz.domain.farm.FarmTile();
        vipTile.plow();
        vipTile.plant(sweetPotato, start);
        assertEquals("1 / 2 · venda bloqueada sem VIP", Hud.seasonLine(vipTile.crop(), false));
        assertEquals("1 / 2 · 1 unidade", Hud.seasonLine(vipTile.crop(), true));
    }

    private static float cx(Rectangle r) { return r.x + r.width / 2f; }
    private static float cy(Rectangle r) { return r.y + r.height / 2f; }

    @Test void toolbarHasEightToolsInBarOrder() {
        Toolbar toolbar = new Toolbar();
        float y = Toolbar.Y + Toolbar.HEIGHT / 2f;
        for (int i = 0; i < Tool.BAR.size(); i++)
            assertEquals(Tool.BAR.get(i), toolbar.hit(Toolbar.slotX(i) + Toolbar.SLOT / 2f, y));
        assertEquals(java.util.List.of(Tool.CURSOR, Tool.HOE, Tool.SEED, Tool.WATER, Tool.WEEDER, Tool.PESTICIDE,
                Tool.HAND, Tool.FERTILIZER), Tool.BAR);
        assertEquals("Pá", Tool.HOE.label());
        assertEquals("Luvinha", Tool.HAND.label());
        assertNull(toolbar.hit(Toolbar.slotX(0) + 10, Toolbar.Y + Toolbar.HEIGHT + 60));
    }

    @Test void seedTrayOpensWithTheBagAndClosesAfterPickingASeed() {
        Toolbar toolbar = new Toolbar();
        assertFalse(toolbar.seedsOpen());
        toolbar.picked(Tool.SEED, Tool.CURSOR);
        assertTrue(toolbar.seedsOpen());
        toolbar.closeSeeds();
        assertFalse(toolbar.seedsOpen());
        toolbar.picked(Tool.SEED, Tool.SEED);
        assertTrue(toolbar.seedsOpen(), "o saco de novo reabre a faixa");
        toolbar.picked(Tool.SEED, Tool.SEED);
        assertFalse(toolbar.seedsOpen(), "e com a faixa aberta, fecha");
        toolbar.picked(Tool.HOE, Tool.SEED);
        assertFalse(toolbar.seedsOpen());
    }

    @Test void settingsPanelReturnsTheClickedRowAndClosesOnOutsideClick() {
        Hud hud = new Hud();
        assertFalse(hud.panelOpen());
        hud.toggleSettings();
        assertTrue(hud.panelOpen());
        Rectangle save = Hud.settingRow(4);
        assertEquals(Hud.Setting.SAVE, hud.panelClick(cx(save), cy(save)));
        Rectangle music = Hud.settingRow(0);
        assertEquals(Hud.Setting.MUSIC, hud.panelClick(cx(music), cy(music)));
        assertEquals(null, hud.panelClick(10, 10));
        assertFalse(hud.panelOpen());
        hud.toggleAlerts();
        hud.toggleAlerts();
        assertFalse(hud.panelOpen());
    }

    @Test void shortcutsSitInTheTopRightCorner() {
        Hud hud = new Hud();
        for (Hud.Shortcut s : Hud.Shortcut.values()) {
            Rectangle r = Hud.shortcutBounds(s);
            assertEquals(s, hud.shortcutHit(cx(r), cy(r)));
            assertTrue(r.x > 950 && r.y > 780, "à direita do card do tutorial e acima dos avisos");
        }
        assertNull(hud.shortcutHit(800, 450));
    }

    @Test void marketWindowBuysSellsAndClosesWithoutSendingClicksToFarm() {
        CropCatalog catalog = CropCatalogLoader.load();
        GameState game = new GameState(new ManualClock()::instant, catalog, bound -> 99);
        CropType turnip = catalog.get("nabo");
        MarketPanel modal = new MarketPanel(catalog);
        game.wallet().credit(1000);
        modal.showShop();
        assertTrue(modal.handles(10, 10));
        assertTrue(modal.click(cx(MarketPanel.cell(0)), cy(MarketPanel.cell(0)), game).success());
        assertEquals(1, game.inventory().seeds(turnip));
        assertTrue(modal.click(cx(MarketPanel.primaryButton(1)), cy(MarketPanel.primaryButton(1)), game).success());
        assertEquals(1, game.inventory().seeds(catalog.get("cenoura")));
        assertFalse(modal.click(cx(MarketPanel.cell(5)), cy(MarketPanel.cell(5)), game).success());
        modal.click(cx(MarketPanel.tab(1)), cy(MarketPanel.tab(1)), game);
        assertTrue(modal.selling());
        game.inventory().addProduceLot(new br.com.colheitafeliz.domain.inventory.ItemLot(turnip.id(), br.com.colheitafeliz.domain.economy.Currency.GOLD, turnip.seedPrice().getValue(), 3, false));
        assertTrue(modal.click(cx(MarketPanel.sellOneButton(0)), cy(MarketPanel.sellOneButton(0)), game).success());
        assertEquals(2, game.inventory().produce(turnip));
        assertTrue(modal.click(cx(MarketPanel.sellAllButton(0)), cy(MarketPanel.sellAllButton(0)), game).success());
        assertEquals(0, game.inventory().produce(turnip));
        modal.click(cx(MarketPanel.closeButton()), cy(MarketPanel.closeButton()), game);
        assertFalse(modal.isOpen());
        assertFalse(modal.handles(10, 10));
    }

    @Test void clickOutsideTheWindowClosesIt() {
        CropCatalog catalog = CropCatalogLoader.load();
        GameState game = new GameState(new ManualClock()::instant, catalog, bound -> 99);
        MarketPanel modal = new MarketPanel(catalog);
        modal.showSell();
        assertNull(modal.click(20, 20, game));
        assertFalse(modal.isOpen());
        MissionsWindow missions = new MissionsWindow();
        missions.toggle();
        missions.click(MissionsWindow.LEFT + 50, MissionsWindow.BOTTOM + 50);
        assertTrue(missions.isOpen());
        missions.click(20, 20);
        assertFalse(missions.isOpen());
    }
}
