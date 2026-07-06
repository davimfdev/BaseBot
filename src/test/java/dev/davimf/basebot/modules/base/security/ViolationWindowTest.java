package dev.davimf.basebot.modules.base.security;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ViolationWindowTest {

    @Test
    void immediateWhenWarnPerIsOne() {
        ViolationWindow w = new ViolationWindow(1, 0);
        assertTrue(w.record("u1", 1000));
        assertTrue(w.record("u1", 1001));
    }

    @Test
    void accumulatesWithinWindow() {
        ViolationWindow w = new ViolationWindow(3, 10_000);
        assertFalse(w.record("u1", 0));
        assertFalse(w.record("u1", 1000));
        assertTrue(w.record("u1", 2000));
        assertFalse(w.record("u1", 3000));
    }

    @Test
    void oldViolationsExpire() {
        ViolationWindow w = new ViolationWindow(3, 10_000);
        assertFalse(w.record("u1", 0));
        assertFalse(w.record("u1", 1000));
        assertFalse(w.record("u1", 20_000));
    }

    @Test
    void perUserIsolation() {
        ViolationWindow w = new ViolationWindow(2, 10_000);
        assertFalse(w.record("a", 0));
        assertFalse(w.record("b", 0));
        assertTrue(w.record("a", 1000));
    }
}
