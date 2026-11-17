package br.com.colheitafeliz.game;

import br.com.colheitafeliz.domain.crop.CropType;
import br.com.colheitafeliz.domain.crop.CropCatalog;
import br.com.colheitafeliz.domain.inventory.AcquisitionSource;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

/** Stateless Sunday windows; the launch date never resets on restart or deploy. */
public final class SundayHarvestEvent {
    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final LocalDate LAUNCH = LocalDate.of(2026, 11, 22);
    private SundayHarvestEvent() {}

    public static boolean eligible(CropType crop) {
        return !crop.vipOnly() && crop.acquisitionSource() == AcquisitionSource.SHOP;
    }

    public static int multiplier(CropType crop, Instant now) {
        LocalDate date = now.atZone(ZONE).toLocalDate();
        return eligible(crop) && !date.isBefore(LAUNCH) && date.getDayOfWeek() == DayOfWeek.SUNDAY ? 2 : 1;
    }

    public static View at(Instant now, CropCatalog catalog) {
        LocalDate date = now.atZone(ZONE).toLocalDate();
        boolean active = !date.isBefore(LAUNCH) && date.getDayOfWeek() == DayOfWeek.SUNDAY;
        LocalDate start = date.isBefore(LAUNCH) ? LAUNCH : date.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        LocalDate next = active ? start.plusWeeks(1) : start;
        return new View("sunday-harvest-" + start, active ? "ACTIVE" : "UPCOMING", 2,
                instant(start), instant(start.plusDays(1)), instant(next), instant(next.plusDays(1)),
                catalog.all().stream().filter(SundayHarvestEvent::eligible).map(CropType::id).sorted().toList(), ZONE.getId());
    }

    private static String instant(LocalDate date) { return date.atStartOfDay(ZONE).toInstant().toString(); }

    public record View(String id, String state, int multiplier, String startsAt, String endsAt,
                       String nextStartsAt, String nextEndsAt, List<String> cropIds, String timezone) {
        public View { cropIds = List.copyOf(cropIds); }
    }
}
