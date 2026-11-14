package br.com.colheitafeliz.server;

import br.com.colheitafeliz.domain.animal.Animal;
import br.com.colheitafeliz.domain.economy.Currency;
import br.com.colheitafeliz.domain.inventory.ItemLot;
import br.com.colheitafeliz.game.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AnimalPushScheduleTest {
    private static final Instant START=Instant.parse("2026-11-01T00:00:00Z");
    private final AnimalPushSchedule schedule=new AnimalPushSchedule();

    @Test void producerSchedulesItsFedMaturityAndIsObservedOnlyOnce(){
        var snapshot=snapshot(List.of(acquire("galinha",START)),null,null);
        var state=schedule.reconcile(snapshot,null,START,true);
        assertEquals(START.plusSeconds(28800),schedule.next(snapshot,state,START));
        assertFalse(schedule.ready(snapshot,state,START.plusSeconds(28799)));
        assertTrue(schedule.ready(snapshot,state,START.plusSeconds(28800)));
        schedule.observeReady(snapshot,state,START.plusSeconds(28800));
        assertFalse(schedule.ready(snapshot,state,START.plusSeconds(28801)));
        assertNull(schedule.next(snapshot,state,START.plusSeconds(28801)));
    }

    @Test void hungryAnimalHasNoInventedDeadlineAndFeedingPreservesAcquisition(){
        var bee=acquire("abelha",START);
        var snapshot=snapshot(List.of(bee),null,null);
        var state=schedule.reconcile(snapshot,null,START,true);
        assertNull(schedule.next(snapshot,state,START));
        assertFalse(schedule.ready(snapshot,state,START.plusSeconds(86400)));
        var fed=snapshot(List.of(bee.feed(START.plusSeconds(86400))),null,null);
        state=schedule.reconcile(fed,state,START.plusSeconds(86400),true);
        assertEquals(START.plusSeconds(100800),schedule.next(fed,state,START.plusSeconds(86400)));
        assertTrue(schedule.ready(fed,state,START.plusSeconds(100800)));
        schedule.observeReady(fed,state,START.plusSeconds(100800));
        state=schedule.reconcile(fed,state,START.plusSeconds(100801),true);
        assertFalse(schedule.ready(fed,state,START.plusSeconds(100801)));
    }

    @Test void activationAndReactivationSuppressHistoricalMaturity(){
        var snapshot=snapshot(List.of(acquire("galinha",START)),null,null);
        var state=schedule.reconcile(snapshot,null,START.plusSeconds(28800),true);
        assertFalse(schedule.ready(snapshot,state,START.plusSeconds(28800)));
        state=schedule.reconcile(snapshot,state,START.plusSeconds(28801),false);
        assertFalse(state.get("enabled").getAsBoolean());
        assertNull(schedule.next(snapshot,state,START.plusSeconds(28801)));
        state=schedule.reconcile(snapshot,state,START.plusSeconds(28802),true);
        assertEquals(START.plusSeconds(28802).toString(),state.get("baseline").getAsString());
        assertFalse(schedule.ready(snapshot,state,START.plusSeconds(28802)));
    }

    @Test void disabledAnimalChannelDoesNotBecomeReady(){
        var snapshot=snapshot(List.of(acquire("galinha",START)),null,null);
        var state=schedule.reconcile(snapshot,null,START,false);
        assertFalse(schedule.ready(snapshot,state,START.plusSeconds(28800)));
        assertNull(schedule.next(snapshot,state,START.plusSeconds(28800)));
    }

    @Test void adultGrowingAnimalsQualifyButProtectorsNeverDo(){
        var pigeon=acquire("pombo",START);
        pigeon=new Animal(pigeon.type(),pigeon.origin(),START,START,128_700_000,START.plusSeconds(43200));
        var snapshot=snapshot(List.of(pigeon,acquire("cachorro-robo",START)),null,null);
        var state=schedule.reconcile(snapshot,null,START,true);
        assertEquals(START.plusSeconds(900),schedule.next(snapshot,state,START));
        assertTrue(schedule.ready(snapshot,state,START.plusSeconds(900)));
        schedule.observeReady(snapshot,state,START.plusSeconds(900));
        assertNull(schedule.next(snapshot,state,START.plusSeconds(900)));
        assertFalse(schedule.ready(snapshot,state,START.plusSeconds(900)));
    }

    @Test void removalClearsObservedIdentityAndReacquisitionCanNotify(){
        var snapshot=snapshot(List.of(acquire("galinha",START)),null,null);
        var state=schedule.reconcile(snapshot,null,START,true);
        schedule.observeReady(snapshot,state,START.plusSeconds(28800));
        state=schedule.reconcile(snapshot(List.of(),null,null),state,START.plusSeconds(28801),true);
        var acquired=START.plusSeconds(28802);
        snapshot=snapshot(List.of(acquire("galinha",acquired)),null,null);
        state=schedule.reconcile(snapshot,state,acquired,true);
        assertTrue(schedule.ready(snapshot,state,acquired.plusSeconds(28800)));
    }

    @Test void expiredAutomationRequestsOneNormalEngineReplay(){
        var now=START.plusSeconds(86400);
        var snapshot=snapshot(List.of(acquire("abelha",START)),START.plusSeconds(43200),START);
        var state=schedule.reconcile(snapshot,null,START,true);
        assertEquals(now,schedule.next(snapshot,state,now));
        assertNull(schedule.next(snapshot.withAutomationCursor(now.toString()),state,now));
    }

    @Test void activeCareCanScheduleMaturityPastCurrentMeal(){
        var snapshot=snapshot(List.of(acquire("abelha",START)),START.plusSeconds(86400),START);
        var state=schedule.reconcile(snapshot,null,START,true);
        assertEquals(START.plusSeconds(43200),schedule.next(snapshot,state,START));
        assertFalse(schedule.ready(snapshot,state,START.plusSeconds(43200)));
    }

    @Test void historicalReplayMaturityBeforeBaselineIsNotNewReadiness(){
        var baseline=START.plusSeconds(60000);
        var initial=snapshot(List.of(acquire("abelha",START)),START.plusSeconds(86400),START);
        var state=schedule.reconcile(initial,null,baseline,true);
        var bee=acquire("abelha",START).feed(START.plusSeconds(28800));
        var replayed=snapshot(List.of(bee),START.plusSeconds(86400),baseline);
        state=schedule.reconcile(replayed,state,baseline.plusSeconds(1),true);
        assertFalse(schedule.ready(replayed,state,baseline.plusSeconds(1)));
        assertNull(schedule.next(replayed,state,baseline.plusSeconds(1)));
    }

    private static Animal acquire(String id,Instant at){
        var type=AnimalCatalogLoader.load().stream().filter(t->t.id().equals(id)).findFirst().orElseThrow();
        return Animal.acquire(type,new ItemLot(id,Currency.GOLD,type.prices().get(Currency.GOLD),1,false),at);
    }
    private static GameSnapshot snapshot(List<Animal> animals,Instant expiry,Instant cursor){
        var clock=Clock.fixed(START,ZoneOffset.UTC);
        var base=new GameState(clock::instant,clock,CropCatalogLoader.load(),bound->bound-1).snapshot();
        return new GameSnapshot(base.coins(),base.seeds(),base.produce(),base.tiles(),base.selectedSeedId(),base.tool(),
                base.level(),base.currentXp(),base.musicEnabled(),base.effectsEnabled(),base.dailyMissionDay(),
                base.dailyMissionProgress(),base.completedDailyMissions(),base.unlockedPlotIds(),base.tutorialStep(),
                animals.stream().map(a->new GameSnapshot.AnimalSnapshot(a.type().id(),a.origin(),a.acquiredAt().toString(),
                        a.growthAnchorAt().toString(),a.grownAtAnchorMillis(),a.fedUntil().toString())).toList(),
                base.feed(),base.fertilizers(),base.selectedFertilizerId(),base.greenCoins(),expiry==null?null:expiry.toString(),
                base.protection(),base.notifications(),base.christmas2026(),base.avatar(),expiry==null?null:
                new GameSnapshot.AutomationSnapshot(cursor.toString(),Map.of("animals",expiry.toString()),Map.of(),Map.of()));
    }
}
