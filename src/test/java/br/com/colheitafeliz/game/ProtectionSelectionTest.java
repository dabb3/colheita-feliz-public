package br.com.colheitafeliz.game;

import br.com.colheitafeliz.domain.animal.AnimalType;
import br.com.colheitafeliz.testsupport.ManualClock;
import br.com.colheitafeliz.testsupport.TestCrops;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProtectionSelectionTest {
    private final ManualClock simulation = new ManualClock();
    private final ManualClock real = new ManualClock();
    private int draws;
    private final GameState owner = new GameState(simulation::instant, real, CropCatalogLoader.load(), bound -> { draws++; return bound - 1; });
    private final GameState visitor = new GameState(simulation::instant, real, CropCatalogLoader.load(), bound -> bound - 1);
    private void affordAnimals() {
        while (owner.progress().level() < 60) owner.progress().award(owner.progress().xpToNext());
        owner.wallet().credit(1_000_000);
    }
    private void buy(String id) { assertTrue(owner.buyAnimal(owner.animalType(id)).success()); }

    @Test void selectionIsStableAcrossCallsSnapshotsRestoreAndFailedVisits() {
        affordAnimals(); buy("cachorro-robo");
        assertEquals(Set.of(4), owner.protectedPlots());
        assertEquals(1, draws);
        var snapshot = owner.snapshot();
        for (int i = 0; i < 3; i++) {
            assertEquals(Set.of(4), owner.protectedPlots());
            assertFalse(new VisitService().steal(owner, visitor, 0, 4, 0).success());
        }
        assertEquals(1, draws);
        var restored = GameState.restore(simulation::instant, real, owner.catalog(), snapshot,
                bound -> { fail("reabertura não sorteia"); return 0; });
        assertEquals(Set.of(4), restored.protectedPlots());
    }
    @Test void expansionHungerFeedingAndSameTypeRepurchaseInvalidateTheSelection() {
        affordAnimals(); buy("cachorro-robo");
        owner.protectedPlots(); assertEquals(1, draws);
        owner.land().purchase(5);
        assertEquals(2, owner.protectedPlots().size()); assertEquals(3, draws);
        simulation.advanceSeconds(6 * 3600 - 1);
        assertEquals(1, owner.activeProtectors()); owner.protectedPlots(); assertEquals(3, draws);
        simulation.advanceSeconds(1);
        assertEquals(0, owner.activeProtectors()); assertTrue(owner.protectedPlots().isEmpty()); assertEquals(3, draws);
        owner.inventory().addFeed("growth", 1); assertTrue(owner.tendAnimal(0).success());
        owner.protectedPlots(); assertEquals(5, draws);
        // Ninguém consulta a seleção durante o segundo intervalo com fome.
        simulation.advanceSeconds(6 * 3600);
        owner.inventory().addFeed("producer", 1); assertTrue(owner.tendAnimal(0).success());
        owner.protectedPlots(); assertEquals(7, draws);
        // Simulação parada, validade real vence exatamente no dia 30 e permite recompra do mesmo tipo.
        real.advanceSeconds(30 * 86400 - 1);
        assertEquals(1, owner.activeProtectors()); owner.protectedPlots(); assertEquals(7, draws);
        real.advanceSeconds(1);
        assertEquals(0, owner.activeProtectors());
        assertTrue(owner.protectedPlots().isEmpty());
        buy("cachorro-robo"); owner.protectedPlots(); assertEquals(9, draws);
        assertEquals(1, owner.barn().size());
    }
    @Test void repurchaseWithoutObservingExpiryStillChangesTheActiveSignature() {
        affordAnimals(); buy("cachorro-robo"); owner.protectedPlots(); assertEquals(1, draws);
        real.advanceSeconds(30 * 86400);
        buy("cachorro-robo"); owner.protectedPlots(); assertEquals(2, draws);
    }
    @Test void onlyCatalogProtectorsContributeAndFiveBlockTheftAndLoveOnEveryPlot() {
        affordAnimals(); buy("galinha");
        assertEquals(0, owner.activeProtectors());
        var tile = owner.farm().tile(0, 0);
        tile.plow(); tile.plant(owner.catalog().get("morango"), simulation.instant());
        var ready = TestCrops.growUntilReady(tile, simulation.instant());
        simulation.advanceSeconds(Duration.between(simulation.instant(), ready).toSeconds());
        for (var type : owner.animalTypes()) if (type.kind() == AnimalType.Kind.PROTECTOR) buy(type.id());
        assertEquals(5, owner.activeProtectors()); assertEquals(Set.of(0, 1, 2, 3, 4), owner.protectedPlots());
        var visits = new VisitService();
        for (int row = 0; row < 5; row++) assertFalse(visits.steal(owner, visitor, 0, row, owner.farm().tile(0, row).harvestRevision()).success());
        assertEquals(1, tile.crop().season());
        for (int row = 1; row < 5; row++) {
            var growing = owner.farm().tile(0, row); growing.plow(); growing.plant(owner.catalog().get("morango"), simulation.instant()); growing.water(simulation.instant());
        }
        while (visitor.progress().level() < 15) visitor.progress().award(visitor.progress().xpToNext());
        visitor.inventory().addFertilizer("amor", 5);
        for (int row = 0; row < 5; row++) assertFalse(visits.fertilizeVisit(owner, visitor, 0, row, "amor", owner.farm().tile(0, row).harvestRevision()).success());
        assertEquals(5, visitor.inventory().fertilizer("amor"));
        simulation.advanceSeconds(6 * 3600);
        assertTrue(owner.protectedPlots().isEmpty());
        assertTrue(visits.steal(owner, visitor, 0, 0, tile.harvestRevision()).success());
        tile.water(simulation.instant());
        assertTrue(visits.fertilizeVisit(owner, visitor, 0, 0, "amor", tile.harvestRevision()).success());
    }
}
