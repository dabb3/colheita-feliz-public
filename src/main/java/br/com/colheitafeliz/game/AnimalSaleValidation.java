package br.com.colheitafeliz.game;

import br.com.colheitafeliz.domain.animal.AnimalType;
import java.util.List;

/** Terms are checked against their authoritative animal definition, never a client quote. */
public final class AnimalSaleValidation {
    private AnimalSaleValidation() {}
    public static void validate(GameSnapshot snapshot, List<AnimalType> types) {
        for (var seed : snapshot.seeds()) if (seed.animalSale() != null)
            throw new IllegalArgumentException("Seed cannot have animal terms");
        for (var tile : snapshot.tiles()) if (tile.origin() != null && tile.origin().animalSale() != null)
            throw new IllegalArgumentException("Plant cannot have animal terms");
        for (var product : snapshot.produce()) {
            var type = types.stream().filter(t -> t.kind() == AnimalType.Kind.PRODUCER
                    && t.productId().equals(product.itemId())).findFirst();
            if (type.isPresent()) type.orElseThrow().validateSaleTerms(product, true);
            else if (product.animalSale() != null) throw new IllegalArgumentException("Unknown animal product terms");
        }
        for (var animal : snapshot.animals()) {
            if (animal.origin() == null || animal.origin().quantity() != 1
                    || !animal.typeId().equals(animal.origin().itemId()) || animal.origin().vipOnly())
                throw new IllegalArgumentException("Invalid animal acquisition");
            types.stream().filter(t -> t.id().equals(animal.typeId()))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown animal"))
                    .validateSaleTerms(animal.origin(), false);
        }
    }
}
