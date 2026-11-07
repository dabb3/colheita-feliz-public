package br.com.colheitafeliz.ui;

import br.com.colheitafeliz.game.*;
import br.com.colheitafeliz.domain.economy.Currency;
import br.com.colheitafeliz.testsupport.ManualClock;
import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlotPurchasePanelTest {
    @Test void openingAndCancellingNeverDebitsAndConfirmationClosesBeforeSecondClick() {
        var clock=new ManualClock();var game=new GameState(clock::instant,clock,CropCatalogLoader.load(),bound->99);
        game.progress().award(new BigDecimal("100000"));game.wallet().credit(Currency.GREEN,new BigDecimal("100"));
        var panel=new PlotPurchasePanel();panel.open(1,0);assertEquals(0,new BigDecimal("105").compareTo(game.greenCoins()));
        var cancel=PlotPurchasePanel.cancelButton();panel.click(cancel.x+2,cancel.y+2,game);assertFalse(panel.isOpen());assertFalse(game.land().isUnlocked(1,0));panel.open(1,0);
        var button=PlotPurchasePanel.confirmButton();assertTrue(panel.click(button.x+2,button.y+2,game).success());
        assertFalse(panel.isOpen());assertNull(panel.click(button.x+2,button.y+2,game));
        assertEquals(0,new BigDecimal("56").compareTo(game.greenCoins()), "50 verdes pelo terreno e 1 verde pela tarefa diária de comércio");
    }
    @Test void confirmRevalidatesCurrentBalance() {
        var clock=new ManualClock();var game=new GameState(clock::instant,clock,CropCatalogLoader.load(),bound->99);
        var panel=new PlotPurchasePanel();panel.open(1,0);var button=PlotPurchasePanel.confirmButton();
        assertFalse(panel.click(button.x+2,button.y+2,game).success());assertFalse(game.land().isUnlocked(1,0));
    }
    @Test void fertilizerDurationKeepsHoursAndRemainingMinutes() {
        assertEquals("1 hora e 30 minutos",FertilizerDuration.format(Duration.ofMinutes(90)));
        assertEquals("2 horas",FertilizerDuration.format(Duration.ofHours(2)));
        assertEquals("1 minuto",FertilizerDuration.format(Duration.ofMinutes(1)));
        assertEquals("1 hora e 1 minuto",FertilizerDuration.format(Duration.ofMinutes(61)));
    }
}
