package br.com.colheitafeliz.game;

import java.util.List;

/**
 * Primeiros passos de uma fazenda nova: tarefas em sequência, uma ação cada, com moedas e XP.
 * Enquanto não termina, as missões diárias não contam.
 */
public final class Tutorial {
    public record Step(String action, String title, String hint, int coins, int xp) {}

    public static final List<Step> STEPS = List.of(
            new Step("harvest", "Colha um nabo pronto", "Escolha a luvinha (7) e clique num nabo", 10, 5),
            new Step("sell", "Venda a colheita", "Abra o Depósito (V) e clique em Vender tudo", 10, 5),
            new Step("buy", "Compre uma semente", "Abra a Loja (B) e compre qualquer semente", 10, 5),
            new Step("plow", "Are a terra", "Use a pá (2) na grama ou na planta morta", 10, 5),
            new Step("plant", "Plante a semente", "Escolha o saco de sementes (3) e clique na terra arada", 10, 5),
            new Step("water", "Regue a plantação", "Use o regador (4) na terra seca", 20, 10)
    );

    private int step;

    /** Novas fazendas começam do primeiro passo; as demais já o concluíram. */
    public Tutorial(boolean done) { step = done ? STEPS.size() : 0; }

    public boolean active() { return step < STEPS.size(); }
    public int stepIndex() { return step; }
    public Step current() { return active() ? STEPS.get(step) : null; }

    /** Avança se a ação for a do passo atual e devolve o passo concluído, ou {@code null}. */
    public Step record(String action) {
        Step current = current();
        if (current == null || !current.action().equals(action)) return null;
        step++;
        return current;
    }

    public void restore(Integer savedStep) {
        if (savedStep == null) { step = STEPS.size(); return; }
        if (savedStep < 0 || savedStep > STEPS.size()) throw new IllegalArgumentException("Invalid tutorial step");
        step = savedStep;
    }
}
