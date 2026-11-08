package br.com.colheitafeliz.game;

import br.com.colheitafeliz.domain.animal.Animal;
import br.com.colheitafeliz.domain.animal.AnimalType;
import br.com.colheitafeliz.domain.animal.Barn;
import br.com.colheitafeliz.domain.animal.ProtectionRules;
import br.com.colheitafeliz.domain.crop.Crop;
import br.com.colheitafeliz.domain.crop.CropCatalog;
import br.com.colheitafeliz.domain.crop.CropType;
import br.com.colheitafeliz.domain.economy.Market;
import br.com.colheitafeliz.domain.economy.Wallet;
import br.com.colheitafeliz.domain.farm.Farm;
import br.com.colheitafeliz.domain.farm.FarmTile;
import br.com.colheitafeliz.domain.farm.PestState;
import br.com.colheitafeliz.domain.farm.PestSystem;
import br.com.colheitafeliz.domain.farm.RandomSource;
import br.com.colheitafeliz.domain.farm.TileState;
import br.com.colheitafeliz.domain.inventory.Inventory;
import br.com.colheitafeliz.domain.player.PlayerProgress;
import java.time.Instant;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.math.BigDecimal;
import java.util.Map;
import br.com.colheitafeliz.domain.inventory.ItemLot;
import br.com.colheitafeliz.domain.crop.FertilizerType;
import br.com.colheitafeliz.domain.economy.Currency;
import br.com.colheitafeliz.domain.player.VipStatus;

public final class GameState {
    public static final int COLUMNS = 6;
    public static final int ROWS = 5;
    public static final int STARTING_COINS = 500;
    public static final BigDecimal VIP_PRICE = new BigDecimal("100");
    public static final BigDecimal AUTOMATION_PRICE = new BigDecimal("100");
    private final Farm farm = new Farm(COLUMNS, ROWS);
    private final Inventory inventory = new Inventory();
    private final Wallet wallet;
    private final Market market;
    private final GameClock clock;
    private Clock realClock;
    private final CropCatalog catalog;
    private final PestSystem pests;
    private final RandomSource random;
    private GameSnapshot.ProtectionSnapshot protection;
    private final PlayerProgress progress;
    private final DailyMissionBoard dailyMissions;
    private final LandExpansion land = new LandExpansion();
    private final Tutorial tutorial = new Tutorial(true);
    private final List<AnimalType> animalTypes = AnimalCatalogLoader.load();
    private final Barn barn = new Barn();
    private final List<FertilizerType> fertilizers = FertilizerCatalogLoader.load();
    /** Tipo usado pela ferramenta de fertilizante. */
    private FertilizerType fertilizer = fertilizers.getFirst();
    private Tool tool = Tool.CURSOR;
    private CropType seed;
    private String message = "Abra a loja para comprar sementes.";
    private PestSystem.Outbreak lastOutbreak = new PestSystem.Outbreak(false, false);
    private boolean musicEnabled = true;
    private boolean effectsEnabled = true;
    private int lastHarvestAmount;
    private int lastCollected;
    private VipStatus vip = new VipStatus(null);
    private Instant replayAt;
    private Instant automationCursor;
    private final Map<String,Instant> automationTerms = new java.util.LinkedHashMap<>();
    private GameSnapshot.ChristmasState christmas2026;
    private PlayerAvatar avatar;
    public PlayerAvatar avatar(){return avatar;}
    public ActionResult selectAvatar(PlayerAvatar choice,boolean onlyIfUnset){
        Objects.requireNonNull(choice);
        if(!onlyIfUnset||avatar==null)avatar=choice;
        return ActionResult.ok("Avatar salvo.");
    }
    public boolean claimChristmasGift(){return claimChristmasGift(Instant.parse(christmas2026.registeredAt()));}
    public boolean claimChristmasGift(Instant registeredAt){
        christmas2026=new GameSnapshot.ChristmasState(registeredAt.toString(),christmas2026.giftGranted());
        if(christmas2026.giftGranted()||!Christmas2026.giftDue(realNow(),registeredAt))return false;
        var type=catalog.get("mini-bis");
        if(inventory.seeds(type)==Integer.MAX_VALUE)return false;
        inventory.addSeedLot(new ItemLot(type.id(),Currency.GREEN,type.seedPrice().getValue(),1,true));
        christmas2026=new GameSnapshot.ChristmasState(registeredAt.toString(),true);
        notifications.record("christmas-gift-2026","2026",br.com.colheitafeliz.domain.notification.GameNotification.Type.CHRISTMAS_GIFT,
                "Presente de Natal: você recebeu 1 semente VIP de Mini-Bis!",realNow(),Map.of("cropId","mini-bis","quantity","1"));
        return true;
    }
    private br.com.colheitafeliz.domain.notification.NotificationInbox notifications = new br.com.colheitafeliz.domain.notification.NotificationInbox();
    public br.com.colheitafeliz.domain.notification.NotificationInbox notifications(){return notifications;}
    public ActionResult markNotificationRead(String id){return notifications.markRead(id)?ActionResult.ok("Notificação lida."):ActionResult.fail("Notificação indisponível.");}
    public ActionResult markAllNotificationsRead(){notifications.markAllRead();return ActionResult.ok("Notificações lidas.");}
    public ActionResult dismissNotification(String id){return notifications.dismissRead(id)?ActionResult.ok("Notificação descartada."):ActionResult.fail("Marque a notificação como lida antes de descartá-la.");}
    public ActionResult dismissReadNotifications(){notifications.dismissAllRead();return ActionResult.ok("Notificações lidas descartadas.");}

    public GameState(GameClock clock, CropCatalog catalog) {
        this(clock, catalog, ThreadLocalRandom.current()::nextInt);
    }

    public GameState(GameClock clock, CropCatalog catalog, RandomSource random) {
        this(clock, Clock.systemUTC(), catalog, random, BigDecimal.valueOf(STARTING_COINS), new BigDecimal("5"), 1, BigDecimal.ZERO);
    }

    public GameState(GameClock clock, Clock realClock, CropCatalog catalog, RandomSource random) {
        this(clock, realClock, catalog, random, BigDecimal.valueOf(STARTING_COINS), new BigDecimal("5"), 1, BigDecimal.ZERO);
    }

    private GameState(GameClock clock, Clock realClock, CropCatalog catalog, RandomSource random,
                      BigDecimal coins, BigDecimal green, int level, BigDecimal currentXp) {
        this.clock = Objects.requireNonNull(clock);
        this.realClock = Objects.requireNonNull(realClock);
        automationCursor = realClock.instant();
        christmas2026=new GameSnapshot.ChristmasState(realNow().toString(),false);
        this.catalog = Objects.requireNonNull(catalog);
        wallet = new Wallet(coins, green);
        market = new Market(wallet, inventory);
        Objects.requireNonNull(random);
        this.random = random;
        pests = new PestSystem(random);
        progress = new PlayerProgress(ProgressionLoader.load(), level, currentXp);
        dailyMissions = new DailyMissionBoard(LocalDate.ofInstant(realNow(), java.time.ZoneOffset.UTC));
        seed = catalog.all().getFirst();
    }

    public Farm farm() { return farm; }
    public Inventory inventory() { return inventory; }
    public Wallet wallet() { return wallet; }
    public CropCatalog catalog() { return catalog; }
    public PlayerProgress progress() { return progress; }
    public DailyMissionBoard dailyMissions() { return dailyMissions; }
    public LandExpansion land() { return land; }
    public Tutorial tutorial() { return tutorial; }
    public List<AnimalType> animalTypes() { return animalTypes; }
    public Barn barn() { return barn; }
    public VipStatus vip() { return vip; }
    public boolean vipActive() { return vip.activeAt(realNow()); }

    private static void requireAutomation(String id) {
        if (id == null || !java.util.Set.of("water", "pests", "animals").contains(id))
            throw new IllegalArgumentException("Unknown automation service: " + id);
    }

    public Instant automationExpiresAt(String id) {
        requireAutomation(id);
        return automationTerms.get(id);
    }

    public ActionResult buyAutomation(String id) {
        requireAutomation(id);
        if (!vipActive()) return report(ActionResult.fail("Ative o cartão VIP para contratar este serviço."));
        if (wallet.balance(Currency.GREEN).compareTo(AUTOMATION_PRICE) < 0)
            return report(ActionResult.fail("O serviço custa 100 moedas verdes."));
        runAutomationsNow();
        automationCursor = realNow();
        Instant now = realNow(), previous = automationTerms.get(id);
        Instant expiry = (previous != null && previous.isAfter(now) ? previous : now).plus(java.time.Duration.ofDays(30));
        wallet.debit(Currency.GREEN, AUTOMATION_PRICE);
        automationTerms.put(id, expiry);
        return report(ActionResult.ok("Serviço contratado por 30 dias. " + recordDailyMission("purchase", 1)));
    }

    private boolean automationActive(String id) {
        Instant expiry = automationTerms.get(id);
        return vipActive() && expiry != null && realNow().isBefore(expiry);
    }

    private GameSnapshot.AutomationSnapshot automationSnapshot() {
        Map<String,String> terms = new java.util.LinkedHashMap<>(), weeds = new java.util.LinkedHashMap<>(), insects = new java.util.LinkedHashMap<>();
        automationTerms.forEach((id, expiry) -> terms.put(id, expiry.toString()));
        for (int i = 0; i < COLUMNS * ROWS; i++) {
            var tile = farm.tile(i % COLUMNS, i / COLUMNS);
            if (tile.weedsSince() != null) weeds.put(Integer.toString(i), tile.weedsSince().toString());
            if (tile.insectsSince() != null) insects.put(Integer.toString(i), tile.insectsSince().toString());
        }
        return new GameSnapshot.AutomationSnapshot(automationCursor.toString(), terms, weeds, insects);
    }

    /** Online clocks are identical; replay runs before the normal refresh can discard event chronology. */
    public static GameState restoreOnline(Instant savedAt, Instant now, CropCatalog catalog,
                                           GameSnapshot snapshot, RandomSource random) {
        Objects.requireNonNull(savedAt); Objects.requireNonNull(now);
        Instant start = snapshot.automations() == null ? savedAt : Instant.parse(snapshot.automations().cursor());
        if (start.isAfter(now)) throw new IllegalArgumentException("Automation cursor is in the future");
        var game = restore(() -> now, Clock.fixed(start, java.time.ZoneOffset.UTC), catalog, snapshot, random, false);
        game.realClock = Clock.fixed(now, java.time.ZoneOffset.UTC);
        game.automationCursor = start;
        // Unknown historical arrivals must wait from this access, never from an assumed infection time.
        for (int i = 0; i < COLUMNS * ROWS; i++) {
            var tile = game.farm.tile(i % COLUMNS, i / COLUMNS);
            if ((tile.weedsSince() != null && tile.weedsSince().isAfter(now))
                    || (tile.insectsSince() != null && tile.insectsSince().isAfter(now)))
                throw new IllegalArgumentException("Pest arrival is in the future");
            tile.observePestArrivals(now);
        }
        game.replayAutomations(now);
        return game;
    }

    public void runAutomationsNow() { replayAutomations(realNow()); }

    private void replayAutomations(Instant target) {
        if (target.isBefore(automationCursor)) return;
        if (automationTerms.isEmpty()) { refresh(); return; }
        Tool previousTool = tool;
        try {
            replayAt = automationCursor;
            while (true) {
                boolean active = automationActive("water") || automationActive("pests") || automationActive("animals");
                if (!active) { replayAt = target; refresh(); break; }
                refresh();
                for (int i = 0; i < COLUMNS * ROWS; i++) {
                    var tile = farm.tile(i % COLUMNS, i / COLUMNS);
                    tile.observePestArrivals(replayAt);
                    if (!land.isUnlocked(i % COLUMNS, i / COLUMNS) || tile.state() != TileState.GROWING
                            || tile.crop().plantedAt().isAfter(replayAt)) continue;
                    if (automationActive("water") && tile.crop().dryAt(replayAt)) {
                        tool = Tool.WATER; act(i % COLUMNS, i / COLUMNS);
                    }
                    if (automationActive("pests")) {
                        if (tile.weedsSince() != null && !replayAt.isBefore(tile.weedsSince().plusSeconds(900))) {
                            tool = Tool.WEEDER; act(i % COLUMNS, i / COLUMNS);
                        }
                        if (tile.insectsSince() != null && !replayAt.isBefore(tile.insectsSince().plusSeconds(900))) {
                            tool = Tool.PESTICIDE; act(i % COLUMNS, i / COLUMNS);
                        }
                    }
                }
                if (automationActive("animals")) for (int i = barn.size() - 1; i >= 0; i--) {
                    Animal animal = barn.get(i);
                    if (animal.acquiredAt().isAfter(replayAt) || animal.growthAnchorAt().isAfter(replayAt)) continue;
                    if (animal.type().kind() == AnimalType.Kind.GROWING && animal.statusAt(replayAt) == Animal.Status.READY) continue;
                    if (animal.hungryAt(replayAt) || animal.statusAt(replayAt) == Animal.Status.READY) tendAnimal(i);
                }
                if (!replayAt.isBefore(target)) break;
                replayAt = nextAutomationEvent(replayAt, target);
            }
            automationCursor = target;
        } finally { replayAt = null; tool = previousTool; }
    }

    private Instant nextAutomationEvent(Instant current, Instant target) {
        Instant next = target;
        next = earlierEvent(current, next, vip.expiresAt());
        for (Instant expiry : automationTerms.values()) next = earlierEvent(current, next, expiry);
        for (var animal : barn.animals()) {
            next = earlierEvent(current, next, animal.acquiredAt());
            next = earlierEvent(current, next, animal.growthAnchorAt());
            if (animal.acquiredAt().isAfter(current) || animal.growthAnchorAt().isAfter(current)) continue;
            if (animal.type().kind() == AnimalType.Kind.PROTECTOR) next = earlierEvent(current, next, animal.expiresAt());
            next = earlierEvent(current, next, animal.fedUntil());
            if (!animal.hungryAt(current) && animal.type().kind() != AnimalType.Kind.PROTECTOR)
                next = earlierEvent(current, next, current.plus(animal.remainingAt(current)));
        }
        for (int i = 0; i < COLUMNS * ROWS; i++) {
            var tile = farm.tile(i % COLUMNS, i / COLUMNS);
            if (tile.state() != TileState.GROWING) continue;
            Crop crop = tile.crop();
            next = earlierEvent(current, next, crop.plantedAt());
            if (crop.plantedAt().isAfter(current)) continue;
            if (automationActive("water") && crop.watered()) next = earlierEvent(current, next, crop.wateredAt().plus(crop.moistDuration()));
            if (automationActive("pests")) {
                if (tile.weedsSince() != null) next = earlierEvent(current, next, tile.weedsSince().plusSeconds(900));
                if (tile.insectsSince() != null) next = earlierEvent(current, next, tile.insectsSince().plusSeconds(900));
            }
            if (!crop.watered()) continue;
            if (tile.pest() == PestState.NONE && crop.stageAt(current) >= 3) next = earlierEvent(current, next, tile.nextPestCheckAt());
            if (tile.insects() > 0 || crop.stageAt(current) >= 2) next = earlierEvent(current, next, tile.nextInsectCheckAt());
            if (!crop.paused()) {
                int stage = crop.stageAt(current);
                long threshold = (crop.currentDuration().toMillis() * stage + crop.type().stages() - 2) / (crop.type().stages() - 1);
                long left = threshold - crop.grownMillisAt(current);
                long moist = Math.max(0, java.time.Duration.between(current, crop.wateredAt().plus(crop.moistDuration())).toMillis());
                next = earlierEvent(current, next, current.plusMillis(left <= moist ? left : moist + (left - moist) * 2));
            }
        }
        return next;
    }

    private static Instant earlierEvent(Instant current, Instant next, Instant candidate) {
        return candidate != null && candidate.isAfter(current) && candidate.isBefore(next) ? candidate : next;
    }

    public ActionResult buyVip() {
        BigDecimal price = VIP_PRICE;
        if (wallet.balance(Currency.GREEN).compareTo(price) < 0)
            return report(ActionResult.fail("O cartão VIP custa 100 moedas verdes."));
        VipStatus renewed = vip.purchase(realNow());
        wallet.debit(Currency.GREEN, price);
        vip = renewed;
        return report(ActionResult.ok("VIP ativo até " + GameText.vipExpiry(vip.expiresAt(), java.time.ZoneId.systemDefault()) + ". " + recordDailyMission("purchase", 1)));
    }

    public AnimalType animalType(String id) {
        for (AnimalType type : animalTypes) if (type.id().equals(id)) return type;
        throw new IllegalArgumentException("Unknown animal: " + id);
    }
    public Tool tool() { return tool; }
    public CropType seed() { return seed; }
    public String message() { return message; }
    public Instant now() { return replayAt == null ? clock.now() : replayAt; }
    public boolean musicEnabled() { return musicEnabled; }
    public boolean effectsEnabled() { return effectsEnabled; }
    public void setMusicEnabled(boolean enabled) { musicEnabled = enabled; }
    public void setEffectsEnabled(boolean enabled) { effectsEnabled = enabled; }
    public void message(String message) { this.message = message; }
    /** Uma cultura fica disponível a partir do seu nível mínimo. */
    public boolean unlocked(CropType type) { return progress.level() >= type.requiredLevel(); }
    public void selectTool(Tool tool) { this.tool = Objects.requireNonNull(tool); }

    public ActionResult selectSeed(CropType type) {
        CropType known = catalog.get(type.id());
        if (!unlocked(known))
            return report(ActionResult.fail(known.name() + " libera no nível " + known.requiredLevel() + "."));
        seed = known;
        tool = Tool.SEED;
        return report(ActionResult.ok("Semente selecionada: " + known.name() + "."));
    }

    public ActionResult act(int col, int row) {
        synchronized (GameState.class) {
            return actLocally(col, row);
        }
    }

    private ActionResult actLocally(int col, int row) {
        if (!farm.contains(col, row)) return report(ActionResult.fail("Clique em um terreno da fazenda."));
        if (!land.isUnlocked(col, row)) return report(ActionResult.fail("Este lote ainda está bloqueado."));
        FarmTile tile = farm.tile(col, row);
        Instant now = now();
        tile.refresh(now);
        ActionResult result;
        CropType harvested = null;
        int harvestedSeason = 0;
        boolean cleared = tool == Tool.HOE && tile.state() == TileState.DEAD;
        switch (tool) {
            case CURSOR -> result = ActionResult.fail("Escolha uma ferramenta na barra.");
            case HOE -> {
                if (tile.state() == TileState.DEAD) {
                    tile.clearDead();
                    result = ActionResult.ok("Planta morta retirada. A terra está pronta para plantar.");
                } else if (tile.state() != TileState.GRASS)
                    result = ActionResult.fail("A pá precisa de grama livre ou de uma planta morta.");
                else {
                    tile.plow();
                    result = ActionResult.ok("Terra arada.");
                }
            }
            case SEED -> {
                if (tile.state() == TileState.DEAD)
                    result = ActionResult.fail("Retire a planta morta com a pá antes de plantar.");
                else if (tile.state() != TileState.PLOWED) result = ActionResult.fail("Este terreno precisa ser arado.");
                else if (inventory.seeds(seed) == 0)
                    result = ActionResult.fail("Você não possui sementes de " + seed.name() + ".");
                else if (seed.vipOnly() && !vipActive())
                    result = ActionResult.fail(seed.name() + " exige cartão VIP ativo para plantar.");
                else if (tile.harvestRevision() == Long.MAX_VALUE)
                    result = ActionResult.fail("Revisão do terreno esgotada.");
                else {
                    tile.plant(seed, now, inventory.takeSeed(seed.id()));
                    result = ActionResult.ok(seed.name() + " plantado. Use o regador.");
                }
            }
            case WATER -> {
                if (tile.state() != TileState.GROWING)
                    result = ActionResult.fail("Regue uma plantação em crescimento.");
                else if (!tile.crop().dryAt(now))
                    result = ActionResult.fail("A terra ainda está úmida.");
                else {
                    boolean first = !tile.crop().watered();
                    tile.water(now);
                    result = ActionResult.ok(first ? "Plantação regada." : "Terra molhada. Crescimento normal de novo.");
                }
            }
            case WEEDER -> {
                if (tile.pest() != PestState.WEEDS)
                    result = ActionResult.fail("Não há ervas daninhas aqui. O herbicida é para ervas.");
                else {
                    tile.removePest(now);
                    result = ActionResult.ok("Ervas removidas." + (tile.insects() > 0
                            ? " Ainda há insetos: use o pesticida." : " Planta voltou a crescer."));
                }
            }
            case FERTILIZER -> {
                FertilizerType type = fertilizer;
                if (tile.state() != TileState.GROWING) result = ActionResult.fail("O fertilizante é para plantas em crescimento.");
                else if (type.friendsOnly()) result = ActionResult.fail(type.name() + " só vale na plantação de amigos.");
                else if (inventory.fertilizer(type.id()) == 0) result = ActionResult.fail("Sem " + type.name().toLowerCase() + ". Compre na Loja.");
                else if (tile.fertilized()) result = ActionResult.fail("Esta planta já recebeu fertilizante nesta temporada.");
                else if (tile.crop().dryAt(now)) result = ActionResult.fail("Regue antes de fertilizar.");
                else if (tile.crop().paused()) result = ActionResult.fail("Remova as pragas e os insetos antes de fertilizar.");
                else {
                    inventory.useFertilizer(type.id());
                    tile.fertilize(now, type.reduction());
                    tile.refresh(now);
                    result = ActionResult.ok(type.name() + " aplicado"
                            + (tile.state() == TileState.READY ? ": pronta para colher." : "."));
                }
            }
            case PESTICIDE -> {
                if (tile.insects() == 0)
                    result = ActionResult.fail("Não há insetos aqui. O pesticida é para minhocas.");
                else {
                    tile.removeInsects(now);
                    result = ActionResult.ok("Insetos eliminados." + (tile.pest() == PestState.WEEDS
                            ? " Ainda há ervas daninhas: use o herbicida." : " Planta voltou a crescer."));
                }
            }
            case HAND -> {
                if (tile.state() != TileState.READY)
                    result = ActionResult.fail("A planta ainda não está madura.");
                else if (!inventory.canAddProduceLot(tile.crop().origin()) || tile.harvestRevision() == Long.MAX_VALUE)
                    result = ActionResult.fail("Depósito ou revisão sem capacidade.");
                else {
                    // A temporada é lida antes da colheita, que já avança para a próxima.
                    harvested = tile.crop().type();
                    harvestedSeason = tile.crop().season();
                    inventory.addProduceLot(tile.harvest(now));
                    lastHarvestAmount = 1;
                    result = ActionResult.ok("+1 " + harvested.name() + " no depósito."
                            + (tile.state() == TileState.GROWING ? " Regue para a próxima temporada." : " Venda na loja."));
                }
            }
            default -> throw new IllegalStateException("Unknown tool");
        }
        if (result.success()) {
            BigDecimal xp = switch (tool) {
                case SEED -> BigDecimal.valueOf(progress.rules().plantXp());
                case WATER -> BigDecimal.valueOf(progress.rules().waterXp());
                case HAND -> harvested.harvestXp(harvestedSeason);
                case WEEDER, PESTICIDE -> BigDecimal.valueOf(progress.rules().removePestXp());
                case HOE -> BigDecimal.valueOf(cleared ? progress.rules().clearDeadXp() : 0);
                case CURSOR, FERTILIZER -> BigDecimal.ZERO;
            };
            String levelReward = awardXp(xp);
            if (!levelReward.isEmpty()) result = ActionResult.ok(result.message() + " " + levelReward);
            String missionReward = recordDailyMission(switch (tool) {
                case HAND -> "harvest";
                case HOE -> cleared ? "clear" : "plow";
                case WATER -> "water";
                case SEED -> "plant";
                case WEEDER -> "weed";
                case PESTICIDE -> "pesticide";
                case CURSOR -> "cursor";
                case FERTILIZER -> "fertilize";
            }, 1);
            if (!missionReward.isEmpty()) result = ActionResult.ok(result.message() + " " + missionReward);
        }
        return report(result);
    }

    public ActionResult buyPlot(int col, int row) {
        LandExpansion.Plot plot = land.at(col, row);
        if (plot == null) return report(ActionResult.fail("Terreno inválido."));
        if (land.isUnlocked(col, row)) return report(ActionResult.fail("Este lote já está liberado."));
        LandExpansion.Plot next = land.next();
        if (next == null || next.id() != plot.id())
            return report(ActionResult.fail("Compre primeiro o próximo lote indicado."));
        if (progress.level() < plot.requiredLevel())
            return report(ActionResult.fail("Este lote libera no nível " + plot.requiredLevel() + "."));
        if (wallet.balance(plot.currency()).compareTo(plot.price()) < 0)
            return report(ActionResult.fail("O lote custa " + GameText.money(plot.price()) + " verdes."));
        wallet.debit(plot.currency(), plot.price());
        land.purchase(plot.id());
        return report(ActionResult.ok("Novo lote liberado por " + GameText.money(plot.price()) + " verdes! " + recordDailyMission("purchase", 1)));
    }

    /** Compra na única moeda que a cultura aceita. */
    public ActionResult buySeed(CropType type) {
        return buySeed(type, catalog.get(type.id()).seedPrice().getKey());
    }

    public ActionResult buySeed(CropType type, Currency currency) {
        CropType known = catalog.get(type.id());
        if (!unlocked(known))
            return report(ActionResult.fail(known.name() + " libera no nível " + known.requiredLevel() + "."));
        if (known.vipOnly() && !vipActive())
            return report(ActionResult.fail(known.name() + " exige cartão VIP ativo."));
        BigDecimal price = known.seedPrices().get(Objects.requireNonNull(currency));
        if (price == null)
            return report(ActionResult.fail(known.name() + " não é vendida por " + currencyName(currency) + "."));
        try {
            market.buySeed(known, currency);
            ActionResult result = ActionResult.ok("+1 semente de " + known.name()
                    + " | -" + GameText.money(price) + " " + currencyName(currency));
            String missionReward = recordDailyMission("buy", 1);
            if (!missionReward.isEmpty()) result = ActionResult.ok(result.message() + " " + missionReward);
            return report(result);
        } catch (IllegalStateException e) {
            return report(ActionResult.fail("Moedas insuficientes."));
        }
    }

    private static String currencyName(Currency currency) { return currency == Currency.GOLD ? "ouro" : "verdes"; }

    public ActionResult sellProduce(CropType type) { return sellProduce(type, 1); }

    public ActionResult sellAllProduce(CropType type) {
        CropType known = catalog.get(type.id());
        return sellProduce(known, inventory.produce(known));
    }

    /** Totais elegíveis do depósito, separados por moeda e perfil atual. */
    public Map<Currency, BigDecimal> depositTotals() { return market.quoteEverything(vipActive()); }
    public Map<Currency, BigDecimal> quoteItem(String id) { return market.quoteProduce(id, 1, vipActive()); }
    public Map<Currency, BigDecimal> quoteAllItem(String id) { return market.quoteProduce(id, Integer.MAX_VALUE, vipActive()); }
    public BigDecimal depositValue() { return depositTotals().getOrDefault(Currency.GOLD, BigDecimal.ZERO); }
    public static String formatTotals(Map<Currency, BigDecimal> totals) {
        List<String> parts = new ArrayList<>();
        for (Currency currency : Currency.values()) {
            BigDecimal value = totals.getOrDefault(currency, BigDecimal.ZERO);
            if (value.signum() > 0) parts.add(GameText.money(value) + (currency == Currency.GOLD ? " ouro" : " verdes"));
        }
        return parts.isEmpty() ? "0,0" : String.join(", ", parts);
    }

    // ---------- animais ----------

    public ActionResult buyAnimal(AnimalType type) { return buyAnimal(type, Currency.GOLD); }

    public ActionResult buyAnimal(AnimalType type, Currency currency) {
        AnimalType known = animalType(type.id());
        barn.removeExpired(realNow());
        if (progress.level() < known.requiredLevel())
            return report(ActionResult.fail(known.name() + " libera no nível " + known.requiredLevel() + "."));
        if (barn.full(known)) return report(ActionResult.fail("Limite do cercado: " + known.max() + " por tipo."));
        BigDecimal price = known.prices().get(Objects.requireNonNull(currency));
        if (price == null || wallet.balance(currency).compareTo(price) < 0)
            return report(ActionResult.fail("Moedas insuficientes."));
        Animal animal = Animal.acquire(known, new ItemLot(known.id(), currency, price, 1, false)
                .withAnimalSale(known.saleTerms(currency)), realNow(), now());
        wallet.debit(currency, price);
        barn.add(animal);
        return report(ActionResult.ok("Comprou " + known.name() + " com alimentação cheia. " + recordDailyMission("purchase", 1)));
    }

    public List<FertilizerType> fertilizers() { return fertilizers; }
    public FertilizerType selectedFertilizer() { return fertilizer; }

    public FertilizerType fertilizer(String id) {
        for (FertilizerType type : fertilizers) if (type.id().equals(id)) return type;
        throw new IllegalArgumentException("Unknown fertilizer: " + id);
    }

    public ActionResult selectFertilizer(String id) {
        fertilizer = fertilizer(id);
        return report(ActionResult.ok(fertilizer.name() + " selecionado."));
    }

    /** Compra um fertilizante numa das moedas da seção 5; não há revenda. */
    public ActionResult buyFertilizer(String id, Currency currency) {
        FertilizerType type = fertilizer(id);
        if (progress.level() < type.requiredLevel())
            return report(ActionResult.fail(type.name() + " libera no nível " + type.requiredLevel() + "."));
        BigDecimal price = type.prices().get(Objects.requireNonNull(currency));
        if (price == null) return report(ActionResult.fail(type.name() + " não é vendido por " + currencyName(currency) + "."));
        if (wallet.balance(currency).compareTo(price) < 0) return report(ActionResult.fail("Moedas insuficientes."));
        wallet.debit(currency, price);
        inventory.addFertilizer(type.id(), 1);
        return report(ActionResult.ok("+1 " + type.name().toLowerCase() + " | -" + GameText.money(price) + " " + currencyName(currency)
                + (type.friendsOnly() ? " (use na plantação de amigos)" : "") + " " + recordDailyMission("purchase", 1)));
    }

    public ActionResult buyFeed(AnimalType type) { return buyFeed(animalType(type.id()).feedId()); }

    public ActionResult buyFeed(String feedId) {
        int price = switch (feedId) { case "growth" -> 80; case "producer" -> 120;
            default -> throw new IllegalArgumentException("Unknown feed: " + feedId); };
        if (!hasAnimalForFeed(feedId)) return report(ActionResult.fail("Você precisa ter um animal que use essa ração."));
        if (wallet.coins().compareTo(BigDecimal.valueOf(price)) < 0) return report(ActionResult.fail("Moedas insuficientes."));
        if (inventory.feed(feedId) == Integer.MAX_VALUE) return report(ActionResult.fail("Estoque de ração cheio."));
        wallet.debit(price);
        inventory.addFeed(feedId, 1);
        return report(ActionResult.ok("+1 ração " + (feedId.equals("growth") ? "de crescimento" : "de produtor") + " | -" + price + " ouro " + recordDailyMission("purchase", 1)));
    }

    public boolean hasAnimalForFeed(String feedId) {
        for (Animal animal : barn.animals()) {
            if (animal.expiredAt(realNow())) continue;
            if (animal.type().kind() == AnimalType.Kind.PROTECTOR || animal.type().feedId().equals(feedId)) return true;
        }
        return false;
    }

    public int activeProtectors() { return protectorSignature().size(); }
    public Instant realNow() { return replayAt == null ? realClock.instant() : replayAt; }

    private List<String> protectorSignature() {
        Instant realNow = realNow();
        Instant simulationNow = now();
        return barn.animals().stream().filter(animal -> animalTypes.contains(animal.type()) && animal.activeAt(realNow, simulationNow))
                .map(animal -> animal.type().id() + ":" + animal.acquiredAt() + ":" + animal.growthAnchorAt() + ":" + animal.fedUntil())
                .sorted().toList();
    }

    /** IDs da expansão; não são índices lineares de Farm. Sorteia apenas quando os conjuntos mudam. */
    public java.util.Set<Integer> protectedPlots() {
        var unlocked = land.snapshot();
        var active = protectorSignature();
        if (protection == null || !protection.unlockedIds().equals(unlocked) || !protection.activeProtectors().equals(active))
            protection = new GameSnapshot.ProtectionSnapshot(unlocked, active,
                    ProtectionRules.selectPlots(unlocked, active.size(), random).stream().sorted().toList());
        return java.util.Set.copyOf(protection.protectedIds());
    }

    /** Resolve o tipo capturado pelo renderer, sem reaproveitar um índice que mudou após remoção. */
    public ActionResult tendAnimal(String typeId) {
        lastCollected = 0;
        for (int i = 0; i < barn.size(); i++)
            if (barn.get(i).type().id().equals(typeId)) return tendAnimal(i);
        return report(ActionResult.fail("Animal inválido."));
    }

    /** Clique coleta o produtor, vende o adulto ou alimenta um animal com fome. */
    public ActionResult tendAnimal(int index) {
        lastCollected = 0;
        if (index < 0 || index >= barn.size()) return report(ActionResult.fail("Animal inválido."));
        Animal animal = barn.get(index);
        AnimalType type = animal.type();
        Instant now = now();
        if (animal.expiredAt(realNow())) return report(ActionResult.fail("Protetor expirado."));
        if (type.kind() == AnimalType.Kind.GROWING && animal.stageAt(now) == Animal.Stage.ADULT) return sellAnimal(index);
        if (type.kind() == AnimalType.Kind.PRODUCER && animal.statusAt(now) == Animal.Status.READY) {
            if (inventory.product(type.productId()) == Integer.MAX_VALUE) return report(ActionResult.fail("Depósito cheio para este produto."));
            inventory.addProduceLot(barn.collect(index, now));
            lastCollected = 1;
            String reward = awardXp(BigDecimal.valueOf(type.collectXp()));
            String mission = recordDailyMission("collect", 1);
            return report(ActionResult.ok("Coletou 1× " + type.productName() + ". Animal removido. " + reward + " " + mission));
        }
        if (!animal.hungryAt(now)) return report(ActionResult.fail(type.name() + " ainda está alimentado."));
        String feedId = type.feedId();
        if (type.kind() == AnimalType.Kind.PROTECTOR && inventory.feed("growth") == 0) feedId = "producer";
        if (inventory.feed(feedId) == 0) return report(ActionResult.fail("Sem ração. Compre na Loja, aba Animais."));
        Animal fed = animal.feed(now);
        inventory.useFeed(feedId);
        barn.feed(index, now);
        return report(ActionResult.ok(type.name() + " alimentado por " + formatDuration(fed.type().foodDuration()) + ". " + recordDailyMission("feed", 1)));
    }

    public ActionResult sellAnimal(int index) {
        if (index < 0 || index >= barn.size()) return report(ActionResult.fail("Animal inválido."));
        Animal animal = barn.get(index);
        if (animal.type().kind() != AnimalType.Kind.GROWING || animal.stageAt(now()) != Animal.Stage.ADULT)
            return report(ActionResult.fail("Só animais de crescimento adultos podem ser vendidos."));
        BigDecimal total = Market.unitRevenue(animal.origin(), vipActive())
                .setScale(1, java.math.RoundingMode.CEILING);
        barn.remove(index);
        wallet.credit(animal.origin().currency(), total);
        String reward = awardXp(BigDecimal.valueOf(animal.type().collectXp()));
        return report(ActionResult.ok("Vendeu " + animal.type().name() + " por " + GameText.money(total) + " "
                + currencyName(animal.origin().currency()) + ". " + reward + " " + recordDailyMission("animal-sale", 1)));
    }

    public ActionResult sellProduct(AnimalType type, boolean all) {
        AnimalType known = animalType(type.id());
        int amount = all ? inventory.product(known.productId()) : Math.min(1, inventory.product(known.productId()));
        if (amount == 0) return report(ActionResult.fail("Você não possui " + known.productName().toLowerCase() + "."));
        return sellItem(known.productId(), amount);
    }

    /** Unidades coletadas no último {@link #tendAnimal(int)} (0 se não houve coleta). */
    public int lastCollected() { return lastCollected; }

    static String formatDuration(java.time.Duration duration) {
        long minutes = Math.max(1, (duration.toSeconds() + 59) / 60);
        if (minutes < 60) return minutes + " min";
        long hours = minutes / 60, rest = minutes % 60;
        return rest == 0 ? hours + "h" : hours + "h" + String.format("%02d", rest);
    }

    /** Vende todos os produtos elegíveis numa operação, com um arredondamento por moeda. */
    public ActionResult sellEverything() {
        try { return recordSale(market.sellEverything(vipActive())); }
        catch (IllegalStateException e) { return report(ActionResult.fail("Sem produtos disponíveis para venda.")); }
    }

    private ActionResult sellProduce(CropType type, int amount) {
        return sellItem(catalog.get(type.id()).id(), amount);
    }

    private ActionResult sellItem(String id, int amount) {
        if (amount <= 0) return report(ActionResult.fail("Você não possui esse produto."));
        try { return recordSale(market.sellProduce(id, amount, vipActive())); }
        catch (IllegalStateException e) { return report(ActionResult.fail("Produto indisponível; verifique estoque e VIP.")); }
    }

    private ActionResult recordSale(Map<Currency, BigDecimal> totals) {
        String reward = recordDailyMission("sell", 1);
        return report(ActionResult.ok("Vendeu produtos | +" + formatTotals(totals) + (reward.isEmpty() ? "" : " " + reward)));
    }

    private ActionResult report(ActionResult result) {
        message = result.message();
        return result;
    }

    /** Apenas orienta uma fazenda nova. */
    public void startNewFarm() {
        tutorial.restore(0);
        message = "Bem-vindo! " + tutorial.current().title() + ".";
    }

    private String recordDailyMission(String action, int amount) {
        String orientation = recordTutorial(action);
        List<DailyMissionBoard.Mission> rewards = dailyMissions.record(action, amount, LocalDate.ofInstant(realNow(), java.time.ZoneOffset.UTC));
        int green = rewards.stream().mapToInt(DailyMissionBoard.Mission::green).sum();
        if (green > 0) wallet.credit(Currency.GREEN, BigDecimal.valueOf(green));
        return orientation + (green == 0 ? "" : " Tarefa concluída: +" + green + " verde(s).");
    }

    private String recordTutorial(String action) {
        Tutorial.Step done = tutorial.record(action);
        if (done == null) return "";
        if (done.action().equals("buy")) {
            for (int row = 0; row < ROWS; row++) for (int col = 0; col < COLUMNS; col++) {
                if (land.isUnlocked(col, row) && farm.tile(col, row).state() == TileState.PLOWED) {
                    tutorial.record("plow");
                    break;
                }
            }
        }
        return tutorial.active() ? "Próximo passo: " + tutorial.current().title() + "."
                : "Primeiros passos concluídos!";
    }

    /** Dá XP e, para cada nível tabelado alcançado, as duas moedas; não concede sementes. */
    private String awardXp(BigDecimal xp) {
        int before = progress.level();
        if (progress.award(xp) == 0) return "";
        Map<Currency, BigDecimal> rewards = new java.util.EnumMap<>(Currency.class);
        for (int level = before + 1; level <= progress.level(); level++) {
            var reward = progress.rules().rewardFor(level);
            if (reward.gold().signum() > 0) wallet.credit(Currency.GOLD, reward.gold());
            if (reward.green().signum() > 0) wallet.credit(Currency.GREEN, reward.green());
            if (reward.gold().signum() > 0) rewards.merge(Currency.GOLD, reward.gold(), BigDecimal::add);
            if (reward.green().signum() > 0) rewards.merge(Currency.GREEN, reward.green(), BigDecimal::add);
        }
        return "Nível " + progress.level() + " alcançado!" + (rewards.isEmpty() ? "" : " +" + formatTotals(rewards) + ".");
    }


    public boolean refresh() {
        Instant now = now();
        dailyMissions.resetIfNewDay(LocalDate.ofInstant(realNow(), java.time.ZoneOffset.UTC));
        boolean removed = barn.removeExpired(realNow());
        farm.refresh(now);
        lastOutbreak = pests.update(farm, now);
        if (lastOutbreak.weeds()) message = "Ervas daninhas! Use o herbicida.";
        else if (lastOutbreak.insects()) message = "Insetos na plantação! Use o pesticida.";
        boolean notified=NotificationEvents.observe(this);
        return removed || lastOutbreak.any() || notified;
    }

    /** Pragas que surgiram no último {@link #refresh()}. */
    public PestSystem.Outbreak lastOutbreak() { return lastOutbreak; }

    /** Saldo de moedas verdes, mantido separado do ouro. */
    public BigDecimal greenCoins() { return wallet.balance(Currency.GREEN); }

    /** Um aviso da fazenda: o que está errado e onde. */
    public record Alert(String title, String detail) {}

    /**
     * Problemas que pedem ação agora: ervas daninhas, insetos, terra seca e animal com fome. Calculado a cada
     * chamada, então um aviso some assim que o problema é resolvido.
     */
    public List<Alert> alerts() {
        Instant now = now();
        List<Alert> alerts = new ArrayList<>();
        for (int row = 0; row < ROWS; row++) for (int col = 0; col < COLUMNS; col++) {
            FarmTile tile = farm.tile(col, row);
            // Planta pronta ainda aparece aqui; só a terra seca deixa de importar depois da maturação.
            if (!land.isUnlocked(col, row) || (tile.state() != TileState.GROWING && tile.state() != TileState.READY))
                continue;
            String name = tile.crop().type().name();
            if (tile.pest() == PestState.WEEDS) alerts.add(new Alert("Ervas daninhas", name));
            if (tile.insects() > 0) alerts.add(new Alert("Insetos", name));
            if (tile.crop().dryAt(now) && !tile.crop().matureAt(now)) alerts.add(new Alert("Terra seca", name));
        }
        for (Animal animal : barn.animals())
            if (animal.statusAt(now) == Animal.Status.HUNGRY) alerts.add(new Alert("Com fome", animal.type().name()));
        return alerts;
    }

    /** Unidades depositadas na última colheita bem-sucedida. */
    public int lastHarvestAmount() { return lastHarvestAmount; }

    public GameSnapshot snapshot() {
        protectedPlots();
        List<GameSnapshot.TileSnapshot> tiles = new ArrayList<>();
        for (int row = 0; row < ROWS; row++) for (int col = 0; col < COLUMNS; col++) {
            FarmTile tile = farm.tile(col, row);
            Crop crop = tile.crop();
            tiles.add(new GameSnapshot.TileSnapshot(tile.state(),
                    crop != null ? crop.type().id() : tile.withered() == null ? null : tile.withered().id(),
                    crop == null ? null : crop.plantedAt().toString(),
                    crop == null || crop.wateredAt() == null ? null : crop.wateredAt().toString(),
                    tile.pest().name(), crop == null || crop.pausedAt() == null ? null : crop.pausedAt().toString(),
                    tile.nextPestCheckAt() == null ? null : tile.nextPestCheckAt().toString(),
                    crop == null || crop.growthAnchorAt() == null ? null : crop.growthAnchorAt().toString(),
                    crop == null ? 0 : crop.grownAtAnchorMillis(), tile.insects(),
                    tile.nextInsectCheckAt() == null ? null : tile.nextInsectCheckAt().toString(), tile.fertilized(), crop == null ? null : crop.origin(),
                    crop == null ? 0 : crop.season(), tile.harvestRevision()));
        }
        return new GameSnapshot(wallet.coins(), inventory.seedLots(), inventory.produceLots(),
                tiles, seed.id(), tool, progress.level(), progress.currentXp(),
                musicEnabled, effectsEnabled, dailyMissions.day().toString(),
                dailyMissions.progressSnapshot(), dailyMissions.completedSnapshot(), land.snapshot(),
                tutorial.stepIndex(), barn.animals().stream().map(animal -> new GameSnapshot.AnimalSnapshot(
                        animal.type().id(), animal.origin(), animal.acquiredAt().toString(), animal.growthAnchorAt().toString(),
                        animal.grownAtAnchorMillis(), animal.fedUntil().toString())).toList(),
                inventory.feedSnapshot(), inventory.fertilizerSnapshot(), fertilizer.id(),
                greenCoins(), vip.expiresAt() == null ? null : vip.expiresAt().toString(), protection, notifications.snapshot(),christmas2026,avatar, automationSnapshot());
    }

    public static GameState restore(GameClock clock, CropCatalog catalog, GameSnapshot snapshot) {
        return restore(clock, catalog, snapshot, ThreadLocalRandom.current()::nextInt);
    }

    public static GameState restore(GameClock clock, CropCatalog catalog, GameSnapshot snapshot,
                                    RandomSource random) {
        return restore(clock, Clock.systemUTC(), catalog, snapshot, random);
    }

    public static GameState restore(GameClock clock, Clock realClock, CropCatalog catalog,
                                    GameSnapshot snapshot, RandomSource random) {
        return restore(clock, realClock, catalog, snapshot, random, true);
    }

    private static GameState restore(GameClock clock, Clock realClock, CropCatalog catalog,
                                     GameSnapshot snapshot, RandomSource random, boolean refresh) {
        Objects.requireNonNull(snapshot);
        if (snapshot.tiles() == null || snapshot.tiles().size() != COLUMNS * ROWS
                || snapshot.seeds() == null || snapshot.produce() == null)
            throw new IllegalArgumentException("Invalid farm snapshot");
        GameState game = new GameState(clock, realClock, catalog, random, snapshot.coins(), snapshot.greenCoins(),
                snapshot.level(), snapshot.currentXp());
        AnimalSaleValidation.validate(snapshot, game.animalTypes);
        game.notifications = new br.com.colheitafeliz.domain.notification.NotificationInbox(snapshot.notifications());
        game.avatar=snapshot.avatar();
        game.christmas2026=snapshot.christmas2026()==null?new GameSnapshot.ChristmasState(Instant.EPOCH.toString(),false):snapshot.christmas2026();
        for(String key:snapshot.notifications().observed().keySet())
            if(key.startsWith("animal:")&&game.animalTypes.stream().noneMatch(type->key.equals("animal:"+type.id())))
                throw new IllegalArgumentException("Unknown notification animal cursor");
        game.vip = new VipStatus(snapshot.vipExpiresAt() == null ? null : Instant.parse(snapshot.vipExpiresAt()));
        game.land.restore(snapshot.unlockedPlotIds());
        game.tutorial.restore(snapshot.tutorialStep());
        game.dailyMissions.restore(snapshot.dailyMissionDay(), snapshot.dailyMissionProgress(),
                snapshot.completedDailyMissions(), LocalDate.ofInstant(realClock.instant(), java.time.ZoneOffset.UTC));
        for (var lot : snapshot.seeds()) {
            catalog.get(lot.itemId());
            if (lot.cropSale() != null) throw new IllegalArgumentException("Seeds cannot contain harvested sale terms");
        }
        for (var lot : snapshot.produce()) {
            boolean animalProduct = game.animalTypes.stream().anyMatch(type -> type.kind() == AnimalType.Kind.PRODUCER && type.productId().equals(lot.itemId()));
            if (!animalProduct) catalog.get(lot.itemId()).validateSaleTerms(lot);
            if (lot.cropSale() != null && (animalProduct || lot.cropSale().seasonIndex() == 0))
                throw new IllegalArgumentException("Invalid product sale metadata");
        }
        game.inventory.restoreLots(snapshot.seeds(), snapshot.produce());
        for (String id : snapshot.feed().keySet())
            if (!id.equals("growth") && !id.equals("producer")) throw new IllegalArgumentException("Unknown feed: " + id);
        game.inventory.restoreFeed(snapshot.feed());
        game.inventory.restoreFertilizers(snapshot.fertilizers());
        for (String id : snapshot.fertilizers().keySet()) game.fertilizer(id);
        if (snapshot.selectedFertilizerId() != null) game.fertilizer = game.fertilizer(snapshot.selectedFertilizerId());
        if (snapshot.animals() != null) {
            List<Animal> animals = new ArrayList<>();
            for (GameSnapshot.AnimalSnapshot saved : snapshot.animals()) {
                if (saved.origin().cropSale() != null) throw new IllegalArgumentException("Animal cannot contain crop sale terms");
                animals.add(new Animal(game.animalType(saved.typeId()),
                        saved.origin(), Instant.parse(saved.acquiredAt()), Instant.parse(saved.growthAnchorAt()),
                        saved.grownAtAnchorMillis(), Instant.parse(saved.fedUntil())));
            }
            game.barn.restore(animals);
        }
        for (int i = 0; i < snapshot.tiles().size(); i++) {
            GameSnapshot.TileSnapshot tile = snapshot.tiles().get(i);
            boolean dead = tile.state() == TileState.DEAD;
            Crop crop = tile.cropId() == null || dead ? null : new Crop(catalog.get(tile.cropId()),
                    Instant.parse(tile.plantedAt()), tile.wateredAt() == null ? null : Instant.parse(tile.wateredAt()),
                    tile.pausedAt() == null ? null : Instant.parse(tile.pausedAt()),
                    tile.growthAnchorAt() == null ? null : Instant.parse(tile.growthAnchorAt()),
                    tile.grownAtAnchorMillis(), tile.origin(), tile.season());
            Instant next = tile.nextPestCheckAt() == null
                    ? (crop != null && crop.watered() ? clock.now().plus(FarmTile.pestCheckInterval(crop)) : null)
                    : Instant.parse(tile.nextPestCheckAt());
            Instant nextInsects = tile.nextInsectCheckAt() == null
                    ? (crop != null && crop.watered() ? next : null)
                    : Instant.parse(tile.nextInsectCheckAt());
            game.farm.tile(i % COLUMNS, i / COLUMNS).restore(tile.state(), crop,
                    tile.pest() == null ? PestState.NONE : PestState.valueOf(tile.pest()), next,
                    tile.insects(), nextInsects, dead ? catalog.get(tile.cropId()) : null, tile.fertilized(), tile.harvestRevision());
        }
        game.seed = catalog.get(snapshot.selectedSeedId());
        game.tool = Objects.requireNonNull(snapshot.tool());
        game.musicEnabled = snapshot.musicEnabled();
        game.effectsEnabled = snapshot.effectsEnabled();
        game.protection = snapshot.protection();
        if (snapshot.automations() != null) {
            var metadata = snapshot.automations();
            game.automationCursor = Instant.parse(metadata.cursor());
            metadata.expiresAt().forEach((id, expiry) -> game.automationTerms.put(id, Instant.parse(expiry)));
            for (int i = 0; i < COLUMNS * ROWS; i++) {
                String key = Integer.toString(i);
                String weeds = metadata.weedsSince().get(key), insects = metadata.insectsSince().get(key);
                game.farm.tile(i % COLUMNS, i / COLUMNS).restorePestArrivals(
                        weeds == null ? null : Instant.parse(weeds), insects == null ? null : Instant.parse(insects));
            }
        }
        if (refresh) game.refresh();
        game.message = "Jogo carregado.";
        return game;
    }
}
