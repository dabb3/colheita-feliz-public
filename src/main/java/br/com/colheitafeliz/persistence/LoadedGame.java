package br.com.colheitafeliz.persistence;

import br.com.colheitafeliz.game.GameSnapshot;
import java.time.Duration;
import java.time.Instant;

public record LoadedGame(GameSnapshot snapshot, Instant savedAtReal, Instant gameTime) {
    public Instant resumedGameTime(Instant realNow) {
        Duration offline = Duration.between(savedAtReal, realNow);
        return gameTime.plus(offline.isNegative() ? Duration.ZERO : offline);
    }
}
