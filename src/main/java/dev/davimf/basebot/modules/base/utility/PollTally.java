package dev.davimf.basebot.modules.base.utility;

import java.util.Map;

/** Apuração pura de enquete: votos → contagens e barrinha. */
public final class PollTally {

    private PollTally() {}

    public static int[] counts(Map<String, Integer> votes, int options) {
        int[] c = new int[options];
        for (int v : votes.values()) {
            if (v >= 0 && v < options) {
                c[v]++;
            }
        }
        return c;
    }

    public static String bar(int count, int total) {
        int filled = total == 0 ? 0 : Math.round(count * 10f / total);
        return "`[" + "█".repeat(filled) + "░".repeat(10 - filled) + "]` " + count;
    }
}
