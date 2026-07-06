package dev.davimf.basebot.modules.facs.actions;

import dev.davimf.basebot.modules.facs.actions.ActionRepository.Action;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.utils.data.SerializableData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Guards that the action panel container serializes cleanly (catches V2 build/send rejections). */
class ActionViewTest {

    private static Action future() {
        return new Action("dd2cc970880f", "123", "456", "789", "26/06/2026 22:08",
                10, "OPEN", "111", "Teste", 3, 500, false, true, 0L, false);
    }

    private static Action past() {
        return new Action("aa11bb22cc33", "123", "456", "789", null,
                10, "OPEN", "111", "Teste", 3, 500, true, false, 0L, false);
    }

    @Test
    void futurePanelSerializes() {
        Container c = ActionView.panel(0x5865F2, future(), List.of(), List.of());
        assertNotNull(c);
        assertDoesNotThrow(() -> ((SerializableData) c).toData());
    }

    @Test
    void pastPanelSerializes() {
        Container c = ActionView.panel(0x5865F2, past(), List.of(), List.of());
        assertNotNull(c);
        assertDoesNotThrow(() -> ((SerializableData) c).toData());
    }

    @Test
    void managementPanelSerializes() {
        assertDoesNotThrow(() -> ((SerializableData) ActionView.managementPanel(0x5865F2)).toData());
    }
}
