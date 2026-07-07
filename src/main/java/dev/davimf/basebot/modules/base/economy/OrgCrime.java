package dev.davimf.basebot.modules.base.economy;

/** Resolvers puros do crime organizado: chance combinada e divisão do pote por peso. */
public final class OrgCrime {
    private OrgCrime() {}

    public static int chance(int base, int avgBonus, int dirtyCount) {
        return Math.max(5, Math.min(90, base + avgBonus - 5 * dirtyCount));
    }

    /** Divide {@code pote} por peso; resto (por arredondamento) vai pra fatia de maior peso. Soma exata = pote. */
    public static long[] split(long pote, double[] weights) {
        if (pote < 0 || weights.length == 0) {
            throw new IllegalArgumentException("pote/weights inválidos");
        }
        for (double weight : weights) {
            if (weight <= 0 || Double.isNaN(weight) || Double.isInfinite(weight)) {
                throw new IllegalArgumentException("peso inválido");
            }
        }
        long[] out = new long[weights.length];
        double sum = 0;
        for (double w : weights) {
            sum += w;
        }
        long distributed = 0;
        int maxIdx = 0;
        for (int i = 0; i < weights.length; i++) {
            out[i] = (long) Math.floor(pote * weights[i] / sum);
            distributed += out[i];
            if (weights[i] > weights[maxIdx]) {
                maxIdx = i;
            }
        }
        out[maxIdx] += pote - distributed; // resto pro maior peso (empate → primeiro índice)
        return out;
    }
}
