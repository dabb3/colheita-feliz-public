package br.com.colheitafeliz.server;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Fundos de fazenda: o padrão é grátis; cada pack pago (dia, tarde e noite) custa 1.000.000 de ouro. */
final class Backgrounds {
    static final String DEFAULT = "default";
    static final BigDecimal PRICE = new BigDecimal("1000000");
    /** Lista fechada; o cliente guarda os arquivos de cada id em background-catalog.ts. Natal fica de fora. */
    static final List<String> PAID = List.of("inverno", "outono", "savana", "cerejeiras", "floresta_encantada", "tropical",
            "mundo_dos_doces", "planeta_alienigena", "terra_vulcanica", "jardim_oriental");

    /** Packs comprados e o ativo; contas sem registro usam o padrão. */
    record State(Set<String> owned, String active) {
        State {
            owned = Set.copyOf(owned);
            if (!owned.stream().allMatch(PAID::contains)) throw new IllegalArgumentException("Unknown background");
            if (!active.equals(DEFAULT) && !owned.contains(active)) throw new IllegalArgumentException("Active background not owned");
        }
        static State initial() { return new State(Set.of(), DEFAULT); }
        State buy(String id) {
            var next = new LinkedHashSet<>(owned);
            next.add(id);
            return new State(next, id);
        }
        State equip(String id) { return new State(owned, id); }
        List<String> ownedIds() {
            var ids = new java.util.ArrayList<String>();
            ids.add(DEFAULT);
            for (String id : PAID) if (owned.contains(id)) ids.add(id);
            return ids;
        }
    }

    private Backgrounds() {}

    static boolean known(String id) { return DEFAULT.equals(id) || PAID.contains(id); }
}
