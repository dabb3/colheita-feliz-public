package br.com.colheitafeliz.server;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CleanNameTest {
    @Test void bidiControlsAreRemoved() {
        assertEquals("Anagnam", OnlineGameService.cleanName("Ana‮gnam"));
        assertEquals("ab", OnlineGameService.cleanName("a؜‎‏‪‫‬‭⁦⁧⁨⁩b"));
    }
    @Test void onlyControlsBecomeDefault() {
        assertEquals("Fazendeiro", OnlineGameService.cleanName("⁦⁩"));
        assertEquals("Fazendeiro", OnlineGameService.cleanName(null));
        assertEquals("Fazendeiro", OnlineGameService.cleanName("   "));
    }
    @Test void zwjEmojiStaysIntact() {
        String family = "👨‍👩‍👧 Ana";
        assertEquals(family, OnlineGameService.cleanName(family));
    }
    @Test void cutDoesNotSplitSurrogatePair() {
        String cut = OnlineGameService.cleanName("a".repeat(39) + "😀");
        assertEquals(39, cut.length());
        assertFalse(Character.isSurrogate(cut.charAt(cut.length() - 1)));
        assertEquals("😀", OnlineGameService.cleanName("a".repeat(38) + "😀").substring(38));
    }
    @Test void longNamesAreCutAt40() {
        assertEquals(40, OnlineGameService.cleanName("a".repeat(50)).length());
    }
    @Test void asciiControlsAreRemoved() {
        assertEquals("Bob", OnlineGameService.cleanName("\u0007Bob"));
    }
}
