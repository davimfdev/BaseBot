package dev.davimf.basebot.modules.base.economy;

import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.util.Emojis;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;

import java.util.ArrayList;
import java.util.List;

/** Leaderboard de riqueza (total carteira+banco), paginado (10/página). */
public final class RicoView {

    public static final String NS = "eco";
    public static final int PAGE = 10;

    private RicoView() {}

    public static Container panel(int accent, List<WalletRepository.Entry> entries, int page, int total, GuildConfig cfg) {
        int pages = Math.max(1, (total + PAGE - 1) / PAGE);
        StringBuilder sb = new StringBuilder("## " + Emojis.of(Emojis.TROPHY, "🏆") + " Ranking de riqueza\n");
        if (entries.isEmpty()) {
            sb.append("-# Ninguém tem moedas ainda.");
        } else {
            int base = page * PAGE;
            for (int i = 0; i < entries.size(); i++) {
                WalletRepository.Entry e = entries.get(i);
                sb.append("\n`").append(base + i + 1).append(".` <@").append(e.userId())
                        .append("> · ").append(EconomyFormat.format(e.total(), cfg));
            }
        }
        List<ContainerChildComponent> kids = new ArrayList<>();
        kids.add(Panels.text(sb.toString()));
        kids.add(Panels.divider());
        kids.add(Panels.text("-# Página " + (page + 1) + "/" + pages));
        if (pages > 1) {
            kids.add(ActionRow.of(
                    Button.secondary(ComponentId.of(NS, "top", String.valueOf(page - 1)), "◀")
                            .withDisabled(page <= 0),
                    Button.secondary(ComponentId.of(NS, "top", String.valueOf(page + 1)), "▶")
                            .withDisabled(page >= pages - 1)));
        }
        return Panels.container(accent, kids.toArray(new ContainerChildComponent[0]));
    }
}
