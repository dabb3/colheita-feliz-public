package br.com.colheitafeliz.domain.economy;

import java.math.BigDecimal;

/** Immutable acquisition terms; integer tenths avoid payment-dependent rounding. */
public record AnimalSale(int economyVersion, String animalId, long commonRevenueTenths, long vipRevenueTenths) {
    public AnimalSale {
        if (economyVersion != 2 || animalId == null || animalId.isBlank()
                || commonRevenueTenths <= 0 || vipRevenueTenths < commonRevenueTenths)
            throw new IllegalArgumentException("Invalid animal sale terms");
    }
    public BigDecimal unitRevenue(boolean vip) {
        return BigDecimal.valueOf(vip ? vipRevenueTenths : commonRevenueTenths, 1);
    }
}
