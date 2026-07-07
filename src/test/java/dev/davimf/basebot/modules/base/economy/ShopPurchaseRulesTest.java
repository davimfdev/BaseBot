package dev.davimf.basebot.modules.base.economy;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShopPurchaseRulesTest {

    @Test
    void perUserReachedNullMeansUnlimited() {
        assertFalse(ShopPurchaseRules.perUserReached(null, 999));
        assertFalse(ShopPurchaseRules.perUserReached(2, 1));
        assertTrue(ShopPurchaseRules.perUserReached(2, 2));
        assertTrue(ShopPurchaseRules.perUserReached(1, 5));
    }

    @Test
    void newExpiryIsNowPlusDuration() {
        assertEquals(1_000L + 7_000L, ShopPurchaseRules.newExpiry(7, 1_000L));
    }

    @Test
    void extendedExpiryStacksOnRemainingTime() {
        // faltam 5s (expira em 6000, agora 1000) + 7s = expira em 13000
        assertEquals(13_000L, ShopPurchaseRules.extendedExpiry(6_000L, 7, 1_000L));
    }

    @Test
    void extendedExpiryFromNowWhenAlreadyLapsed() {
        // já venceu (expira em 500, agora 1000) → conta a partir de agora
        assertEquals(1_000L + 7_000L, ShopPurchaseRules.extendedExpiry(500L, 7, 1_000L));
    }

    @Test
    void parseLimitsHandlesBlankAndPairs() {
        assertEquals(new ShopPurchaseRules.Limits(null, null, true), ShopPurchaseRules.parseLimits(""));
        assertEquals(new ShopPurchaseRules.Limits(10, 1, true), ShopPurchaseRules.parseLimits("10/1"));
        assertEquals(new ShopPurchaseRules.Limits(10, null, true), ShopPurchaseRules.parseLimits("10/"));
        assertEquals(new ShopPurchaseRules.Limits(null, 1, true), ShopPurchaseRules.parseLimits("/1"));
    }

    @Test
    void parseLimitsRejectsNonPositiveOrGarbage() {
        assertFalse(ShopPurchaseRules.parseLimits("abc").valid());
        assertFalse(ShopPurchaseRules.parseLimits("0/1").valid());
        assertFalse(ShopPurchaseRules.parseLimits("-3").valid());
        assertFalse(ShopPurchaseRules.parseLimits("1/2/3").valid());
    }
}
