package dev.davimf.basebot.modules.base.economy;

import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.util.Emojis;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.entities.Member;

public final class SaldoView {
    private SaldoView() {}

    public static Container panel(int accent, Member member, WalletRepository.Wallet w, int rank, GuildConfig cfg) {
        long total = w.cash() + w.bank();
        String pos = rank > 0 ? "#" + rank : "—";
        String body = "## " + Emojis.of(Emojis.MONEY, "💰") + " Saldo de " + member.getEffectiveName() + "\n"
                + Emojis.of(Emojis.CASH, "💵") + " **Carteira** · " + EconomyFormat.format(w.cash(), cfg) + "\n"
                + Emojis.of(Emojis.BANK, "🏦") + " **Banco** · " + EconomyFormat.format(w.bank(), cfg) + "\n"
                + Emojis.of(Emojis.GEM, "💠") + " **Total** · " + EconomyFormat.formatNamed(total, cfg) + "\n"
                + Emojis.of(Emojis.GROWTH, "📈") + " **Posição** · `" + pos + "`";
        return Panels.container(accent, Panels.text(body));
    }
}
