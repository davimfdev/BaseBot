package dev.davimf.basebot.modules.base.leveling;

import dev.davimf.basebot.core.component.RankingPanel;
import dev.davimf.basebot.util.Emojis;
import net.dv8tion.jda.api.components.container.Container;

import java.util.ArrayList;
import java.util.List;

/** Ranking semanal de tempo em call, paginado (10/página). */
public final class TopCallView {

    public static final String NS = "vtime";
    public static final int PAGE = 10;

    private TopCallView() {}

    public static Container panel(int accent, List<VoiceTimeRepository.Entry> entries, int page, int total) {
        List<String> lines = new ArrayList<>();
        int base = page * PAGE;
        for (int i = 0; i < entries.size(); i++) {
            VoiceTimeRepository.Entry e = entries.get(i);
            lines.add("`" + (base + i + 1) + ".` <@" + e.userId() + "> — `"
                    + VoiceFormat.duration(e.ms()) + "`");
        }
        return RankingPanel.of(accent, "## " + Emojis.of(Emojis.TROPHY, "🏆") + " Ranking de call — esta semana",
                "-# Ninguém entrou em call esta semana.", lines, NS, page, total, PAGE,
                "zera toda segunda 00:00");
    }
}
