package br.com.colheitafeliz.ui;

import br.com.colheitafeliz.domain.crop.CropCatalog;
import br.com.colheitafeliz.domain.crop.CropType;
import br.com.colheitafeliz.game.CropCatalogLoader;
import br.com.colheitafeliz.game.GameState;
import br.com.colheitafeliz.game.Tool;
import br.com.colheitafeliz.testsupport.ManualClock;
import com.badlogic.gdx.math.Rectangle;
import java.util.List;
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
        assertEquals(0, java.math.BigDecimal.ONE.compareTo(game.greenCoins()));
        assertEquals(0, new java.math.BigDecimal("500").compareTo(game.wallet().coins()));
        assertTrue(panel.handles(0, 0));
    }

    @Test void marketScrollsTheCatalogAndKeepsVipSeedsInTheirOwnFilter() {
        CropCatalog catalog = CropCatalogLoader.load();
        ManualClock clock = new ManualClock();
        GameState game = new GameState(clock::instant, clock, catalog, bound -> 99);
        game.progress().award(new java.math.BigDecimal("10000000"));
        game.wallet().credit(100_000);
        game.wallet().credit(br.com.colheitafeliz.domain.economy.Currency.GREEN, new java.math.BigDecimal("200"));
        assertTrue(game.buyVip().success());
        MarketPanel modal = new MarketPanel(catalog);
        modal.showShop();
        assertTrue(modal.scroll(cx(MarketPanel.cell(0)), cy(MarketPanel.cell(0)), 20, game));
        assertEquals(4, modal.scrollRows(), "a rolagem para na última linha comum");
        assertTrue(modal.isOpen());

        assertTrue(modal.click(cx(MarketPanel.cell(7)), cy(MarketPanel.cell(7)), game).success());
        assertEquals(1, game.inventory().seeds(catalog.get("caviar")), "última semente comum");
        assertNull(modal.click(cx(MarketPanel.cell(8)), cy(MarketPanel.cell(8)), game), "posição vazia após a última semente comum");
        Rectangle vip = MarketPanel.seedGroupButton(true);
        modal.click(cx(vip), cy(vip), game);
        assertEquals(0, modal.scrollRows(), "trocar filtro volta ao início");
        assertTrue(modal.click(cx(MarketPanel.cell(4)), cy(MarketPanel.cell(4)), game).success());
        assertEquals(1, game.inventory().seeds(catalog.get("coco")), "última semente VIP");

        assertNull(modal.click(MarketPanel.LEFT + MarketPanel.WIDTH / 2f, MarketPanel.BOTTOM + MarketPanel.HEIGHT - 20, game));
        assertTrue(modal.isOpen(), "clique fora da grade não fecha nem vaza");
        assertTrue(modal.handles(10, 10));
        modal.click(cx(MarketPanel.tab(1)), cy(MarketPanel.tab(1)), game);
        assertEquals(0, modal.scrollRows(), "trocar de aba volta ao início da lista");
        modal.click(cx(MarketPanel.tab(0)), cy(MarketPanel.tab(0)), game);
        assertEquals(0, modal.scrollRows());
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

    @Test void animalScrollReachesEveryTypeIncludingLastProtector() {
        var clock = new ManualClock();
        var game = new GameState(clock::instant, clock, CropCatalogLoader.load(), bound -> 99);
        while (game.progress().level() < 60) game.progress().award(game.progress().xpToNext());
        game.wallet().credit(10_000_000);
        var panel = new MarketPanel(game.catalog()); panel.showAnimals();
        for (int index = 0; index < 15; index++) {
            if (index >= 4) panel.scroll(cx(MarketPanel.animalRow(0)), cy(MarketPanel.animalRow(0)), 1, game);
            int row = index - panel.scrollRows();
            assertTrue(panel.click(cx(MarketPanel.buyAnimalButton(row)), cy(MarketPanel.buyAnimalButton(row)), game).success());
            assertEquals(game.animalTypes().get(index).id(), game.barn().get(index).type().id());
        }
        assertEquals(11, panel.scrollRows(), "a rolagem para no último grupo de quatro animais");
        panel.showShop(); assertEquals(0, panel.scrollRows());
    }

    @Test void scrollbarStartsAtTopAndCanBeDraggedToTheBottom() {
        var game = new GameState(new ManualClock()::instant, CropCatalogLoader.load(), bound -> 99);
        var panel = new MarketPanel(game.catalog());
        panel.showShop();
        float trackX = MarketPanel.LEFT + MarketPanel.WIDTH - 24.5f;
        float bottom = MarketPanel.BOTTOM + 26;
        float height = MarketPanel.HEIGHT - 46 - 18 - 36 - 12 - 76;
        float thumbHeight = height * 2 / 6;

        assertTrue(panel.beginScrollDrag(trackX, bottom + height - 1, game));
        assertEquals(0, panel.scrollRows(), "o indicador começa no topo da lista");
        assertTrue(panel.dragScroll(bottom + thumbHeight - 1, game));
        assertEquals(4, panel.scrollRows(), "arrastar o indicador até embaixo mostra o fim da lista");
        panel.endScrollDrag();
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

    @Test void everySeedOfTheCatalogFitsOnScreenAndCanBePicked() {
        List<CropType> crops = CropCatalogLoader.load().all();
        Toolbar toolbar = new Toolbar();
        for (int i = 0; i < crops.size(); i++) {
            Rectangle r = Toolbar.seedRect(i, crops.size());
            assertTrue(r.x >= 0 && r.x + r.width <= 1600 && r.y >= 0 && r.y + r.height <= 900, crops.get(i).id());
            assertEquals(crops.get(i), toolbar.seedHit(cx(r), cy(r), crops), crops.get(i).id());
        }
    }

    @Test void shopListsFertilizersAndBuysInEachAcceptedCurrency() {
        CropCatalog catalog = CropCatalogLoader.load();
        ManualClock clock = new ManualClock();
        GameState game = new GameState(clock::instant, clock, catalog, bound -> 99);
        game.progress().award(new java.math.BigDecimal("1000000"));
        game.wallet().credit(10_000);
        game.wallet().credit(br.com.colheitafeliz.domain.economy.Currency.GREEN, new java.math.BigDecimal("5"));
        MarketPanel modal = new MarketPanel(catalog);
        modal.showShop();
        assertNull(modal.click(cx(MarketPanel.fertilizerButton()), cy(MarketPanel.fertilizerButton()), game));
        assertTrue(modal.fertilizers());
        int rapido = game.fertilizers().indexOf(game.fertilizer("rapido"));
        Rectangle green = MarketPanel.buyFertilizerButton(rapido, 1);
        java.math.BigDecimal before = game.greenCoins();
        assertTrue(modal.click(cx(green), cy(green), game).success());
        assertEquals(1, game.inventory().fertilizer("rapido"));
        assertEquals(0, before.compareTo(game.greenCoins()), "1 verde, sem converter para ouro");
        int comum = game.fertilizers().indexOf(game.fertilizer("comum"));
        Rectangle noGreen = MarketPanel.buyFertilizerButton(comum, 1);
        assertNull(modal.click(cx(noGreen), cy(noGreen), game), "o comum não tem preço em verdes");
        modal.click(cx(MarketPanel.tab(0)), cy(MarketPanel.tab(0)), game);
        assertFalse(modal.fertilizers(), "a aba volta para a grade de sementes");
    }

    @Test void fertilizerTrayOpensWithTheToolAndPicksAType() {
        CropCatalog catalog = CropCatalogLoader.load();
        GameState game = new GameState(new ManualClock()::instant, catalog, bound -> 99);
        Toolbar toolbar = new Toolbar();
        toolbar.picked(Tool.FERTILIZER, Tool.HAND);
        assertTrue(toolbar.trayOpen());
        var types = game.fertilizers();
        for (int i = 0; i < types.size(); i++) {
            Rectangle r = Toolbar.seedRect(i, types.size());
            assertEquals(types.get(i), toolbar.fertilizerHit(cx(r), cy(r), types));
        }
        toolbar.picked(Tool.FERTILIZER, Tool.FERTILIZER);
        assertFalse(toolbar.trayOpen(), "a mesma ferramenta de novo fecha a bandeja");
    }

    @Test void seedTrayOpensWithTheBagAndClosesAfterPickingASeed() {
        Toolbar toolbar = new Toolbar();
        assertFalse(toolbar.trayOpen());
        toolbar.picked(Tool.SEED, Tool.CURSOR);
        assertTrue(toolbar.trayOpen());
        toolbar.closeTray();
        assertFalse(toolbar.trayOpen());
        toolbar.picked(Tool.SEED, Tool.SEED);
        assertTrue(toolbar.trayOpen(), "o saco de novo reabre a faixa");
        toolbar.picked(Tool.SEED, Tool.SEED);
        assertFalse(toolbar.trayOpen(), "e com a faixa aberta, fecha");
        toolbar.picked(Tool.HOE, Tool.SEED);
        assertFalse(toolbar.trayOpen());
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
