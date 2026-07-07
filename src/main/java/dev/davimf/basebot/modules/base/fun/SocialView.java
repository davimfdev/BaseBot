package dev.davimf.basebot.modules.base.fun;

import dev.davimf.basebot.core.component.Panels;
import net.dv8tion.jda.api.components.container.Container;

import java.util.List;

public final class SocialView {
    private SocialView() {}

    public static Container ranking(int accent, String titleEmoji, String label, List<SocialRepository.Entry> top) {
        StringBuilder sb = new StringBuilder("## " + titleEmoji + " Ranking de " + label + "\n");
        if (top.isEmpty()) {
            sb.append("-# Ninguém pontuou ainda.");
        } else {
            for (int i = 0; i < top.size(); i++) {
                sb.append("\n`").append(i + 1).append(".` <@").append(top.get(i).userId())
                        .append("> · `").append(top.get(i).points()).append("`");
            }
        }
        return Panels.container(accent, Panels.text(sb.toString()));
    }
}
