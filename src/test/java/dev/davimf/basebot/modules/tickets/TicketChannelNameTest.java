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
    void ofCombinesEmojiCategoryAndUser() {
        assertEquals("🛠️suporte-davi", TicketChannelName.of("🛠️", "Suporte", "Davi"));
        assertEquals("suporte-davi", TicketChannelName.of(null, "Suporte", "Davi"));
        assertEquals("suporte-davi", TicketChannelName.of("", "Suporte", "Davi"));
    }

    @Test
    void ofClampsLength() {
        String name = TicketChannelName.of("", "x".repeat(80), "y".repeat(80));
        assertTrue(name.length() <= 90);
    }
}
