package dev.davimf.basebot.util;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PaginatorTest {

    @Test
    void pageCountRoundsUpAndIsAtLeastOne() {
        assertEquals(1, Paginator.pageCount(0, 20));
        assertEquals(1, Paginator.pageCount(20, 20));
        assertEquals(2, Paginator.pageCount(21, 20));
        assertEquals(3, Paginator.pageCount(45, 20));
    }

    @Test
    void pageReturnsTheRightSlice() {
        List<Integer> items = List.of(0, 1, 2, 3, 4);
        assertEquals(List.of(0, 1), Paginator.page(items, 0, 2));
        assertEquals(List.of(2, 3), Paginator.page(items, 1, 2));
        assertEquals(List.of(4), Paginator.page(items, 2, 2));
    }

    @Test
    void pageOutOfRangeIsEmpty() {
        assertEquals(List.of(), Paginator.page(List.of(1, 2), 5, 2));
    }
}
