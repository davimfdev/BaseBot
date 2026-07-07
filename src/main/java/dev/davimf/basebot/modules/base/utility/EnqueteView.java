package dev.davimf.basebot.modules.base.utility;

import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.util.Emojis;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;

import java.util.ArrayList;
import java.util.List;

public final class EnqueteView {
    public static final String NS = "enq";

    private EnqueteView() {}

    public static Container panel(int accent, String question, List<String> options, int[] counts, boolean ended) {
        int total = 0;
        for (int c : counts) {
            total += c;
        }
        StringBuilder sb = new StringBuilder("## " + Emojis.of(Emojis.LIST, "📊") + " " + question + "\n");
        for (int i = 0; i < options.size(); i++) {
            sb.append("\n**").append(options.get(i)).append("**\n").append(PollTally.bar(counts[i], total));
        }
        sb.append("\n\n-# ").append(total).append(" voto(s)").append(ended ? " · **encerrada**" : "");

        List<ContainerChildComponent> kids = new ArrayList<>();
        kids.add(Panels.text(sb.toString()));
        if (!ended) {
            kids.add(Panels.divider());
            List<Button> voteButtons = new ArrayList<>();
            for (int i = 0; i < options.size(); i++) {
                voteButtons.add(Button.secondary(ComponentId.of(NS, "vote", String.valueOf(i)), trim(options.get(i))));
            }
            for (int i = 0; i < voteButtons.size(); i += 5) {
                kids.add(ActionRow.of(voteButtons.subList(i, Math.min(i + 5, voteButtons.size()))));
            }
            kids.add(ActionRow.of(Button.danger(ComponentId.of(NS, "end"), "Encerrar")));
        }
        return Panels.container(accent, kids.toArray(new ContainerChildComponent[0]));
    }

    private static String trim(String s) {
        return s.length() > 78 ? s.substring(0, 78) : s;
    }
}
