package br.com.colheitafeliz.game;

import br.com.colheitafeliz.domain.animal.Animal;
import br.com.colheitafeliz.domain.animal.AnimalType;
import br.com.colheitafeliz.domain.animal.Barn;
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
    /** Chance, em %, de achar um baú do tesouro ao arar grama. */
    public static final int TREASURE_CHANCE = 5;

    private final Farm farm = new Farm(COLUMNS, ROWS);
    private final Inventory inventory = new Inventory();
    private final Wallet wallet;
    private final Market market;
    private final GameClock clock;
    private final Clock realClock;
    private final CropCatalog catalog;
    private final PestSystem pests;
    private final RandomSource random;
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
    private int lastTreasureCoins;
    private int lastCollected;
    private VipStatus vip = new VipStatus(null);

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
        this.catalog = Objects.requireNonNull(catalog);
        wallet = new Wallet(coins, green);
        market = new Market(wallet, inventory);
        this.random = Objects.requireNonNull(random);
        pests = new PestSystem(random);
        progress = new PlayerProgress(ProgressionLoader.load(), level, currentXp);
        dailyMissions = new DailyMissionBoard(LocalDate.now(realClock));
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
    public boolean vipActive() { return vip.activeAt(realClock.instant()); }

    public ActionResult buyVip() {
        BigDecimal price = new BigDecimal("100");
        if (wallet.balance(Currency.GREEN).compareTo(price) < 0)
            return report(ActionResult.fail("O cartão VIP custa 100 moedas verdes."));
        VipStatus renewed = vip.purchase(realClock.instant());
        wallet.debit(Currency.GREEN, price);
        vip = renewed;
        return report(ActionResult.ok("VIP ativo até " + GameText.vipExpiry(vip.expiresAt(), java.time.ZoneId.systemDefault()) + "."));
    }

    public AnimalType animalType(String id) {
        for (AnimalType type : animalTypes) if (type.id().equals(id)) return type;
        throw new IllegalArgumentException("Unknown animal: " + id);
    }
    public Tool tool() { return tool; }
    public CropType seed() { return seed; }
    public String message() { return message; }
    public Instant now() { return clock.now(); }
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
        if (!farm.contains(col, row)) return report(ActionResult.fail("Clique em um terreno da fazenda."));
        if (!land.isUnlocked(col, row)) return report(ActionResult.fail("Este lote ainda está bloqueado."));
        FarmTile tile = farm.tile(col, row);
        Instant now = clock.now();
        tile.refresh(now);
        ActionResult result;
        CropType harvested = null;
        int harvestedSeason = 0;
        lastTreasureCoins = 0;
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
                    if (random.nextInt(100) < TREASURE_CHANCE) {
                        lastTreasureCoins = treasureCoins();
                        wallet.credit(lastTreasureCoins);
                        result = ActionResult.ok("Terra arada. Você achou um baú do tesouro: +" + lastTreasureCoins + " moedas!");
                    } else result = ActionResult.ok("Terra arada.");
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
                case HOE -> "plow";
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
        if (wallet.coins().compareTo(BigDecimal.valueOf(plot.price())) < 0)
            return report(ActionResult.fail("O lote custa " + plot.price() + " moedas."));
        wallet.debit(plot.price());
        land.purchase(plot.id());
        return report(ActionResult.ok("Novo lote liberado por " + plot.price() + " moedas!"));
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

    public ActionResult buyAnimal(AnimalType type) {
        AnimalType known = animalType(type.id());
        if (progress.level() < known.requiredLevel())
            return report(ActionResult.fail(known.name() + " libera no nível " + known.requiredLevel() + "."));
        if (barn.full(known))
            return report(ActionResult.fail("O cercado está cheio: cabem " + known.max() + " de " + known.name().toLowerCase() + "."));
        if (wallet.coins().compareTo(BigDecimal.valueOf(known.price())) < 0) return report(ActionResult.fail("Moedas insuficientes."));
        wallet.debit(known.price());
        barn.add(known);
        dailyMissions.animalsAvailable(true);
        return report(ActionResult.ok("Comprou: " + known.name() + "! Dê ração no cercado para produzir."));
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
                + (type.friendsOnly() ? " (use na plantação de amigos)" : "")));
    }

    public ActionResult buyFeed(AnimalType type) {
        AnimalType known = animalType(type.id());
        if (progress.level() < known.requiredLevel())
            return report(ActionResult.fail("Ração de " + known.name().toLowerCase() + " libera no nível " + known.requiredLevel() + "."));
        if (wallet.coins().compareTo(BigDecimal.valueOf(known.feedPrice())) < 0) return report(ActionResult.fail("Moedas insuficientes."));
        wallet.debit(known.feedPrice());
        inventory.addFeed(known.id(), 1);
        return report(ActionResult.ok("+1 ração de " + known.name().toLowerCase() + " | -" + known.feedPrice() + " moedas"));
    }

    /** Clique num animal: coleta o produto pronto ou, com fome, dá uma ração. */
    public ActionResult tendAnimal(int index) {
        if (index < 0 || index >= barn.size()) return report(ActionResult.fail("Animal inválido."));
        Instant now = clock.now();
        Animal animal = barn.get(index);
        AnimalType type = animal.type();
        lastCollected = 0;
        switch (animal.statusAt(now)) {
            case READY -> {
                // Base temporária do catálogo repetível; PR4 usa aquisição real e remove o produtor após uma coleta.
                inventory.addProduceLot(new ItemLot(type.productId(), Currency.GOLD, BigDecimal.valueOf(type.productPrice()), type.productYield(), false));
                barn.collect(index, now);
                lastCollected = type.productYield();
                ActionResult result = ActionResult.ok("Coletou " + type.productYield() + "× " + type.productName()
                        + ". Dê ração de novo para produzir mais.");
                String levelReward = awardXp(BigDecimal.valueOf(type.collectXp()));
                if (!levelReward.isEmpty()) result = ActionResult.ok(result.message() + " " + levelReward);
                String missionReward = recordDailyMission("collect", 1);
                if (!missionReward.isEmpty()) result = ActionResult.ok(result.message() + " " + missionReward);
                return report(result);
            }
            case HUNGRY -> {
                if (inventory.feed(type.id()) == 0)
                    return report(ActionResult.fail("Sem ração de " + type.name().toLowerCase() + ". Compre na Loja, aba Animais."));
                inventory.useFeed(type.id());
                barn.feed(index, now);
                return report(ActionResult.ok(type.name() + " comeu a ração. " + type.productName() + " pronto em "
                        + formatDuration(type.productionTime()) + "."));
            }
            default -> {
                return report(ActionResult.fail(type.name() + " ainda está produzindo: "
                        + formatDuration(animal.remainingAt(now)) + "."));
            }
        }
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
        BigDecimal gold = totals.getOrDefault(Currency.GOLD, BigDecimal.ZERO);
        int amount = gold.min(BigDecimal.valueOf(Integer.MAX_VALUE)).intValue();
        String reward = recordDailyMission("sell", amount);
        return report(ActionResult.ok("Vendeu produtos | +" + formatTotals(totals) + (reward.isEmpty() ? "" : " " + reward)));
    }

    private ActionResult report(ActionResult result) {
        message = result.message();
        return result;
    }

    /**
     * Fazenda de quem começa agora: dois nabos prontos para a primeira colheita e o tutorial no primeiro passo.
     * Só vale para um jogo recém-criado, nunca para um save carregado.
     */
    public void startNewFarm() {
        CropType turnip = catalog.get("nabo");
        Instant plantedAt = clock.now().minus(turnip.growthTime(1)).minusSeconds(60);
        var price = turnip.seedPrice();
        ItemLot origin = new ItemLot(turnip.id(), price.getKey(), price.getValue(), 1, turnip.vipOnly());
        for (int row = 0; row < 2; row++) {
            Crop ready = new Crop(turnip, plantedAt, plantedAt, null, plantedAt, turnip.growthTime(1).toMillis(), origin, 1);
            farm.tile(0, row).restore(TileState.READY, ready);
        }
        tutorial.restore(0);
        message = "Bem-vindo à sua fazenda! " + tutorial.current().title() + ".";
    }

    private String recordDailyMission(String action, int amount) {
        if (tutorial.active()) return recordTutorial(action);
        List<DailyMissionBoard.Mission> rewards = dailyMissions.record(action, amount, LocalDate.now(realClock));
        int coins = 0;
        int xp = 0;
        for (DailyMissionBoard.Mission mission : rewards) {
            wallet.credit(mission.coins());
            coins += mission.coins();
            xp += mission.xp();
        }
        if (xp == 0) return "";
        String levelReward = awardXp(BigDecimal.valueOf(xp));
        return "Missão diária concluída: +" + coins + " moedas, +" + xp + " XP!"
                + (levelReward.isEmpty() ? "" : " " + levelReward);
    }

    private String recordTutorial(String action) {
        Tutorial.Step done = tutorial.record(action);
        if (done == null) return "";
        wallet.credit(done.coins());
        String levelReward = awardXp(BigDecimal.valueOf(done.xp()));
        String next = tutorial.active() ? " Agora: " + tutorial.current().title() + "."
                : " Primeiros passos concluídos! As missões diárias começam agora.";
        return "Tarefa concluída: +" + done.coins() + " moedas, +" + done.xp() + " XP!" + next
                + (levelReward.isEmpty() ? "" : " " + levelReward);
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
        Instant now = clock.now();
        dailyMissions.resetIfNewDay(LocalDate.now(realClock));
        farm.refresh(now);
        lastOutbreak = pests.update(farm, now);
        if (lastOutbreak.weeds()) message = "Ervas daninhas! Use o herbicida.";
        else if (lastOutbreak.insects()) message = "Insetos na plantação! Use o pesticida.";
        return lastOutbreak.any();
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
        Instant now = clock.now();
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

    /** Moedas do baú do tesouro: crescem com o nível para continuar valendo a pena. */
    public int treasureCoins() { return 20 + 10 * progress.level(); }

    /** Moedas do baú achado na última ação (0 se não houve baú). */
    public int lastTreasureCoins() { return lastTreasureCoins; }

    /** Unidades depositadas na última colheita bem-sucedida. */
    public int lastHarvestAmount() { return lastHarvestAmount; }

    public GameSnapshot snapshot() {
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
                    crop == null ? 0 : crop.season()));
        }
        return new GameSnapshot(wallet.coins(), inventory.seedLots(), inventory.produceLots(),
                tiles, seed.id(), tool, progress.level(), progress.currentXp(),
                musicEnabled, effectsEnabled, dailyMissions.day().toString(),
                dailyMissions.progressSnapshot(), dailyMissions.completedSnapshot(), land.snapshot(),
                tutorial.stepIndex(), barn.animals().stream().map(animal -> new GameSnapshot.AnimalSnapshot(
                        animal.type().id(), animal.fedAt() == null ? null : animal.fedAt().toString())).toList(),
                inventory.feedSnapshot(), inventory.fertilizerSnapshot(), fertilizer.id(),
                greenCoins(), vip.expiresAt() == null ? null : vip.expiresAt().toString());
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
        Objects.requireNonNull(snapshot);
        if (snapshot.tiles() == null || snapshot.tiles().size() != COLUMNS * ROWS
                || snapshot.seeds() == null || snapshot.produce() == null)
            throw new IllegalArgumentException("Invalid farm snapshot");
        GameState game = new GameState(clock, realClock, catalog, random, snapshot.coins(), snapshot.greenCoins(),
                snapshot.level(), snapshot.currentXp());
        game.vip = new VipStatus(snapshot.vipExpiresAt() == null ? null : Instant.parse(snapshot.vipExpiresAt()));
        game.land.restore(snapshot.unlockedPlotIds());
        game.tutorial.restore(snapshot.tutorialStep());
        game.dailyMissions.animalsAvailable(snapshot.animals() != null && !snapshot.animals().isEmpty());
        game.dailyMissions.restore(snapshot.dailyMissionDay(), snapshot.dailyMissionProgress(),
                snapshot.completedDailyMissions(), LocalDate.now(realClock));
        for (var lot : snapshot.seeds()) catalog.get(lot.itemId());
        for (var lot : snapshot.produce()) {
            boolean animalProduct = game.animalTypes.stream().anyMatch(type -> type.productId().equals(lot.itemId()));
            if (!animalProduct) catalog.get(lot.itemId());
        }
        game.inventory.restoreLots(snapshot.seeds(), snapshot.produce());
        game.inventory.restoreFeed(snapshot.feed());
        game.inventory.restoreFertilizers(snapshot.fertilizers());
        for (String id : snapshot.fertilizers().keySet()) game.fertilizer(id);
        if (snapshot.selectedFertilizerId() != null) game.fertilizer = game.fertilizer(snapshot.selectedFertilizerId());
        if (snapshot.animals() != null) {
            List<Animal> animals = new ArrayList<>();
            for (GameSnapshot.AnimalSnapshot saved : snapshot.animals())
                animals.add(new Animal(game.animalType(saved.typeId()),
                        saved.fedAt() == null ? null : Instant.parse(saved.fedAt())));
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
                    tile.insects(), nextInsects, dead ? catalog.get(tile.cropId()) : null, tile.fertilized());
        }
        game.seed = catalog.get(snapshot.selectedSeedId());
        game.tool = Objects.requireNonNull(snapshot.tool());
        game.musicEnabled = snapshot.musicEnabled();
        game.effectsEnabled = snapshot.effectsEnabled();
        game.refresh();
        game.message = "Jogo carregado.";
        return game;
    }
}
