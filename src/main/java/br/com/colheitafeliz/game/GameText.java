package br.com.colheitafeliz.game;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class GameText {
    private static final DateTimeFormatter VIP_EXPIRY = DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm");
    private GameText() {}
    public static String money(BigDecimal value) { return value.setScale(1).toPlainString().replace('.', ','); }
    public static String xp(BigDecimal value) { return value.stripTrailingZeros().toPlainString().replace('.', ','); }
    public static String animalSale(br.com.colheitafeliz.domain.animal.Animal animal, boolean vip) {
        var amount = br.com.colheitafeliz.domain.economy.Market.unitRevenue(animal.origin(),vip)
                .setScale(1,java.math.RoundingMode.CEILING);
        return money(amount) + (animal.origin().currency()==br.com.colheitafeliz.domain.economy.Currency.GOLD ? " ouro" : " verdes");
    }
    public static String vipExpiry(Instant expiry, ZoneId zone) { return VIP_EXPIRY.withZone(zone).format(expiry); }
}
