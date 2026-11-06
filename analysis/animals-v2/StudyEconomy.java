import br.com.colheitafeliz.domain.animal.Animal;
import br.com.colheitafeliz.domain.animal.AnimalType;
import br.com.colheitafeliz.domain.economy.Currency;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Proposed values exist only in this offline study. Production catalogs are read, never changed. */
final class StudyEconomy {
    static final String[] ORDER = {"galinha","abelha","pombo","coelho","pato","pavao","porco","burro","ovelha","vaca"};
    static final long[] GREEN_GAIN_TENTHS = {1,2,7,12,5,10,24,32,10,13};
    static final long[] VIP_GREEN_GAIN_TENTHS = {2,4,13,18,8,15,36,48,15,20};
    static final BigDecimal ZERO = BigDecimal.ZERO;
    static int greenMultiplier = 1; // Offline A/B/C sensitivity; production never reads this class.
    enum Model { CURRENT, PROPOSED }

    static int rank(AnimalType type) {
        for (int i=0;i<ORDER.length;i++) if (ORDER[i].equals(type.id())) return i;
        throw new IllegalArgumentException(type.id());
    }
    static BigDecimal hours(AnimalType type) { return BigDecimal.valueOf(type.requiredMillis()).divide(new BigDecimal("3600000")); }
    static long meals(AnimalType type) { return (type.requiredMillis()+type.foodDuration().toMillis()-1)/type.foodDuration().toMillis(); }
    static BigDecimal feedCost(AnimalType type) { return BigDecimal.valueOf((meals(type)-1)*type.feedPrice()); }
    static BigDecimal sale(AnimalType type, Currency currency, boolean vip, Model model) {
        BigDecimal cost=type.prices().get(currency);
        if (model==Model.CURRENT) return cost.multiply(new BigDecimal(vip?"1.25":"1.15")).setScale(1,RoundingMode.CEILING);
        int i=rank(type);
        if (currency==Currency.GREEN) return cost.add(BigDecimal.valueOf((vip?VIP_GREEN_GAIN_TENTHS[i]:GREEN_GAIN_TENTHS[i])*greenMultiplier,1));
        BigDecimal margin=hours(type).multiply(BigDecimal.valueOf(60+30L*i));
        if (vip) margin=margin.multiply(new BigDecimal("1.25"));
        return cost.add(feedCost(type)).add(margin).setScale(1,RoundingMode.UNNECESSARY);
    }
    static BigDecimal divide(BigDecimal a, BigDecimal b) { return a.divide(b,8,RoundingMode.HALF_EVEN); }
    static BigDecimal percent(BigDecimal a, BigDecimal b) { return divide(a.multiply(new BigDecimal("100")),b); }

    static long wallSeconds(AnimalType type,int logins) {
        Instant start=Instant.parse("2026-01-01T00:00:00Z");
        var origin=new br.com.colheitafeliz.domain.inventory.ItemLot(type.id(),Currency.GOLD,type.prices().get(Currency.GOLD),1,false);
        var animal=Animal.acquire(type,origin,start);
        long step=86400/logins;int paid=0;
        for (long elapsed=step;elapsed<86400L*90;elapsed+=step) {
            Instant now=start.plusSeconds(elapsed);
            if (animal.statusAt(now)==Animal.Status.READY) {
                if(paid+1!=meals(type))throw new AssertionError("Meal count differs from real animal clock");
                return elapsed;
            }
            if (animal.hungryAt(now)){animal=animal.feed(now);paid++;}
        }
        throw new IllegalStateException("Cycle did not finish");
    }
    static void animalTable(List<AnimalType> types,Path output) throws Exception {
        var csv=new Csv("model,animal,kind,level,currency,vip,buy,nominal_hours,meals_including_initial,paid_meals,feed_gold,sale,delta_gold,delta_green,gold_roi_pct,green_principal_roi_pct,gold_per_hour,green_per_hour,xp_per_cycle,xp_per_hour,first_principal_return_hours,profit_equivalent_principal_hours,cycle_hours_3_logins,cycle_hours_6_logins,cycle_hours_10_logins");
        for (var type:types) {
            if(type.kind()==AnimalType.Kind.PROTECTOR) continue;
            for(var model:Model.values()) for(var currency:Currency.values()) for(boolean vip:new boolean[]{false,true}) {
                BigDecimal cost=type.prices().get(currency),revenue=sale(type,currency,vip,model),gain=revenue.subtract(cost);
                BigDecimal gold=currency==Currency.GOLD?gain.subtract(feedCost(type)):feedCost(type).negate();
                BigDecimal green=currency==Currency.GREEN?gain:ZERO;
                BigDecimal ownGain=currency==Currency.GOLD?gold:green;
                csv.row(model,type.id(),type.kind(),type.requiredLevel(),currency,vip,cost,hours(type),meals(type),meals(type)-1,
                        feedCost(type),revenue,gold,green,currency==Currency.GOLD?percent(gold,cost):"NA",
                        currency==Currency.GREEN?percent(green,cost):"NA",divide(gold,hours(type)),divide(green,hours(type)),
                        type.collectXp(),divide(BigDecimal.valueOf(type.collectXp()),hours(type)),hours(type),divide(cost,divide(ownGain,hours(type))),
                        BigDecimal.valueOf(wallSeconds(type,3)).divide(new BigDecimal("3600")),
                        BigDecimal.valueOf(wallSeconds(type,6)).divide(new BigDecimal("3600")),
                        BigDecimal.valueOf(wallSeconds(type,10)).divide(new BigDecimal("3600")));
                if(ownGain.signum()<=0) throw new AssertionError("Nonpositive route gain");
            }
        }
        csv.save(output.resolve("animal-metrics.csv"));
        var proposal=new Csv("animal,kind,level,limit,current_buy_gold,proposed_buy_gold,current_buy_green,proposed_buy_green,hours,feed_hours,feed_unit_gold,xp,current_common_gold,proposed_common_gold,current_vip_gold,proposed_vip_gold,current_common_green,proposed_common_green,current_vip_green,proposed_vip_green");
        for(var type:types) {
            var fields=new ArrayList<Object>(List.of(type.id(),type.kind(),type.requiredLevel(),type.max(),type.prices().get(Currency.GOLD),type.prices().get(Currency.GOLD),type.prices().get(Currency.GREEN),type.prices().get(Currency.GREEN),
                    type.kind()==AnimalType.Kind.PROTECTOR?720:hours(type),type.foodDuration().toHours(),type.feedPrice(),type.collectXp()));
            for(var currency:Currency.values()) for(boolean vip:new boolean[]{false,true}) for(var model:Model.values())
                fields.add(type.kind()==AnimalType.Kind.PROTECTOR?"NOT_SELLABLE":sale(type,currency,vip,model));
            proposal.row(fields.toArray());
        }
        proposal.save(output.resolve("current-proposed.csv"));
    }
    static void protectorTable(List<AnimalType> types,Path output) throws Exception {
        var csv=new Csv("days,logins_per_day,feed_unit_gold,protectors,coverage_when_fed_pct,min_level,buy_gold,buy_green,meals_including_initial_per_animal,paid_meals_per_animal,feed_gold,all_gold_cost,green_route_gold_cost,green_route_green_cost,active_hours,chosen_coverage_active_time_pct");
        var protectors=types.stream().filter(t->t.kind()==AnimalType.Kind.PROTECTOR).toList();
        for(int days:new int[]{7,15,30}) for(int logins:new int[]{3,6,10,4}) for(int food:new int[]{80,120}) {
            long duration=days*86400L,interval=86400/logins,active=0,until=6*3600;
            int paid=0;
            for(long t=0;t<duration;t+=interval) {
                if(t>=until){until=t+6*3600;paid++;}
                active+=Math.max(0,Math.min(Math.min(t+interval,duration),until)-t);
            }
            BigDecimal gold=ZERO,green=ZERO;
            for(int count=1;count<=5;count++) {
                var type=protectors.get(count-1);gold=gold.add(type.prices().get(Currency.GOLD));green=green.add(type.prices().get(Currency.GREEN));
                var feed=BigDecimal.valueOf(paid*(long)food*count);
                csv.row(days,logins,food,count,count*20,type.requiredLevel(),gold,green,paid+1,paid,feed,gold.add(feed),feed,green,
                        divide(BigDecimal.valueOf(active),new BigDecimal("3600")),percent(BigDecimal.valueOf(active),BigDecimal.valueOf(duration)));
                if(logins==4&&days==30&&paid!=119) throw new AssertionError("Initial feeding counted twice");
            }
        }
        csv.save(output.resolve("protection-costs.csv"));
    }
    static final class Csv {
        private final StringBuilder data;
        Csv(String header){data=new StringBuilder(header).append('\n');}
        void row(Object... values){for(int i=0;i<values.length;i++){if(i>0)data.append(',');var v=values[i];data.append(v instanceof BigDecimal b?b.toPlainString():v);}data.append('\n');}
        void save(Path path)throws Exception {Files.createDirectories(path.toAbsolutePath().getParent());Files.writeString(path,data);}
    }
}
