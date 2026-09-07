package dev.davimf.basebot.modules.tickets;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TicketChannelNameTest {

    @Test
    void slugLowercasesAndHyphenates() {
        assertEquals("suporte-geral", TicketChannelName.slug("Suporte Geral"));
        assertEquals("vendas", TicketChannelName.slug("  Vendas!  "));
        assertEquals("ticket", TicketChannelName.slug("***"));   // falls back when empty
        assertEquals("ticket", TicketChannelName.slug(null));
    }

    @Test
    void openedUsesOpenLockAndCreator() {
        assertEquals("🔓・davi", TicketChannelName.opened("Davi"));
    }

    @Test
    void assumedKeepsCategoryEmojiOrFallsBackToClosedLock() {
        assertEquals("🛠️・mod", TicketChannelName.assumed("🛠️", "Mod"));
        assertEquals("🔒・mod", TicketChannelName.assumed(null, "Mod"));
        assertEquals("🔒・mod", TicketChannelName.assumed("", "Mod"));
    }

    @Test
    void renamedKeepsCategoryEmojiPrefix() {
        assertEquals("🛠️・urgente", TicketChannelName.renamed("🛠️", "Urgente"));
        assertEquals("🔒・urgente", TicketChannelName.renamed(null, "Urgente"));
    }

    @Test
    void clampsLength() {
        assertTrue(TicketChannelName.renamed("", "y".repeat(120)).length() <= 90);
    }
}
