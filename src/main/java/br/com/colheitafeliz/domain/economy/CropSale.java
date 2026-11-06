package br.com.colheitafeliz.domain.economy;

import java.math.BigDecimal;

/** Immutable terms captured at planting. Index zero denotes the plant, positive indices its products. */
public record CropSale(int economyVersion, int seasonIndex, int seasons,
                       long cycleRevenueTenths, long cycleVipBonusTenths) {
    public CropSale {
        if (economyVersion != 1 || seasons < 1 || seasonIndex < 0 || seasonIndex > seasons
                || cycleRevenueTenths < seasons || cycleVipBonusTenths < 0)
            throw new IllegalArgumentException("Invalid crop sale terms");
    }

    public CropSale forSeason(int index) {
        if (index < 1) throw new IllegalArgumentException("Product must identify its season");
        return new CropSale(economyVersion, index, seasons, cycleRevenueTenths, cycleVipBonusTenths);
    }

    public BigDecimal unitRevenue(boolean vipActive) {
        if (seasonIndex == 0) throw new IllegalStateException("Plant origin is not saleable");
        long base = share(cycleRevenueTenths, seasons, seasonIndex);
        long bonus = vipActive ? share(cycleVipBonusTenths, seasons, seasonIndex) : 0;
        return BigDecimal.valueOf(Math.addExact(base, bonus), 1);
    }

    public static long share(long totalTenths, int seasons, int index) {
        if (totalTenths < 0 || seasons < 1 || index < 1 || index > seasons)
            throw new IllegalArgumentException("Invalid season allocation");
        return totalTenths / seasons + (index <= totalTenths % seasons ? 1 : 0);
    }
}
