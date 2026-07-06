// [OUTLINE START]
// Package: dev.davimf.basebot.modules.base.moderation
// 
// Class: MessagePurgeTest
// 
// Fields:
//   - `Field` : `private static final long NOW`
//   - `Field` : `private static final long DAY`
// [OUTLINE END]



package dev.davimf.basebot.modules.base.moderation;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MessagePurgeTest {

    private static final long NOW = 1_000_000_000_000L;
    private static final long DAY = 24L * 60 * 60 * 1000;

    @Test
    void recentMessagesAreAllBulkDeletable() {
        List<Long> ts = List.of(NOW - DAY, NOW - 2 * DAY, NOW - 13 * DAY);
        MessagePurge.Partition p = MessagePurge.partitionByAge(ts, NOW);
        assertEquals(3, p.bulkDeletable().size());
        assertEquals(0, p.tooOld().size());
    }

    @Test
    void messagesOlderThan14DaysAreTooOld() {
        List<Long> ts = List.of(NOW - 15 * DAY, NOW - 30 * DAY);
        MessagePurge.Partition p = MessagePurge.partitionByAge(ts, NOW);
        assertEquals(0, p.bulkDeletable().size());
        assertEquals(2, p.tooOld().size());
    }

    @Test
    void exactly14DaysIsTooOld() {
        List<Long> ts = List.of(NOW - 14 * DAY);
        MessagePurge.Partition p = MessagePurge.partitionByAge(ts, NOW);
        assertEquals(0, p.bulkDeletable().size());
        assertEquals(1, p.tooOld().size());
    }

    @Test
    void mixedAgesSplitCorrectly() {
        List<Long> ts = List.of(NOW - DAY, NOW - 20 * DAY, NOW - 5 * DAY);
        MessagePurge.Partition p = MessagePurge.partitionByAge(ts, NOW);
        assertEquals(2, p.bulkDeletable().size());
        assertEquals(1, p.tooOld().size());
    }
}
