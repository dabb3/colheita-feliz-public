package br.com.colheitafeliz.persistence;

import br.com.colheitafeliz.game.*;
import br.com.colheitafeliz.domain.notification.GameNotification;
import br.com.colheitafeliz.testsupport.ManualClock;
import com.google.gson.JsonParser;
import java.nio.file.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class NotificationSaveTest {
    @TempDir Path dir;
    @Test void readHistoryAndCursorsSurviveSaveAndMissingHistoricalStateStartsEmpty() throws Exception {
        var clock=new ManualClock();var g=new GameState(clock::instant,clock,CropCatalogLoader.load(),bound->99);
        g.notifications().record("vip","once",GameNotification.Type.VIP_EXPIRED,"VIP expirou",clock.instant(),Map.of());
        g.markAllNotificationsRead();var repo=new JsonSaveRepository(dir.resolve("save.json"));repo.save(g.snapshot(),clock.instant(),clock.instant());
        var loaded=repo.load().orElseThrow();assertEquals(g.notifications().snapshot(),loaded.snapshot().notifications());
        var restored=GameState.restore(clock::instant,clock,CropCatalogLoader.load(),loaded.snapshot(),bound->99);
        assertFalse(restored.notifications().record("vip","once",GameNotification.Type.VIP_EXPIRED,"VIP expirou",clock.instant(),Map.of()));
        assertEquals(0,restored.notifications().unread());
        var json=JsonParser.parseString(Files.readString(dir.resolve("save.json"))).getAsJsonObject();json.getAsJsonObject("snapshot").remove("notifications");
        Files.writeString(dir.resolve("save.json"),json.toString());assertTrue(repo.load().orElseThrow().snapshot().notifications().entries().isEmpty());
    }
    @ParameterizedTest
    @ValueSource(strings={"version","read","date","duplicate","tooMany","metadata"})
    void corruptInboxCannotBeLoadedOrOverwriteOriginal(String kind) throws Exception {
        var clock=new ManualClock();var g=new GameState(clock::instant,clock,CropCatalogLoader.load(),bound->99);
        g.notifications().record("vip","once",GameNotification.Type.VIP_EXPIRED,"VIP expirou",clock.instant(),Map.of());
        var path=dir.resolve("bad.json");var repo=new JsonSaveRepository(path);repo.save(g.snapshot(),clock.instant(),clock.instant());
        var json=JsonParser.parseString(Files.readString(path)).getAsJsonObject();var inbox=json.getAsJsonObject("snapshot").getAsJsonObject("notifications");var entries=inbox.getAsJsonArray("entries");var note=entries.get(0).getAsJsonObject();
        switch(kind){
            case "version" -> inbox.addProperty("version",new java.math.BigDecimal("1.5"));
            case "read" -> note.addProperty("read","false");
            case "date" -> note.addProperty("createdAt","invalid");
            case "duplicate" -> entries.add(note.deepCopy());
            case "tooMany" -> {for(int i=0;i<100;i++){var next=note.deepCopy();next.addProperty("id",java.util.UUID.randomUUID().toString());entries.add(next);}}
            case "metadata" -> note.getAsJsonObject("metadata").addProperty("quantity",1);
            default -> throw new AssertionError();
        }
        Files.writeString(path,json.toString());var bytes=Files.readAllBytes(path);
        assertThrows(java.io.IOException.class,repo::load);assertThrows(java.io.IOException.class,()->repo.save(g.snapshot(),clock.instant(),clock.instant()));
        assertArrayEquals(bytes,Files.readAllBytes(path));
    }
}
