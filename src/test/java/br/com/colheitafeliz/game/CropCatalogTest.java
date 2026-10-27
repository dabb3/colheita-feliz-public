package br.com.colheitafeliz.game;

import br.com.colheitafeliz.domain.crop.CropCatalog;
import br.com.colheitafeliz.domain.crop.CropType;
import br.com.colheitafeliz.domain.economy.Currency;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** O catálogo em disco precisa ser a transcrição exata da tabela da seção 2 do game-rule. */
class CropCatalogTest {
    private final CropCatalog catalog = CropCatalogLoader.load();

    /** Uma linha da tabela normativa, com os números já convertidos. */
    private record Row(String name, boolean vip, int level, Currency currency, BigDecimal price,
                       String commonSale, String vipSale, long firstHours, long nextHours, int seasons,
                       int units, List<BigDecimal> xp) {}

    @Test void catalogHasExactlyTheThirtyThreeCropsOfTheRule() throws IOException {
        List<Row> rows = normativeRows();
        assertEquals(33, rows.size(), "a tabela da seção 2 tem 33 culturas");
        assertEquals(rows.stream().map(Row::name).toList(), catalog.all().stream().map(CropType::name).toList());
        for (String legacy : new String[]{"arroz", "repolho", "girassol"})
            assertThrows(IllegalArgumentException.class, () -> catalog.get(legacy), legacy);
    }

    @Test void everyCropMatchesItsRowOfTheRule() throws IOException {
        for (Row row : normativeRows()) {
            CropType type = catalog.all().stream().filter(crop -> crop.name().equals(row.name())).findFirst().orElseThrow();
            String id = type.id();
            assertEquals(row.vip(), type.vipOnly(), id);
            assertEquals(row.level(), type.requiredLevel(), id);
            assertEquals(Map.of(row.currency(), row.price()).keySet(), type.seedPrices().keySet(), id);
            assertEquals(0, row.price().compareTo(type.seedPrices().get(row.currency())), id);
            assertEquals(Duration.ofHours(row.firstHours()), type.growthTime(1), id);
            assertEquals(row.seasons(), type.seasons(), id);
            for (int season = 2; season <= type.seasons(); season++)
                assertEquals(Duration.ofHours(row.nextHours()), type.growthTime(season), id);
            assertEquals(1, row.units(), id);
            assertEquals(row.seasons(), row.xp().size(), id);
            for (int season = 1; season <= type.seasons(); season++)
                assertEquals(0, row.xp().get(season - 1).compareTo(type.harvestXp(season)), id + " temporada " + season);
            assertEquals(5, type.stages(), id);
            assertEquals(row.vipSale(), sale(row.price(), "1.25") + suffix(row.currency()), id);
            assertEquals(row.commonSale(), row.vip() ? "Bloqueada sem VIP" : sale(row.price(), "1.15") + suffix(row.currency()), id);
        }
    }

    @Test void spotChecksOfTheTable() {
        CropType turnip = catalog.get("nabo");
        assertEquals(Map.of(Currency.GOLD, new BigDecimal("300")), turnip.seedPrices());
        assertEquals(Duration.ofSeconds(36_000), turnip.growthTime(1));
        assertEquals(1, turnip.requiredLevel());
        CropType strawberry = catalog.get("morango");
        assertEquals(Duration.ofSeconds(111_600), strawberry.growthTime(1));
        assertEquals(Duration.ofSeconds(43_200), strawberry.growthTime(2));
        assertEquals(Duration.ofSeconds(43_200), strawberry.growthTime(3));
        CropType coconut = catalog.get("coco");
        assertEquals(Map.of(Currency.GREEN, new BigDecimal("20")), coconut.seedPrices());
        assertEquals(50, coconut.requiredLevel());
        assertEquals(6, coconut.seasons());
        assertTrue(coconut.vipOnly());
        assertEquals("batata-doce", catalog.get("batata-doce").spriteStem());
    }

    @Test void invalidCatalogFailsNamingTheBrokenCrop() {
        String broken = "[{\"id\":\"nabo\", \"name\":\"Nabo\", \"growthSeconds\":36000, \"regrowthSeconds\":0, "
                + "\"seasons\":1, \"stages\":5, \"spriteStem\":\"nabo\", \"requiredLevel\":1, \"vipOnly\":false}]";
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> CropCatalogLoader.load(new java.io.StringReader(broken)));
        assertTrue(error.getCause().getMessage().contains("nabo"), error.getCause().getMessage());
        String noRegrowth = "[{\"id\":\"tomate\", \"name\":\"Tomate\", \"growthSeconds\":93600, \"regrowthSeconds\":0, "
                + "\"seasons\":2, \"stages\":5, \"spriteStem\":\"tomate\", \"requiredLevel\":5, \"vipOnly\":false, "
                + "\"seedPrices\":{\"GOLD\":950}}]";
        error = assertThrows(IllegalStateException.class, () -> CropCatalogLoader.load(new java.io.StringReader(noRegrowth)));
        assertTrue(error.getCause().getMessage().contains("tomate"), error.getCause().getMessage());
        assertThrows(IllegalStateException.class, () -> CropCatalogLoader.load(new java.io.StringReader("[]")));
    }

    /** Valor de venda de uma unidade, arredondado para cima em uma casa decimal e escrito como na tabela. */
    private static String sale(BigDecimal price, String multiplier) {
        BigDecimal value = price.multiply(new BigDecimal(multiplier)).setScale(1, RoundingMode.CEILING);
        String text = value.stripTrailingZeros().toPlainString();
        String[] parts = text.split("\\.");
        StringBuilder integer = new StringBuilder(parts[0]);
        for (int i = integer.length() - 3; i > 0; i -= 3) integer.insert(i, '.');
        return integer + (parts.length > 1 ? "," + parts[1] : "");
    }

    private static String suffix(Currency currency) { return currency == Currency.GOLD ? " ouro" : " verdes"; }

    private static List<Row> normativeRows() throws IOException {
        String rule = Files.readString(Path.of("docs/game-rule.md"));
        String section = rule.substring(rule.indexOf("## 2."), rule.indexOf("## 3."));
        List<Row> rows = new ArrayList<>();
        for (String line : section.lines().toList()) {
            if (!line.startsWith("| ") || line.startsWith("| Semente")) continue;
            String[] cells = line.substring(2, line.length() - 2).split(" \\| ");
            String[] price = cells[3].split(" ");
            rows.add(new Row(cells[0], cells[1].equals("VIP"), Integer.parseInt(cells[2]),
                    price[1].equals("ouro") ? Currency.GOLD : Currency.GREEN, number(price[0]),
                    cells[4], cells[5], hours(cells[6]), cells[7].equals("Não se aplica") ? 0 : hours(cells[7]),
                    Integer.parseInt(cells[8]), Integer.parseInt(cells[9]),
                    java.util.Arrays.stream(cells[10].split(" → ")).map(CropCatalogTest::number).toList()));
        }
        return rows;
    }

    private static long hours(String cell) { return Long.parseLong(cell.replace(" h", "")); }

    private static BigDecimal number(String cell) { return new BigDecimal(cell.replace(".", "").replace(',', '.')); }
}
