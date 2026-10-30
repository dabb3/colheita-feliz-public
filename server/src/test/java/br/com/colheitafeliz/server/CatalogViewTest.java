package br.com.colheitafeliz.server;

import org.junit.jupiter.api.Test;
import java.time.Clock;
import static org.junit.jupiter.api.Assertions.*;

class CatalogViewTest {
    @Test void shopIncludesAuthoritativeFeedAndVipOffers() {
        var service = new OnlineGameService(null, Clock.systemUTC(), bound -> bound - 1, 5);
        var catalog = service.catalog();
        assertEquals(100, catalog.getAsJsonObject("vip").get("greenPrice").getAsInt());
        assertEquals(80, catalog.getAsJsonObject("feedPrices").get("growth").getAsInt());
        assertEquals(120, catalog.getAsJsonObject("feedPrices").get("producer").getAsInt());
    }
}
