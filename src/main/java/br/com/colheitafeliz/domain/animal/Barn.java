package br.com.colheitafeliz.domain.animal;

import br.com.colheitafeliz.domain.inventory.ItemLot;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Animais na ordem de compra, com limite independente por tipo. */
public final class Barn {
    private final List<Animal> animals = new ArrayList<>();
    public List<Animal> animals() { return List.copyOf(animals); }
    public Animal get(int index) { return animals.get(index); }
    public int size() { return animals.size(); }
    public int count(AnimalType type) { return (int) animals.stream().filter(a -> a.type().id().equals(type.id())).count(); }
    public boolean full(AnimalType type) { return count(type) >= type.max(); }
    public void add(Animal animal) {
        if (full(animal.type())) throw new IllegalStateException("No room for " + animal.type().id());
        animals.add(animal);
    }
    public void feed(int index, Instant now) { animals.set(index, animals.get(index).feed(now)); }
    public Animal remove(int index) { return animals.remove(index); }
    public ItemLot collect(int index, Instant now) {
        Animal animal = get(index);
        if (animal.type().kind() != AnimalType.Kind.PRODUCER || animal.statusAt(now) != Animal.Status.READY)
            throw new IllegalStateException("Nothing to collect");
        remove(index);
        return new ItemLot(animal.type().productId(), animal.origin().currency(), animal.origin().unitCost(), 1, false);
    }
    public int activeProtectors(Instant realNow, Instant simulationNow) {
        return (int) animals.stream().filter(a -> a.activeAt(realNow, simulationNow)).count();
    }
    public boolean removeExpired(Instant realNow) { return animals.removeIf(a -> a.expiredAt(realNow)); }
    public void restore(List<Animal> saved) {
        Barn validated = new Barn();
        for (Animal animal : saved) validated.add(animal);
        animals.clear(); animals.addAll(validated.animals);
    }
}
