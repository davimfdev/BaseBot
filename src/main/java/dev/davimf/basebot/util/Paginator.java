// [OUTLINE START]
// Package: dev.davimf.basebot.util
// 
// Class: Paginator
// 
// Constructors:
//   - `Constructor` : `private Paginator()`
// 
// Methods:
//   - `Method` : `public static int pageCount(int total, int pageSize)`
//   - `Method` : `public static <T> List<T> page(List<T> items, int pageIndex, int pageSize)`
// [OUTLINE END]



package dev.davimf.basebot.util;

import java.util.List;

/** Stateless list pagination helper. */
public final class Paginator {

    private Paginator() {}

    public static int pageCount(int total, int pageSize) {
        if (pageSize <= 0) {
            throw new IllegalArgumentException("pageSize must be > 0");
        }
        if (total <= 0) {
            return 1;
        }
        return (total + pageSize - 1) / pageSize;
    }

    public static <T> List<T> page(List<T> items, int pageIndex, int pageSize) {
        int from = Math.max(0, pageIndex) * pageSize;
        if (from >= items.size()) {
            return List.of();
        }
        int to = Math.min(items.size(), from + pageSize);
        return items.subList(from, to);
    }
}
