package dev.davimf.basebot.modules.base.economy;

/** Aritmética pura de compra/expiração da loja (sem JDA/DB) — testável isoladamente. */
public final class ShopPurchaseRules {

    /** Limites parseados do campo "estoque/usuário" do modal; {@code valid=false} se malformado. */
    public record Limits(Integer stock, Integer perUser, boolean valid) {}

    private ShopPurchaseRules() {}

    /** Limite por usuário atingido? null = ilimitado. */
    public static boolean perUserReached(Integer perUser, int alreadyActive) {
        return perUser != null && alreadyActive >= perUser;
    }

    /** Expiração de uma compra nova de cargo temporário. */
    public static long newExpiry(long durationS, long nowMs) {
        return nowMs + durationS * 1000L;
    }

    /** Renovação: estende a partir do tempo restante (ou de agora, se já venceu por corrida). */
    public static long extendedExpiry(long currentExpiresAt, long durationS, long nowMs) {
        return Math.max(currentExpiresAt, nowMs) + durationS * 1000L;
    }

    /** Parseia "estoque/porUsuario" (ambos opcionais). Vazio = sem limites. Inválido → valid=false. */
    public static Limits parseLimits(String raw) {
        if (raw == null || raw.isBlank()) {
            return new Limits(null, null, true);
        }
        String[] parts = raw.trim().split("/", -1);
        if (parts.length > 2) {
            return new Limits(null, null, false);
        }
        Integer stock = side(parts[0]);
        Integer perUser = parts.length == 2 ? side(parts[1]) : null;
        boolean stockBad = !parts[0].isBlank() && stock == null;
        boolean perUserBad = parts.length == 2 && !parts[1].isBlank() && perUser == null;
        return new Limits(stock, perUser, !stockBad && !perUserBad);
    }

    private static Integer side(String s) {
        s = s.trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            int v = Integer.parseInt(s);
            return v > 0 ? v : null; // 0/negativo = inválido (side() devolve null → valid=false)
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
