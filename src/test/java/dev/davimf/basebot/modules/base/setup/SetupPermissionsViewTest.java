package dev.davimf.basebot.modules.base.setup;

import dev.davimf.basebot.database.model.GuildConfig;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.utils.data.SerializableData;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/** The permissions detail screen must serialize cleanly as a V2 container. */
class SetupPermissionsViewTest {

    private static GuildConfig cfg() {
        return new GuildConfig("g1", null, null, Map.of(), Map.of("gerente-farm", "100"),
                Map.of(), List.of(), Map.of());
    }

    @Test
    void detailSerializes() {
        Container c = SetupView.permissionsDetail(cfg(), "gerente-farm", "Gerente de Farm");
        assertDoesNotThrow(() -> ((SerializableData) c).toData());
    }

    @Test
    void detailForUnconfiguredRoleSerializes() {
        Container c = SetupView.permissionsDetail(cfg(), "gerente-elite", "Gerente de Elite");
        assertDoesNotThrow(() -> ((SerializableData) c).toData());
    }
}
