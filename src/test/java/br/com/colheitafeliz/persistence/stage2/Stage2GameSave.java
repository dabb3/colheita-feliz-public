package br.com.colheitafeliz.persistence.stage2;

import br.com.colheitafeliz.game.GameSnapshot;

/** GameSave da etapa 2 (8dc1fcb): versão 16 e o mesmo ruleset e caminho; usado só no teste cruzado. */
public record Stage2GameSave(int version, String ruleset, String savedAtReal, String gameTime, GameSnapshot snapshot) {
    public static final int VERSION = 16;
    public static final String RULESET = "economy-2026-10";
}
