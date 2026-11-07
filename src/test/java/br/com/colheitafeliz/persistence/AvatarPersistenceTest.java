package br.com.colheitafeliz.persistence;

import br.com.colheitafeliz.game.*;
import com.google.gson.JsonParser;
import java.nio.file.*;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class AvatarPersistenceTest {
    @TempDir Path directory;
    private final Clock clock=Clock.fixed(Instant.parse("2026-11-07T12:00:00Z"),ZoneOffset.UTC);
    private GameState game(){return new GameState(clock::instant,clock,CropCatalogLoader.load(),bound->99);}
    @ParameterizedTest @ValueSource(strings={"classic","male-2","male-3","female-1","female-2","female-3","google"})
    void everyApprovedAvatarRoundTripsWithoutLosingExplicitChoice(String id) throws Exception {
        var game=game();var preference=new PlayerAvatar(id,id.equals("google")?"https://lh3.googleusercontent.com/a/photo=s96-c":null);
        game.selectAvatar(preference,false);var repo=new JsonSaveRepository(directory.resolve("avatar.json"));repo.save(game.snapshot(),clock.instant(),clock.instant());
        var restored=GameState.restore(clock::instant,clock,CropCatalogLoader.load(),repo.load().orElseThrow().snapshot(),bound->99);
        assertEquals(preference,restored.avatar());restored.selectAvatar(new PlayerAvatar("female-2",null),true);assertEquals(preference,restored.avatar());
    }
    @Test void historicalSnapshotWithoutAvatarCanImportExactlyOnce() throws Exception {
        var file=directory.resolve("old.json");var repo=new JsonSaveRepository(file);repo.save(game().snapshot(),clock.instant(),clock.instant());
        var json=JsonParser.parseString(Files.readString(file)).getAsJsonObject();json.addProperty("ruleset",GameSave.CHRISTMAS_RULESET);json.getAsJsonObject("snapshot").remove("avatar");Files.writeString(file,json.toString());
        var restored=GameState.restore(clock::instant,clock,CropCatalogLoader.load(),repo.load().orElseThrow().snapshot(),bound->99);assertNull(restored.avatar());
        restored.selectAvatar(new PlayerAvatar("male-2",null),true);restored.selectAvatar(new PlayerAvatar("female-3",null),true);assertEquals("male-2",restored.avatar().id());
    }
    @ParameterizedTest @ValueSource(strings={"numberId","booleanPhoto","missingId","unknownId","arbitraryPhoto","extraField"})
    void corruptAvatarCannotLoadOrOverwriteSave(String corruption) throws Exception {
        var game=game();game.selectAvatar(new PlayerAvatar("google","https://lh3.googleusercontent.com/a/photo"),false);
        var file=directory.resolve("bad.json");var repo=new JsonSaveRepository(file);repo.save(game.snapshot(),clock.instant(),clock.instant());
        var json=JsonParser.parseString(Files.readString(file)).getAsJsonObject();var avatar=json.getAsJsonObject("snapshot").getAsJsonObject("avatar");
        switch(corruption){
            case "numberId"->avatar.addProperty("id",1);
            case "booleanPhoto"->avatar.addProperty("photoUrl",true);
            case "missingId"->avatar.remove("id");
            case "unknownId"->avatar.addProperty("id","external");
            case "arbitraryPhoto"->avatar.addProperty("photoUrl","https://evil.example/image.png");
            case "extraField"->avatar.addProperty("token","invalid-field");
            default->throw new AssertionError(corruption);
        }
        Files.writeString(file,json.toString());var original=Files.readAllBytes(file);
        assertThrows(java.io.IOException.class,repo::load);assertThrows(java.io.IOException.class,()->repo.save(game.snapshot(),clock.instant(),clock.instant()));assertArrayEquals(original,Files.readAllBytes(file));
    }
}
