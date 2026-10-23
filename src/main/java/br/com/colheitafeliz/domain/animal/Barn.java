package br.com.colheitafeliz.domain.animal;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Os animais do jogador, na ordem em que foram comprados; cada tipo tem seu limite no cercado. */
public final class Barn {
    private final List<Animal> animals = new ArrayList<>();

    public List<Animal> animals() { return List.copyOf(animals); }
    public Animal get(int index) { return animals.get(index); }
    public int size() { return animals.size(); }

    public int count(AnimalType type) {
        int count = 0;
        for (Animal animal : animals) if (animal.type().id().equals(type.id())) count++;
        return count;
    }

    public boolean full(AnimalType type) { return count(type) >= type.max(); }

    public void add(AnimalType type) {
        if (full(type)) throw new IllegalStateException("No room for another " + type.id());
        animals.add(Animal.hungry(type));
    }

    public void feed(int index, Instant now) { animals.set(index, animals.get(index).feed(now)); }

    public void collect(int index, Instant now) { animals.set(index, animals.get(index).collect(now)); }

    public void restore(List<Animal> saved) {
        animals.clear();
        for (Animal animal : saved) {
            if (full(animal.type())) throw new IllegalArgumentException("Too many animals of " + animal.type().id());
            animals.add(animal);
        }
    }
}
