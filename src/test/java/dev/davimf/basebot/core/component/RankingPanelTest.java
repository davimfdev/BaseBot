package dev.davimf.basebot.core.component;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RankingPanelTest {

    @Test
    void emptyRankingStillHasOnePage() {
        assertEquals(1, RankingPanel.pageCount(0, 10));
    }

    @Test
    void exactMultipleDoesNotAddATrailingEmptyPage() {
        assertEquals(1, RankingPanel.pageCount(10, 10));
        assertEquals(2, RankingPanel.pageCount(20, 10));
    }

    @Test
    void remainderRoundsUp() {
        assertEquals(2, RankingPanel.pageCount(11, 10));
        assertEquals(3, RankingPanel.pageCount(21, 10));
    }

    @Test
    void bodyKeepsTheBlankLineBetweenHeadingAndFirstRow() {
        assertEquals("## T\n\n`1.` a\n`2.` b",
                RankingPanel.body("## T", "-# vazio", List.of("`1.` a", "`2.` b")));
    }

    @Test
    void bodyHasNoBlankLineWhenEmpty() {
        assertEquals("## T\n-# vazio",
                RankingPanel.body("## T", "-# vazio", List.of()));
    }
}
