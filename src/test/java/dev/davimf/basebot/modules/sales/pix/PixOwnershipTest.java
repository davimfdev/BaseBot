package dev.davimf.basebot.modules.sales.pix;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PixOwnershipTest {

    @Test
    void ownerMayConfirm() {
        assertTrue(PixOwnership.isOwner("123", "123"));
    }

    @Test
    void nonOwnerMayNotConfirm() {
        assertFalse(PixOwnership.isOwner("999", "123"));
        assertFalse(PixOwnership.isOwner(null, "123"));
    }
}
