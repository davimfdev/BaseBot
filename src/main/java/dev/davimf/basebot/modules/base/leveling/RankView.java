package dev.davimf.basebot.modules.base.leveling;

import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.util.Emojis;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.entities.Member;

/** Cartão de rank em Components V2 (consome o mesmo RankData de um renderer de imagem futuro). */
public final class RankView {

    private static final int BAR = 12;

    private RankView() {}

    public static Container panel(int accent, Member member, RankData d) {
        long into = d.into();
        long needed = d.needed();
        int filled = needed <= 0 ? BAR : (int) Math.min(BAR, (into * BAR) / needed);
        String bar = "`[" + "█".repeat(filled) + "░".repeat(BAR - filled) + "]`";
        String rank = d.rank() > 0 ? "#" + d.rank() : "—";
        String body = "## " + Emojis.of(Emojis.RANK, "📊") + " Rank de " + member.getEffectiveName() + "\n"
                + Emojis.of(Emojis.STAR, "⭐") + " **Nível** · `" + d.level() + "`\n"
                + Emojis.of(Emojis.GROWTH, "📈") + " **Posição** · `" + rank + "`\n"
                + Emojis.of(Emojis.STATS, "🔢") + " **XP** · `" + into + "/" + needed + "` (total `" + d.xpTotal() + "`)\n"
                + bar;
        return Panels.container(accent, Panels.text(body));
    }
}
