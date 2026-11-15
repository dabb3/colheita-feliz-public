package br.com.colheitafeliz.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class LoginRewardCalendarTest {
    @Test void definesThirtyRewardsFor29500GoldAndFourExactSeeds() {
        var rewards = LoginRewardCalendar.rewards();
        assertEquals(30, rewards.size());
        assertEquals(29_500, rewards.stream().filter(reward -> reward.kind().equals("GOLD"))
                .mapToInt(reward -> Integer.parseInt(reward.amount())).sum());
        assertEquals(java.util.List.of(7, 14, 21, 30), rewards.stream().filter(reward -> reward.kind().equals("SEED"))
                .map(LoginRewardCalendar.Reward::day).toList());
        assertEquals(java.util.List.of("semente-bronze", "semente-prata", "semente-ouro", "semente-diamante"),
                rewards.stream().filter(reward -> reward.kind().equals("SEED")).map(LoginRewardCalendar.Reward::itemId).toList());
        assertEquals("2500", rewards.get(27).amount());
        assertEquals("3000", rewards.get(28).amount());
    }

    @Test void advancesOnlyOnTheNextCivilDateAndResetsAfterAMissedDateOrCycleEnd() {
        var yesterday = LocalDate.parse("2026-11-14");
        var today = yesterday.plusDays(1);
        assertEquals(2, LoginRewardCalendar.nextDay(yesterday, 1, today).day());
        assertEquals(1, LoginRewardCalendar.nextDay(yesterday, 1, today.plusDays(1)).day());
        assertTrue(LoginRewardCalendar.nextDay(yesterday, 1, today.plusDays(1)).streakReset());
        assertEquals(1, LoginRewardCalendar.nextDay(yesterday, 30, today).day());
        assertFalse(LoginRewardCalendar.nextDay(yesterday, 30, today).streakReset());
        var duplicate = LoginRewardCalendar.nextDay(today, 1, today);
        assertEquals(2, duplicate.day());
        assertFalse(duplicate.available());
        assertFalse(LoginRewardCalendar.nextDay(today, 1, today.minusDays(1)).available());
    }
}
