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
        Rectangle save = Hud.settingRow(2);
        assertEquals(Hud.Setting.SAVE, hud.panelClick(cx(save), cy(save)));
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
        modal.showShop();
        assertTrue(modal.handles(10, 10));
        assertTrue(modal.click(cx(MarketPanel.cell(0)), cy(MarketPanel.cell(0)), game).success());
        assertEquals(1, game.inventory().seeds(turnip));
        assertTrue(modal.click(cx(MarketPanel.primaryButton(4)), cy(MarketPanel.primaryButton(4)), game).success());
        assertEquals(1, game.inventory().seeds(catalog.get("morango")));
        assertFalse(modal.click(cx(MarketPanel.cell(5)), cy(MarketPanel.cell(5)), game).success());
        modal.click(cx(MarketPanel.tab(1)), cy(MarketPanel.tab(1)), game);
        assertTrue(modal.selling());
        game.inventory().addProduce(turnip, 3);
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
