package br.com.colheitafeliz.domain.crop;

import java.time.Duration;

public record CropType(String id, String name, int seedPrice, int salePrice,
                       long growthSeconds, int stages, String spriteStem, int requiredLevel,
                       int harvestYield, int harvestXp) {
    public CropType {
        if (id == null || id.isBlank() || name == null || name.isBlank()
                || spriteStem == null || spriteStem.isBlank()
                || seedPrice <= 0 || salePrice <= 0 || growthSeconds <= 0 || stages < 2
                || requiredLevel < 1 || harvestYield < 1 || harvestXp < 0)
            throw new IllegalArgumentException("Invalid crop definition");
    }

    public Duration growthTime() { return Duration.ofSeconds(growthSeconds); }
}
