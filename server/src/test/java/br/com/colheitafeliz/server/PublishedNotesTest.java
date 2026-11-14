package br.com.colheitafeliz.server;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PublishedNotesTest {
    @Test void onlyMatchingPublicationMetadataEnablesTheRealPullRequestManifest(){
        var active=PublishedNotes.fromEnvironment(Map.of("PUBLISHED_UPDATE_NOTES_VERSION","ui-mobile-v1","PUBLISHED_UPDATE_NOTES_PR","84"));
        assertNotNull(active);assertEquals(84,active.prNumber());assertEquals(3,active.items().size());
        assertNull(PublishedNotes.fromEnvironment(Map.of("PUBLISHED_UPDATE_NOTES_VERSION","ui-mobile-v1","PUBLISHED_UPDATE_NOTES_PR","83")));
        assertNull(PublishedNotes.fromEnvironment(Map.of()));
    }
    @Test void unpublishedNotesAreDisabledByDefaultAndMisconfiguredActivationIsRejected(){
        assertNull(PublishedNotes.fromEnvironment(Map.of()));
        assertNull(PublishedNotes.fromEnvironment(Map.of("PUBLISHED_UPDATE_NOTES_VERSION","vip-benefits-v1")));
        assertNull(PublishedNotes.fromEnvironment(Map.of("PUBLISHED_UPDATE_NOTES_VERSION","unknown","PUBLISHED_UPDATE_NOTES_PR","1")));
        assertThrows(IllegalArgumentException.class,()->new PublishedNotes("draft",0,List.of("Texto")));
    }
    @Test void headingDateUsesSaoPauloInsteadOfUtc(){
        var note=new PublishedNotes("test-v1",123,List.of("Novidades"));
        assertEquals("2026-11-07",note.view(Instant.parse("2026-11-08T02:59:59Z")).get("date").getAsString());
        var next=note.view(Instant.parse("2026-11-08T03:00:00Z"));
        assertEquals("2026-11-08",next.get("date").getAsString());assertEquals(123,next.get("prNumber").getAsInt());
    }
}
