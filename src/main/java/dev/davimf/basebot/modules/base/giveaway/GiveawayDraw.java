package dev.davimf.basebot.modules.base.giveaway;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Sorteia N ganhadores distintos. Puro (Random injetável). */
public final class GiveawayDraw {
    private GiveawayDraw() {}

    public static List<String> pick(List<String> entrants, int n, Random r) {
        List<String> copy = new ArrayList<>(entrants);
        Collections.shuffle(copy, r);
        return new ArrayList<>(copy.subList(0, Math.min(Math.max(0, n), copy.size())));
    }
}
