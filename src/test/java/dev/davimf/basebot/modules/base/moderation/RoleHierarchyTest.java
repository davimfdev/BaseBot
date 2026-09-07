package dev.davimf.basebot.modules.base.moderation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoleHierarchyTest {

    @Test
    void higherActorOutranksLowerTarget() {
        assertTrue(RoleHierarchy.actorOutranks(5, 3, false));
    }

    @Test
    void equalPositionsCannotAct() {
        assertFalse(RoleHierarchy.actorOutranks(3, 3, false));
    }

    @Test
    void ownerBypassesPosition() {
        assertTrue(RoleHierarchy.actorOutranks(1, 9, true));
    }

    @Test
    void canModerateRequiresBothActorAndBotToOutrank() {
        assertTrue(RoleHierarchy.canModerate(5, 3, false, 6));   // actor>target, bot>target
        assertFalse(RoleHierarchy.canModerate(5, 3, false, 2));  // bot does NOT outrank target
        assertFalse(RoleHierarchy.canModerate(3, 5, false, 6));  // actor does NOT outrank target
    }

    @Test
    void ownerStillBlockedWhenBotCannotOutrank() {
        assertFalse(RoleHierarchy.canModerate(1, 5, true, 4));   // owner ok, but bot<target
    }
}
