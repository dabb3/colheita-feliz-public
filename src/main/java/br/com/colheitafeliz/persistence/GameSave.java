package br.com.colheitafeliz.persistence;

import br.com.colheitafeliz.game.GameSnapshot;

public record GameSave(int version, String ruleset, String savedAtReal, String gameTime, GameSnapshot snapshot) {
    /** Revisões e proteção social local; v19 conserva a mesma economia e pode ser atualizado. */
    public static final int VERSION = 20;
    public static final String RULESET = "game-rules-2026-11-usability-v1";
    public static final String CHRISTMAS_RULESET = "economy-2026-11-mini-bis-v1";
    public static final String ANIMALS_RULESET = "economy-2026-11-animals-v2";
    public static final String PREVIOUS_RULESET = "economy-2026-11-plants-v1";
    public static final String LEGACY_RULESET = "economy-2026-10";
    public static boolean supports(String ruleset) {
        return RULESET.equals(ruleset) || CHRISTMAS_RULESET.equals(ruleset) || ANIMALS_RULESET.equals(ruleset) || PREVIOUS_RULESET.equals(ruleset) || LEGACY_RULESET.equals(ruleset);
    }
    public static final String DEFAULT_PATH = "save/game-rules-v4.json";
}
