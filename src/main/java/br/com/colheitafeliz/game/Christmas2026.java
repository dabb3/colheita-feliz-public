package br.com.colheitafeliz.game;

import java.time.Instant;

/** Real calendar in São Paulo (UTC-03 at these dates), independent of accelerated cultivation time. */
public final class Christmas2026 {
    public static final Instant START=Instant.parse("2026-11-06T03:00:00Z");
    public static final Instant END=Instant.parse("2027-01-01T03:00:00Z");
    public static final Instant GIFT_START=Instant.parse("2026-12-24T03:00:00Z");
    public static final Instant REGISTRATION_CUTOFF=Instant.parse("2026-12-25T03:00:00Z");
    private Christmas2026() {}
    public static boolean active(Instant now){return !now.isBefore(START)&&now.isBefore(END);}
    public static boolean giftDue(Instant now,Instant registeredAt){return !now.isBefore(GIFT_START)&&registeredAt.isBefore(REGISTRATION_CUTOFF);}
    public static String backgroundPath(Instant now,String period){
        if(active(now))return switch(period){case "day"->"classic/background/background-natal-dia.png";case "sunset"->"classic/background/background-natal-por-do-sol.png";case "night"->"classic/background/background-natal-noite.png";default->throw new IllegalArgumentException("Unknown period");};
        return switch(period){case "day"->"classic/background/background.png";case "sunset"->"classic/background/background-sunset.png";case "night"->"classic/background/background-night.png";default->throw new IllegalArgumentException("Unknown period");};
    }
    public static String musicPath(Instant now,boolean night){
        return active(now)?(night?"sounds/musica-evento-natal-noite.mp3":"sounds/musica-evento-natal-dia-e-por-do-sol.mp3"):(night?"sounds/noite.mp3":"sounds/farm_loop.mp3");
    }
}
