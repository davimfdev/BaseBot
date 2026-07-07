package dev.davimf.basebot.modules.base.giveaway;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class GiveawayDrawTest {
    @Test
    void picksDistinctWinners() {
        List<String> w = GiveawayDraw.pick(List.of("a", "b", "c", "d"), 2, new Random(1));
        assertEquals(2, w.size());
        assertEquals(2, w.stream().distinct().count());
        assertTrue(List.of("a", "b", "c", "d").containsAll(w));
    }

    @Test
    void capsAtEntrantCount() {
        assertEquals(3, GiveawayDraw.pick(List.of("a", "b", "c"), 10, new Random(1)).size());
        assertTrue(GiveawayDraw.pick(List.of(), 3, new Random(1)).isEmpty());
    }
}
