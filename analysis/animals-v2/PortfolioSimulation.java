import br.com.colheitafeliz.domain.animal.Animal;
import br.com.colheitafeliz.domain.animal.AnimalType;
import br.com.colheitafeliz.domain.economy.Currency;
import br.com.colheitafeliz.domain.farm.TileState;
import br.com.colheitafeliz.game.*;
import com.google.gson.Gson;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Runs unchanged GameState. PROPOSED overlays only animal settlement, explicitly tracked in the ledger. */
final class PortfolioSimulation {
    enum Strategy { PLANTS, ANIMALS, MIXED, PROTECTED_FARM }
    enum Funding { REAL_OR_RETAINED_REWARDS, CONTROLLED_LIQUIDITY }
    private static final BigDecimal ZERO=BigDecimal.ZERO;
    private static final BigDecimal HUNDRED=new BigDecimal("100");
    private final StudyEconomy.Model model;
    private final Currency route;
    private final Strategy strategy;
    private final Funding funding;
    private final int startLevel,logins;
    private final boolean wantVip;
    private final StudyClock clock=new StudyClock();
    private final GameState game;
    private final Pair initial=new Pair(),animalBuy=new Pair(),animalSales=new Pair(),closedAnimalCost=new Pair();
    private final Pair seedBuy=new Pair(),cropSales=new Pair(),cropCost=new Pair(),protectorBuy=new Pair();
    private final Map<String,BigDecimal> cycleFeed=new HashMap<>();
    private BigDecimal feed=ZERO,closedFeed=ZERO,daily=ZERO,plotSpend=ZERO,vipSpend=ZERO;
    private BigDecimal protectedPlotHours=ZERO,totalPlotHours=ZERO;
    private BigDecimal vipHours=ZERO;
    private int cycles,paidMeals,feedMisses,cropHarvests;
    private LocalDate lastAllowance;

    PortfolioSimulation(int level,int logins,boolean vip,Currency route,Strategy strategy,Funding funding,StudyEconomy.Model model) {
        this.startLevel=level;this.logins=logins;this.wantVip=vip;this.route=route;this.strategy=strategy;this.funding=funding;this.model=model;
        var catalog=CropCatalogLoader.load();
        var fresh=new GameState(clock::instant,clock,catalog,bound->bound-1);
        initial.gold=fresh.wallet().coins();initial.green=fresh.greenCoins();
        for(int i=2;i<=level;i++){var reward=fresh.progress().rules().rewardFor(i);initial.gold=initial.gold.add(reward.gold());initial.green=initial.green.add(reward.green());}
        if(funding==Funding.CONTROLLED_LIQUIDITY){initial.gold=new BigDecimal("2000000");initial.green=new BigDecimal("2000");}
        var json=new Gson().toJsonTree(fresh.snapshot()).getAsJsonObject();
        json.addProperty("coins",initial.gold);json.addProperty("greenCoins",initial.green);json.addProperty("level",level);
        var state=new Gson().fromJson(json,GameSnapshot.class);
        game=GameState.restore(clock::instant,clock,catalog,state,bound->bound-1);
        if(level==1&&(initial.gold.compareTo(new BigDecimal("500"))!=0||initial.green.compareTo(new BigDecimal("5"))!=0))throw new AssertionError("New account modified");
    }
    static String header(){return "model,funding,start_level,logins,vip_intent,animal_currency,strategy,days,end_level,initial_gold,initial_green,cash_gold,cash_green,animal_buy_gold,animal_buy_green,animal_sales_gold,animal_sales_green,animal_closed_cost_gold,animal_closed_cost_green,feed_gold,closed_cycle_feed_gold,animal_realized_gold,animal_realized_green,seed_buy_gold,seed_buy_green,crop_sales_gold,crop_sales_green,crop_realized_gold,crop_realized_green,protector_buy_gold,protector_buy_green,plots_green,vip_green,daily_gold,level_reward_gold,level_reward_green,mission_green,animal_capital_gold,animal_capital_green,crop_capital_gold,crop_capital_green,protector_book_gold,protector_book_green,active_cycle_feed_gold,total_xp_gained,animal_cycles,paid_meals,missed_feed_visits,crop_harvests,plots,live_animals,vip_active_hours,average_protected_land_pct,protector_feed_gold,vip_book_green,land_book_green";}
    void run(StudyEconomy.Csv output) {
        int nextHorizon=0;int[] days={7,15,30};long step=86400/logins;
        for(int visit=0;visit<30*logins;visit++) {
            long current=visit*step,next=(visit+1)*step;
            clock.elapsed(current);login();
            long segmentStart=current;
            while(nextHorizon<days.length&&days[nextHorizon]*86400L<=next){
                long boundary=days[nextHorizon]*86400L;
                integrateProtection(segmentStart,boundary);clock.elapsed(boundary);game.refresh();
                snapshot(output,days[nextHorizon]);nextHorizon++;segmentStart=boundary;
            }
            integrateProtection(segmentStart,next);
        }
    }
    private void login() {
        game.refresh();
        LocalDate date=LocalDate.ofInstant(clock.instant(),ZoneOffset.UTC);
        // Emulates OnlineGameService.CLAIM_DAILY_ALLOWANCE (100 GOLD/day UTC), not its HTTP/receipt layer.
        if(!date.equals(lastAllowance)){game.wallet().credit(Currency.GOLD,HUNDRED);daily=daily.add(HUNDRED);lastAllowance=date;}
        boolean wasVip=game.vipActive();
        if(wantVip&&!wasVip&&afford(Currency.GREEN,GameState.VIP_PRICE)){
            ok(game.buyVip());vipSpend=vipSpend.add(GameState.VIP_PRICE);
        }
        tendAnimals();
        if(strategy!=Strategy.ANIMALS) harvestPlants();
        if(strategy==Strategy.ANIMALS||strategy==Strategy.MIXED||strategy==Strategy.PROTECTED_FARM) buyAnimals();
        if(strategy!=Strategy.ANIMALS){expand();plantAndWater();}
        for(var type:game.animalTypes())if(game.barn().count(type)>type.max())throw new AssertionError("Animal cap bypass");
    }
    private boolean afford(Currency currency,BigDecimal cost){return game.wallet().balance(currency).compareTo(cost)>=0;}
    private static void ok(ActionResult result){if(!result.success())throw new AssertionError(result.message());}
    private void tendAnimals() {
        for(var animal:game.barn().animals()) {
            var type=animal.type();
            if(type.kind()!=AnimalType.Kind.PROTECTOR&&animal.statusAt(clock.instant())==Animal.Status.READY){
                BigDecimal settled=br.com.colheitafeliz.domain.economy.Market.unitRevenue(animal.origin(),game.vipActive())
                        .setScale(1,java.math.RoundingMode.CEILING);
                BigDecimal price=StudyEconomy.sale(type,animal.origin().currency(),game.vipActive(),model);
                ok(game.tendAnimal(type.id()));
                if(type.kind()==AnimalType.Kind.PRODUCER)ok(game.sellProduct(type,false));
                // Counterfactual only: replace the current credit with the proposed exact unit credit.
                BigDecimal adjustment=price.subtract(settled);
                if(adjustment.signum()>0)game.wallet().credit(animal.origin().currency(),adjustment);
                else if(adjustment.signum()<0)game.wallet().debit(animal.origin().currency(),adjustment.negate());
                animalSales.add(animal.origin().currency(),price);closedAnimalCost.add(animal.origin().currency(),animal.origin().unitCost());
                closedFeed=closedFeed.add(cycleFeed.remove(type.id()));cycles++;
            }else if(animal.hungryAt(clock.instant())){
                if(!afford(Currency.GOLD,BigDecimal.valueOf(type.feedPrice()))){feedMisses++;continue;}
                ok(game.buyFeed(type.feedId()));ok(game.tendAnimal(type.id()));
                BigDecimal cost=BigDecimal.valueOf(type.feedPrice());feed=feed.add(cost);paidMeals++;
                if(type.kind()!=AnimalType.Kind.PROTECTOR)cycleFeed.merge(type.id(),cost,BigDecimal::add);
            }
        }
    }
    private void buyAnimals() {
        for(var type:game.animalTypes()){
            boolean protector=type.kind()==AnimalType.Kind.PROTECTOR;
            if(protector!=(strategy==Strategy.PROTECTED_FARM)||type.requiredLevel()>game.progress().level()||game.barn().full(type))continue;
            BigDecimal price=type.prices().get(route);
            BigDecimal reserve=wantVip&&!game.vipActive()?GameState.VIP_PRICE:ZERO;
            if(route==Currency.GREEN&&!afford(route,price.add(reserve)))continue;
            if(!afford(route,price))continue;
            if(route==Currency.GOLD&&strategy!=Strategy.ANIMALS&&!afford(route,price.add(new BigDecimal("1500"))))continue;
            ok(game.buyAnimal(type,route));
            if(protector)protectorBuy.add(route,price);
            else{animalBuy.add(route,price);cycleFeed.put(type.id(),ZERO);}
        }
    }
    private void harvestPlants() {
        for(int row=0;row<GameState.ROWS;row++)for(int col=0;col<GameState.COLUMNS;col++) {
            if(!game.land().isUnlocked(col,row)||game.farm().tile(col,row).state()!=TileState.READY)continue;
            game.selectTool(Tool.HAND);ok(game.act(col,row));cropHarvests++;
        }
        if(game.inventory().produceLots().isEmpty())return;
        for(var lot:game.inventory().produceLots()) {
            if(lot.cropSale()==null)throw new AssertionError("Unexpected non-crop product in crop sale");
            cropCost.add(lot.currency(),StudyEconomy.divide(lot.unitCost(),BigDecimal.valueOf(lot.cropSale().seasons())).multiply(BigDecimal.valueOf(lot.quantity())));
        }
        game.depositTotals().forEach(cropSales::add);ok(game.sellEverything());
    }
    private void expand() {
        var reserve=wantVip&&!game.vipActive()?GameState.VIP_PRICE:ZERO;
        while(game.land().next()!=null&&game.progress().level()>=game.land().next().requiredLevel()
                &&afford(Currency.GREEN,game.land().next().price().add(reserve))){
            var plot=game.land().next();ok(game.buyPlot(plot.column(),plot.row()));plotSpend=plotSpend.add(plot.price());
        }
    }
    private void plantAndWater(){
        for(int row=0;row<GameState.ROWS;row++)for(int col=0;col<GameState.COLUMNS;col++){
            if(!game.land().isUnlocked(col,row))continue;
            var tile=game.farm().tile(col,row);
            if(tile.state()==TileState.GRASS||tile.state()==TileState.DEAD){game.selectTool(Tool.HOE);ok(game.act(col,row));}
            if(tile.state()==TileState.PLOWED){
                var eligible=game.catalog().all().stream().filter(type->game.unlocked(type)&&(!type.vipOnly()||game.vipActive())
                        &&afford(type.seedPrice().getKey(),type.seedPrice().getValue())).toList();
                var preferred=eligible.stream().filter(type->type.vipOnly()==(route==Currency.GREEN&&game.vipActive())).toList();
                var choices=preferred.isEmpty()?eligible:preferred;
                if(!choices.isEmpty()){
                    var seed=choices.getLast();ok(game.buySeed(seed));seedBuy.add(seed.seedPrice().getKey(),seed.seedPrice().getValue());
                    ok(game.selectSeed(seed));ok(game.act(col,row));
                }
            }
            if(tile.crop()!=null&&tile.crop().dryAt(clock.instant())){game.selectTool(Tool.WATER);ok(game.act(col,row));}
        }
    }
    private void integrateProtection(long start,long end){
        if(end<=start)return;
        if(game.vip().expiresAt()!=null){
            long until=Duration.between(StudyClock.START,game.vip().expiresAt()).getSeconds();
            vipHours=vipHours.add(StudyEconomy.divide(BigDecimal.valueOf(Math.max(0,Math.min(end,until)-start)),new BigDecimal("3600")));
        }
        var points=new TreeSet<Long>();points.add(start);points.add(end);
        var guards=game.barn().animals().stream().filter(a->a.type().kind()==AnimalType.Kind.PROTECTOR).toList();
        for(var guard:guards)for(var time:List.of(guard.fedUntil(),guard.expiresAt())){
            long t=Duration.between(StudyClock.START,time).getSeconds();if(t>start&&t<end)points.add(t);
        }
        var ordered=new ArrayList<>(points);int plots=game.land().snapshot().size();
        for(int i=1;i<ordered.size();i++){
            long left=ordered.get(i-1),right=ordered.get(i);Instant mid=StudyClock.START.plusSeconds(left+(right-left)/2);
            int active=(int)guards.stream().filter(a->a.activeAt(mid)).count();
            int protectedCount=br.com.colheitafeliz.domain.animal.ProtectionRules.protectedCount(plots,active);
            BigDecimal hours=StudyEconomy.divide(BigDecimal.valueOf(right-left),new BigDecimal("3600"));
            protectedPlotHours=protectedPlotHours.add(hours.multiply(BigDecimal.valueOf(protectedCount)));
            totalPlotHours=totalPlotHours.add(hours.multiply(BigDecimal.valueOf(plots)));
        }
    }
    private void snapshot(StudyEconomy.Csv output,int days){
        var animalCapital=new Pair();var cropCapital=new Pair();var protectorBook=new Pair();
        for(var animal:game.barn().animals()){
            if(animal.type().kind()==AnimalType.Kind.PROTECTOR){
                long left=Math.max(0,Duration.between(clock.instant(),animal.expiresAt()).getSeconds());
                protectorBook.add(animal.origin().currency(),StudyEconomy.divide(animal.origin().unitCost().multiply(BigDecimal.valueOf(left)),BigDecimal.valueOf(30*86400L)));
            }else animalCapital.add(animal.origin().currency(),animal.origin().unitCost());
        }
        for(int row=0;row<GameState.ROWS;row++)for(int col=0;col<GameState.COLUMNS;col++){
            var crop=game.farm().tile(col,row).crop();if(crop==null)continue;
            cropCapital.add(crop.origin().currency(),StudyEconomy.divide(crop.origin().unitCost().multiply(BigDecimal.valueOf(crop.type().seasons()-crop.season()+1L)),BigDecimal.valueOf(crop.type().seasons())));
        }
        BigDecimal rewardGold=ZERO,rewardGreen=ZERO;
        for(int l=startLevel+1;l<=game.progress().level();l++){var reward=game.progress().rules().rewardFor(l);rewardGold=rewardGold.add(reward.gold());rewardGreen=rewardGreen.add(reward.green());}
        BigDecimal residualGold=game.wallet().coins().subtract(initial.gold).add(animalBuy.gold).add(protectorBuy.gold).add(seedBuy.gold).add(feed).subtract(animalSales.gold).subtract(cropSales.gold).subtract(daily);
        BigDecimal residualGreen=game.greenCoins().subtract(initial.green).add(animalBuy.green).add(protectorBuy.green).add(seedBuy.green).add(plotSpend).add(vipSpend).subtract(animalSales.green).subtract(cropSales.green);
        if(residualGold.compareTo(rewardGold)!=0)throw new AssertionError("Gold ledger does not reconcile");
        BigDecimal mission=residualGreen.subtract(rewardGreen);
        if(mission.signum()<0||mission.stripTrailingZeros().scale()>0||mission.compareTo(BigDecimal.valueOf(days*2L))>0)throw new AssertionError("Unexpected grants");
        BigDecimal xp=game.progress().currentXp();for(int l=startLevel;l<game.progress().level();l++)xp=xp.add(game.progress().rules().xpToNext(l));
        BigDecimal activeFeed=cycleFeed.values().stream().reduce(ZERO,BigDecimal::add);
        BigDecimal guardFeed=feed.subtract(closedFeed).subtract(activeFeed);
        if(guardFeed.signum()<0)throw new AssertionError("Food cost classification failed");
        BigDecimal vipBook=ZERO;
        if(game.vipActive()){
            long left=Math.min(30*86400L,Math.max(0,Duration.between(clock.instant(),game.vip().expiresAt()).getSeconds()));
            vipBook=StudyEconomy.divide(GameState.VIP_PRICE.multiply(BigDecimal.valueOf(left)),BigDecimal.valueOf(30*86400L));
        }
        output.row(model,funding,startLevel,logins,wantVip,route,strategy,days,game.progress().level(),initial.gold,initial.green,game.wallet().coins(),game.greenCoins(),
                animalBuy.gold,animalBuy.green,animalSales.gold,animalSales.green,closedAnimalCost.gold,closedAnimalCost.green,feed,closedFeed,
                animalSales.gold.subtract(closedAnimalCost.gold).subtract(closedFeed),animalSales.green.subtract(closedAnimalCost.green),
                seedBuy.gold,seedBuy.green,cropSales.gold,cropSales.green,cropSales.gold.subtract(cropCost.gold),cropSales.green.subtract(cropCost.green),
                protectorBuy.gold,protectorBuy.green,plotSpend,vipSpend,daily,rewardGold,rewardGreen,mission,animalCapital.gold,animalCapital.green,
                cropCapital.gold,cropCapital.green,protectorBook.gold,protectorBook.green,activeFeed,xp,cycles,paidMeals,feedMisses,cropHarvests,
                game.land().snapshot().size(),game.barn().size(),vipHours,totalPlotHours.signum()==0?ZERO:StudyEconomy.percent(protectedPlotHours,totalPlotHours),guardFeed,vipBook,plotSpend);
    }
    private static final class Pair {
        BigDecimal gold=ZERO,green=ZERO;
        void add(Currency c,BigDecimal value){if(c==Currency.GOLD)gold=gold.add(value);else green=green.add(value);}
    }
    private static final class StudyClock extends Clock{
        static final Instant START=Instant.parse("2026-01-01T00:00:00Z");long seconds;
        void elapsed(long value){seconds=value;}
        @Override public Instant instant(){return START.plusSeconds(seconds);}
        @Override public ZoneId getZone(){return ZoneOffset.UTC;}
        @Override public Clock withZone(ZoneId zone){return this;}
    }
}
