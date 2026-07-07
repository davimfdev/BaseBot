package dev.davimf.basebot.modules.base.utility;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class PollTallyTest {
    @Test
    void countsVotesPerOption() {
        int[] c = PollTally.counts(Map.of("a", 0, "b", 0, "c", 1), 3);
        assertArrayEquals(new int[]{2, 1, 0}, c);
    }

    @Test
    void ignoresOutOfRange() {
        int[] c = PollTally.counts(Map.of("a", 5), 3);
        assertArrayEquals(new int[]{0, 0, 0}, c);
    }

    @Test
    void emptyVotes() {
        assertArrayEquals(new int[]{0, 0}, PollTally.counts(Map.of(), 2));
    }
}
