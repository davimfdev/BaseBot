package dev.davimf.basebot.modules.base.economy;

import dev.davimf.basebot.modules.base.economy.EquipmentCatalog.Equip;
import dev.davimf.basebot.modules.base.economy.EquipmentCatalog.Slot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EquipmentCatalogTest {

    @Test
    void byKeyResolvesAndUnknownIsNull() {
        Equip iron = EquipmentCatalog.byKey("pickaxe_iron");
        assertNotNull(iron);
        assertEquals(Slot.MINING, iron.slot());
        assertEquals(4_000, iron.price());
        assertEquals(50, iron.maxUsos());
        assertNull(EquipmentCatalog.byKey("nope"));
    }

    @Test
    void ofSlotReturnsTierOrdered() {
        var motos = EquipmentCatalog.ofSlot(Slot.DELIVERY);
        assertEquals(5, motos.size());
        assertEquals("moto_pop", motos.get(0).key());
        assertEquals("moto_bmw", motos.get(4).key());
        for (int i = 1; i < motos.size(); i++) {
            assertTrue(motos.get(i).tier() > motos.get(i - 1).tier());
        }
    }

    @Test
    void weaponsCarryBonusMultAndRobCap() {
        Equip rifle = EquipmentCatalog.byKey("weapon_rifle");
        assertEquals(Slot.WEAPON, rifle.slot());
        assertEquals(40, rifle.chanceBonus());
        assertEquals(2.5, rifle.mult());
        assertEquals(50_000, rifle.robCap());
    }

    @Test
    void goldPickaxeHas25Uses() {
        assertEquals(25, EquipmentCatalog.byKey("pickaxe_gold").maxUsos());
    }

    @Test
    void tierOf() {
        assertEquals(3, EquipmentCatalog.tierOf("pickaxe_iron"));
        assertEquals(0, EquipmentCatalog.tierOf("nope"));
    }

    @Test
    void everyKeyUniqueAndCountsMatch() {
        assertEquals(5, EquipmentCatalog.ofSlot(Slot.MINING).size());
        assertEquals(5, EquipmentCatalog.ofSlot(Slot.COOKING).size());
        assertEquals(5, EquipmentCatalog.ofSlot(Slot.DELIVERY).size());
        assertEquals(4, EquipmentCatalog.ofSlot(Slot.WEAPON).size());
        assertEquals(19, EquipmentCatalog.all().size());
    }
}
