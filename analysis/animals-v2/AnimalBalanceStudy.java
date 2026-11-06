import br.com.colheitafeliz.game.*;
import br.com.colheitafeliz.domain.animal.AnimalType;
import br.com.colheitafeliz.domain.economy.Currency;
import java.nio.file.Path;
import java.time.*;

/** Offline analysis entry point. Run with assertions enabled and the existing core/Gson classpath. */
public final class AnimalBalanceStudy {
    public static void main(String[] args)throws Exception {
        Path output=Path.of("analysis/animals-v2/results");
        Clock clock=Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"),ZoneOffset.UTC);
        var game=new GameState(clock::instant,clock,CropCatalogLoader.load(),bound->99);
        var types=game.animalTypes();
        if(types.size()!=15||types.stream().filter(t->t.kind()!=AnimalType.Kind.PROTECTOR).count()!=10)throw new AssertionError("Catalog shape changed");
        for(var type:types)if(type.max()!=1||type.prices().size()!=2)throw new AssertionError("Catalog constraints changed");
        long previousVip=0,previousHours=1;
        for(int i=0;i<StudyEconomy.ORDER.length;i++){
            String id=StudyEconomy.ORDER[i];var type=types.stream().filter(t->t.id().equals(id)).findFirst().orElseThrow();
            long hours=StudyEconomy.hours(type).longValueExact();
            long common=java.math.BigDecimal.valueOf(hours*(10+5L*i)).divide(new java.math.BigDecimal("100"),0,java.math.RoundingMode.HALF_UP).longValueExact();
            long vip=java.math.BigDecimal.valueOf(common).multiply(new java.math.BigDecimal("1.5")).setScale(0,java.math.RoundingMode.HALF_UP).longValueExact();
            if(i>0)vip=Math.max(vip,previousVip*hours/previousHours+1);
            if(common!=StudyEconomy.GREEN_GAIN_TENTHS[i]||vip!=StudyEconomy.VIP_GREEN_GAIN_TENTHS[i])throw new AssertionError("Proposal differs from formula");
            previousVip=vip;previousHours=hours;
        }
        StudyEconomy.animalTable(types,output);
        StudyEconomy.protectorTable(types,output);
        var csv=new StudyEconomy.Csv(PortfolioSimulation.header());int runs=0;
        for(var funding:PortfolioSimulation.Funding.values())for(int level:new int[]{1,10,20,30,40,50,60}){
            if(level==1&&funding==PortfolioSimulation.Funding.CONTROLLED_LIQUIDITY)continue;
            for(var model:StudyEconomy.Model.values())for(var route:Currency.values())for(int logins:new int[]{3,6,10})
                for(boolean vip:new boolean[]{false,true})for(var strategy:PortfolioSimulation.Strategy.values()){
                    new PortfolioSimulation(level,logins,vip,route,strategy,funding,model).run(csv);runs++;
                }
            System.out.println("Completed "+funding+" level "+level+"; "+runs+" portfolios");
        }
        if(runs!=1248)throw new AssertionError("Scenario count changed: "+runs);
        csv.save(output.resolve("portfolio-simulations.csv"));
        System.out.println("OK: 80 analytical animal routes, 15 current/proposed rows, 120 protector combinations and 3744 portfolio observations.");
    }
}
