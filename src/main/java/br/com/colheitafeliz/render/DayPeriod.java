package br.com.colheitafeliz.render;

import java.time.Clock;
import java.time.LocalTime;

/** Ciclo visual pelo horário real local; não depende da velocidade da simulação. */
enum DayPeriod {
    DAY, SUNSET, NIGHT;

    static DayPeriod at(Clock localClock) {
        LocalTime time = LocalTime.now(localClock);
        if (time.isBefore(LocalTime.of(6, 0)) || !time.isBefore(LocalTime.of(18, 31))) return NIGHT;
        return time.isBefore(LocalTime.of(17, 0)) ? DAY : SUNSET;
    }
}
