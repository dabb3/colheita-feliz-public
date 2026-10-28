package br.com.colheitafeliz.persistence;

import br.com.colheitafeliz.game.GameSnapshot;

public record GameSave(int version, String ruleset, String savedAtReal, String gameTime, GameSnapshot snapshot) {
    /** v17 guarda o estoque de fertilizantes por tipo; o leitor da etapa 2 só aceita v16 e recusa sem sobrescrever. */
    public static final int VERSION = 17;
    /** Único formato anterior carregado: o save v16 da etapa 2, sem fertilizantes por tipo. */
    public static final int STAGE_2_VERSION = 16;
    public static final String RULESET = "economy-2026-10";
    public static final String DEFAULT_PATH = "save/game-rules-v2.json";
}
