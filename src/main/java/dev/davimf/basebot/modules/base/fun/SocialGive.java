package dev.davimf.basebot.modules.base.fun;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.util.EmbedColor;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

/** Fluxo compartilhado de /rep e /biscoito. */
public final class SocialGive {

    private static final long COOLDOWN_MS = 24L * 60 * 60 * 1000;

    private SocialGive() {}

    public static void handle(SlashCommandInteractionEvent event, BotContext ctx, String type,
                              String label, String titleEmoji) {
        if (event.getGuild() == null || event.getMember() == null) {
            Replies.ephemeral(event, ctx, "Use este comando em um servidor.");
            return;
        }
        SocialRepository repo = new SocialRepository(ctx.database().sqlite());
        String g = event.getGuild().getId();
        int accent = EmbedColor.resolve(ctx.database().guildConfig().findOrEmpty(g));
        Member target = event.getOption("usuario", OptionMapping::getAsMember);
        if (target == null) {
            event.replyComponents(SocialView.ranking(accent, titleEmoji, label, repo.top(g, type, 10)))
                    .useComponentsV2().setEphemeral(true).queue();
            return;
        }
        if (target.getUser().isBot() || target.getId().equals(event.getUser().getId())) {
            Replies.ephemeral(event, ctx, "Você não pode dar " + label + " para si mesmo ou para um bot.");
            return;
        }
        SocialRepository.GiveResult r = repo.give(g, event.getUser().getId(), target.getId(), type,
                System.currentTimeMillis(), COOLDOWN_MS);
        if (!r.ok()) {
            Replies.ephemeral(event, ctx, "Você já deu " + label + " hoje. Tente de novo <t:"
                    + (r.readyAt() / 1000) + ":R>.");
            return;
        }
        Replies.reply(event, ctx, titleEmoji + " " + event.getMember().getAsMention() + " deu " + label
                + " para " + target.getAsMention() + "! Agora ele(a) tem **" + r.newPoints() + "**.");
    }
}
