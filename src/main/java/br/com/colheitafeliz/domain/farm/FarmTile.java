package br.com.colheitafeliz.domain.farm;

import br.com.colheitafeliz.domain.crop.Crop;
import br.com.colheitafeliz.domain.crop.CropType;
import java.time.Instant;
import br.com.colheitafeliz.domain.inventory.ItemLot;
import java.util.Objects;

public final class FarmTile {
    private TileState state = TileState.GRASS;
    private Crop crop;
    private PestState pest = PestState.NONE;
    private Instant nextPestCheckAt;
    /** Minhocas (0 = nenhuma, até {@link #MAX_INSECTS}); pausam o crescimento como as ervas, sem reduzir a colheita. */
    private int insects;
    private Instant nextInsectCheckAt;
    /** Cultura da planta morta que fica depois da colheita, até a pá retirá-la. */
    private CropType withered;
    /** Fertilizante já usado nesta temporada; vale uma vez por temporada. */
    private boolean fertilized;
    private long harvestRevision;
    public static final int MAX_INSECTS = 3;

    public TileState state() { return state; }
    public int insects() { return insects; }
    public Instant nextInsectCheckAt() { return nextInsectCheckAt; }
    public Crop crop() { return crop; }
    public PestState pest() { return pest; }
    public Instant nextPestCheckAt() { return nextPestCheckAt; }
    public CropType withered() { return withered; }
    public boolean fertilized() { return fertilized; }
    public long harvestRevision() { return harvestRevision; }

    public void plow() {
        require(TileState.GRASS);
        state = TileState.PLOWED;
    }

    /** A pá retira a planta morta e deixa a terra arada para o próximo plantio. */
    public void clearDead() {
        require(TileState.DEAD);
        withered = null;
        state = TileState.PLOWED;
    }

    public void plant(CropType type, Instant now) {
        var price = type.seedPrices().entrySet().iterator().next();
        plant(type, now, new ItemLot(type.id(), price.getKey(), price.getValue(), 1, type.vipOnly()));
    }

    public void plant(CropType type, Instant now, ItemLot origin) {
        require(TileState.PLOWED);
        long nextRevision = Math.incrementExact(harvestRevision);
        startSeason(new Crop(type, now, origin));
        harvestRevision = nextRevision;
    }

    /** Planta nova ou próxima temporada: estágio 1, sem rega, pragas, insetos nem fertilizante. */
    private void startSeason(Crop next) {
        crop = next;
        pest = PestState.NONE;
        nextPestCheckAt = null;
        insects = 0;
        nextInsectCheckAt = null;
        fertilized = false;
        state = TileState.GROWING;
    }

    public void water(Instant now) {
        require(TileState.GROWING);
        crop = crop.water(now);
        if (nextPestCheckAt == null) nextPestCheckAt = now.plus(pestCheckInterval());
        if (nextInsectCheckAt == null) nextInsectCheckAt = now.plus(pestCheckInterval());
    }

    public void checkPest(Instant now, boolean infected) {
        require(TileState.GROWING);
        if (pest != PestState.NONE || crop == null || !crop.watered())
            throw new IllegalStateException("Pest check not allowed");
        nextPestCheckAt = now.plus(pestCheckInterval());
        if (infected) {
            crop = crop.pause(now);
            pest = PestState.WEEDS;
        }
    }

    public void removePest(Instant now) {
        if (pest != PestState.WEEDS) throw new IllegalStateException("No weeds");
        pest = PestState.NONE;
        if (insects == 0) crop = crop.resume(now);
        nextPestCheckAt = now.plus(pestCheckInterval());
    }

    /**
     * Fertilizante: reduz o tempo restante da temporada atual em {@code reduction}; o que passar do restante se perde.
     * Uma vez por temporada, só em planta regada, úmida, sem pragas e ainda não pronta.
     */
    public void fertilize(Instant now, java.time.Duration reduction) {
        require(TileState.GROWING);
        if (fertilized || crop.dryAt(now) || crop.paused() || crop.matureAt(now) || reduction.isNegative())
            throw new IllegalStateException("Cannot fertilize");
        crop = crop.advance(now, reduction.toMillis());
        fertilized = true;
    }

    /** Sorteio de insetos: se {@code infected}, aparece a primeira minhoca; o próximo sorteio é reagendado. */
    public void checkInsects(Instant now, boolean infected) {
        require(TileState.GROWING);
        if (insects != 0 || crop == null || !crop.watered())
            throw new IllegalStateException("Insect check not allowed");
        nextInsectCheckAt = now.plus(pestCheckInterval());
        if (infected) {
            crop = crop.pause(now);
            insects = 1;
        }
    }

    /** Insetos ignorados se multiplicam a cada intervalo, até {@link #MAX_INSECTS}. */
    public void worsenInsects(Instant now) {
        require(TileState.GROWING);
        if (insects == 0) throw new IllegalStateException("No insects");
        insects = Math.min(MAX_INSECTS, insects + 1);
        nextInsectCheckAt = now.plus(pestCheckInterval());
    }

    public void removeInsects(Instant now) {
        if (insects == 0) throw new IllegalStateException("No insects");
        insects = 0;
        if (pest == PestState.NONE) crop = crop.resume(now);
        nextInsectCheckAt = now.plus(pestCheckInterval());
    }

    public void refresh(Instant now) {
        Objects.requireNonNull(now);
        if (state == TileState.GROWING && crop.matureAt(now)) state = TileState.READY;
    }

    /**
     * Colhe uma unidade, com a origem da semente. Se restam temporadas, a planta recomeça no estágio 1 da próxima;
     * se era a última, vira planta morta.
     */
    public ItemLot harvest(Instant now) {
        refresh(now);
        require(TileState.READY);
        long nextRevision = Math.incrementExact(harvestRevision);
        Crop done = crop;
        if (done.season() < done.type().seasons()) {
            startSeason(new Crop(done.type(), now, done.origin(), done.season() + 1));
            harvestRevision = nextRevision;
            return done.origin();
        }
        crop = null;
        pest = PestState.NONE;
        nextPestCheckAt = null;
        insects = 0;
        nextInsectCheckAt = null;
        withered = done.type();
        fertilized = false;
        state = TileState.DEAD;
        harvestRevision = nextRevision;
        return done.origin();
    }

    public void restore(TileState state, Crop crop) {
        restore(state, crop, PestState.NONE, null);
    }

    public void restore(TileState state, Crop crop, PestState pest, Instant nextPestCheckAt) {
        restore(state, crop, pest, nextPestCheckAt, 0, null);
    }

    public void restore(TileState state, Crop crop, PestState pest, Instant nextPestCheckAt,
                        int insects, Instant nextInsectCheckAt) {
        restore(state, crop, pest, nextPestCheckAt, insects, nextInsectCheckAt, null, false);
    }

    public void restore(TileState state, Crop crop, PestState pest, Instant nextPestCheckAt,
                        int insects, Instant nextInsectCheckAt, CropType withered, boolean fertilized) {
        restore(state, crop, pest, nextPestCheckAt, insects, nextInsectCheckAt, withered, fertilized, 0);
    }

    public void restore(TileState state, Crop crop, PestState pest, Instant nextPestCheckAt,
                        int insects, Instant nextInsectCheckAt, CropType withered, boolean fertilized, long revision) {
        Objects.requireNonNull(state);
        Objects.requireNonNull(pest);
        boolean needsCrop = state == TileState.GROWING || state == TileState.READY;
        if (revision < 0 || needsCrop != (crop != null) || (state == TileState.READY && !crop.watered())
                || (pest == PestState.WEEDS || insects > 0) != (crop != null && crop.paused())
                || insects < 0 || insects > MAX_INSECTS || (insects > 0 && (crop == null || !crop.watered()))
                || (state == TileState.DEAD) != (withered != null))
            throw new IllegalArgumentException("Invalid tile snapshot");
        this.state = state;
        this.crop = crop;
        this.pest = pest;
        this.nextPestCheckAt = nextPestCheckAt;
        this.insects = insects;
        this.nextInsectCheckAt = nextInsectCheckAt;
        this.withered = withered;
        this.fertilized = fertilized && state == TileState.GROWING;
        this.harvestRevision = revision;
    }

    private void require(TileState expected) {
        if (state != expected) throw new IllegalStateException("Tile is " + state + ", expected " + expected);
    }

    private java.time.Duration pestCheckInterval() {
        return pestCheckInterval(crop);
    }

    /** Intervalo entre sorteios de pragas: um décimo da temporada atual, no mínimo 20 s. */
    public static java.time.Duration pestCheckInterval(Crop crop) {
        return java.time.Duration.ofSeconds(Math.max(20, crop.currentDuration().toSeconds() / 10));
    }
}
