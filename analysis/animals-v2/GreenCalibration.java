import br.com.colheitafeliz.game.*;
import br.com.colheitafeliz.domain.economy.Currency;
import java.nio.file.Path;
import java.time.*;

/** Sensitivity analysis only: same domain simulator, alternative settlement margins. */
public final class GreenCalibration {
    public static void main(String[] args) throws Exception {
        var clock=Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"),ZoneOffset.UTC);
        var game=new GameState(clock::instant,clock,CropCatalogLoader.load(),bound->99);
        int runs=0;
        for(int multiplier=1;multiplier<=3;multiplier++) {
            StudyEconomy.greenMultiplier=multiplier;
            var output=Path.of("analysis/animals-v2/green-calibration/"+(char)('A'+multiplier-1));
            StudyEconomy.animalTable(game.animalTypes(),output);
            var csv=new StudyEconomy.Csv(PortfolioSimulation.header());
            for(var funding:PortfolioSimulation.Funding.values())for(int level:new int[]{10,20,30,40,50,60})
                for(var route:Currency.values())for(int logins:new int[]{3,6,10})for(boolean vip:new boolean[]{false,true})
                    for(var strategy:new PortfolioSimulation.Strategy[]{PortfolioSimulation.Strategy.ANIMALS,PortfolioSimulation.Strategy.MIXED,PortfolioSimulation.Strategy.PLANTS}) {
                        new PortfolioSimulation(level,logins,vip,route,strategy,funding,StudyEconomy.Model.PROPOSED).run(csv);
                        runs++;
                    }
            csv.save(output.resolve("portfolio-simulations.csv"));
            System.out.println("Completed alternative "+(char)('A'+multiplier-1));
        }
        if(runs!=1296)throw new AssertionError("Wrong matrix: "+runs);
        StudyEconomy.greenMultiplier=1;
        System.out.println("OK: 1296 trajectories, 3888 observations; balances reconciled by domain harness.");
    }
}
