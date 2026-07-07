package dev.davimf.basebot.modules.base.setup;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class QuickLogSetupTest {

    @Test
    void channelNameStripsLogPrefix() {
        assertEquals("📂・mensagens", QuickLogSetup.channelName("log-mensagens"));
        assertEquals("📂・comandos", QuickLogSetup.channelName("log-comandos"));
    }

    @Test
    void categoryNameIsLowercased() {
        assertEquals("logs base", QuickLogSetup.categoryName("Base"));
        assertEquals("logs vendas", QuickLogSetup.categoryName("Vendas"));
    }

    @Test
    void standardChannelNameCanBeRecognizedForReuse() {
        String standard = QuickLogSetup.channelName("log-mensagens");
        assertTrue(QuickLogSetup.hasStandardName("log-mensagens", standard));
        assertTrue(QuickLogSetup.hasStandardName("log-mensagens", standard.toUpperCase()));
        assertFalse(QuickLogSetup.hasStandardName("log-comandos", standard));
    }

    @Test
    void reconciliationKeepsConfiguredStandardChannelAndDeletesOtherCopies() {
        String name = QuickLogSetup.channelName("log-mensagens");
        var result = QuickLogSetup.reconcile("log-mensagens", "configured", List.of(
                new QuickLogSetup.ChannelRef("duplicate", name),
                new QuickLogSetup.ChannelRef("configured", name)));

        assertEquals("configured", result.logChannelId());
        assertFalse(result.updateConfig());
        assertEquals(List.of("duplicate"), result.duplicateIds());
    }

    @Test
    void reconciliationUsesFirstStandardChannelWhenConfigIsMissing() {
        String name = QuickLogSetup.channelName("log-mensagens");
        var result = QuickLogSetup.reconcile("log-mensagens", null, List.of(
                new QuickLogSetup.ChannelRef("first", name),
                new QuickLogSetup.ChannelRef("second", name)));

        assertEquals("first", result.logChannelId());
        assertTrue(result.updateConfig());
        assertEquals(List.of("second"), result.duplicateIds());
    }

    @Test
    void reconciliationKeepsDifferentConfiguredChannelAndOneStandardCopy() {
        String name = QuickLogSetup.channelName("log-mensagens");
        var result = QuickLogSetup.reconcile("log-mensagens", "custom", List.of(
                new QuickLogSetup.ChannelRef("custom", "auditoria"),
                new QuickLogSetup.ChannelRef("first", name),
                new QuickLogSetup.ChannelRef("second", name)));

        assertEquals("custom", result.logChannelId());
        assertFalse(result.updateConfig());
        assertEquals(List.of("second"), result.duplicateIds());
    }

    @Test
    void salesMapsToVendas() {
        assertEquals("Vendas", QuickLogSetup.logModuleFor("Sales"));
        assertEquals("Base", QuickLogSetup.logModuleFor("Base"));
    }

    @Test
    void typesForActiveFiltersByMappedModule() {
        // Bot só com Base + Sales -> traz tipos de Base e de Vendas, nada de Tickets/Facs.
        List<SetupLogTypes.LogType> types = QuickLogSetup.typesForActive(Set.of("Base", "Sales"));
        assertTrue(types.stream().allMatch(t -> t.module().equals("Base") || t.module().equals("Vendas")));
        assertTrue(types.stream().anyMatch(t -> t.module().equals("Base")));
        assertTrue(types.stream().anyMatch(t -> t.module().equals("Vendas")));
        assertFalse(types.stream().anyMatch(t -> t.module().equals("Facs")));
    }
}
