package dev.davimf.basebot.modules.base.leveling;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class VoicePauseReasonTest {

    /** Humano, sozinho basta para tempo, mic aberto, ouvindo, fora do AFK, canal no escopo. */
    private static VoiceStateSnapshot counting() {
        return new VoiceStateSnapshot(false, 1, false, false, false, false, true);
    }

    @Test
    void countingMemberHasNoReason() {
        assertNull(VoicePauseReason.of(counting()));
    }

    @Test
    void selfMuteIsReported() {
        assertEquals("microfone fechado", VoicePauseReason.of(counting().withSelfMuted(true)));
    }

    @Test
    void bothDeafenFlagsReportTheSameReason() {
        assertEquals("ensurdecido", VoicePauseReason.of(counting().withSelfDeafened(true)));
        assertEquals("ensurdecido", VoicePauseReason.of(counting().withGuildDeafened(true)));
    }

    @Test
    void afkChannelIsReported() {
        assertEquals("canal AFK", VoicePauseReason.of(
                new VoiceStateSnapshot(false, 1, false, false, false, true, true)));
    }

    @Test
    void outOfScopeChannelIsReported() {
        assertEquals("canal fora do escopo", VoicePauseReason.of(
                new VoiceStateSnapshot(false, 1, false, false, false, false, false)));
    }

    @Test
    void selfMuteWinsOverEveryOtherReason() {
        VoiceStateSnapshot everything = new VoiceStateSnapshot(false, 1, true, true, true, true, false);
        assertEquals("microfone fechado", VoicePauseReason.of(everything));
    }
}
