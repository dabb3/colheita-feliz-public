package br.com.colheitafeliz.ui;

import java.time.Duration;

/** Fertilizer effects keep their full duration, including remaining minutes. */
public final class FertilizerDuration {
    private FertilizerDuration() {}
    public static String format(Duration duration) {
        long minutes=Math.max(1,(duration.getSeconds()+59)/60),hours=minutes/60,rest=minutes%60;
        String hour=hours+ (hours==1?" hora":" horas");
        String minute=rest+(rest==1?" minuto":" minutos");
        return hours==0?minute:rest==0?hour:hour+" e "+minute;
    }
}
