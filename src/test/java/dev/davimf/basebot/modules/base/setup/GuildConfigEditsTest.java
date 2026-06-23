package dev.davimf.basebot.modules.base.setup;

import dev.davimf.basebot.database.model.GuildConfig;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GuildConfigEditsTest {

    private final GuildConfig base = GuildConfig.empty("g1");

    @Test
    void withLogChannelSetsItAndKeepsGuildId() {
        GuildConfig c = GuildConfigEdits.withLogChannel(base, "100");
        assertEquals("100", c.logChannelId());
        assertEquals("g1", c.guildId());
    }

    @Test
    void withTicketLogChannelSetsItIndependently() {
        GuildConfig c = GuildConfigEdits.withTicketLogChannel(
                GuildConfigEdits.withLogChannel(base, "100"), "200");
        assertEquals("100", c.logChannelId());
        assertEquals("200", c.ticketLogChannelId());
    }

    @Test
    void withRoleAddsEntryWithoutMutatingOriginal() {
        GuildConfig c = GuildConfigEdits.withRole(base, "staff", "55");
        assertEquals("55", c.role("staff"));
        assertTrue(base.roles().isEmpty(), "original must be unchanged");
    }

    @Test
    void withToggleSetsFlag() {
        GuildConfig c = GuildConfigEdits.withToggle(base, "laundering", true);
        assertTrue(c.toggle("laundering", false));
    }

    @Test
    void withStaffRolesReplacesList() {
        GuildConfig c = GuildConfigEdits.withStaffRoles(base, java.util.List.of("1", "2"));
        assertEquals(java.util.List.of("1", "2"), c.staffRoleIds());
    }

    @Test
    void withChannelStoresUnderKey() {
        GuildConfig c = GuildConfigEdits.withChannel(base, "tickets-category", "999");
        assertEquals("999", c.channel("tickets-category"));
    }
}
