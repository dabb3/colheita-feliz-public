package br.com.colheitafeliz.domain.animal;

import java.time.Duration;

/**
 * Um animal produtivo: preço, nível, ração por ciclo, tempo para produzir e o produto que rende.
 * {@code max} limita quantos cabem no cercado.
 */
public record AnimalType(String id, String name, int price, int requiredLevel, int feedPrice,
                         long productionSeconds, String productId, String productName, int productYield,
                         int productPrice, int collectXp, int max) {
    public AnimalType {
        if (id == null || id.isBlank() || name == null || name.isBlank() || productId == null
                || productId.isBlank() || productName == null || productName.isBlank()
                || price <= 0 || requiredLevel < 1 || feedPrice <= 0 || productionSeconds <= 0
                || productYield < 1 || productPrice <= 0 || collectXp < 0 || max < 1)
            throw new IllegalArgumentException("Invalid animal definition");
    }

    public Duration productionTime() { return Duration.ofSeconds(productionSeconds); }
}
