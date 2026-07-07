package dev.davimf.basebot.modules.base.utility;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.util.EmbedColor;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.List;
import java.util.concurrent.TimeUnit;

/** Remove o AFK de quem volta a falar e avisa quando alguém AFK é mencionado. */
public final class AfkListener extends ListenerAdapter {

    private final BotContext ctx;
    private final AfkRegistry registry;

    public AfkListener(BotContext ctx, AfkRegistry registry) {
        this.ctx = ctx;
        this.registry = registry;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (!event.isFromGuild() || event.getAuthor().isBot()) {
            return;
        }
        String guildId = event.getGuild().getId();
        int accent = EmbedColor.resolve(ctx.database().guildConfig().findOrEmpty(guildId));

        if (registry.remove(guildId, event.getAuthor().getId()).isPresent()) {
            event.getChannel().sendMessageComponents(Panels.container(accent,
                            Panels.text("👋 Bem-vindo de volta, " + event.getAuthor().getAsMention() + "! Removi seu AFK.")))
                    .useComponentsV2().setAllowedMentions(List.of())
                    .queue(msg -> msg.delete().queueAfter(10, TimeUnit.SECONDS, null, err -> { }), err -> { });
        }

        for (Member m : event.getMessage().getMentions().getMembers()) {
            registry.get(guildId, m.getId()).ifPresent(afk -> event.getChannel().sendMessageComponents(
                            Panels.container(accent, Panels.text(m.getAsMention() + " está **AFK**: " + afk.reason()
                                    + " (desde <t:" + (afk.since() / 1000) + ":R>)")))
                    .useComponentsV2().setAllowedMentions(List.of(Message.MentionType.USER))
                    .queue(msg -> msg.delete().queueAfter(15, TimeUnit.SECONDS, null, e -> { }), e -> { }));
        }
    }
}
