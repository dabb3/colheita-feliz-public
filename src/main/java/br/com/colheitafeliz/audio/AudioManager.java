package br.com.colheitafeliz.audio;

import br.com.colheitafeliz.game.GameState;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import java.util.EnumMap;

public final class AudioManager implements AutoCloseable {
    public enum Cue { PLOW, PLANT, WATER, HARVEST, BUY, SELL, WEED, LEVEL_UP }

    private final GameState state;
    private final EnumMap<Cue, Sound> sounds = new EnumMap<>(Cue.class);
    private Music music;

    public AudioManager(GameState state) {
        this.state = state;
        try {
            music = Gdx.audio.newMusic(Gdx.files.internal("sounds/farm_loop.mp3"));
            music.setLooping(true);
            sounds.put(Cue.PLOW, load("click_button.wav"));
            sounds.put(Cue.PLANT, load("plant.wav"));
            sounds.put(Cue.WATER, load("water.wav"));
            sounds.put(Cue.HARVEST, load("harvest.wav"));
            sounds.put(Cue.BUY, load("buy.wav"));
            sounds.put(Cue.SELL, load("sell.wav"));
            sounds.put(Cue.WEED, load("weed.wav"));
            sounds.put(Cue.LEVEL_UP, load("levelup.wav"));
            applySettings();
        } catch (RuntimeException e) {
            Gdx.app.error("audio", "Audio unavailable; game continues silently", e);
            close();
        }
    }

    private Sound load(String name) { return Gdx.audio.newSound(Gdx.files.internal("sounds/" + name)); }

    public void applySettings() {
        if (music == null) return;
        music.setVolume(state.musicEnabled() ? .30f : 0f);
        if (!music.isPlaying()) music.play();
    }

    public void play(Cue cue) {
        Sound sound = sounds.get(cue);
        if (state.effectsEnabled() && sound != null) sound.play(.65f);
    }

    @Override public void close() {
        for (Sound sound : sounds.values()) sound.dispose();
        sounds.clear();
        if (music != null) { music.dispose(); music = null; }
    }
}
