package dev.davimf.basebot.modules.base.snapshot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GuildSnapshotSyncTest {

    @Test
    void canAssignRequiresAllThree() {
        assertTrue(GuildSnapshotSync.botCanAssign(true, true, false));
    }

    @Test
    void cannotAssignWithoutManageRoles() {
        assertFalse(GuildSnapshotSync.botCanAssign(false, true, false));
    }

    @Test
    void cannotAssignAboveHierarchy() {
        assertFalse(GuildSnapshotSync.botCanAssign(true, false, false));
    }

    @Test
    void cannotAssignManagedRole() {
        assertFalse(GuildSnapshotSync.botCanAssign(true, true, true));
    }
}
