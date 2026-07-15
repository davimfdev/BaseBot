package dev.davimf.basebot.modules.base.economy;

import dev.davimf.basebot.modules.base.economy.EquipmentCatalog.Equip;
import dev.davimf.basebot.modules.base.economy.EquipmentCatalog.Slot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EquipmentCatalogTest {

    @Test
    void newSlotsEachHaveTools() {
        assertEquals(5, EquipmentCatalog.ofSlot(Slot.TECH).size());
        assertEquals(5, EquipmentCatalog.ofSlot(Slot.FARM).size());
        assertEquals(5, EquipmentCatalog.ofSlot(Slot.FISHING).size());
        assertEquals(5, EquipmentCatalog.ofSlot(Slot.EXPEDITION).size());
        assertEquals(4, EquipmentCatalog.ofSlot(Slot.BUSINESS).size());
    }

    @Test
    void newItemsAreWellFormed() {
        for (Slot s : new Slot[]{Slot.TECH, Slot.FARM, Slot.FISHING, Slot.EXPEDITION, Slot.BUSINESS}) {
            for (Equip e : EquipmentCatalog.ofSlot(s)) {
                assertTrue(e.price() > 0, e.key() + " price");
                assertTrue(e.maxUsos() > 0, e.key() + " usos");
                assertTrue(e.payoutMin() > 0 && e.payoutMax() >= e.payoutMin(), e.key() + " payout");
                assertEquals(0, e.fuel(), e.key() + " fuel");
            }
        }
    }

    @Test
    void tiersAscendWithinSlot() {
        long prev = 0;
        for (Equip e : EquipmentCatalog.ofSlot(Slot.FISHING)) {
            assertTrue(e.price() > prev, e.key() + " tier price ascends");
            prev = e.price();
        }
    }

    @Test
    void sampleKeysResolve() {
        assertEquals("Servidor Quântico", EquipmentCatalog.byKey("tech_quantum").name());
        assertEquals(20, EquipmentCatalog.byKey("tech_quantum").maxUsos());
        assertEquals("Barco de Pesca", EquipmentCatalog.byKey("fish_boat").name());
        assertEquals("Holding Multinacional", EquipmentCatalog.byKey("biz_holding").name());
    }
}
