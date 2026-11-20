package br.com.colheitafeliz.server;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

record Command(UUID requestId, String expectedRevision, String action, String itemId, String currency,
               Integer column, Integer row, String harvestRevision, String photoUrl, String feedId, Integer quantity) {
    static final int MAX_PURCHASE_QUANTITY = 99;
    Command(UUID requestId,String expectedRevision,String action,String itemId,String currency,Integer column,Integer row,String harvestRevision){
        this(requestId,expectedRevision,action,itemId,currency,column,row,harvestRevision,null,null);
    }
    Command(UUID requestId,String expectedRevision,String action,String itemId,String currency,Integer column,Integer row,String harvestRevision,String photoUrl){
        this(requestId,expectedRevision,action,itemId,currency,column,row,harvestRevision,photoUrl,null);
    }
    Command(UUID requestId,String expectedRevision,String action,String itemId,String currency,Integer column,Integer row,String harvestRevision,String photoUrl,String feedId){
        this(requestId,expectedRevision,action,itemId,currency,column,row,harvestRevision,photoUrl,feedId,null);
    }
    private static final Set<String> ACTIONS = Set.of("BUY_SEED", "BUY_ANIMAL", "BUY_FEED", "BUY_FERTILIZER",
            "BUY_VIP", "BUY_PLOT", "SELL_ALL", "SELL_ITEM", "SELL_ITEM_ALL", "SELL_ANIMAL", "TEND_ANIMAL",
            "PLOW", "PLANT", "WATER", "HARVEST", "WEED", "PESTICIDE", "FERTILIZE", "STEAL", "LOVE",
            "SPREAD_INSECTS", "SPREAD_WEEDS", "CLEAN_WEEDS", "CLEAN_INSECTS", "FEED_ANIMAL", "CLAIM_DAILY_ALLOWANCE", "CLAIM_LOGIN_REWARD", "READ_NOTIFICATION", "READ_ALL_NOTIFICATIONS",
            "SET_AVATAR", "IMPORT_AVATAR", "DISMISS_NOTIFICATION", "DISMISS_READ_NOTIFICATIONS", "BUY_AUTOMATION");
    Command {
        Objects.requireNonNull(requestId);
        if (!ACTIONS.contains(action)) throw new ApiFailure(400, "Ação desconhecida.");
        if(photoUrl!=null&&!Set.of("SET_AVATAR","IMPORT_AVATAR").contains(action))throw new ApiFailure(400,"Foto não permitida nesta ação.");
        if(action.equals("CLAIM_LOGIN_REWARD")&&(itemId!=null||currency!=null||column!=null||row!=null||harvestRevision!=null||feedId!=null))
            throw new ApiFailure(400,"O calendário determina a recompensa no servidor.");
        if (feedId != null && (!action.equals("FEED_ANIMAL") || !Set.of("growth", "producer").contains(feedId)))
            throw new ApiFailure(400, "Ração inválida.");
        if (quantity != null && !Set.of("BUY_SEED", "BUY_FERTILIZER").contains(action))
            throw new ApiFailure(400, "Quantidade não permitida nesta ação.");
        if (quantity != null && (quantity < 1 || quantity > MAX_PURCHASE_QUANTITY))
            throw new ApiFailure(400, "Escolha de 1 a " + MAX_PURCHASE_QUANTITY + " unidades.");
        revision(expectedRevision);
        if (harvestRevision != null) revision(harvestRevision);
        if (itemId != null && !itemId.matches("[a-z0-9_-]{1,60}")) throw new ApiFailure(400, "Item inválido.");
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
    /** Compras antigas não mandam quantidade e continuam comprando uma unidade. */
    int purchaseQuantity() { return quantity == null ? 1 : quantity; }
    String requireItem() {
        if (itemId == null) throw new ApiFailure(400, "Escolha um item.");
        return itemId;
    }
}
