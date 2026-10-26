package br.com.colheitafeliz.game;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GameTextTest {
    @Test void moneyUsesOneDecimalWithoutLosingPrecision() {
        assertEquals("345,0", GameText.money(new BigDecimal("345")));
        assertEquals("0,0", GameText.money(BigDecimal.ZERO));
        assertEquals("20,7", GameText.money(new BigDecimal("20.7")));
        assertEquals("9007199254740993,1", GameText.money(new BigDecimal("9007199254740993.1")));
    }
    @Test void xpKeepsEveryFractionWithoutExtraZeros() {
        assertEquals("14,4375", GameText.xp(new BigDecimal("14.437500")));
        assertEquals("0", GameText.xp(BigDecimal.ZERO));
        assertEquals("1000", GameText.xp(new BigDecimal("1000.00")));
    }
    @Test void vipExpiryUsesExplicitZone() {
        assertEquals("25/11/2026 09:00", GameText.vipExpiry(
                Instant.parse("2026-11-25T12:00:00Z"), ZoneId.of("America/Sao_Paulo")));
    }
    @Test void totalsUseTheSameMoneyFormat() {
        assertEquals("0,0", GameState.formatTotals(java.util.Map.of()));
        assertEquals("345,0 ouro, 20,7 verdes", GameState.formatTotals(java.util.Map.of(
                br.com.colheitafeliz.domain.economy.Currency.GOLD, new BigDecimal("345"),
                br.com.colheitafeliz.domain.economy.Currency.GREEN, new BigDecimal("20.7"))));
    }
}
