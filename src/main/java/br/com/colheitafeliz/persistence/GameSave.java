package br.com.colheitafeliz.persistence;

import br.com.colheitafeliz.game.GameSnapshot;

public record GameSave(int version, String ruleset, String savedAtReal, String gameTime, GameSnapshot snapshot) {
    public static final int VERSION = 16;
    public static final String RULESET = "economy-2026-10";
    public static final String DEFAULT_PATH = "save/game-rules-v2.json";
}
