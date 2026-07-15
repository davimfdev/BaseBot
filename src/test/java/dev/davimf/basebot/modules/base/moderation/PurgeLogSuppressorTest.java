package dev.davimf.basebot.modules.base.moderation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PurgeLogSuppressorTest {

    @Test
    void claimsMarkedIdOnce() {
        PurgeLogSuppressor s = new PurgeLogSuppressor();
        s.mark(List.of(1L, 2L, 3L));
        assertTrue(s.claim(2L), "id marcado deve ser reconhecido");
        assertFalse(s.claim(2L), "claim consome o registro; segunda vez é falso");
    }

    @Test
    void unmarkedIdIsNotClaimed() {
        PurgeLogSuppressor s = new PurgeLogSuppressor();
        s.mark(List.of(10L));
        assertFalse(s.claim(99L), "id nunca marcado não é reconhecido");
    }

    @Test
    void claimAnyReturnsTrueWhenBatchContainsMarkedId() {
        PurgeLogSuppressor s = new PurgeLogSuppressor();
        s.mark(List.of(1L, 2L, 3L));
        assertTrue(s.claimAny(List.of(2L, 3L)), "lote com ids marcados = deleção nossa");
    }

    @Test
    void claimAnyReturnsFalseForUnrelatedBatch() {
        PurgeLogSuppressor s = new PurgeLogSuppressor();
        s.mark(List.of(1L, 2L));
        assertFalse(s.claimAny(List.of(50L, 51L)), "bulk delete manual não deve ser suprimido");
    }
}
