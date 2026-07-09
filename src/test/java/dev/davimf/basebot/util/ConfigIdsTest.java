package dev.davimf.basebot.util;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ConfigIdsTest {

    @Test
    void nullOrBlankYieldsEmpty() {
        assertTrue(ConfigIds.parse(null).isEmpty());
        assertTrue(ConfigIds.parse("").isEmpty());
        assertTrue(ConfigIds.parse("   ").isEmpty());
    }

    @Test
    void trimsAndDropsEmptySegments() {
        assertEquals(Set.of("123", "456"), ConfigIds.parse("123, 456"));
        assertTrue(ConfigIds.parse(",,,").isEmpty());
        assertEquals(Set.of("123"), ConfigIds.parse(" ,123, "));
    }

    @Test
    void dropsDuplicatesAndPreservesOrder() {
        assertEquals(List.of("9", "7"), List.copyOf(ConfigIds.parse("9,7,9")));
    }
}
