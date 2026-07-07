// [OUTLINE START]
// Package: dev.davimf.basebot.modules.base.moderation
// 
// Class: MessagePurge
// 
// Constructors:
//   - `Constructor` : `private MessagePurge()`
// 
// Methods:
//   - `Method` : `public static Partition partitionByAge(List<Long> timestampsMillis, long nowMillis)`
// 
// Fields:
//   - `Field` : `public static final long BULK_MAX_AGE_MILLIS`
// 
// Record: Partition
// 
// Record Components:
//   - Record Component : public final List<Long> bulkDeletable
//   - Record Component : public final List<Long> tooOld
// [OUTLINE END]



package dev.davimf.basebot.modules.base.moderation;

import java.util.ArrayList;
import java.util.List;

/**
 * Partitions messages by age for bulk deletion. Discord refuses to bulk-delete messages
 * 14 days or older, so {@code /clear} must skip them gracefully (BOTSPECS §API Limitations).
 */
public final class MessagePurge {

    public static final long BULK_MAX_AGE_MILLIS = 14L * 24 * 60 * 60 * 1000;

    private MessagePurge() {}

    /** A message is bulk-deletable only if strictly younger than 14 days. */
    public static Partition partitionByAge(List<Long> timestampsMillis, long nowMillis) {
        List<Long> deletable = new ArrayList<>();
        List<Long> tooOld = new ArrayList<>();
        for (long ts : timestampsMillis) {
            if (nowMillis - ts < BULK_MAX_AGE_MILLIS) {
                deletable.add(ts);
            } else {
                tooOld.add(ts);
            }
        }
        return new Partition(deletable, tooOld);
    }

    public record Partition(List<Long> bulkDeletable, List<Long> tooOld) {}
}
