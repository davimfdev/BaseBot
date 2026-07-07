package dev.davimf.basebot.modules.base.leveling;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class LevelRewardsTest {
    @Test
    void collectsRolesForEveryLevelCrossed() {
        Map<Integer, String> rewards = Map.of(1, "r1", 3, "r3", 5, "r5");
        assertEquals(List.of("r1", "r3"), LevelRewards.rolesForCrossedLevels(rewards, 0, 4));
    }

    @Test
    void inclusiveOfDestinationLevel() {
        Map<Integer, String> rewards = Map.of(5, "r5");
        assertEquals(List.of("r5"), LevelRewards.rolesForCrossedLevels(rewards, 4, 5));
    }

    @Test
    void emptyWhenNoRewardInRange() {
        assertEquals(List.of(), LevelRewards.rolesForCrossedLevels(Map.of(10, "r10"), 0, 4));
    }
}
