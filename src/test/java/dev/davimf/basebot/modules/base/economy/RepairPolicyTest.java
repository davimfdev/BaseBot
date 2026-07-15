package dev.davimf.basebot.modules.base.economy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RepairPolicyTest {

    @Test
    void costIsHalfPrice() {
        assertEquals(500, RepairPolicy.cost(1_000));
        assertEquals(30_000, RepairPolicy.cost(60_000));
    }

    @Test
    void restoredUsosIs75PctOfMaxAtLeastOne() {
        assertEquals(15, RepairPolicy.restoredUsos(20));
        assertEquals(4, RepairPolicy.restoredUsos(5));   // round(3.75)=4
        assertEquals(30, RepairPolicy.restoredUsos(40));
    }

    @Test
    void thresholdIsFivePctCeilAtLeastOne() {
        assertEquals(1, RepairPolicy.eligibleThreshold(5));   // ceil(0.25)=1
        assertEquals(1, RepairPolicy.eligibleThreshold(20));  // ceil(1.0)=1
        assertEquals(2, RepairPolicy.eligibleThreshold(40));  // ceil(2.0)=2
    }

    @Test
    void eligibleOnlyWhenNearlyBrokenAndUnderRepairCap() {
        assertTrue(RepairPolicy.eligible(1, 20, 0));    // 1 <= threshold(1), repairs<3
        assertFalse(RepairPolicy.eligible(10, 20, 0));  // acima do limiar
        assertFalse(RepairPolicy.eligible(0, 20, 0));   // já quebrado
        assertFalse(RepairPolicy.eligible(1, 20, 3));   // atingiu o teto de reparos
    }
}
