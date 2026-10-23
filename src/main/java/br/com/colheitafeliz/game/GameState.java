package br.com.colheitafeliz.game;

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
import java.util.Set;
import java.util.HashSet;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

public final class GameState {
    public static final int COLUMNS = 6;
    public static final int ROWS = 5;
    public static final int STARTING_COINS = 100;
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
    private final Set<String> unlockedIds = new HashSet<>();
    private Tool tool = Tool.HAND;
    private CropType seed;
    private String message = "Abra a loja para comprar sementes.";
    private PestSystem.Outbreak lastOutbreak = new PestSystem.Outbreak(false, false);
    private boolean musicEnabled = true;
    private boolean effectsEnabled = true;
    private int lastHarvestAmount;
    private int lastTreasureCoins;

    public GameState(GameClock clock, CropCatalog catalog) {
        this(clock, catalog, ThreadLocalRandom.current()::nextInt);
    }

    public GameState(GameClock clock, CropCatalog catalog, RandomSource random) {
        this(clock, Clock.systemUTC(), catalog, random, STARTING_COINS, 1, 0);
    }

    public GameState(GameClock clock, Clock realClock, CropCatalog catalog, RandomSource random) {
        this(clock, realClock, catalog, random, STARTING_COINS, 1, 0);
    }

    private GameState(GameClock clock, Clock realClock, CropCatalog catalog, RandomSource random,
                      int coins, int level, int currentXp) {
        this.clock = Objects.requireNonNull(clock);
        this.realClock = Objects.requireNonNull(realClock);
        this.catalog = Objects.requireNonNull(catalog);
        wallet = new Wallet(coins);
        market = new Market(wallet, inventory);
        this.random = Objects.requireNonNull(random);
        pests = new PestSystem(random);
        progress = new PlayerProgress(ProgressionLoader.load(), level, currentXp);
        dailyMissions = new DailyMissionBoard(LocalDate.now(realClock));
        seed = catalog.all().getFirst();
        unlockAvailable();
    }

    public Farm farm() { return farm; }
    public Inventory inventory() { return inventory; }
    public Wallet wallet() { return wallet; }
    public CropCatalog catalog() { return catalog; }
    public PlayerProgress progress() { return progress; }
    public DailyMissionBoard dailyMissions() { return dailyMissions; }
    public LandExpansion land() { return land; }
    public Tutorial tutorial() { return tutorial; }
    public Tool tool() { return tool; }
    public CropType seed() { return seed; }
    public String message() { return message; }
    public Instant now() { return clock.now(); }
    public boolean musicEnabled() { return musicEnabled; }
    public boolean effectsEnabled() { return effectsEnabled; }
    public void setMusicEnabled(boolean enabled) { musicEnabled = enabled; }
    public void setEffectsEnabled(boolean enabled) { effectsEnabled = enabled; }
    public void message(String message) { this.message = message; }
    public boolean unlocked(CropType type) { return unlockedIds.contains(type.id()); }
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
        lastTreasureCoins = 0;
        boolean cleared = tool == Tool.HOE && tile.state() == TileState.DEAD;
        switch (tool) {
            case HOE -> {
                if (tile.state() == TileState.DEAD) {
                    tile.clearDead();
                    result = ActionResult.ok("Planta morta retirada. A terra está pronta para plantar.");
                } else if (tile.state() != TileState.GRASS)
                    result = ActionResult.fail("A enxada precisa de grama livre ou de uma planta morta.");
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
                    result = ActionResult.fail("Retire a planta morta com a enxada antes de plantar.");
                else if (tile.state() != TileState.PLOWED) result = ActionResult.fail("Este terreno precisa ser arado.");
                else if (inventory.seeds(seed) == 0)
                    result = ActionResult.fail("Você não possui sementes de " + seed.name() + ".");
                else {
                    tile.plant(seed, now);
                    inventory.useSeed(seed);
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
                else { tile.removePest(now); result = ActionResult.ok("Ervas removidas. Planta voltou a crescer."); }
            }
            case PESTICIDE -> {
                if (tile.insects() == 0)
                    result = ActionResult.fail("Não há insetos aqui. O pesticida é para minhocas.");
                else { tile.removeInsects(now); result = ActionResult.ok("Insetos eliminados. A colheita está protegida."); }
            }
            case HAND -> {
                if (tile.state() != TileState.READY)
                    result = ActionResult.fail("A planta ainda não está madura.");
                else {
                    int amount = tile.harvestYield();
                    int lost = tile.insects() == 0 ? 0 : tile.crop().type().harvestYield() - amount;
                    harvested = tile.harvest(now);
                    inventory.addProduce(harvested, amount);
                    lastHarvestAmount = amount;
                    result = ActionResult.ok("+" + amount + " " + harvested.name() + " no depósito."
                            + (lost > 0 ? " Os insetos comeram " + lost + "." : " Venda na loja."));
                }
            }
            default -> throw new IllegalStateException("Unknown tool");
        }
        if (result.success()) {
            int xp = switch (tool) {
                case SEED -> progress.rules().plantXp();
                case WATER -> progress.rules().waterXp();
                case HAND -> harvested.harvestXp();
                case WEEDER, PESTICIDE -> progress.rules().removePestXp();
                case HOE -> cleared ? progress.rules().clearDeadXp() : 0;
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
        if (wallet.coins() < plot.price())
            return report(ActionResult.fail("O lote custa " + plot.price() + " moedas."));
        wallet.debit(plot.price());
        land.purchase(plot.id());
        return report(ActionResult.ok("Novo lote liberado por " + plot.price() + " moedas!"));
    }

    public ActionResult buySeed(CropType type) {
        CropType known = catalog.get(type.id());
        if (!unlocked(known))
            return report(ActionResult.fail(known.name() + " libera no nível " + known.requiredLevel() + "."));
        try {
            market.buySeed(known);
            ActionResult result = ActionResult.ok("+1 semente de " + known.name()
                    + " | -" + known.seedPrice() + " moedas");
            String missionReward = recordDailyMission("buy", 1);
            if (!missionReward.isEmpty()) result = ActionResult.ok(result.message() + " " + missionReward);
            return report(result);
        } catch (IllegalStateException e) {
            return report(ActionResult.fail("Moedas insuficientes."));
        }
    }

    public ActionResult sellProduce(CropType type) { return sellProduce(type, 1); }

    public ActionResult sellAllProduce(CropType type) {
        CropType known = catalog.get(type.id());
        return sellProduce(known, inventory.produce(known));
    }

    /** Valor de todo o depósito pelos preços atuais. */
    public int depositValue() {
        int total = 0;
        for (CropType crop : catalog.all()) total = Math.addExact(total, Math.multiplyExact(crop.salePrice(), inventory.produce(crop)));
        return total;
    }

    /** Vende todos os produtos de todas as culturas de uma vez. */
    public ActionResult sellEverything() {
        int units = 0;
        int total = depositValue();
        for (CropType crop : catalog.all()) {
            int amount = inventory.produce(crop);
            if (amount == 0) continue;
            market.sellProduce(crop, amount);
            units += amount;
        }
        if (units == 0) return report(ActionResult.fail("O depósito está vazio."));
        ActionResult result = ActionResult.ok("Vendeu " + units + " itens | +" + total + " moedas");
        String missionReward = recordDailyMission("sell", total);
        if (!missionReward.isEmpty()) result = ActionResult.ok(result.message() + " " + missionReward);
        return report(result);
    }

    private ActionResult sellProduce(CropType type, int amount) {
        CropType known = catalog.get(type.id());
        try {
            market.sellProduce(known, amount);
            ActionResult result = ActionResult.ok("Vendeu " + amount + "x " + known.name()
                    + " | +" + (known.salePrice() * amount) + " moedas");
            String missionReward = recordDailyMission("sell", known.salePrice() * amount);
            if (!missionReward.isEmpty()) result = ActionResult.ok(result.message() + " " + missionReward);
            return report(result);
        } catch (IllegalStateException e) {
            return report(ActionResult.fail("Você não possui esse produto."));
        }
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
        Instant plantedAt = clock.now().minus(turnip.growthTime()).minusSeconds(60);
        for (int row = 0; row < 2; row++) {
            Crop ready = new Crop(turnip, plantedAt, plantedAt, null, plantedAt, turnip.growthTime().toMillis());
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
        String levelReward = awardXp(xp);
        return "Missão diária concluída: +" + coins + " moedas, +" + xp + " XP!"
                + (levelReward.isEmpty() ? "" : " " + levelReward);
    }

    private String recordTutorial(String action) {
        Tutorial.Step done = tutorial.record(action);
        if (done == null) return "";
        wallet.credit(done.coins());
        String levelReward = awardXp(done.xp());
        String next = tutorial.active() ? " Agora: " + tutorial.current().title() + "."
                : " Primeiros passos concluídos! As missões diárias começam agora.";
        return "Tarefa concluída: +" + done.coins() + " moedas, +" + done.xp() + " XP!" + next
                + (levelReward.isEmpty() ? "" : " " + levelReward);
    }

    /** Moedas de presente ao chegar a um nível. */
    public static int levelUpCoins(int level) { return 25 * level; }

    /** Sementes grátis de cada cultura liberada ao chegar ao nível. */
    public static final int LEVEL_UP_SEEDS = 2;

    /** Dá XP e, para cada nível alcançado, moedas e sementes das culturas que ele libera. */
    private String awardXp(int xp) {
        int before = progress.level();
        if (progress.award(xp) == 0) return "";
        int coins = 0;
        for (int level = before + 1; level <= progress.level(); level++) {
            coins += levelUpCoins(level);
            for (CropType crop : catalog.all())
                if (crop.requiredLevel() == level) inventory.addSeeds(crop, LEVEL_UP_SEEDS);
        }
        wallet.credit(coins);
        unlockAvailable();
        return "Nível " + progress.level() + " alcançado! +" + coins + " moedas de presente.";
    }

    private void unlockAvailable() {
        for (CropType crop : catalog.all())
            if (crop.requiredLevel() <= progress.level()) unlockedIds.add(crop.id());
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
                    tile.pest().name(), crop == null || crop.pestAt() == null ? null : crop.pestAt().toString(),
                    tile.nextPestCheckAt() == null ? null : tile.nextPestCheckAt().toString(),
                    crop == null || crop.growthAnchorAt() == null ? null : crop.growthAnchorAt().toString(),
                    crop == null ? 0 : crop.grownAtAnchorMillis(), tile.insects(),
                    tile.nextInsectCheckAt() == null ? null : tile.nextInsectCheckAt().toString()));
        }
        List<String> unlocked = catalog.all().stream().filter(this::unlocked).map(CropType::id).toList();
        return new GameSnapshot(wallet.coins(), inventory.seedSnapshot(), inventory.produceSnapshot(),
                tiles, seed.id(), tool, progress.level(), progress.currentXp(), unlocked,
                musicEnabled, effectsEnabled, dailyMissions.day().toString(),
                dailyMissions.progressSnapshot(), dailyMissions.completedSnapshot(), land.snapshot(),
                tutorial.stepIndex());
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
        GameState game = new GameState(clock, realClock, catalog, random, snapshot.coins(),
                snapshot.level(), snapshot.currentXp());
        game.land.restore(snapshot.unlockedPlotIds());
        game.tutorial.restore(snapshot.tutorialStep());
        game.dailyMissions.restore(snapshot.dailyMissionDay(), snapshot.dailyMissionProgress(),
                snapshot.completedDailyMissions(), LocalDate.now(realClock));
        if (snapshot.unlockedCropIds() != null)
            for (String id : snapshot.unlockedCropIds()) game.unlockedIds.add(catalog.get(id).id());
        for (String id : snapshot.seeds().keySet()) catalog.get(id);
        for (String id : snapshot.produce().keySet()) catalog.get(id);
        game.inventory.restore(snapshot.seeds(), snapshot.produce());
        for (int i = 0; i < snapshot.tiles().size(); i++) {
            GameSnapshot.TileSnapshot tile = snapshot.tiles().get(i);
            boolean dead = tile.state() == TileState.DEAD;
            Crop crop = tile.cropId() == null || dead ? null : new Crop(catalog.get(tile.cropId()),
                    Instant.parse(tile.plantedAt()), tile.wateredAt() == null ? null : Instant.parse(tile.wateredAt()),
                    tile.pestAt() == null ? null : Instant.parse(tile.pestAt()),
                    tile.growthAnchorAt() == null ? null : Instant.parse(tile.growthAnchorAt()),
                    tile.grownAtAnchorMillis());
            Instant next = tile.nextPestCheckAt() == null
                    ? (crop != null && crop.watered()
                    ? clock.now().plusSeconds(Math.max(20, crop.type().growthSeconds() / 10)) : null)
                    : Instant.parse(tile.nextPestCheckAt());
            Instant nextInsects = tile.nextInsectCheckAt() == null
                    ? (crop != null && crop.watered() ? next : null)
                    : Instant.parse(tile.nextInsectCheckAt());
            game.farm.tile(i % COLUMNS, i / COLUMNS).restore(tile.state(), crop,
                    tile.pest() == null ? PestState.NONE : PestState.valueOf(tile.pest()), next,
                    tile.insects(), nextInsects, dead ? catalog.get(tile.cropId()) : null);
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
