package dev.davimf.basebot.modules.base.security;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ActorWindowTest {

    @Test
    void countsPerActorWithinWindow() {
        ActorWindow w = new ActorWindow(60_000);
        assertEquals(1, w.record("a", 0));
        assertEquals(2, w.record("a", 1000));
        assertEquals(1, w.record("b", 1000));
    }

    @Test
    void expiresOld() {
        ActorWindow w = new ActorWindow(60_000);
        w.record("a", 0);
        assertEquals(1, w.record("a", 61_000));
    }
}
