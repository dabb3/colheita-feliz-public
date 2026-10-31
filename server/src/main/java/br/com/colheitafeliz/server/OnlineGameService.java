package br.com.colheitafeliz.server;

import br.com.colheitafeliz.domain.crop.CropCatalog;
import br.com.colheitafeliz.domain.economy.Currency;
import br.com.colheitafeliz.domain.farm.RandomSource;
import br.com.colheitafeliz.game.ActionResult;
import br.com.colheitafeliz.game.CropCatalogLoader;
import br.com.colheitafeliz.game.GameSnapshot;
import br.com.colheitafeliz.game.GameState;
import br.com.colheitafeliz.game.Tool;
import br.com.colheitafeliz.game.VisitService;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import java.util.function.Supplier;
import static br.com.colheitafeliz.server.OnlineViews.JSON;
import static br.com.colheitafeliz.server.OnlineViews.object;

final class OnlineGameService implements AutoCloseable {
    private record Loaded(UUID owner, String name, GameState game, long revision, GameSnapshot before) {}
    private final JdbcOnlineStore store;
    private final Clock clock;
    private final RandomSource random;
    private final int limit;
    private final CropCatalog catalog = CropCatalogLoader.load();
    private final SecureRandom codes = new SecureRandom();

    OnlineGameService(JdbcOnlineStore store, Clock clock, RandomSource random, int limit) {
        this.store = store; this.clock = clock; this.random = random; this.limit = limit;
    }

    @Override public void close() { store.close(); }

    JsonObject catalog() {
        var fixed = Clock.fixed(clock.instant(), ZoneOffset.UTC);
        var game = new GameState(fixed::instant, fixed, catalog, bound -> 99);
        var feedPrices = new JsonObject();
        for (var type : game.animalTypes()) if (type.kind() != br.com.colheitafeliz.domain.animal.AnimalType.Kind.PROTECTOR)
            feedPrices.addProperty(type.feedId(), type.feedPrice());
        return object("crops", catalog.all(), "animals", game.animalTypes(), "fertilizers", game.fertilizers(),
                "feedPrices", feedPrices, "vip", object("greenPrice", GameState.VIP_PRICE));
    }

    JsonObject requestId() {
        Instant now = clock.instant();
        UUID id = RequestIds.issue(now);
        return object("requestId", id.toString(), "expiresAt", RequestIds.validate(id, now).toString());
    }

    JsonObject farm(UUID actor) {
        return farm(actor, null);
    }

    JsonObject farm(UUID actor, String authenticatedName) {
        return store.transaction(actor, tx -> {
            active(tx, actor);
            if (authenticatedName != null) tx.updateName(actor, cleanName(authenticatedName));
            tx.lockFarms(actor);
            var farm = load(tx, actor);
            return persist(tx, farm, false, true);
        });
    }

    JsonObject command(UUID actor, Command command) {
        return store.transaction(actor, tx -> {
            active(tx, actor);
            return receipt(tx, actor, command.requestId(), hash("OWN:" + JSON.toJson(command)), () -> {
                tx.lockFarms(actor);
                var farm = load(tx, actor);
                checkRevision(farm, command);
                var result = act(farm.game(), command);
                return object("result", result, "farm", persist(tx, farm, true, true));
            });
        });
    }

    JsonObject createInvitation(UUID actor, UUID requestId) {
        return store.transaction(actor, tx -> {
            active(tx, actor);
            return receipt(tx, actor, requestId, hash("INVITE"), () -> {
                byte[] bytes = new byte[32]; codes.nextBytes(bytes);
                String code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
                Instant expires = clock.instant().plus(Duration.ofDays(7));
                tx.invite(hash(code), actor, expires);
                return object("code", code, "expiresAt", expires.toString());
            });
        });
    }

    JsonObject acceptInvitation(UUID actor, String name, String code, UUID requestId) {
        if (code == null || !code.matches("[A-Za-z0-9_-]{43}")) throw new ApiFailure(400, "Convite inválido.");
        return store.transaction(actor, tx -> {
            var participant = tx.player(actor);
            if (participant != null && !participant.active()) throw new ApiFailure(403, "Participação revogada.");
            return receipt(tx, actor, requestId, hash("ACCEPT:" + code), () -> {
                var invite = tx.invitation(hash(code));
                if (invite == null || !clock.instant().isBefore(invite.expiresAt()) || actor.equals(invite.owner()))
                    throw new ApiFailure(404, "Convite indisponível.");
                active(tx, invite.owner());
                if (invite.usedBy() != null && !invite.usedBy().equals(actor)) throw new ApiFailure(409, "Convite já utilizado.");
                tx.addPlayer(actor, cleanName(name), limit);
                tx.befriend(actor, invite.owner());
                tx.accept(hash(code), actor);
                return object("success", true, "message", "Convite aceito. Vocês são amigos.");
            });
        });
    }

    JsonArray friends(UUID actor) {
        return store.transaction(actor, tx -> { active(tx, actor); return JSON.toJsonTree(tx.friends(actor)).getAsJsonArray(); });
    }

    private static String cleanName(String name) {
        String clean = name == null ? "Fazendeiro" : name.replaceAll("\\p{Cntrl}", "").strip();
        if (clean.isEmpty()) clean = "Fazendeiro";
        return clean.substring(0, Math.min(40, clean.length()));
    }

    JsonObject visit(UUID actor, UUID owner) {
        return store.transaction(actor, tx -> {
            authorizeVisit(tx, actor, owner);
            tx.lockFarms(owner);
            return persist(tx, load(tx, owner), false, false);
        });
    }

    JsonObject visitAction(UUID actor, UUID owner, Command command) {
        command.requireTile();
        if (command.harvestRevision() == null) throw new ApiFailure(400, "Falta a revisão da temporada.");
        if (!command.action().equals("STEAL") && !command.action().equals("LOVE")) throw new ApiFailure(400, "Ação de visita inválida.");
        return store.transaction(actor, tx -> {
            authorizeVisit(tx, actor, owner);
            return receipt(tx, actor, command.requestId(), hash("VISIT:" + owner + ":" + JSON.toJson(command)), () -> {
                tx.lockFarms(actor, owner);
                var destination = load(tx, owner); var visitor = load(tx, actor);
                checkRevision(destination, command);
                long season = Command.revision(command.harvestRevision());
                var visits = new VisitService();
                var result = command.action().equals("STEAL")
                        ? visits.steal(destination.game(), visitor.game(), command.column(), command.row(), season)
                        : visits.fertilizeVisit(destination.game(), visitor.game(), command.column(), command.row(), "amor", season);
                return object("result", result, "farm", persist(tx, visitor, false, true),
                        "visitedFarm", persist(tx, destination, false, false));
            });
        });
    }

    private Loaded load(JdbcOnlineStore.Tx tx, UUID owner) {
        var participant = active(tx, owner);
        var saved = tx.farm(owner);
        Instant now = saved != null && clock.instant().isBefore(saved.time()) ? saved.time() : clock.instant();
        var fixed = Clock.fixed(now, ZoneOffset.UTC);
        GameState game = saved == null ? new GameState(fixed::instant, fixed, catalog, random)
                : GameState.restore(fixed::instant, fixed, catalog, saved.snapshot(), random);
        if (saved == null) game.startNewFarm();
        game.refresh(); game.protectedPlots();
        return new Loaded(owner, participant.name(), game, saved == null ? 0 : saved.revision(), saved == null ? null : saved.snapshot());
    }

    private JsonObject persist(JdbcOnlineStore.Tx tx, Loaded loaded, boolean force, boolean own) {
        var snapshot = loaded.game().snapshot();
        long revision = force || !snapshot.equals(loaded.before()) ? Math.incrementExact(loaded.revision()) : loaded.revision();
        tx.save(loaded.owner(), snapshot, revision, loaded.game().now());
        return OnlineViews.farm(loaded.owner(), loaded.name(), loaded.game(), revision, own);
    }

    private static JdbcOnlineStore.Player active(JdbcOnlineStore.Tx tx, UUID actor) {
        var player = tx.player(actor);
        if (player == null || !player.active()) throw new ApiFailure(403, "Esta conta não participa do grupo de teste.");
        return player;
    }
    private static void authorizeVisit(JdbcOnlineStore.Tx tx, UUID actor, UUID owner) {
        active(tx, actor);
        if (actor.equals(owner) || !tx.friends(actor, owner)) throw new ApiFailure(404, "Fazenda indisponível.");
        active(tx, owner);
    }
    private static void checkRevision(Loaded farm, Command command) {
        if (farm.revision() != Command.revision(command.expectedRevision())) throw new ApiFailure(409, "A fazenda mudou. Atualize antes de agir.");
    }
    private JsonObject receipt(JdbcOnlineStore.Tx tx, UUID actor, UUID id, String hash, Supplier<JsonObject> operation) {
        Instant now = clock.instant();
        Instant expires = RequestIds.validate(id, now);
        var previous = tx.receipt(actor, id);
        if (previous != null) {
            if (!hash.equals(previous.hash())) throw new ApiFailure(409, "Identificador reutilizado com outra ação.");
            return previous.response();
        }
        tx.prepareReceipt(actor, now);
        var response = operation.get(); tx.remember(actor, id, hash, response, expires); return response;
    }
    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    private ActionResult act(GameState game, Command c) {
        Currency currency = c.currency() == null ? Currency.GOLD : Currency.valueOf(c.currency());
        return switch (c.action()) {
            case "BUY_SEED" -> game.buySeed(catalog.get(c.requireItem()), currency);
            case "BUY_ANIMAL" -> game.buyAnimal(game.animalType(c.requireItem()), currency);
            case "BUY_FEED" -> game.buyFeed(c.requireItem());
            case "BUY_FERTILIZER" -> game.buyFertilizer(c.requireItem(), currency);
            case "BUY_VIP" -> game.buyVip();
            case "SELL_ALL" -> game.sellEverything();
            case "SELL_ITEM" -> catalog.all().stream().anyMatch(type -> type.id().equals(c.requireItem()))
                    ? game.sellProduce(catalog.get(c.requireItem())) : game.sellProduct(game.animalTypes().stream()
                    .filter(type -> type.productId().equals(c.requireItem())).findFirst().orElseThrow(() -> new ApiFailure(400, "Produto inválido.")), false);
            case "SELL_ITEM_ALL" -> catalog.all().stream().anyMatch(type -> type.id().equals(c.requireItem()))
                    ? game.sellAllProduce(catalog.get(c.requireItem())) : game.sellProduct(game.animalTypes().stream()
                    .filter(type -> type.productId().equals(c.requireItem())).findFirst().orElseThrow(() -> new ApiFailure(400, "Produto inválido.")), true);
            case "TEND_ANIMAL" -> game.tendAnimal(c.requireItem());
            case "SELL_ANIMAL" -> {
                int index = -1;
                for (int i = 0; i < game.barn().size(); i++) if (game.barn().get(i).type().id().equals(c.requireItem())) { index = i; break; }
                yield index < 0 ? ActionResult.fail("Animal indisponível.") : game.sellAnimal(index);
            }
            case "BUY_PLOT" -> { c.requireTile(); yield game.buyPlot(c.column(), c.row()); }
            case "PLOW", "PLANT", "WATER", "HARVEST", "WEED", "PESTICIDE", "FERTILIZE" -> {
                c.requireTile();
                if (c.action().equals("PLANT")) {
                    var selected = game.selectSeed(catalog.get(c.requireItem()));
                    if (!selected.success()) yield selected;
                }
                if (c.action().equals("FERTILIZE")) game.selectFertilizer(c.requireItem());
                game.selectTool(switch (c.action()) {
                    case "PLOW" -> Tool.HOE; case "PLANT" -> Tool.SEED; case "WATER" -> Tool.WATER;
                    case "HARVEST" -> Tool.HAND; case "WEED" -> Tool.WEEDER; case "PESTICIDE" -> Tool.PESTICIDE;
                    default -> Tool.FERTILIZER;
                });
                yield game.act(c.column(), c.row());
            }
            default -> throw new ApiFailure(400, "Ação indisponível na própria fazenda.");
        };
    }
}
