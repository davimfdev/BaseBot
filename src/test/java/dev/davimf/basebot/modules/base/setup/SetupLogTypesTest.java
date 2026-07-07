// [OUTLINE START]
// Package: dev.davimf.basebot.modules.base.setup
// 
// Class: SetupLogTypesTest
// [OUTLINE END]



package dev.davimf.basebot.modules.base.setup;

import dev.davimf.basebot.modules.base.setup.SetupLogTypes.LogType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SetupLogTypesTest {

    @Test
    void pagesAreGroupedByModuleAndChunked() {
        // Base has 14 logs -> with a cap of 8 it splits into 8 + 6 (both Base), then one
        // page each for Tickets (1), Vendas (2) and Facs (7).
        List<List<LogType>> pages = SetupLogTypes.pages(8);
        assertEquals(5, pages.size());
        assertEquals(8, pages.get(0).size());
        assertTrue(pages.get(0).stream().allMatch(t -> t.module().equals("Base")));
        assertEquals(6, pages.get(1).size());
        assertEquals("Base", pages.get(1).get(0).module());
        assertEquals("Tickets", pages.get(2).get(0).module());
        assertEquals("Vendas", pages.get(3).get(0).module());
        assertEquals("Facs", pages.get(4).get(0).module());
    }

    @Test
    void everyPageStaysWithinTheCapAndNoModuleMixing() {
        for (List<LogType> page : SetupLogTypes.pages(8)) {
            assertTrue(page.size() <= 8);
            String module = page.get(0).module();
            assertTrue(page.stream().allMatch(t -> t.module().equals(module)), "no module mixing per page");
        }
    }
}
