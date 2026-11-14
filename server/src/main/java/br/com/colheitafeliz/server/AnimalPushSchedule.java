package br.com.colheitafeliz.server;

import br.com.colheitafeliz.domain.animal.Animal;
import br.com.colheitafeliz.domain.animal.AnimalType;
import br.com.colheitafeliz.game.AnimalCatalogLoader;
import br.com.colheitafeliz.game.GameSnapshot;
import com.google.gson.JsonObject;
import java.time.Instant;
import java.util.List;

/** Private acquisition cursors; feeding changes progress, never the notification identity. */
final class AnimalPushSchedule {
    // shortcut: catalog max=1 permits one observed acquisition per type; use acquisition keys if limits increase.
    private final List<AnimalType> catalog=AnimalCatalogLoader.load();

    JsonObject reconcile(GameSnapshot snapshot,JsonObject previous,Instant now,boolean enabled){
        var state=new JsonObject();
        state.addProperty("enabled",enabled);
        Instant baseline=previous==null||!enabled(previous)?now:baseline(previous);
        state.addProperty("baseline",baseline.toString());
        var observed=new JsonObject();
        for(var saved:snapshot.animals()){
            var animal=animal(saved);
            if(!eligible(animal))continue;
            if(seen(previous,animal)||maturedBefore(animal,baseline,now))
                observed.addProperty(animal.type().id(),animal.acquiredAt().toString());
        }
        state.add("observed",observed);
        return state;
    }

    boolean ready(GameSnapshot snapshot,JsonObject state,Instant now){
        if(!enabled(state))return false;
        for(var saved:snapshot.animals()){
            var animal=animal(saved);
            if(eligible(animal)&&!seen(state,animal)&&animal.statusAt(now)==Animal.Status.READY
                    &&!maturedBefore(animal,baseline(state),now))return true;
        }
        return false;
    }

    void observeReady(GameSnapshot snapshot,JsonObject state,Instant now){
        if(!enabled(state))return;
        var observed=state.getAsJsonObject("observed");
        for(var saved:snapshot.animals()){
            var animal=animal(saved);
            if(eligible(animal)&&animal.statusAt(now)==Animal.Status.READY)
                observed.addProperty(animal.type().id(),animal.acquiredAt().toString());
        }
    }

    Instant next(GameSnapshot snapshot,JsonObject state,Instant now){
        if(!enabled(state))return null;
        Instant next=null;
        Instant expiry=expiry(snapshot);
        for(var saved:snapshot.animals()){
            var animal=animal(saved);
            if(!eligible(animal)||seen(state,animal)||maturedBefore(animal,baseline(state),now))continue;
            Instant candidate=null;
            if(animal.statusAt(now)==Animal.Status.READY)candidate=now;
            else{
                Instant maturity=now.plus(animal.remainingAt(now));
                if(!maturity.isAfter(animal.fedUntil())||(expiry!=null&&expiry.isAfter(now)))candidate=maturity;
                if(needsReplay(snapshot,animal,expiry,now))candidate=now;
            }
            next=earlier(next,candidate);
        }
        return next;
    }

    private Animal animal(GameSnapshot.AnimalSnapshot saved){
        var type=catalog.stream().filter(t->t.id().equals(saved.typeId())).findFirst().orElseThrow();
        return new Animal(type,saved.origin(),Instant.parse(saved.acquiredAt()),Instant.parse(saved.growthAnchorAt()),
                saved.grownAtAnchorMillis(),Instant.parse(saved.fedUntil()));
    }
    private static boolean eligible(Animal animal){return animal.type().kind()!=AnimalType.Kind.PROTECTOR;}
    private static boolean enabled(JsonObject state){return state!=null&&state.has("enabled")&&state.get("enabled").getAsBoolean();}
    private static Instant baseline(JsonObject state){return Instant.parse(state.get("baseline").getAsString());}
    private static boolean seen(JsonObject state,Animal animal){
        if(state==null||!state.has("observed"))return false;
        var observed=state.getAsJsonObject("observed");
        return observed.has(animal.type().id())&&animal.acquiredAt().toString().equals(observed.get(animal.type().id()).getAsString());
    }
    private static boolean maturedBefore(Animal animal,Instant baseline,Instant now){
        return animal.statusAt(now)==Animal.Status.READY
                &&!animal.growthAnchorAt().plusMillis(animal.type().requiredMillis()-animal.grownAtAnchorMillis()).isAfter(baseline);
    }
    private static boolean needsReplay(GameSnapshot snapshot,Animal animal,Instant expiry,Instant now){
        if(expiry==null)return false;
        Instant cursor=Instant.parse(snapshot.automations().cursor());
        if(animal.acquiredAt().isAfter(cursor))cursor=animal.acquiredAt();
        return cursor.isBefore(now)&&cursor.isBefore(expiry);
    }
    private static Instant expiry(GameSnapshot snapshot){
        if(snapshot.automations()==null||snapshot.vipExpiresAt()==null)return null;
        String expiry=snapshot.automations().expiresAt().get("animals");
        return expiry==null?null:earlier(Instant.parse(expiry),Instant.parse(snapshot.vipExpiresAt()));
    }
    private static Instant earlier(Instant a,Instant b){return a==null?b:b==null?a:a.isBefore(b)?a:b;}
}
