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
import java.time.LocalDate;
import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import java.util.Set;
import java.util.function.Supplier;
import static br.com.colheitafeliz.server.OnlineViews.JSON;
import static br.com.colheitafeliz.server.OnlineViews.object;

final class OnlineGameService implements AutoCloseable {
    private static final BigDecimal DAILY_ALLOWANCE = BigDecimal.valueOf(100);
    private static final Set<String> PEST_ACTIONS = Set.of("SPREAD_INSECTS", "SPREAD_WEEDS");
    private static final Set<String> VISIT_ACTIONS = Set.of("STEAL", "LOVE", "SPREAD_INSECTS", "SPREAD_WEEDS");
    private record Loaded(UUID owner, String name, GameState game, long revision, GameSnapshot before, LocalDate allowanceDay) {}
    private final JdbcOnlineStore store;
    private final PublishedNotes publishedNotes;
    private final Clock clock;
    private final RandomSource random;
    private final int limit;
    private final CropCatalog catalog = CropCatalogLoader.load();
    private final SecureRandom codes = new SecureRandom();

    OnlineGameService(JdbcOnlineStore store, Clock clock, RandomSource random, int limit) {
        this(store,clock,random,limit,null);
    }
    OnlineGameService(JdbcOnlineStore store, Clock clock, RandomSource random, int limit,PublishedNotes publishedNotes) {
        this.store = store; this.clock = clock; this.random = random; this.limit = limit;
        this.publishedNotes=publishedNotes;
    }

    record AutomationOffer(String id,String name,String description,BigDecimal greenPrice,int durationDays) {}
    static java.util.List<AutomationOffer> automationOffers(){return java.util.List.of(
            new AutomationOffer("water","Regador automático","Rega suas plantas quando necessário, mesmo com você fora do jogo.",GameState.AUTOMATION_PRICE,30),
            new AutomationOffer("pests","Removedor automático de pragas","Remove ervas e insetos 15 minutos após surgirem.",GameState.AUTOMATION_PRICE,30),
            new AutomationOffer("animals","Cuidador automático de animais","Usa sua ração e recolhe produtos prontos quando há espaço. Não vende animais.",GameState.AUTOMATION_PRICE,30));}

    @Override public void close() { store.close(); }

    JsonObject catalog() {
        var fixed = Clock.fixed(clock.instant(), ZoneOffset.UTC);
        var game = new GameState(fixed::instant, fixed, catalog, bound -> 99);
        var feedPrices = new JsonObject();
        for (var type : game.animalTypes()) if (type.kind() != br.com.colheitafeliz.domain.animal.AnimalType.Kind.PROTECTOR)
            feedPrices.addProperty(type.feedId(), type.feedPrice());
        return object("crops", catalog.all(), "animals", game.animalTypes(), "fertilizers", game.fertilizers(),
                "feedPrices", feedPrices, "vip", object("greenPrice", GameState.VIP_PRICE),"automationServices",automationOffers());
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
        return store.transaction(actor, tx -> farm(tx, actor, authenticatedName));
    }

    JsonObject farm(JdbcOnlineStore.Tx tx, UUID actor, String authenticatedName) {
            var participant = active(tx, actor);
            if (authenticatedName != null && !cleanName(authenticatedName).equals(participant.name()))
                tx.updateName(actor, cleanName(authenticatedName));
            tx.lockFarms(actor);
            var farm = load(tx, actor);
            farm.game().claimChristmasGift(participant.registeredAt());
            var view=withRequestLease(persist(tx, farm, false, true));
            if(publishedNotes!=null&&tx.claimUpdateNotes(actor,publishedNotes.version(),publishedNotes.day(clock.instant())))
                view.add("updateNotes",publishedNotes.view(clock.instant()));
            return view;
    }

    JsonObject command(UUID actor, Command command) {
        return store.transaction(actor, tx -> command(tx, actor, command));
    }

    JsonObject command(JdbcOnlineStore.Tx tx, UUID actor, Command command) {
            active(tx, actor);
            return receipt(tx, actor, command.requestId(), hash("OWN:" + JSON.toJson(command)), () -> {
                tx.lockFarms(actor);
                var farm = load(tx, actor);
                checkRevision(farm, command);
                ActionResult result;
                if (command.action().equals("CLAIM_DAILY_ALLOWANCE")) {
                    LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
                    if (farm.allowanceDay() != null && !today.isAfter(farm.allowanceDay())) {
                        result = ActionResult.fail("A ajuda diária já foi resgatada. Aguarde a próxima virada do dia UTC.");
                    } else {
                        farm.game().wallet().credit(Currency.GOLD, DAILY_ALLOWANCE);
                        tx.recordAllowance(actor, today);
                        farm = new Loaded(farm.owner(), farm.name(), farm.game(), farm.revision(), farm.before(), today);
                        result = ActionResult.ok("Ajuda diária resgatada: +100 ouro.");
                    }
                } else result = act(farm.game(), command);
                return object("result", result, "farm", persist(tx, farm, true, true));
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
                tx.addPlayer(actor, cleanName(name), limit,clock.instant());
                tx.befriend(actor, invite.owner());
                tx.accept(hash(code), actor);
                return object("success", true, "message", "Convite aceito. Vocês são amigos.");
            });
        });
    }

    JsonArray friends(UUID actor) {
        return store.transaction(actor, tx -> friends(tx, actor));
    }

    JsonArray friends(JdbcOnlineStore.Tx tx, UUID actor) {
        active(tx, actor);var result=new JsonArray();
        for(var player:tx.friends(actor,clock.instant()))result.add(object("id",player.id(),"name",player.name(),"active",player.active(),"avatar",player.avatar(),"vipActive",player.vipActive()));
        return result;
    }

    static String cleanName(String name) {
        String clean = name == null ? "" : name.replaceAll("[\\p{Cntrl}\\u061C\\u200E\\u200F\\u202A-\\u202E\\u2066-\\u2069]", "").strip();
        if (clean.isEmpty()) return "Fazendeiro";
        int end = Math.min(40, clean.length());
        if (end == 40 && Character.isHighSurrogate(clean.charAt(39))) end = 39;
        return clean.substring(0, end);
    }

    JsonObject visit(UUID actor, UUID owner) {
        return store.transaction(actor, tx -> visit(tx, actor, owner));
    }

    JsonObject visit(JdbcOnlineStore.Tx tx, UUID actor, UUID owner) {
            authorizeVisit(tx, actor, owner);
            tx.lockFarms(owner);
            return persist(tx, load(tx, owner), false, false);
    }

    JsonObject visitAction(UUID actor, UUID owner, Command command) {
        command.requireTile();
        if (command.harvestRevision() == null) throw new ApiFailure(400, "Falta a revisão da temporada.");
        if (!VISIT_ACTIONS.contains(command.action())) throw new ApiFailure(400, "Ação de visita inválida.");
        return store.transaction(actor, tx -> visitAction(tx, actor, owner, command));
    }

    JsonObject visitAction(JdbcOnlineStore.Tx tx, UUID actor, UUID owner, Command command) {
        command.requireTile();
        if (command.harvestRevision() == null) throw new ApiFailure(400, "Falta a revisão da temporada.");
        if (!VISIT_ACTIONS.contains(command.action())) throw new ApiFailure(400, "Ação de visita inválida.");
            authorizeVisit(tx, actor, owner);
            return receipt(tx, actor, command.requestId(), hash("VISIT:" + owner + ":" + JSON.toJson(command)), () -> {
                tx.lockFarms(actor, owner);
                var destination = load(tx, owner); var visitor = load(tx, actor);
                checkRevision(destination, command);
                long season = Command.revision(command.harvestRevision());
                var visits = new VisitService();
                ActionResult result;
                br.com.colheitafeliz.game.TheftAttempt theft=null;
                if (command.action().equals("STEAL")) {
                    // The enclosing transaction holds the actor lock across cooldown, transfer and receipt.
                    Instant now = clock.instant();
                    Instant lastTheft = tx.lastTheft(actor);
                    Instant availableAt = lastTheft == null ? now : lastTheft.plus(Duration.ofHours(24));
                    if (now.isBefore(availableAt)) {
                        Duration remaining = Duration.between(now, availableAt);
                        long seconds = remaining.getSeconds() + (remaining.getNano() == 0 ? 0 : 1);
                        theft = br.com.colheitafeliz.game.TheftAttempt.blocked("Você já usou sua tentativa de furto nas últimas 24 horas. Nova tentativa em "
                                + seconds / 3600 + "h " + seconds % 3600 / 60 + "min " + seconds % 60 + "s.");
                    } else {
                        theft = stealAfterOwnerWindow(visits, destination, visitor, command, season, now);
                        if (theft.consumesCooldown()) tx.recordTheft(actor, now);
                    }
                    result=theft.result();
                } else if (PEST_ACTIONS.contains(command.action())) {
                    Instant now = clock.instant();
                    Instant last = tx.lastPest(actor, command.action());
                    Instant availableAt = last == null ? now : last.plus(Duration.ofHours(12));
                    if (now.isBefore(availableAt)) {
                        Duration remaining = Duration.between(now, availableAt);
                        long seconds = remaining.getSeconds() + (remaining.getNano() == 0 ? 0 : 1);
                        result = ActionResult.fail("Você já lançou esse tipo de praga nas últimas 12 horas. Disponível em "
                                + seconds / 3600 + "h " + seconds % 3600 / 60 + "min " + seconds % 60 + "s.");
                    } else {
                        result = command.action().equals("SPREAD_INSECTS")
                                ? visits.spreadInsects(destination.game(), visitor.game(), command.column(), command.row(), season)
                                : visits.spreadWeeds(destination.game(), visitor.game(), command.column(), command.row(), season);
                        if (result.success()) tx.recordPest(actor, command.action(), now);
                    }
                } else {
                    result = visits.fertilizeVisit(destination.game(), visitor.game(), command.column(), command.row(), "amor", season);
                }
                var reply=object("result", result, "farm", persist(tx, visitor, false, true),
                        "visitedFarm", persist(tx, destination, false, false));
                if(theft!=null)reply.add("theftOutcome",object("kind",theft.kind(),"goldDebited",theft.goldDebited().toPlainString()));
                return reply;
            });
    }

    private br.com.colheitafeliz.game.TheftAttempt stealAfterOwnerWindow(VisitService visits, Loaded destination, Loaded visitor,
                                                       Command command, long season, Instant now) {
        var crop = destination.game().farm().tile(command.column(), command.row()).crop();
        Instant matured = crop == null ? null : crop.maturedAt(destination.game().now());
        if (matured != null) {
            Instant availableAt = matured.plus(Duration.ofMinutes(5));
            if (now.isBefore(availableAt)) {
                Duration remaining = Duration.between(now, availableAt);
                long seconds = remaining.getSeconds() + (remaining.getNano() == 0 ? 0 : 1);
                return br.com.colheitafeliz.game.TheftAttempt.blocked("Colheita reservada ao dono por mais " + seconds / 60 + "min " + seconds % 60 + "s.");
            }
        }
        return visits.attemptSteal(destination.game(), visitor.game(), command.column(), command.row(), season, visitor.name(),random);
    }

    private Loaded load(JdbcOnlineStore.Tx tx, UUID owner) {
        var participant = active(tx, owner);
        var saved = tx.farm(owner);
        Instant now = saved != null && clock.instant().isBefore(saved.time()) ? saved.time() : clock.instant();
        var fixed = Clock.fixed(now, ZoneOffset.UTC);
        GameState game = saved == null ? new GameState(fixed::instant, fixed, catalog, random)
                : GameState.restoreOnline(saved.time(),now,catalog,saved.snapshot(),random);
        if (saved == null) game.startNewFarm();
        game.refresh(); game.protectedPlots();
        return new Loaded(owner, participant.name(), game, saved == null ? 0 : saved.revision(), saved == null ? null : saved.snapshot(), saved == null ? null : saved.allowanceDay());
    }

    private JsonObject persist(JdbcOnlineStore.Tx tx, Loaded loaded, boolean force, boolean own) {
        loaded.game().runAutomationsNow();
        var snapshot = loaded.game().snapshot();
        var compared=snapshot;
        if(loaded.before()!=null&&loaded.before().automations()!=null&&snapshot.automations()!=null)
            compared=snapshot.withAutomationCursor(loaded.before().automations().cursor());
        long revision = force || !compared.equals(loaded.before()) ? Math.incrementExact(loaded.revision()) : loaded.revision();
        tx.save(loaded.owner(), snapshot, revision, loaded.game().now());
        var view = OnlineViews.farm(loaded.owner(), loaded.name(), loaded.game(), revision, own);
        if (own) {
            LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
            boolean available = loaded.allowanceDay() == null || today.isAfter(loaded.allowanceDay());
            LocalDate resetDay = (available ? today : loaded.allowanceDay()).plusDays(1);
            view.add("dailyAllowance", object("amount", DAILY_ALLOWANCE.toPlainString(), "available", available,
                    "nextResetAt", resetDay.atStartOfDay(ZoneOffset.UTC).toInstant().toString()));
        }
        return view;
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
        tx.collectSaves();
        var response = withRequestLease(operation.get()); tx.remember(actor, id, hash, response, expires); return response;
    }
    private JsonObject withRequestLease(JsonObject response) {
        response.add("requestLease", requestId());
        return response;
    }
    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    private ActionResult act(GameState game, Command c) {
        Currency currency = c.currency() == null ? Currency.GOLD : Currency.valueOf(c.currency());
        return switch (c.action()) {
            case "BUY_AUTOMATION" -> game.buyAutomation(c.requireItem());
            case "SET_AVATAR", "IMPORT_AVATAR" -> game.selectAvatar(new br.com.colheitafeliz.game.PlayerAvatar(c.requireItem(),c.photoUrl()),c.action().equals("IMPORT_AVATAR"));
            case "DISMISS_NOTIFICATION" -> game.dismissNotification(c.requireItem());
            case "DISMISS_READ_NOTIFICATIONS" -> game.dismissReadNotifications();
            case "READ_NOTIFICATION" -> game.markNotificationRead(c.requireItem());
            case "READ_ALL_NOTIFICATIONS" -> game.markAllNotificationsRead();
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
