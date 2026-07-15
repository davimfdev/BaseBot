package dev.davimf.basebot.modules.base.economy;

/** Regras puras de reparo de itens: custo, restauração, elegibilidade e teto. Sem estado. */
public final class RepairPolicy {

    public static final int MAX_REPAIRS = 3;
    private static final double RESTORE_PCT = 0.75;
    private static final double ELIGIBLE_PCT = 0.05;

    private RepairPolicy() {}

    /** Custo do reparo: metade do preço de compra. */
    public static long cost(long price) { return price / 2; }

    /** Usos restaurados: 75% do máximo (no mínimo 1). */
    public static int restoredUsos(int maxUsos) {
        return Math.max(1, (int) Math.round(maxUsos * RESTORE_PCT));
    }

    /** Maior valor de usos_left que ainda permite reparar (item quase quebrado): 5% do máximo (no mínimo 1). */
    public static int eligibleThreshold(int maxUsos) {
        return Math.max(1, (int) Math.ceil(maxUsos * ELIGIBLE_PCT));
    }

    /** Só repara se ainda não quebrou, está quase quebrado, e não bateu o teto de 3 reparos. */
    public static boolean eligible(int usosLeft, int maxUsos, int repairs) {
        return repairs < MAX_REPAIRS && usosLeft > 0 && usosLeft <= eligibleThreshold(maxUsos);
    }
}
