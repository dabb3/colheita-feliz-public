package br.com.colheitafeliz.game;

/**
 * HAND é a luvinha (colhe), HOE é a pá, WEEDER é o herbicida (remove ervas daninhas) e PESTICIDE remove insetos.
 * CURSOR não age nos canteiros. A ordem na toolbar é {@link #BAR}.
 */
public enum Tool {
    CURSOR, HAND, HOE, WATER, SEED, WEEDER, PESTICIDE, FERTILIZER;

    /** Ordem de exibição e atalhos 1–8: cursor, pá, sementes, regador, herbicida, pesticida, luvinha, adubo. */
    public static final java.util.List<Tool> BAR = java.util.List.of(CURSOR, HOE, SEED, WATER, WEEDER, PESTICIDE, HAND, FERTILIZER);

    public String label() {
        return switch (this) {
            case CURSOR -> "Cursor";
            case HAND -> "Luvinha";
            case HOE -> "Pá";
            case WATER -> "Regador";
            case SEED -> "Sementes";
            case WEEDER -> "Herbicida";
            case PESTICIDE -> "Pesticida";
            case FERTILIZER -> "Adubo";
        };
    }
}
