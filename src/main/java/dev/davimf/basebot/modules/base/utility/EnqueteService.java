package dev.davimf.basebot.modules.base.utility;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.util.EmbedColor;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/** Enquetes in-memory (uma por mensagem). */
public final class EnqueteService {

    private static final class Poll {
        final String question;
        final List<String> options;
        final String creatorId;
        final ConcurrentHashMap<String, Integer> votes = new ConcurrentHashMap<>();
        Poll(String question, List<String> options, String creatorId) {
            this.question = question;
            this.options = options;
            this.creatorId = creatorId;
        }
    }

    private final BotContext ctx;
    private final ConcurrentHashMap<String, Poll> polls = new ConcurrentHashMap<>();

    public EnqueteService(BotContext ctx) { this.ctx = ctx; }

    public void create(SlashCommandInteractionEvent event, String question, List<String> options) {
        if (!(event.getChannel() instanceof TextChannel)) {
            Replies.ephemeral(event, ctx, "Use num canal de texto.");
            return;
        }
        int accent = accent(event.getGuild() == null ? "0" : event.getGuild().getId());
        Poll poll = new Poll(question, options, event.getUser().getId());
        int[] zero = new int[options.size()];
        event.replyComponents(EnqueteView.panel(accent, question, options, zero, false)).useComponentsV2()
                .queue(hook -> hook.retrieveOriginal().queue(msg -> polls.put(msg.getId(), poll), err -> { }));
    }

    public void vote(ButtonInteractionEvent event, int idx) {
        Poll poll = polls.get(event.getMessageId());
        if (poll == null) {
            Replies.ephemeral(event, ctx, "Essa enquete já encerrou.");
            return;
        }
        if (idx < 0 || idx >= poll.options.size()) {
            return;
        }
        poll.votes.put(event.getUser().getId(), idx);
        edit(event, poll, false);
    }

    public void end(ButtonInteractionEvent event) {
        Poll poll = polls.get(event.getMessageId());
        if (poll == null) {
            Replies.ephemeral(event, ctx, "Essa enquete já encerrou.");
            return;
        }
        if (!event.getUser().getId().equals(poll.creatorId)) {
            Replies.ephemeral(event, ctx, "Só quem criou a enquete pode encerrá-la.");
            return;
        }
        polls.remove(event.getMessageId());
        edit(event, poll, true);
    }

    private void edit(ButtonInteractionEvent event, Poll poll, boolean ended) {
        int accent = accent(event.getGuild() == null ? "0" : event.getGuild().getId());
        int[] counts = PollTally.counts(poll.votes, poll.options.size());
        event.editComponents(EnqueteView.panel(accent, poll.question, poll.options, counts, ended))
                .useComponentsV2().queue(ok -> { }, err -> { });
    }

    private int accent(String guildId) {
        return EmbedColor.resolve(ctx.database().guildConfig().findOrEmpty(guildId));
    }

    public static List<String> parseOptions(String raw) {
        List<String> out = new ArrayList<>();
        for (String s : raw.split("\\|")) {
            if (!s.trim().isEmpty()) {
                out.add(s.trim());
            }
        }
        return out;
    }
}
