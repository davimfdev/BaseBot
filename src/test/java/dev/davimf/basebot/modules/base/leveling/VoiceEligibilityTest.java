package dev.davimf.basebot.modules.base.leveling;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VoiceEligibilityTest {

    /** Humano, dois na call, mic aberto, ouvindo, fora do AFK, canal no escopo. */
    private static VoiceStateSnapshot active() {
        return new VoiceStateSnapshot(false, 2, false, false, false, false, true);
    }

    @Test
    void activeMemberEarnsBoth() {
        assertTrue(VoiceEligibility.xpEligible(active()));
        assertTrue(VoiceEligibility.timeEligible(active()));
    }

    @Test
    void selfMuteBlocksBoth() {
        VoiceStateSnapshot s = active().withSelfMuted(true);
        assertFalse(VoiceEligibility.xpEligible(s));
        assertFalse(VoiceEligibility.timeEligible(s));
    }

    /**
     * Server-mute não pausa nada, e a garantia disso é ESTRUTURAL: o snapshot não modela
     * {@code guildMuted}, então nenhum predicado consegue consultá-lo. Este teste falha se
     * alguém acrescentar o campo — forçando a revisitar a decisão de produto em vez de
     * silenciosamente passar a punir quem foi mutado por um moderador.
     */
    @Test
    void serverMuteIsDeliberatelyNotModeled() {
        boolean hasGuildMuted = java.util.Arrays.stream(VoiceStateSnapshot.class.getRecordComponents())
                .anyMatch(rc -> rc.getName().equals("guildMuted"));
        assertFalse(hasGuildMuted,
                "server-mute nao deve pausar XP nem tempo; se este campo passou a existir, "
                        + "revise VoiceEligibility e o spec antes de remover este teste");
    }

    @Test
    void selfDeafenBlocksBoth() {
        VoiceStateSnapshot s = active().withSelfDeafened(true);
        assertFalse(VoiceEligibility.xpEligible(s));
        assertFalse(VoiceEligibility.timeEligible(s));
    }

    @Test
    void guildDeafenBlocksBoth() {
        VoiceStateSnapshot s = active().withGuildDeafened(true);
        assertFalse(VoiceEligibility.xpEligible(s));
        assertFalse(VoiceEligibility.timeEligible(s));
    }

    @Test
    void deafenedIsTheDisjunctionOfBothFlags() {
        assertFalse(active().deafened());
        assertTrue(active().withSelfDeafened(true).deafened());
        assertTrue(active().withGuildDeafened(true).deafened());
        assertTrue(active().withSelfDeafened(true).withGuildDeafened(true).deafened());
    }

    @Test
    void afkChannelBlocksBoth() {
        VoiceStateSnapshot s = new VoiceStateSnapshot(false, 2, false, false, false, true, true);
        assertFalse(VoiceEligibility.xpEligible(s));
        assertFalse(VoiceEligibility.timeEligible(s));
    }

    @Test
    void aloneInChannelBlocksXpButNotTime() {
        VoiceStateSnapshot s = new VoiceStateSnapshot(false, 1, false, false, false, false, true);
        assertFalse(VoiceEligibility.xpEligible(s));
        assertTrue(VoiceEligibility.timeEligible(s));
    }

    @Test
    void channelOutOfScopeBlocksTimeButNotXp() {
        VoiceStateSnapshot s = new VoiceStateSnapshot(false, 2, false, false, false, false, false);
        assertTrue(VoiceEligibility.xpEligible(s));
        assertFalse(VoiceEligibility.timeEligible(s));
    }

    @Test
    void botsEarnNothing() {
        VoiceStateSnapshot s = new VoiceStateSnapshot(true, 5, false, false, false, false, true);
        assertFalse(VoiceEligibility.xpEligible(s));
        assertFalse(VoiceEligibility.timeEligible(s));
    }
}
