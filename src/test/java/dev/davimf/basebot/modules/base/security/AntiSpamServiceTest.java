package dev.davimf.basebot.modules.base.security;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.ToLongFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AntiSpamServiceTest {

    @Test
    void selectsTenMostRecentDescending() {
        List<Long> ts = List.of(1L, 5L, 3L, 9L, 2L, 8L, 4L, 7L, 6L, 10L, 11L, 0L);
        ToLongFunction<Long> id = Long::longValue;
        List<Long> got = AntiSpamService.selectMostRecent(ts, id, 10);
        assertEquals(List.of(11L, 10L, 9L, 8L, 7L, 6L, 5L, 4L, 3L, 2L), got);
    }

    @Test
    void returnsAllWhenFewerThanLimit() {
        List<Long> ts = List.of(3L, 1L, 2L);
        List<Long> got = AntiSpamService.selectMostRecent(ts, Long::longValue, 10);
        assertEquals(List.of(3L, 2L, 1L), got);
    }
}
