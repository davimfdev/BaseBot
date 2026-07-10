package dev.davimf.basebot.modules.base.leveling;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoiceRankingTest {

    private static VoiceTimeRepository.Entry e(String user, long ms) {
        return new VoiceTimeRepository.Entry(user, ms);
    }

    @Test
    void addsPendingOnTopOfSaved() {
        List<VoiceTimeRepository.Entry> out =
                VoiceRanking.merge(List.of(e("u1", 1_000)), Map.of("u1", 500L));
        assertEquals(List.of(e("u1", 1_500)), out);
    }

    @Test
    void memberWithOnlyPendingTimeAppears() {
        // Entrou na call há 30s: ainda não tem linha no banco, mas já deve aparecer.
        List<VoiceTimeRepository.Entry> out =
                VoiceRanking.merge(List.of(), Map.of("novato", 30_000L));
        assertEquals(List.of(e("novato", 30_000)), out);
    }

    @Test
    void pendingCanChangeTheOrder() {
        List<VoiceTimeRepository.Entry> out = VoiceRanking.merge(
                List.of(e("lider", 10_000), e("vice", 9_500)),
                Map.of("vice", 1_000L));
        assertEquals(List.of(e("vice", 10_500), e("lider", 10_000)), out);
    }

    @Test
    void zeroTotalsAreHidden() {
        List<VoiceTimeRepository.Entry> out =
                VoiceRanking.merge(List.of(e("fantasma", 0)), Map.of());
        assertTrue(out.isEmpty());
    }

    @Test
    void tiesBreakOnUserIdSoPaginationIsStable() {
        List<VoiceTimeRepository.Entry> out =
                VoiceRanking.merge(List.of(e("b", 100), e("a", 100)), Map.of());
        assertEquals(List.of(e("a", 100), e("b", 100)), out);
    }

    @Test
    void savedWithoutPendingIsUntouched() {
        List<VoiceTimeRepository.Entry> out =
                VoiceRanking.merge(List.of(e("u1", 7_000)), Map.of("outro", 400L));
        assertEquals(List.of(e("u1", 7_000), e("outro", 400)), out);
    }
}
