package dev.davimf.basebot.modules.base.leveling;

import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.util.Emojis;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;

import java.util.ArrayList;
import java.util.List;

/** Leaderboard paginado (10/página). */
public final class TopView {

    public static final String NS = "lvl";
    public static final int PAGE = 10;

    private TopView() {}

    public static Container panel(int accent, List<UserLevelRepository.Entry> entries, int page, int total) {
        int pages = Math.max(1, (total + PAGE - 1) / PAGE);
        StringBuilder sb = new StringBuilder("## " + Emojis.of(Emojis.TROPHY, "🏆") + " Ranking de nível\n");
        if (entries.isEmpty()) {
            sb.append("-# Ninguém pontuou ainda.");
        } else {
            int base = page * PAGE;
            for (int i = 0; i < entries.size(); i++) {
                UserLevelRepository.Entry e = entries.get(i);
                int lvl = LevelFormula.levelForXp(e.xp());
                sb.append("\n`").append(base + i + 1).append(".` <@").append(e.userId())
                        .append("> · nível `").append(lvl).append("` · `").append(e.xp()).append(" XP`");
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
