package dev.davimf.basebot.modules.base.leveling;

import dev.davimf.basebot.core.component.RankingPanel;
import dev.davimf.basebot.util.Emojis;
import net.dv8tion.jda.api.components.container.Container;

import java.util.ArrayList;
import java.util.List;

/** Leaderboard de nível, paginado (10/página). */
public final class TopView {

    public static final String NS = "lvl";
    public static final int PAGE = 10;

    private TopView() {}

    public static Container panel(int accent, List<UserLevelRepository.Entry> entries, int page, int total) {
        List<String> lines = new ArrayList<>();
        int base = page * PAGE;
        for (int i = 0; i < entries.size(); i++) {
            UserLevelRepository.Entry e = entries.get(i);
            lines.add("`" + (base + i + 1) + ".` <@" + e.userId() + "> · nível `"
                    + LevelFormula.levelForXp(e.xp()) + "` · `" + e.xp() + " XP`");
        }
        return RankingPanel.of(accent, "## " + Emojis.of(Emojis.TROPHY, "🏆") + " Ranking de nível",
                "-# Ninguém pontuou ainda.", lines, NS, page, total, PAGE, null);
    }
}
