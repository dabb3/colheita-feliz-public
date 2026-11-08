package br.com.colheitafeliz.server;

import br.com.colheitafeliz.game.GameState;
import br.com.colheitafeliz.game.LandExpansion;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.UUID;

final class OnlineViews {
    static final Gson JSON = new Gson();
    private OnlineViews() {}

    static JsonObject object(Object... values) {
        JsonObject result = new JsonObject();
        for (int i = 0; i < values.length; i += 2) result.add((String) values[i], JSON.toJsonTree(values[i + 1]));
        return result;
    }

    static JsonObject farm(UUID owner, String name, GameState game, long revision, boolean own) {
        var now = game.now();
        var protectedPlots = game.protectedPlots();
        var nextPlot=game.land().next();
        JsonArray tiles = new JsonArray();
        for (int row = 0; row < GameState.ROWS; row++) for (int col = 0; col < GameState.COLUMNS; col++) {
            var tile = game.farm().tile(col, row);
            var crop = tile.crop();
            var plot=game.land().at(col,row);
            tiles.add(object("column", col, "row", row, "unlocked", game.land().isUnlocked(col, row),
                    "requiredLevel",plot.requiredLevel(),"price",plot.price(),"currency",plot.currency(),
                    "purchasable",nextPlot!=null&&nextPlot.id()==plot.id(),
                    "state", tile.state(), "cropId", crop == null ? null : crop.type().id(),
                    "stage", crop == null ? 0 : crop.stageAt(now), "season", crop == null ? 0 : crop.season(),
                    "seasons", crop == null ? 0 : crop.type().seasons(), "watered", crop != null && crop.watered(),
                    "dry", crop != null && crop.dryAt(now),
                    "paused", crop != null && crop.paused(), "weeds", tile.pest().name().equals("WEEDS"),
                    "insects", tile.insects(), "fertilized", tile.fertilized(), "protected", protectedPlots.contains(game.land().at(col, row).id()),
                    "harvestRevision", Long.toString(tile.harvestRevision()),
                    "remainingSeconds", crop == null ? 0 : crop.remainingAt(now).toSeconds(),
                    "nominalRemainingSeconds", crop == null ? 0 : Math.max(0, crop.currentDuration().toMillis() - crop.grownMillisAt(now)) / 1000));
        }
        JsonArray animals = new JsonArray();
        for (var animal : game.barn().animals()) {
            var entry = object("id", animal.type().id(), "stage", animal.stageAt(now),
                    "status", animal.statusAt(now), "remainingSeconds", animal.remainingAt(now).toSeconds());
            if (own && animal.type().kind() != br.com.colheitafeliz.domain.animal.AnimalType.Kind.PROTECTOR) {
                entry.addProperty("saleQuote", br.com.colheitafeliz.domain.economy.Market.unitRevenue(animal.origin(),game.vipActive())
                        .setScale(1,java.math.RoundingMode.CEILING).toPlainString());
                entry.addProperty("saleCurrency",animal.origin().currency().name());
                entry.addProperty("economyVersion",animal.origin().economyVersion());
            }
            animals.add(entry);
        }
        JsonObject view = object("owner", owner.toString(), "name", name, "revision", Long.toString(revision),
                "serverTime", now.toString(), "own", own, "tiles", tiles, "animals", animals);
        view.add("avatar",JSON.toJsonTree(game.avatar()==null?new br.com.colheitafeliz.game.PlayerAvatar("classic",null):game.avatar()));
        view.addProperty("coins", game.wallet().coins().stripTrailingZeros().toPlainString());
        view.addProperty("greenCoins", game.greenCoins().stripTrailingZeros().toPlainString());
        view.addProperty("level", game.progress().level());
        view.addProperty("currentXp", game.progress().currentXp().toPlainString());
        view.addProperty("xpToNext", game.progress().xpToNext().toPlainString());
        view.addProperty("vipActive",game.vipActive());
        if (!own) return view;
        view.addProperty("avatarSelected",game.avatar()!=null);
        view.add("notifications",JSON.toJsonTree(game.notifications().entries()));
        view.addProperty("unreadNotifications",game.notifications().unread());
        var automations=new JsonArray();
        for(var offer:OnlineGameService.automationOffers()){
            var expiry=game.automationExpiresAt(offer.id());
            String status=expiry==null?"NOT_PURCHASED":!game.realNow().isBefore(expiry)?"EXPIRED":game.vipActive()?"ACTIVE":"SUSPENDED";
            var benefit=JSON.toJsonTree(offer).getAsJsonObject();benefit.add("expiresAt",JSON.toJsonTree(expiry==null?null:expiry.toString()));benefit.addProperty("status",status);automations.add(benefit);
        }
        view.add("automations",automations);
        view.add("vipExpiresAt", JSON.toJsonTree(game.vip().expiresAt() == null ? null : game.vip().expiresAt().toString()));
        JsonObject seeds = new JsonObject();
        for (var crop : game.catalog().all()) seeds.addProperty(crop.id(), game.inventory().seeds(crop));
        view.add("seedCounts", seeds);
        JsonArray produce = new JsonArray();
        JsonObject itemTotals = new JsonObject();
        JsonObject itemSingles = new JsonObject();
        for (var lot : game.inventory().produceLots()) {
            JsonObject product = JSON.toJsonTree(lot).getAsJsonObject();
            product.addProperty("unitSale", br.com.colheitafeliz.domain.economy.Market.unitRevenue(lot, game.vipActive()).toPlainString());
            produce.add(product);
            if (!itemTotals.has(lot.itemId())) {
                itemTotals.add(lot.itemId(), JSON.toJsonTree(game.quoteAllItem(lot.itemId())));
                itemSingles.add(lot.itemId(), JSON.toJsonTree(game.quoteItem(lot.itemId())));
            }
        }
        view.add("produceLots", produce);
        view.add("itemSaleTotals", itemTotals);
        view.add("itemSaleSingles", itemSingles);
        view.add("depositTotals", JSON.toJsonTree(game.depositTotals()));
        view.add("feed", JSON.toJsonTree(game.inventory().feedSnapshot()));
        view.add("fertilizers", JSON.toJsonTree(game.inventory().fertilizerSnapshot()));
        view.add("tutorial", JSON.toJsonTree(game.tutorial().current()));
        view.addProperty("tutorialStep", game.tutorial().stepIndex());
        view.addProperty("tutorialTotal", br.com.colheitafeliz.game.Tutorial.STEPS.size());
        JsonArray missions = new JsonArray();
        for (var mission : game.dailyMissions().missions()) missions.add(object("id", mission.id(), "title", mission.title(),
                "green", mission.green(), "progress", game.dailyMissions().progress(mission), "target", mission.target(),
                "done", game.dailyMissions().completed(mission)));
        view.add("missions", missions);
        LandExpansion.Plot next = game.land().next();
        view.add("nextPlot",next==null?com.google.gson.JsonNull.INSTANCE:object("id",next.id(),"column",next.column(),"row",next.row(),"requiredLevel",next.requiredLevel(),"price",next.price(),"currency",next.currency()));
        return view;
    }
}
