package dev.davimf.basebot.modules.base.economy;

/** Constantes v1 da economia (crime/roubo fixos; defaults de config em EconomyConfig). */
public final class EconomyDefaults {
    private EconomyDefaults() {}

    public static final long DAILY_COOLDOWN_S = 86_400;

    public static final long CRIME_COOLDOWN_S = 3_600;
    public static final int CRIME_SUCCESS_PCT = 50;
    public static final long CRIME_WIN_MIN = 100, CRIME_WIN_MAX = 500;
    public static final long CRIME_FINE_MIN = 50, CRIME_FINE_MAX = 250;

    public static final long ROB_COOLDOWN_S = 7_200;
    public static final int ROB_SUCCESS_PCT = 40;
    public static final int ROB_STEAL_MIN_PCT = 10, ROB_STEAL_MAX_PCT = 30;
    public static final long ROB_FINE_MIN = 50, ROB_FINE_MAX = 200;
    public static final long ROB_TARGET_MIN_CASH = 100;
    public static final long ROB_PROTECTED_FLOOR = 500;
    public static final long ROB_PAIR_COOLDOWN_S = 43_200;

    public static final long BAIL_BASE = 15_000;
    public static final long EXPUNGE = 30_000;
    public static final int FICHA_PENALTY_PCT = 15;

    public static final long MINE_COOLDOWN_S = 1_800;   // 30min
    public static final long COOK_COOLDOWN_S = 1_800;   // 30min
    public static final long DELIVERY_COOLDOWN_S = 900; // 15min

    public static final long PROGRAM_COOLDOWN_S = 18_000;  // 5h
    public static final long PLANT_COOLDOWN_S   = 18_000;  // 5h
    public static final long FISH_COOLDOWN_S    = 7_200;   // 2h
    public static final long EXPLORE_COOLDOWN_S = 7_200;   // 2h
    public static final long INVOICE_COOLDOWN_S = 86_400;  // 24h

    public static final int ORG_BASE_CHANCE = 30;
    public static final long ORG_POT_PER_PLAYER = 4_500;
    public static final int ORG_MIN = 5, ORG_MAX = 10;
    public static final long ORG_DAILY_COOLDOWN_S = 86_400;   // 24h
    public static final long ORG_JAIL_MIN_S = 21_600;         // 6h
    public static final long ORG_JAIL_MAX_S = 43_200;         // 12h
}
