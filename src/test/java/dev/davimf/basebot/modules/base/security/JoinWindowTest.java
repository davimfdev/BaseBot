package dev.davimf.basebot.modules.base.security;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JoinWindowTest {

    @Test
    void countsWithinWindow() {
        JoinWindow w = new JoinWindow(10_000);
        assertEquals(1, w.record("g", 0));
        assertEquals(2, w.record("g", 1000));
        assertEquals(3, w.record("g", 2000));
    }

    @Test
    void expiresOld() {
        JoinWindow w = new JoinWindow(10_000);
        w.record("g", 0);
        w.record("g", 1000);
        assertEquals(1, w.record("g", 20_000));
    }

    @Test
    void perGuild() {
        JoinWindow w = new JoinWindow(10_000);
        w.record("a", 0);
        assertEquals(1, w.record("b", 0));
    }
}
