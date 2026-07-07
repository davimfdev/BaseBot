package dev.davimf.basebot.modules.base.giveaway;

/** Elegibilidade de participação: devolve o 1º requisito ativo não cumprido, ou null se ok. Puro. */
public final class GiveawayRequirements {

    private GiveawayRequirements() {}

    public static String firstUnmet(Giveaway g, boolean hasRole, long joinedEpochMs, long totalVoiceMs,
                                    boolean windowOk, long now) {
        if (g.reqRoleId() != null && !g.reqRoleId().isBlank() && !hasRole) {
            return "Você precisa do cargo <@&" + g.reqRoleId() + "> para participar.";
        }
        if (g.reqMinDays() > 0 && (now - joinedEpochMs) < g.reqMinDays() * 86_400_000L) {
            return "Você precisa estar há pelo menos **" + g.reqMinDays() + " dia(s)** no servidor.";
        }
        if (g.reqMinVoiceHours() > 0 && totalVoiceMs < g.reqMinVoiceHours() * 3_600_000L) {
            return "Você precisa de pelo menos **" + g.reqMinVoiceHours() + "h** em call.";
        }
        if (g.hasWindow() && !windowOk) {
            return "Você precisa ter ficado em call entre **" + g.reqWindowStart() + "h e " + g.reqWindowEnd() + "h**.";
        }
        return null;
    }
}
