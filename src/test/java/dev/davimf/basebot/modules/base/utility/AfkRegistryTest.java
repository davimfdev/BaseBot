package dev.davimf.basebot.modules.base.utility;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AfkRegistryTest {
    @Test
    void setGetRemove() {
        AfkRegistry r = new AfkRegistry();
        assertTrue(r.get("g", "u").isEmpty());
        r.set("g", "u", "almoço", 1000L);
        assertEquals("almoço", r.get("g", "u").orElseThrow().reason());
        assertTrue(r.remove("g", "u").isPresent());
        assertTrue(r.get("g", "u").isEmpty());
    }

    @Test
    void scopedByGuildAndUser() {
        AfkRegistry r = new AfkRegistry();
        r.set("g1", "u", "x", 1L);
        assertTrue(r.get("g2", "u").isEmpty());
        assertTrue(r.get("g1", "other").isEmpty());
    }
}
