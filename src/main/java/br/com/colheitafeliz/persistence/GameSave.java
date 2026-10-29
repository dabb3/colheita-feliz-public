package br.com.colheitafeliz.persistence;

import br.com.colheitafeliz.game.GameSnapshot;

public record GameSave(int version, String ruleset, String savedAtReal, String gameTime, GameSnapshot snapshot) {
    /** Progressão quadrática e tarefas fixas; formatos anteriores permanecem em disco. */
    public static final int VERSION = 19;
    public static final String RULESET = "economy-2026-10";
    public static final String DEFAULT_PATH = "save/game-rules-v4.json";
}
