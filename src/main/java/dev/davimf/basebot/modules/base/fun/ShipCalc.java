package dev.davimf.basebot.modules.base.fun;

/** Compatibilidade 0–100 estável por par (hash determinístico da dupla ordenada). Puro. */
public final class ShipCalc {

    private ShipCalc() {}

    public static int percent(String idA, String idB) {
        String key = idA.compareTo(idB) <= 0 ? idA + ":" + idB : idB + ":" + idA;
        return Math.floorMod(key.hashCode(), 101);
    }

    /** Barrinha visual de 10 blocos para uma porcentagem. */
    public static String bar(int percent) {
        int filled = Math.round(percent / 10f);
        return "`[" + "█".repeat(filled) + "░".repeat(10 - filled) + "]` " + percent + "%";
    }
}
