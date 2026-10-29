package br.com.colheitafeliz.persistence;

import br.com.colheitafeliz.game.GameSnapshot;

public record GameSave(int version, String ruleset, String savedAtReal, String gameTime, GameSnapshot snapshot) {
    /** v18 guarda origem, alimentação e progresso individual; leitores anteriores recusam sem sobrescrever. */
    public static final int VERSION = 18;
    /** Formatos anteriores só carregam sem animais nem ração legada; v16 também exige adubo antigo vazio. */
    public static final int STAGE_2_VERSION = 16;
    public static final int STAGE_3_VERSION = 17;
    public static final String RULESET = "economy-2026-10";
    public static final String DEFAULT_PATH = "save/game-rules-v3.json";
}
