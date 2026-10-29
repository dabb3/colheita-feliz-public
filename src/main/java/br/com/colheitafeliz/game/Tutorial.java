package br.com.colheitafeliz.game;
import java.util.List;
/** Orientação em sequência; as ações mantêm apenas suas recompensas normais. */
public final class Tutorial {
    public record Step(String action, String title, String hint) {}
    public static final List<Step> STEPS = List.of(
            new Step("buy", "Compre uma semente", "Abra a Loja (B) e compre um nabo"),
            new Step("plow", "Are a terra", "Use a pá (2) na grama"),
            new Step("plant", "Plante a semente", "Escolha sementes (3) e clique na terra arada"),
            new Step("water", "Regue a plantação", "Use o regador (4) na terra seca"),
            new Step("harvest", "Espere crescer e colha", "Aguarde a maturação; depois use a luvinha (7)"),
            new Step("sell", "Venda a colheita", "Abra o Depósito (V) e clique em Vender tudo")
    );
    private int step;
    public Tutorial(boolean done) { step = done ? STEPS.size() : 0; }
    public boolean active() { return step < STEPS.size(); }
    public int stepIndex() { return step; }
    public Step current() { return active() ? STEPS.get(step) : null; }
    public Step record(String action) {
        Step current = current();
        if (current == null || !current.action().equals(action)) return null;
        step++;
        return current;
    }
    public void restore(Integer savedStep) {
        if (savedStep == null || savedStep < 0 || savedStep > STEPS.size())
            throw new IllegalArgumentException("Invalid tutorial step");
        step = savedStep;
    }
}
