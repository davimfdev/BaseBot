package dev.davimf.basebot.modules.base.economy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShopItemSoldTest {

    private ShopItem item(Integer stock, int sold) {
        return new ShopItem(1L, "g", ShopItem.Type.ROLE_PERM, "r", "n", "d",
                100L, null, stock, null, sold, 0L);
    }

    @Test
    void withSoldReplacesCounterOnly() {
        ShopItem base = item(5, 0);
        ShopItem filled = base.withSold(3);
        assertEquals(3, filled.sold());
        assertEquals(base.id(), filled.id());
        assertEquals(base.stock(), filled.stock());
        assertEquals(2, filled.remaining());
        assertFalse(filled.soldOut());
    }

    @Test
    void soldOutWhenFinite() {
        assertTrue(item(2, 2).soldOut());
        assertEquals(0, item(2, 2).remaining());
    }

    @Test
    void unlimitedNeverSoldOut() {
        assertFalse(item(null, 999).soldOut());
        assertEquals(Integer.MAX_VALUE, item(null, 999).remaining());
    }
}
