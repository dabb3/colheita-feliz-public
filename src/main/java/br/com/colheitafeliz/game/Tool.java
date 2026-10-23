package br.com.colheitafeliz.game;

/** WEEDER é o herbicida (remove ervas daninhas); PESTICIDE remove insetos. A ordem na toolbar é {@link #BAR}. */
public enum Tool {
    HAND, HOE, WATER, SEED, WEEDER, PESTICIDE, FERTILIZER;

    /** Ordem de exibição e atalhos 1–7: mão, enxada, regador, sementes, pesticida, herbicida, adubo. */
    public static final java.util.List<Tool> BAR = java.util.List.of(HAND, HOE, WATER, SEED, PESTICIDE, WEEDER, FERTILIZER);

    public String label() {
        return switch (this) {
            case HAND -> "Mão";
            case HOE -> "Enxada";
            case WATER -> "Regador";
            case SEED -> "Sementes";
            case WEEDER -> "Herbicida";
            case PESTICIDE -> "Pesticida";
            case FERTILIZER -> "Adubo";
        };
    }
}
