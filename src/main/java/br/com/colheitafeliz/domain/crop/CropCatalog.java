package br.com.colheitafeliz.domain.crop;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CropCatalog {
    private final Map<String, CropType> byId;
    private final List<CropType> ordered;

    public CropCatalog(List<CropType> crops) {
        if (crops == null || crops.isEmpty()) throw new IllegalArgumentException("Empty crop catalog");
        Map<String, CropType> entries = new LinkedHashMap<>();
        for (CropType crop : crops)
            if (entries.putIfAbsent(crop.id(), crop) != null)
                throw new IllegalArgumentException("Duplicate crop: " + crop.id());
        byId = Map.copyOf(entries);
        ordered = List.copyOf(crops);
    }

    public CropType get(String id) {
        CropType crop = byId.get(id);
        if (crop == null) throw new IllegalArgumentException("Unknown crop: " + id);
        return crop;
    }

    public List<CropType> all() { return ordered; }
}
