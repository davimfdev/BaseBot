package dev.davimf.basebot.modules.base.moderation;

import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.utils.data.SerializableData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/** Guards that the moderation panels serialize cleanly as V2 containers. */
class InfractionViewTest {

    private static Infraction warn() {
        return new Infraction(1L, "g1", 1, "100", "200", "WARN", "spam no chat",
                1_700_000_000_000L, 1_700_086_400_000L, null, true);
    }

    private static Infraction tempban() {
        return new Infraction(2L, "g1", 2, "100", "system", "TEMPBAN", "raid",
                1_700_000_000_000L, 1_700_172_800_000L, 172_800_000L, true);
    }

    private static void ok(Container c) {
        assertDoesNotThrow(() -> ((SerializableData) c).toData());
    }

    @Test
    void historySerializes() {
        ok(InfractionView.history(0x5865F2, "100", List.of(warn(), tempban()), 0));
        ok(InfractionView.history(0x5865F2, "100", List.of(), 0));
    }

    @Test
    void caseDetailSerializes() {
        ok(InfractionView.caseDetail(0x5865F2, warn()));
        ok(InfractionView.caseDetail(0x5865F2, tempban()));
    }

    @Test
    void revokePickerSerializes() {
        ok(InfractionView.revokePicker(0x5865F2, "100", List.of(warn(), tempban())));
        ok(InfractionView.revokePicker(0x5865F2, "100", List.of()));
    }
}
