package br.com.colheitafeliz.domain.crop;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import br.com.colheitafeliz.domain.inventory.AcquisitionSource;

public final class CropCatalog {
    private final Map<String, CropType> byId;
    private final List<CropType> ordered;

    public CropCatalog(List<CropType> crops) {
        if (crops == null || crops.isEmpty()) throw new IllegalArgumentException("Empty crop catalog");
        Map<String, CropType> entries = new LinkedHashMap<>();
        for (CropType crop : crops)
            if (entries.putIfAbsent(crop.id(), crop) != null)
                throw new IllegalArgumentException("Duplicate crop: " + crop.id());
        validateRewardCrops(entries);
        byId = Map.copyOf(entries);
        ordered = List.copyOf(crops);
    }

    public CropType get(String id) {
        CropType crop = byId.get(id);
        if (crop == null) throw new IllegalArgumentException("Unknown crop: " + id);
        return crop;
    }

    public List<CropType> all() { return ordered; }

    private static void validateRewardCrops(Map<String, CropType> entries) {
        String[] ids = {"semente-bronze", "semente-prata", "semente-ouro", "semente-diamante"};
        String[] names = {"Semente de Bronze", "Semente de Prata", "Semente de Ouro", "Semente de Diamante"};
        String[] stems = {"semente-bronze", "semente-prata", "semente-ouro", "semente-diamante"};
        long[] growth = {86_400, 129_600, 172_800, 259_200};
        long[] revenue = {50, 100, 150, 200};
        for (int i = 0; i < ids.length; i++) {
            CropType crop = entries.get(ids[i]);
            if (crop != null && (!names[i].equals(crop.name()) || !stems[i].equals(crop.spriteStem())
                    || crop.growthSeconds() != growth[i] || crop.regrowthSeconds() != 0 || crop.seasons() != 1
                    || crop.stages() != 5 || crop.requiredLevel() != 1 || crop.vipOnly()
                    || crop.acquisitionSource() != AcquisitionSource.LOGIN_REWARD || !crop.seedPrices().isEmpty()
                    || crop.cycleRevenueTenths() != revenue[i]))
                throw new IllegalArgumentException("Invalid login reward crop: " + ids[i]);
        }
        for (CropType crop : entries.values())
            if (crop.acquisitionSource() == AcquisitionSource.LOGIN_REWARD && !java.util.Arrays.asList(ids).contains(crop.id()))
                throw new IllegalArgumentException("Unknown login reward crop: " + crop.id());
    }
}
