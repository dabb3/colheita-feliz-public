package br.com.colheitafeliz.testsupport;

import br.com.colheitafeliz.domain.crop.CropType;
import br.com.colheitafeliz.domain.economy.Currency;
import br.com.colheitafeliz.domain.farm.FarmTile;
import br.com.colheitafeliz.domain.farm.TileState;
import br.com.colheitafeliz.domain.inventory.ItemLot;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/** Culturas da tabela normativa montadas à mão, para os testes de domínio não dependerem do catálogo em disco. */
public final class TestCrops {
    private TestCrops() {}

    /** Morango: 31 h na primeira temporada, 12 h nas duas seguintes. */
    public static CropType strawberry() {
        return new CropType("morango", "Morango", 1550, 1783, 111_600, 5, "morango", 11, 1, 0,
                43_200, 3, false, Map.of(Currency.GOLD, new BigDecimal("1550")));
    }

    /** Nabo: 10 h, uma temporada, 15 XP. */
    public static CropType turnip() {
        return new CropType("nabo", "Nabo", 300, 345, 36_000, 5, "nabo", 1, 1, 0,
                0, 1, false, Map.of(Currency.GOLD, new BigDecimal("300")));
    }

    public static ItemLot origin(CropType type) {
        return new ItemLot(type.id(), Currency.GOLD, type.seedPrices().get(Currency.GOLD), 1, false);
    }

    /** Rega sempre que a terra seca e avança o relógio até a planta ficar pronta; devolve o instante da maturação. */
    public static Instant growUntilReady(FarmTile tile, Instant now) {
        Instant t = now;
        while (tile.state() != TileState.READY) {
            if (tile.crop().dryAt(t)) tile.water(t);
            t = t.plus(tile.crop().moistDuration());
            tile.refresh(t);
        }
        return t;
    }
}
