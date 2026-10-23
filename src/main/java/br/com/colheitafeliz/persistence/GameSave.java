package br.com.colheitafeliz.persistence;

import br.com.colheitafeliz.game.GameSnapshot;

public record GameSave(int version, String savedAtReal, String gameTime, GameSnapshot snapshot) {
    public static final int VERSION = 14;
}
