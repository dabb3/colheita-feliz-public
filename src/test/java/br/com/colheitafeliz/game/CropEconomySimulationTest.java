package br.com.colheitafeliz.game;

import br.com.colheitafeliz.domain.crop.CropType;
import br.com.colheitafeliz.domain.economy.Currency;
import br.com.colheitafeliz.domain.economy.Market;
import br.com.colheitafeliz.domain.economy.Wallet;
import br.com.colheitafeliz.domain.farm.FarmTile;
import br.com.colheitafeliz.domain.farm.TileState;
import br.com.colheitafeliz.domain.inventory.Inventory;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import br.com.colheitafeliz.testsupport.ManualClock;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Fixed-crop strategies isolate the plant economy from rewards, level unlocks and animals. */
class CropEconomySimulationTest {
    private static final String CASHFLOW_HEADER = "crop,logins,plots,vip,loss_percent,initial_crop_capital,final_crop_cash,net_crop_cash,harvest_opportunities,stolen,in_progress,vip_cost_green,nominal_profit_hour,seed_spend,revenue,book_investment,potential_receipts,book_assets\n";

    @Test void compareCulturesWithTheSameCapitalAndSeparateCashFromInvestment() throws Exception {
        StringBuilder csv = new StringBuilder(CASHFLOW_HEADER);
        for (var crop : CropCatalogLoader.load().all()) {
            if (crop.vipOnly()) continue;
            for (int logins : new int[]{1,3,6}) run(csv, crop, logins, 5, false, 0, new BigDecimal("125000"));
        }
        Path output = Path.of("build/reports/economy-v1/equal-capital-cashflow.csv");
        Files.createDirectories(java.util.Objects.requireNonNull(output.getParent()));
        Files.writeString(output, csv);
    }
    @Test void thirtyDayNewFarmProgressionRetainsLevelsDailyRewardsAndSeedLiquidity() throws Exception {
        StringBuilder csv = new StringBuilder("logins,plots,vip,loss_percent,level,gold,green,harvest_opportunities,stolen\n");
        int scenarios = 0;
        for (int logins : new int[]{1,3,6}) for (int plots : new int[]{5,15,30})
            for (boolean vip : new boolean[]{false,true}) for (int loss : new int[]{0,10,25}) {
                var clock = new ManualClock();
                var game = new GameState(clock::instant, clock, CropCatalogLoader.load(), bound -> bound - 1);
                // Already-owned plots and the VIP fee represent capital earned before this experiment.
                game.land().restore(java.util.stream.IntStream.range(0, plots).boxed().toList());
                if (vip) {
                    game.wallet().credit(Currency.GREEN, GameState.VIP_PRICE);
                    assertTrue(game.buyVip().success());
                }
                int opportunities = 0, stolen = 0;
                for (int visit = 0; visit < 30 * logins; visit++) {
                    if (visit > 0) clock.advanceSeconds(86400L / logins);
                    // Models the manual web allowance claim once per day; it grants no XP.
                    if (visit % logins == 0) game.wallet().credit(Currency.GOLD, new BigDecimal("100"));
                    game.refresh();
                    for (int row = 0; row < GameState.ROWS; row++) for (int col = 0; col < GameState.COLUMNS; col++) {
                        if (!game.land().isUnlocked(col, row)) continue;
                        var tile = game.farm().tile(col, row);
                        if (tile.state() == TileState.READY) {
                            opportunities++;
                            if (stolen < opportunities * loss / 100) { tile.harvest(clock.instant()); stolen++; }
                            else { game.selectTool(Tool.HAND); assertTrue(game.act(col, row).success()); }
                        }
                    }
                    if (!game.inventory().produceLots().isEmpty()) assertTrue(game.sellEverything().success());
                    for (int row = 0; row < GameState.ROWS; row++) for (int col = 0; col < GameState.COLUMNS; col++) {
                        if (!game.land().isUnlocked(col, row)) continue;
                        var tile = game.farm().tile(col, row);
                        if (tile.state() == TileState.GRASS || tile.state() == TileState.DEAD) {
                            game.selectTool(Tool.HOE); assertTrue(game.act(col, row).success());
                        }
                        if (tile.state() == TileState.PLOWED) {
                            var seed = game.catalog().all().stream().filter(type -> !type.vipOnly() && game.unlocked(type)
                                            && game.wallet().coins().compareTo(type.seedPrice().getValue()) >= 0)
                                    .reduce((previous, current) -> current);
                            if (seed.isPresent()) {
                                assertTrue(game.buySeed(seed.get(), Currency.GOLD).success());
                                assertTrue(game.selectSeed(seed.get()).success());
                                assertTrue(game.act(col, row).success());
                            }
                        }
                        if (tile.crop() != null && tile.crop().dryAt(clock.instant())) {
                            game.selectTool(Tool.WATER); assertTrue(game.act(col, row).success());
                        }
                    }
                }
                assertTrue(game.wallet().coins().signum() >= 0);
                assertEquals(opportunities * loss / 100, stolen);
                csv.append(logins).append(',').append(plots).append(',').append(vip).append(',').append(loss)
                        .append(',').append(game.progress().level()).append(',').append(game.wallet().coins().toPlainString())
                        .append(',').append(game.greenCoins().toPlainString()).append(',').append(opportunities)
                        .append(',').append(stolen).append('\n');
                scenarios++;
            }
        assertEquals(54, scenarios);
        Path output = Path.of("build/reports/economy-v1/progression-30-days.csv");
        Files.createDirectories(java.util.Objects.requireNonNull(output.getParent()));
        Files.writeString(output, csv);
    }

    @Test void thirtyDaysAcrossLoginLandVipAndLossProfiles() throws Exception {
        StringBuilder csv = new StringBuilder(CASHFLOW_HEADER);
        int scenarios = 0;
        for (var crop : CropCatalogLoader.load().all()) for (int logins : new int[]{1,3,6})
            for (int plots : new int[]{5,15,30}) for (boolean vip : new boolean[]{false,true})
                for (int loss : new int[]{0,10,25}) {
                    if (crop.vipOnly() && !vip) continue;
                    run(csv, crop, logins, plots, vip, loss);
                    scenarios++;
                }
        assertEquals(1647, scenarios); // 28 common × 54 + 5 VIP-only × 27.
        Path output = Path.of("build/reports/economy-v1/30-days.csv");
        Files.createDirectories(java.util.Objects.requireNonNull(output.getParent()));
        Files.writeString(output, csv);
    }

    private static void run(StringBuilder csv, CropType crop, int logins, int plots, boolean vip, int loss) {
        run(csv, crop, logins, plots, vip, loss, crop.seedPrice().getValue().multiply(BigDecimal.valueOf(plots)));
    }

    private static void run(StringBuilder csv, CropType crop, int logins, int plots, boolean vip, int loss, BigDecimal capital) {
        var price = crop.seedPrice();
        var currency = price.getKey();
        var wallet = new Wallet(currency == Currency.GOLD ? capital : BigDecimal.ZERO,
                currency == Currency.GREEN ? capital : BigDecimal.ZERO);
        var inventory = new Inventory();
        var market = new Market(wallet, inventory);
        FarmTile[] field = new FarmTile[plots];
        for (int i = 0; i < plots; i++) field[i] = new FarmTile();
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        int opportunities = 0, stolen = 0;
        BigDecimal revenue = BigDecimal.ZERO, seedSpend = BigDecimal.ZERO;
        for (int visit = 0; visit < 30 * logins; visit++) {
            Instant now = start.plusSeconds(visit * (86400L / logins));
            for (var tile : field) {
                tile.refresh(now);
                if (tile.state() == TileState.READY) {
                    opportunities++;
                    var product = tile.harvest(now);
                    if (stolen < opportunities * loss / 100) stolen++;
                    else inventory.addProduceLot(product);
                }
            }
            if (!inventory.produceLots().isEmpty()) revenue = revenue.add(market.sellEverything(vip).get(currency));
            for (var tile : field) {
                if (tile.state() == TileState.GRASS) tile.plow();
                if (tile.state() == TileState.DEAD) tile.clearDead();
                if (tile.state() == TileState.PLOWED && wallet.balance(currency).compareTo(price.getValue()) >= 0) {
                    market.buySeed(crop, currency);
                    seedSpend = seedSpend.add(price.getValue());
                    tile.plant(crop, now, inventory.takeSeed(crop.id()));
                }
                if (tile.crop() != null && tile.crop().dryAt(now)) tile.water(now);
            }
        }
        int planted = 0;
        BigDecimal investment = BigDecimal.ZERO, potential = BigDecimal.ZERO;
        for (var tile : field) if (tile.crop() != null) {
            planted++;
            int left = crop.seasons() - tile.crop().season() + 1;
            investment = investment.add(price.getValue().multiply(BigDecimal.valueOf(left))
                    .divide(BigDecimal.valueOf(crop.seasons()), 8, java.math.RoundingMode.HALF_EVEN));
            for (int season = tile.crop().season(); season <= crop.seasons(); season++)
                potential = potential.add(tile.crop().origin().harvested(season).cropSale().unitRevenue(vip));
        }
        assertEquals(opportunities * loss / 100, stolen);
        assertTrue(wallet.balance(currency).signum() >= 0);
        assertTrue(inventory.produceLots().isEmpty());
        assertEquals(0, capital.subtract(seedSpend).add(revenue).compareTo(wallet.balance(currency)));
        long hours = (crop.growthSeconds() + (crop.seasons() - 1L) * crop.regrowthSeconds()) / 3600;
        var nominal = BigDecimal.valueOf(crop.cycleRevenueTenths(), 1).subtract(price.getValue())
                .divide(BigDecimal.valueOf(hours), 4, java.math.RoundingMode.HALF_EVEN);
        csv.append(crop.id()).append(',').append(logins).append(',').append(plots).append(',').append(vip)
                .append(',').append(loss).append(',').append(capital.toPlainString()).append(',')
                .append(wallet.balance(currency).toPlainString()).append(',')
                .append(wallet.balance(currency).subtract(capital).toPlainString()).append(',')
                .append(opportunities).append(',').append(stolen).append(',').append(planted)
                .append(',').append(vip ? 100 : 0).append(',').append(nominal).append(',').append(seedSpend)
                .append(',').append(revenue).append(',').append(investment).append(',').append(potential)
                .append(',').append(wallet.balance(currency).add(investment)).append('\n');
    }
}
