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
        JsonArray tiles = new JsonArray();
        for (int row = 0; row < GameState.ROWS; row++) for (int col = 0; col < GameState.COLUMNS; col++) {
            var tile = game.farm().tile(col, row);
            var crop = tile.crop();
            tiles.add(object("column", col, "row", row, "unlocked", game.land().isUnlocked(col, row),
                    "state", tile.state(), "cropId", crop == null ? null : crop.type().id(),
                    "stage", crop == null ? 0 : crop.stageAt(now), "season", crop == null ? 0 : crop.season(),
                    "seasons", crop == null ? 0 : crop.type().seasons(), "dry", crop != null && crop.dryAt(now),
                    "paused", crop != null && crop.paused(), "weeds", tile.pest().name().equals("WEEDS"),
                    "insects", tile.insects(), "fertilized", tile.fertilized(), "protected", protectedPlots.contains(game.land().at(col, row).id()),
                    "harvestRevision", Long.toString(tile.harvestRevision()),
                    "remainingSeconds", crop == null ? 0 : crop.remainingAt(now).toSeconds()));
        }
        JsonArray animals = new JsonArray();
        for (var animal : game.barn().animals()) animals.add(object("id", animal.type().id(), "stage", animal.stageAt(now),
                "status", animal.statusAt(now), "remainingSeconds", animal.remainingAt(now).toSeconds()));
        JsonObject view = object("owner", owner.toString(), "name", name, "revision", Long.toString(revision),
                "serverTime", now.toString(), "own", own, "tiles", tiles, "animals", animals);
        if (!own) return view;
        view.addProperty("coins", game.wallet().coins().stripTrailingZeros().toPlainString());
        view.addProperty("greenCoins", game.greenCoins().stripTrailingZeros().toPlainString());
        view.addProperty("level", game.progress().level());
        view.addProperty("currentXp", game.progress().currentXp().toPlainString());
        view.addProperty("xpToNext", game.progress().xpToNext().toPlainString());
        view.addProperty("vipActive", game.vipActive());
        view.add("vipExpiresAt", JSON.toJsonTree(game.vip().expiresAt() == null ? null : game.vip().expiresAt().toString()));
        JsonObject seeds = new JsonObject();
        for (var crop : game.catalog().all()) seeds.addProperty(crop.id(), game.inventory().seeds(crop));
        view.add("seedCounts", seeds);
        view.add("produceLots", JSON.toJsonTree(game.inventory().produceLots()));
        view.add("depositTotals", JSON.toJsonTree(game.depositTotals()));
        view.add("feed", JSON.toJsonTree(game.inventory().feedSnapshot()));
        view.add("fertilizers", JSON.toJsonTree(game.inventory().fertilizerSnapshot()));
        view.add("tutorial", JSON.toJsonTree(game.tutorial().current()));
        JsonArray missions = new JsonArray();
        for (var mission : game.dailyMissions().missions()) missions.add(object("id", mission.id(), "title", mission.title(),
                "green", mission.green(), "done", game.dailyMissions().completed(mission)));
        view.add("missions", missions);
        LandExpansion.Plot next = game.land().next();
        view.add("nextPlot", JSON.toJsonTree(next));
        return view;
    }
}
