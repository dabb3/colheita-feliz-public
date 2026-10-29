package br.com.colheitafeliz.game;

import br.com.colheitafeliz.domain.crop.CropCatalog;
import br.com.colheitafeliz.persistence.LoadedGame;
import br.com.colheitafeliz.persistence.SaveRepository;
import br.com.colheitafeliz.domain.farm.RandomSource;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

public final class GameSession {
    private final Clock realClock;
    private final SaveRepository repository;
    private final AdjustableGameClock gameClock;
    private final GameState state;
    private final boolean fresh;

    private GameSession(Clock realClock, SaveRepository repository,
                        AdjustableGameClock gameClock, GameState state, boolean fresh) {
        this.realClock = realClock;
        this.repository = repository;
        this.gameClock = gameClock;
        this.state = state;
        this.fresh = fresh;
    }

    public static GameSession open(Clock realClock, CropCatalog catalog, SaveRepository repository)
            throws IOException {
        return open(realClock, catalog, repository, java.util.concurrent.ThreadLocalRandom.current()::nextInt);
    }

    public static GameSession open(Clock realClock, CropCatalog catalog, SaveRepository repository,
                                   RandomSource random) throws IOException {
        Optional<LoadedGame> loaded = repository.load();
        try {
            Instant initial = loaded.map(save -> save.resumedGameTime(realClock.instant()))
                    .orElseGet(realClock::instant);
            AdjustableGameClock gameClock = new AdjustableGameClock(realClock, initial);
            GameState state = loaded.map(save -> GameState.restore(gameClock, realClock, catalog, save.snapshot(), random))
                    .orElseGet(() -> new GameState(gameClock, realClock, catalog, random));
            return new GameSession(realClock, repository, gameClock, state, loaded.isEmpty());
        } catch (RuntimeException e) {
            throw new IOException("Invalid saved game state", e);
        }
    }

    public GameState state() { return state; }
    /** Nenhum save existia: é a primeira vez que o jogador abre a fazenda. */
    public boolean fresh() { return fresh; }
    public AdjustableGameClock clock() { return gameClock; }

    public void save() throws IOException {
        repository.save(state.snapshot(), gameClock.now(), realClock.instant());
    }

    /** Grava e diz se deu certo; na falha, deixa "Erro ao salvar: ..." como mensagem do jogo. */
    public boolean trySave() {
        try {
            save();
            return true;
        } catch (IOException e) {
            state.message("Erro ao salvar: " + e.getMessage());
            return false;
        }
    }
}
