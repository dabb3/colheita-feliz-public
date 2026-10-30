package br.com.colheitafeliz.server;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

record Command(UUID requestId, String expectedRevision, String action, String itemId, String currency,
               Integer column, Integer row, String harvestRevision) {
    private static final Set<String> ACTIONS = Set.of("BUY_SEED", "BUY_ANIMAL", "BUY_FEED", "BUY_FERTILIZER",
            "BUY_VIP", "BUY_PLOT", "SELL_ALL", "SELL_ITEM", "SELL_ANIMAL", "TEND_ANIMAL",
            "PLOW", "PLANT", "WATER", "HARVEST", "WEED", "PESTICIDE", "FERTILIZE", "STEAL", "LOVE");
    Command {
        Objects.requireNonNull(requestId);
        if (!ACTIONS.contains(action)) throw new ApiFailure(400, "Ação desconhecida.");
        revision(expectedRevision);
        if (harvestRevision != null) revision(harvestRevision);
        if (itemId != null && !itemId.matches("[a-z0-9-]{1,60}")) throw new ApiFailure(400, "Item inválido.");
        if (currency != null && !Set.of("GOLD", "GREEN").contains(currency)) throw new ApiFailure(400, "Moeda inválida.");
        if ((column != null && (column < 0 || column >= 6)) || (row != null && (row < 0 || row >= 5)))
            throw new ApiFailure(400, "Terreno inválido.");
    }
    static long revision(String value) {
        try {
            if (value == null || !value.matches("[0-9]{1,19}")) throw new NumberFormatException();
            long parsed = Long.parseLong(value);
            if (parsed < 0) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException e) { throw new ApiFailure(400, "Revisão inválida."); }
    }
    void requireTile() {
        if (column == null || row == null) throw new ApiFailure(400, "Escolha um terreno.");
    }
    String requireItem() {
        if (itemId == null) throw new ApiFailure(400, "Escolha um item.");
        return itemId;
    }
}
