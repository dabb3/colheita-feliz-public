package br.com.colheitafeliz.game;

import java.util.LinkedHashMap;
import java.util.Map;

/** Care performed by automations since the last drain; transient, never part of the save. */
public final class AwayTally {
    private int waterings, pestsRemoved, animalCares;
    private final Map<String,Integer> collected = new LinkedHashMap<>();

    void addWatering() { waterings++; }
    void addPest() { pestsRemoved++; }
    void addAnimalCare() { animalCares++; }
    void addCollected(String productId, int quantity) { collected.merge(productId, quantity, Integer::sum); }

    public int waterings() { return waterings; }
    public int pestsRemoved() { return pestsRemoved; }
    public int animalCares() { return animalCares; }
    public Map<String,Integer> collected() { return Map.copyOf(collected); }
    public boolean isEmpty() { return waterings == 0 && pestsRemoved == 0 && animalCares == 0 && collected.isEmpty(); }
}
