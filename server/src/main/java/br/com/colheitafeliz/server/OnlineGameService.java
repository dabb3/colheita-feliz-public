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
import java.time.ZoneId;
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
    private static final ZoneId LOGIN_REWARD_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final Set<String> PEST_ACTIONS = Set.of("SPREAD_INSECTS", "SPREAD_WEEDS");
    // Decisão de 2026-11-13: furto e cada tipo de praga liberam uma nova tentativa a cada 6 horas.
    static final Duration THEFT_COOLDOWN = Duration.ofHours(6), PEST_COOLDOWN = Duration.ofHours(6), GIFT_COOLDOWN = Duration.ofHours(24);
    private static final Set<String> VISIT_ACTIONS = Set.of("STEAL", "LOVE", "SPREAD_INSECTS", "SPREAD_WEEDS", "CLEAN_WEEDS", "CLEAN_INSECTS", "FEED_ANIMAL");
    private static final Set<String> CLEAN_ACTIONS = Set.of("CLEAN_WEEDS", "CLEAN_INSECTS");
    // Orçamento de XP cooperativo por visitante e dia UTC, compartilhado pelas ações de ajuda a amigos.
    static final int HELP_XP = 2, COOPERATIVE_XP_DAILY_CAP = 20;
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
        LoginRewardCalendar.requireCrops(catalog);
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
            Instant now = clock.instant();
            var ledger = tx.awayLedger(actor);
            if (ledger != null && Duration.between(ledger.lastSeenAt(), now).compareTo(AwayReports.MIN_ABSENCE) >= 0) {
                var report = AwayReports.build(farm.game(), ledger, now);
                if (report != null) {
                    // An unacknowledged report is folded into the new one, so no period is lost before the ACK.
                    var previous = tx.pendingAwayReport(actor);
                    if (previous != null) report = AwayReports.merge(previous, report, farm.game());
                    report.addProperty("id", UUID.randomUUID().toString());
                    tx.savePendingAwayReport(actor, report);
                }
            }
            // Every own visit starts a new window; shorter absences are seen in the farm itself.
            tx.saveAwayLedger(actor, AwayLedger.empty(now));
            var pendingReport = tx.pendingAwayReport(actor);
            if (pendingReport != null) {
                pendingReport.remove("windows"); // only the server needs them, to merge the next report
                view.add("awayReport", pendingReport);
            }
            if(publishedNotes!=null&&tx.claimUpdateNotes(actor,publishedNotes.version(),publishedNotes.day(clock.instant())))
                view.add("updateNotes",publishedNotes.view(clock.instant()));
            return view;
    }

    /** The client confirms it showed the report; repeating it, or naming an older report, changes nothing. */
    JsonObject acknowledgeAwayReport(UUID actor, String reportId) {
        try { UUID.fromString(reportId); } catch (RuntimeException e) { throw new ApiFailure(400, "Relatório inválido."); }
        return store.transaction(actor, tx -> {
            active(tx, actor);
            tx.acknowledgeAwayReport(actor, reportId);
            return object("ok", true);
        });
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
                } else if (command.action().equals("CLAIM_LOGIN_REWARD")) {
                    var previous = tx.loginRewardProgress(actor);
                    LocalDate today = LocalDate.ofInstant(clock.instant(), LOGIN_REWARD_ZONE);
                    var next = LoginRewardCalendar.nextDay(previous == null ? null : previous.lastClaimDate(),
                            previous == null ? 0 : previous.completedDay(), today);
                    if (!next.available()) {
                        result = ActionResult.fail("A recompensa do calendário já foi resgatada nesta data de São Paulo.");
                    } else {
                        var reward = LoginRewardCalendar.reward(next.day());
                        if (reward.kind().equals("SEED")) result = farm.game().grantLoginRewardSeed(reward.itemId());
                        else {
                            farm.game().wallet().credit(Currency.GOLD, new BigDecimal(reward.amount()));
                            result = ActionResult.ok("Recompensa do calendário resgatada: +" + reward.amount() + " ouro.");
                        }
                        if (result.success()) {
                            long cycle = previous == null ? 1 : previous.cycle() + (next.day() == 1 ? 1 : 0);
                            long total = previous == null ? 1 : previous.totalClaims() + 1;
                            tx.recordLoginRewardProgress(actor, today, next.day(), cycle, total, clock.instant());
                        }
                    }
                } else if (command.action().equals("BUY_BACKGROUND") || command.action().equals("EQUIP_BACKGROUND")) {
                    result = background(tx, actor, farm.game(), command);
                } else if (command.action().equals("EQUIP_TITLE") || command.action().equals("CLEAR_TITLE")) {
                    result = equipTitle(tx, actor, farm.game(), command);
                } else {
                    result = act(farm.game(), command);
                    if (command.action().equals("HARVEST")) result = advanceWeekly(tx, actor, farm.game(), WeeklyTasks.Task.HARVEST, result);
                    else if (command.action().equals("BUY_ANIMAL")) result = advanceWeekly(tx, actor, farm.game(), WeeklyTasks.Task.BUY_ANIMAL, result);
                    if (result.success()) {
                        var category = switch (command.action()) {
                            case "PLANT" -> Achievements.Category.PLANT;
                            case "HARVEST" -> Achievements.Category.HARVEST;
                            case "WEED", "PESTICIDE" -> Achievements.Category.CLEAN;
                            default -> null;
                        };
                        var delta = (category == null ? Achievements.Delta.NONE : Achievements.Delta.of(category))
                                .withGreen(farm.game().wallet().spent(Currency.GREEN));
                        if (!delta.empty()) tx.addAchievementCounters(actor, delta, clock.instant());
                    }
                }
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
        for(var player:tx.friends(actor,clock.instant()))result.add(object("id",player.id(),"name",player.name(),"active",player.active(),"avatar",player.avatar(),"vipActive",player.vipActive(),
                "equippedTitle",Achievements.equippedTitle(tx.achievements(player.id()).equipped())));
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
        validateVisit(command);
        return store.transaction(actor, tx -> visitAction(tx, actor, owner, command));
    }

    JsonObject visitAction(JdbcOnlineStore.Tx tx, UUID actor, UUID owner, Command command) {
        validateVisit(command);
            authorizeVisit(tx, actor, owner);
            return receipt(tx, actor, command.requestId(), hash("VISIT:" + owner + ":" + JSON.toJson(command)), () -> {
                tx.lockFarms(actor, owner);
                var destination = load(tx, owner); var visitor = load(tx, actor);
                checkRevision(destination, command);
                long season = command.harvestRevision() == null ? -1 : Command.revision(command.harvestRevision());
                var visits = new VisitService();
                ActionResult result;
                br.com.colheitafeliz.game.TheftAttempt theft=null;
                var ownerEvents = new java.util.ArrayList<AwayLedger.Event>();
                if (command.action().equals("STEAL")) {
                    // The enclosing transaction holds the actor lock across cooldown, transfer and receipt.
                    Instant now = clock.instant();
                    Instant lastTheft = tx.lastTheft(actor);
                    Instant availableAt = lastTheft == null ? now : lastTheft.plus(THEFT_COOLDOWN);
                    if (now.isBefore(availableAt)) {
                        theft = br.com.colheitafeliz.game.TheftAttempt.blocked("Você já usou sua tentativa de furto nas últimas 6 horas. Nova tentativa em "
                                + remaining(now, availableAt) + ".");
                    } else {
                        var target = destination.game().farm().tile(command.column(), command.row()).crop();
                        theft = stealAfterOwnerWindow(visits, destination, visitor, command, season, now);
                        if (theft.consumesCooldown()) tx.recordTheft(actor, now);
                        var event = awayEvent(theft, visitor.name(), target, now);
                        if (event != null) ownerEvents.add(event);
                    }
                    result=theft.result();
                } else if (PEST_ACTIONS.contains(command.action())) {
                    Instant now = clock.instant();
                    Instant last = tx.lastPest(actor, command.action());
                    Instant availableAt = last == null ? now : last.plus(PEST_COOLDOWN);
                    if (now.isBefore(availableAt)) {
                        result = ActionResult.fail("Você já lançou esse tipo de praga nas últimas 6 horas. Disponível em "
                                + remaining(now, availableAt) + ".");
                    } else {
                        var pest = command.action().equals("SPREAD_INSECTS")
                                ? visits.spreadInsects(destination.game(), visitor.game(), command.column(), command.row(), season)
                                : visits.spreadWeeds(destination.game(), visitor.game(), command.column(), command.row(), season);
                        result = pest.result();
                        if (pest.consumesCooldown()) {
                            tx.recordPest(actor, command.action(), now);
                            if (result.success()) {
                                int plotId = destination.game().land().at(command.column(), command.row()).id();
                                String arrivedAt = pestArrival(destination.game(), command.column(), command.row(), command.action());
                                if (arrivedAt == null) throw new IllegalStateException("Successful pest spread has no arrival timestamp");
                                tx.recordPestOrigin(owner, plotId, command.action(), actor, season, arrivedAt);
                            }
                        }
                    }
                } else if (CLEAN_ACTIONS.contains(command.action()) || command.action().equals("FEED_ANIMAL")) {
                    // The actor lock serializes this read-modify-write of the daily cooperative budget.
                    LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
                    if (command.action().equals("FEED_ANIMAL")) {
                        int xp = cooperativeHelpXp(tx, actor, today);
                        result = visits.feedAnimal(destination.game(), visitor.game(), command.itemId(), command.feedId(), xp);
                        if (result.success() && xp > 0) tx.addCooperativeXp(actor, today, xp);
                    } else {
                        String kind = command.action().equals("CLEAN_INSECTS") ? "SPREAD_INSECTS" : "SPREAD_WEEDS";
                        int plotId = destination.game().land().at(command.column(), command.row()).id();
                        var origin = tx.pestOrigin(owner, plotId, kind);
                        String arrivedAt = pestArrival(destination.game(), command.column(), command.row(), kind);
                        boolean reward = origin == null || !origin.actor().equals(actor) || origin.harvestRevision() != season
                                || !origin.arrivedAt().equals(arrivedAt);
                        int xp = reward ? cooperativeHelpXp(tx, actor, today) : 0;
                        result = visits.cleanPest(destination.game(), visitor.game(), command.column(), command.row(), season,
                                command.action().equals("CLEAN_INSECTS"), xp, reward);
                        if (result.success()) {
                            tx.deletePestOrigin(owner, plotId, kind);
                            if (reward && xp > 0) tx.addCooperativeXp(actor, today, xp);
                        }
                    }
                } else {
                    result = visits.fertilizeVisit(destination.game(), visitor.game(), command.column(), command.row(), "amor", season);
                    // O XP da tarefa vai para quem adubou, nunca para o dono da plantação.
                    result = advanceWeekly(tx, actor, visitor.game(), WeeklyTasks.Task.LOVE, result);
                }
                // Só ações que surtiram efeito contam; furto bloqueado, protegido ou mordido não é colheita roubada.
                var visitAchievement = !result.success() ? null
                        : command.action().equals("STEAL") ? (theft != null && theft.kind() == br.com.colheitafeliz.game.TheftAttempt.Kind.STOLEN ? Achievements.Category.STEAL : null)
                        : PEST_ACTIONS.contains(command.action()) ? Achievements.Category.SPREAD
                        : CLEAN_ACTIONS.contains(command.action()) ? Achievements.Category.CLEAN : null;
                if (visitAchievement != null) tx.addAchievementCounters(actor, Achievements.Delta.of(visitAchievement), clock.instant());
                if (result.success() && (command.action().equals("STEAL") || PEST_ACTIONS.contains(command.action()))) {
                    String type = command.action().equals("STEAL") ? "THEFT" : "PEST";
                    store.pushStore().recordEvent(tx, command.requestId(), owner, actor, type, clock.instant());
                }
                var reply=object("result", result, "farm", persist(tx, visitor, false, true),
                        "visitedFarm", persist(tx, destination, false, false, ownerEvents));
                if(theft!=null)reply.add("theftOutcome",object("kind",theft.kind(),"goldDebited",theft.goldDebited().toPlainString()));
                return reply;
            });
    }

    /** Compra (1.000.000 de ouro, uma vez, já aplicando) ou aplica de graça um fundo que a conta possui. */
    private ActionResult background(JdbcOnlineStore.Tx tx, UUID actor, GameState game, Command command) {
        String id = command.requireItem();
        if (!Backgrounds.known(id)) throw new ApiFailure(400, "Fundo inválido.");
        var state = tx.backgrounds(actor);
        if (command.action().equals("EQUIP_BACKGROUND")) {
            if (command.currency() != null) throw new ApiFailure(400, "Aplicar um fundo não tem custo.");
            if (!id.equals(Backgrounds.DEFAULT) && !state.owned().contains(id)) return ActionResult.fail("Compre esse fundo antes de aplicar.");
            if (!id.equals(state.active())) tx.saveBackgrounds(actor, state.equip(id), clock.instant());
            return ActionResult.ok(id.equals(Backgrounds.DEFAULT) ? "Fundo padrão aplicado." : "Fundo aplicado.");
        }
        if (command.currency() != null && !command.currency().equals("GOLD")) throw new ApiFailure(400, "Fundos só são vendidos por ouro.");
        if (id.equals(Backgrounds.DEFAULT)) return ActionResult.fail("O fundo padrão é gratuito.");
        if (state.owned().contains(id)) return ActionResult.fail("Você já tem esse fundo. Use Aplicar.");
        if (game.wallet().balance(Currency.GOLD).compareTo(Backgrounds.PRICE) < 0) return ActionResult.fail("Moedas insuficientes.");
        game.wallet().debit(Currency.GOLD, Backgrounds.PRICE);
        tx.saveBackgrounds(actor, state.buy(id), clock.instant());
        return ActionResult.ok("Fundo comprado e aplicado. -1.000.000 ouro.");
    }

    /** Equipa um título liberado da própria conta, ou remove o atual; nunca aceita título de outra pessoa ou bloqueado. */
    private ActionResult equipTitle(JdbcOnlineStore.Tx tx, UUID actor, GameState game, Command command) {
        var progress = tx.achievements(actor);
        var unlocked = Achievements.unlocked(progress, game);
        if (command.action().equals("CLEAR_TITLE")) {
            tx.saveTitles(actor, unlocked, null, clock.instant());
            return ActionResult.ok("Título removido.");
        }
        var achievement = Achievements.find(command.requireItem());
        if (achievement == null) throw new ApiFailure(400, "Título inválido.");
        if (!unlocked.contains(achievement.id())) return ActionResult.fail("Esse título ainda está bloqueado.");
        tx.saveTitles(actor, unlocked, achievement, clock.instant());
        return ActionResult.ok("Título equipado: " + achievement.title());
    }

    /** Conta uma ação já confirmada e paga 100 XP na única vez em que a tarefa chega à meta na semana. */
    private ActionResult advanceWeekly(JdbcOnlineStore.Tx tx, UUID actor, GameState game, WeeklyTasks.Task task, ActionResult result) {
        if (!result.success()) return result;
        Instant now = clock.instant();
        var before = WeeklyTasks.current(tx.weeklyTasks(actor), now);
        var after = before.plusOne(task);
        if (after == before) return result;
        tx.recordWeeklyTasks(actor, after, now);
        if (after.value(task) < task.target) return result;
        String levelUp = game.awardTaskXp(WeeklyTasks.REWARD_XP);
        return ActionResult.ok((result.message() + " Tarefa semanal concluída: +" + WeeklyTasks.REWARD_XP + " XP. " + levelUp).strip());
    }

    private static int cooperativeHelpXp(JdbcOnlineStore.Tx tx, UUID actor, LocalDate today) {
        return Math.max(0, Math.min(HELP_XP, COOPERATIVE_XP_DAILY_CAP - tx.cooperativeXp(actor, today)));
    }

    private static String pestArrival(GameState game, int column, int row, String kind) {
        var tile = game.farm().tile(column, row);
        var arrivedAt = kind.equals("SPREAD_INSECTS") ? tile.insectsSince() : tile.weedsSince();
        return arrivedAt == null ? null : arrivedAt.toString();
    }

    JsonObject sendGift(UUID actor, UUID recipient, String cropId, Integer quantity, UUID requestId) {
        if (quantity == null || quantity < 1 || quantity > VisitService.MAX_GIFT_SEEDS) throw new ApiFailure(400, "Escolha de 1 a 5 sementes.");
        try { catalog.get(cropId == null ? "" : cropId); } catch (IllegalArgumentException e) { throw new ApiFailure(400, "Semente inválida."); }
        return store.transaction(actor, tx -> sendGift(tx, actor, recipient, cropId, quantity, requestId));
    }

    JsonObject sendGift(JdbcOnlineStore.Tx tx, UUID actor, UUID recipient, String cropId, int quantity, UUID requestId) {
        authorizeVisit(tx, actor, recipient);
        return receipt(tx, actor, requestId, hash("GIFT:" + recipient + ":" + cropId + ":" + quantity), () -> {
            tx.lockFarms(actor, recipient);
            var sender = load(tx, actor); var target = load(tx, recipient);
            // The actor and farm locks serialize this check with the insert, so concurrent sends cannot both pass.
            Instant now = clock.instant(), last = tx.lastGift(actor, recipient);
            Instant availableAt = last == null ? now : last.plus(GIFT_COOLDOWN);
            ActionResult result;
            if (now.isBefore(availableAt))
                result = ActionResult.fail("Você já enviou um presente a este amigo nas últimas 24 horas. Novo envio em " + remaining(now, availableAt) + ".");
            else {
                UUID gift = UUID.randomUUID();
                result = new VisitService().giftSeeds(sender.game(), target.game(), cropId, quantity, sender.name(), gift.toString());
                if (result.success()) tx.recordGift(gift, actor, recipient, cropId, quantity, now);
            }
            persist(tx, target, false, false);
            return object("result", result, "farm", persist(tx, sender, false, true));
        });
    }

    JsonObject gifts(UUID actor) {
        return store.transaction(actor, tx -> {
            active(tx, actor);
            var received = new JsonArray(); var sent = new JsonArray();
            for (var gift : tx.gifts(actor)) {
                boolean incoming = gift.recipient().equals(actor);
                (incoming ? received : sent).add(object("id", gift.id(), incoming ? "senderId" : "recipientId", incoming ? gift.sender() : gift.recipient(),
                        incoming ? "senderName" : "recipientName", incoming ? gift.senderName() : gift.recipientName(),
                        "cropId", gift.cropId(), "quantity", gift.quantity(), "createdAt", gift.createdAt().toString(), "state", gift.state()));
            }
            return object("received", received, "sent", sent);
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
        GameState game = saved == null ? GameState.newOnline(fixed, catalog, random)
                : GameState.restoreOnline(saved.time(),now,catalog,saved.snapshot(),random);
        if (saved == null) game.startNewFarm();
        game.refresh(); game.protectedPlots();
        return new Loaded(owner, participant.name(), game, saved == null ? 0 : saved.revision(), saved == null ? null : saved.snapshot(), saved == null ? null : saved.allowanceDay());
    }

    /** Normal online replay without owner-only gifts, notes, request leases or economic actions. */
    GameSnapshot advanceForCropPush(JdbcOnlineStore.Tx tx,UUID owner){
        var loaded=load(tx,owner);loaded.game().runAutomationsNow();
        accumulate(tx,loaded,java.util.List.of());
        var snapshot=loaded.game().snapshot();
        // Avanço temporal em segundo plano não é uma ação do jogador: preservar a
        // revisão evita invalidar um clique feito com a projeção ainda aberta.
        tx.save(owner,snapshot,loaded.revision(),loaded.game().now());return snapshot;
    }

    /** Owner-facing record of a theft attempt; blocked attempts (cooldown, window, invalid) are not reported. */
    private static AwayLedger.Event awayEvent(br.com.colheitafeliz.game.TheftAttempt theft, String visitorName,
                                              br.com.colheitafeliz.domain.crop.Crop target, Instant now) {
        return switch (theft.kind()) {
            case STOLEN -> new AwayLedger.Event("THEFT", now.toString(), visitorName, target.type().id(), 1, null);
            // Plot protection is collective, so the defense is not credited to a specific animal.
            case PROTECTED, BITTEN -> new AwayLedger.Event("DEFENDED", now.toString(), visitorName, null, 0, null);
            case BLOCKED -> null;
        };
    }

    private JsonObject persist(JdbcOnlineStore.Tx tx, Loaded loaded, boolean force, boolean own) {
        return persist(tx, loaded, force, own, java.util.List.of());
    }

    private JsonObject persist(JdbcOnlineStore.Tx tx, Loaded loaded, boolean force, boolean own, java.util.List<AwayLedger.Event> events) {
        loaded.game().runAutomationsNow();
        accumulate(tx, loaded, events);
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
            view.add("loginRewards", loginRewardView(tx, loaded.owner()));
            view.add("weeklyTasks", WeeklyTasks.view(WeeklyTasks.current(tx.weeklyTasks(loaded.owner()), clock.instant())));
            var achievements = tx.achievements(loaded.owner());
            var unlocked = Achievements.unlocked(achievements, loaded.game());
            if (!unlocked.equals(achievements.unlocked())) {
                tx.saveTitles(loaded.owner(), unlocked, Achievements.find(achievements.equipped()), clock.instant());
                achievements = new Achievements.Progress(achievements.clean(), achievements.plant(), achievements.harvest(),
                        achievements.spread(), achievements.steal(), achievements.green(), unlocked, achievements.equipped());
            }
            var titles = Achievements.view(achievements, loaded.game());
            for (var entry : titles.entrySet()) view.add(entry.getKey(), entry.getValue());
        }
        else view.addProperty("equippedTitle", Achievements.equippedTitle(tx.achievements(loaded.owner()).equipped()));
        var backgrounds = tx.backgrounds(loaded.owner());
        view.addProperty("activeBackgroundId", backgrounds.active());
        if (own) {
            var owned = new JsonArray();
            for (String id : backgrounds.ownedIds()) owned.add(id);
            view.add("ownedBackgroundIds", owned);
            view.addProperty("backgroundPrice", Backgrounds.PRICE.toPlainString());
        }
        return view;
    }

    private JsonObject loginRewardView(JdbcOnlineStore.Tx tx, UUID owner) {
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, LOGIN_REWARD_ZONE);
        var progress = tx.loginRewardProgress(owner);
        var next = LoginRewardCalendar.nextDay(progress == null ? null : progress.lastClaimDate(),
                progress == null ? 0 : progress.completedDay(), today);
        var rewards = new JsonArray();
        for (var reward : LoginRewardCalendar.rewards()) {
            var item = object("day", reward.day(), "kind", reward.kind(), "currency", reward.currency(), "amount", reward.amount());
            if (reward.itemId() != null) item.addProperty("itemId", reward.itemId());
            rewards.add(item);
        }
        int completedDay = progress == null || next.available() && next.day() == 1 ? 0 : progress.completedDay();
        return object("completedDay", completedDay, "nextDay", next.day(),
                "available", next.available(), "streakReset", next.streakReset(),
                "nextResetAt", today.plusDays(1).atStartOfDay(LOGIN_REWARD_ZONE).toInstant().toString(), "rewards", rewards);
    }

    /** Adds automation care and visit events to the owner's away ledger in the farm's own transaction. */
    private void accumulate(JdbcOnlineStore.Tx tx, Loaded loaded, java.util.List<AwayLedger.Event> events) {
        var tally = loaded.game().drainAwayTally();
        if (tally.isEmpty() && events.isEmpty()) return;
        var ledger = tx.awayLedger(loaded.owner());
        ledger = (ledger == null ? AwayLedger.empty(clock.instant()) : ledger).plus(tally);
        for (var event : events) ledger = ledger.withEvent(event);
        tx.saveAwayLedger(loaded.owner(), ledger);
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
    private static void validateVisit(Command command) {
        if (!VISIT_ACTIONS.contains(command.action())) throw new ApiFailure(400, "Ação de visita inválida.");
        if (command.action().equals("FEED_ANIMAL")) { command.requireItem(); return; }
        command.requireTile();
        if (command.harvestRevision() == null) throw new ApiFailure(400, "Falta a revisão da temporada.");
    }
    private static void checkRevision(Loaded farm, Command command) {
        if (farm.revision() != Command.revision(command.expectedRevision())) throw new ApiFailure(409, "A fazenda mudou. Atualize antes de agir.");
    }
    private static String remaining(Instant now, Instant availableAt) {
        Duration remaining = Duration.between(now, availableAt);
        long seconds = remaining.getSeconds() + (remaining.getNano() == 0 ? 0 : 1);
        return seconds / 3600 + "h " + seconds % 3600 / 60 + "min " + seconds % 60 + "s";
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
            case "BUY_SEED" -> game.buySeed(catalog.get(c.requireItem()), currency, c.purchaseQuantity());
            case "BUY_ANIMAL" -> game.buyAnimal(game.animalType(c.requireItem()), currency);
            case "BUY_FEED" -> game.buyFeed(c.requireItem());
            case "BUY_FERTILIZER" -> game.buyFertilizer(c.requireItem(), currency, c.purchaseQuantity());
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
