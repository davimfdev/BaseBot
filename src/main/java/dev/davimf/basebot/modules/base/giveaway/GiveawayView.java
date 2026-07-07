package dev.davimf.basebot.modules.base.giveaway;

import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.util.Emojis;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;

import java.util.ArrayList;
import java.util.List;

/** Render dos painéis de sorteio (namespace "gwy"). */
public final class GiveawayView {

    public static final String NS = "gwy";

    private GiveawayView() {}

    public static Container panel(int accent, Giveaway g, int entryCount) {
        StringBuilder head = new StringBuilder("## " + Emojis.of(Emojis.GIFT, "🎉") + " Sorteio: " + g.prize() + "\n");
        head.append(Emojis.of(Emojis.CLOCK, "⏰")).append(" Encerra <t:").append(g.endsAt() / 1000).append(":R>\n");
        head.append(Emojis.of(Emojis.MEMBERS, "👥")).append(" Participantes: `").append(entryCount).append("`\n");
        head.append(Emojis.of(Emojis.TROPHY, "🏆")).append(" Ganhadores: `").append(g.winners()).append("`");
        if (g.coinReward() > 0) {
            head.append(" · ").append(Emojis.of(Emojis.MONEY, "💰")).append(" `").append(g.coinReward()).append("` moedas");
        }

        List<ContainerChildComponent> kids = new ArrayList<>();
        kids.add(Panels.text(head.toString()));
        List<String> reqs = requirements(g);
        if (!reqs.isEmpty()) {
            kids.add(Panels.divider());
            StringBuilder rb = new StringBuilder(Emojis.of(Emojis.INFO, "ℹ️") + " **Requisitos:**\n");
            for (String r : reqs) {
                rb.append("• ").append(r).append("\n");
            }
            kids.add(Panels.text(rb.toString().strip()));
        }
        kids.add(Panels.text("-# id: `" + g.id() + "`"));
        kids.add(Panels.divider());
        kids.add(ActionRow.of(Button.success(ComponentId.of(NS, "enter", g.id()), "Participar")
                .withEmoji(Emojis.button(Emojis.GIFT))));
        return Panels.container(accent, kids.toArray(new ContainerChildComponent[0]));
    }

    public static Container ended(int accent, Giveaway g, List<String> winnerMentions) {
        String winners = winnerMentions.isEmpty() ? "*sem ganhadores*" : String.join(", ", winnerMentions);
        String body = Emojis.of(Emojis.TROPHY, "🏆") + " **Ganhador(es):** " + winners
                + (g.coinReward() > 0 ? "\n" + Emojis.of(Emojis.MONEY, "💰") + " **+" + g.coinReward()
                + "** moedas para cada um." : "");
        return Panels.container(accent,
                Panels.text("## " + Emojis.of(Emojis.HIGHLIGHT, "🏆") + " Sorteio encerrado: " + g.prize()),
                Panels.divider(),
                Panels.text(body));
    }

    private static List<String> requirements(Giveaway g) {
        List<String> r = new ArrayList<>();
        if (g.reqRoleId() != null && !g.reqRoleId().isBlank()) {
            r.add("Ter o cargo <@&" + g.reqRoleId() + ">");
        }
        if (g.reqMinDays() > 0) {
            r.add("Estar há " + g.reqMinDays() + "+ dia(s) no servidor");
        }
        if (g.reqMinVoiceHours() > 0) {
            r.add("Ter " + g.reqMinVoiceHours() + "h+ em call");
        }
        if (g.hasWindow()) {
            r.add("Ter ficado em call entre " + g.reqWindowStart() + "h e " + g.reqWindowEnd() + "h");
        }
        return r;
    }
}
