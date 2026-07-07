// [OUTLINE START]
// Package: dev.davimf.basebot.modules.base.moderation
// 
// Class: ModerationTopPositionTest
// [OUTLINE END]



package dev.davimf.basebot.modules.base.moderation;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ModerationTopPositionTest {

    @Test
    void returnsHighestPosition() {
        assertEquals(7, Moderation.topPosition(List.of(2, 7, 5)));
    }

    @Test
    void returnsZeroWhenNoRoles() {
        assertEquals(0, Moderation.topPosition(List.of()));
    }
}
