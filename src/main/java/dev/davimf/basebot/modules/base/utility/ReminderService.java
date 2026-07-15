package dev.davimf.basebot.modules.base.utility;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.util.EmbedColor;
import dev.davimf.basebot.util.Emojis;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import java.util.List;

/** Dispara os lembretes vencidos: DM (fallback pro canal). À prova de restart. */
public final class ReminderService {

    private final BotContext ctx;
    private final ReminderRepository repo;

    public ReminderService(BotContext ctx) {
        this.ctx = ctx;
        this.repo = new ReminderRepository(ctx.database().sqlite());
    }

    public ReminderRepository repo() { return repo; }

    public void sweep() {
        if (ctx.jda() == null) {
            return;
        }
        for (Reminder r : repo.due(System.currentTimeMillis())) {
            repo.delete(r.id()); // claim: evita entrega dupla no próximo ciclo
            deliver(r);
        }
    }

    private void deliver(Reminder r) {
        int accent = EmbedColor.resolve(ctx.database().guildConfig().findOrEmpty(r.guildId()));
        String text = Emojis.of(Emojis.CLOCK, "⏰") + " **Lembrete:** " + r.message();
        ctx.jda().openPrivateChannelById(r.userId()).queue(
                pc -> pc.sendMessageComponents(Panels.container(accent, Panels.text(text))).useComponentsV2()
                        .queue(ok -> { }, err -> fallback(r, accent, text)),
                err -> fallback(r, accent, text));
    }

    private void fallback(Reminder r, int accent, String text) {
        TextChannel ch = ctx.jda().getTextChannelById(r.channelId());
        if (ch != null) {
            ch.sendMessageComponents(Panels.container(accent, Panels.text("<@" + r.userId() + "> " + text)))
                    .useComponentsV2().setAllowedMentions(List.of(Message.MentionType.USER))
                    .queue(ok -> { }, err -> { });
        }
    }
}
