package dev.davimf.basebot.modules.base.economy;

import java.util.List;

/** Catálogo fixo de equipamentos (mesmo em todo servidor). Puro, sem estado. */
public final class EquipmentCatalog {

    public enum Slot { MINING, COOKING, DELIVERY, WEAPON }

    /** Campos não usados pelo slot ficam 0. Mineração/cozinha: payout. Entrega: payout+fuel. Arma: chanceBonus+mult+robCap. */
    public record Equip(String key, String name, Slot slot, int tier, long price, int maxUsos,
                        long payoutMin, long payoutMax, long fuel, int chanceBonus, double mult, long robCap) {}

    private static Equip tool(String key, String name, Slot slot, int tier, long price, int usos, long min, long max) {
        return new Equip(key, name, slot, tier, price, usos, min, max, 0, 0, 0, 0);
    }

    private static Equip moto(String key, String name, int tier, long price, int usos, long fuel, long min, long max) {
        return new Equip(key, name, Slot.DELIVERY, tier, price, usos, min, max, fuel, 0, 0, 0);
    }

    private static Equip weapon(String key, String name, int tier, long price, int usos, int bonus, double mult, long cap) {
        return new Equip(key, name, Slot.WEAPON, tier, price, usos, 0, 0, 0, bonus, mult, cap);
    }

    private static final List<Equip> ALL = List.of(
            tool("pickaxe_wood",    "Picareta de Madeira",     Slot.MINING, 1,    500,  15,  60,  120),
            tool("pickaxe_stone",   "Picareta de Pedra",       Slot.MINING, 2,  1_500,  30, 120,  220),
            tool("pickaxe_iron",    "Picareta de Ferro",       Slot.MINING, 3,  4_000,  50, 250,  400),
            tool("pickaxe_gold",    "Picareta de Ouro",        Slot.MINING, 4, 10_000,  25, 600,  900),
            tool("pickaxe_diamond", "Picareta de Diamante",    Slot.MINING, 5, 25_000, 100, 800, 1400),
            tool("cook_spoon",      "Colher de Pau",           Slot.COOKING, 1,    300, 20,  50,  100),
            tool("cook_whisk",      "Fouet de Silicone",       Slot.COOKING, 2,  1_200, 30, 110,  200),
            tool("cook_knife",      "Faca do Chef (Aço Inox)", Slot.COOKING, 3,  3_500, 40, 220,  380),
            tool("cook_torch",      "Maçarico Culinário",      Slot.COOKING, 4,  8_000, 35, 400,  700),
            tool("cook_case",       "Maleta Masterchef",       Slot.COOKING, 5, 20_000, 80, 750, 1200),
            moto("moto_pop",   "Honda Pop 100",           1,  2_500,  40,  30,  150,  250),
            moto("moto_titan", "CG Titan 160",            2,  8_000,  60,  60,  300,  500),
            moto("moto_xre",   "Honda XRE 300",           3, 18_000,  80, 100,  550,  850),
            moto("moto_xt",    "Yamaha XT 660 (Meiota)",  4, 40_000, 100, 180, 1000, 1500),
            moto("moto_bmw",   "BMW R1250 GS (Foguete)",  5, 90_000, 120, 300, 1800, 2800),
            weapon("weapon_knife",   "Canivete Borboleta", 1,  2_000, 15,  5, 1.0,  5_000),
            weapon("weapon_machete", "Facão de Selva",     2,  6_000, 25, 12, 1.3, 10_000),
            weapon("weapon_pistol",  "Pistola 9mm",        3, 20_000, 40, 25, 1.8, 25_000),
            weapon("weapon_rifle",   "Fuzil AR-15",        4, 50_000, 60, 40, 2.5, 50_000));

    private EquipmentCatalog() {}

    public static List<Equip> all() { return ALL; }

    public static Equip byKey(String key) {
        for (Equip e : ALL) {
            if (e.key().equals(key)) {
                return e;
            }
        }
        return null;
    }

    public static List<Equip> ofSlot(Slot slot) {
        return ALL.stream().filter(e -> e.slot() == slot)
                .sorted(java.util.Comparator.comparingInt(Equip::tier)).toList();
    }

    public static int tierOf(String key) {
        Equip e = byKey(key);
        return e == null ? 0 : e.tier();
    }
}
