package dev.davimf.basebot.modules.base.fun;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.modules.base.events.QuizBank;
import dev.davimf.basebot.util.EmbedColor;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/** Quiz personalizado em jogo (in-memory, um por canal). Fallback pro banco dos eventos. */
public final class QuizPlayService {

    private record Active(String messageId, String question, List<String> options, int correct) {}

    private record Prepared(String question, List<String> options, int correct) {}

    private final BotContext ctx;
    private final QuizRepository repo;
    private final ConcurrentHashMap<String, Active> active = new ConcurrentHashMap<>();

    public QuizPlayService(BotContext ctx) {
        this.ctx = ctx;
        this.repo = new QuizRepository(ctx.database().postgres());
    }

    public void start(SlashCommandInteractionEvent event) {
        if (!(event.getChannel() instanceof TextChannel channel)) {
            Replies.ephemeral(event, ctx, "Use num canal de texto.");
            return;
        }
        String chId = channel.getId();
        if (active.containsKey(chId)) {
            Replies.ephemeral(event, ctx, "Já tem um quiz rolando neste canal.");
            return;
        }
        String guildId = event.getGuild() == null ? "0" : event.getGuild().getId();
        Prepared p = prepare(guildId);
        int accent = EmbedColor.resolve(ctx.database().guildConfig().findOrEmpty(guildId));
        event.replyComponents(QuizPlayView.panel(accent, p.question(), p.options())).useComponentsV2()
                .queue(hook -> hook.retrieveOriginal().queue(msg -> {
                    active.put(chId, new Active(msg.getId(), p.question(), p.options(), p.correct()));
                    ctx.scheduler().once(() -> expire(chId, msg.getId()), 60, TimeUnit.SECONDS);
                }, err -> { }));
    }

    public void onAnswer(ButtonInteractionEvent event, int clicked) {
        String chId = event.getChannel().getId();
        Active a = active.get(chId);
        if (a == null) {
            Replies.ephemeral(event, ctx, "Esse quiz já encerrou.");
            return;
        }
        if (clicked != a.correct()) {
            Replies.ephemeral(event, ctx, "Resposta errada!");
            return;
        }
        if (!active.remove(chId, a)) {
            Replies.ephemeral(event, ctx, "Alguém já acertou!");
            return;
        }
        int accent = EmbedColor.resolve(ctx.database().guildConfig()
                .findOrEmpty(event.getGuild() == null ? "0" : event.getGuild().getId()));
        event.editComponents(QuizPlayView.answered(accent, a.question(),
                        event.getUser().getAsMention(), a.options().get(a.correct())))
                .useComponentsV2().queue(ok -> { }, err -> { });
    }

    private void expire(String chId, String messageId) {
        Active a = active.get(chId);
        if (a != null && a.messageId().equals(messageId) && active.remove(chId, a)) {
            TextChannel ch = ctx.jda() == null ? null : ctx.jda().getTextChannelById(chId);
            if (ch != null) {
                ch.editMessageComponentsById(messageId, QuizPlayView.answered(
                                EmbedColor.resolve(ctx.database().guildConfig().findOrEmpty("0")),
                                a.question(), "*Ninguém*", a.options().get(a.correct())))
                        .useComponentsV2().queue(ok -> { }, err -> { });
            }
        }
    }

    private Prepared prepare(String guildId) {
        Optional<QuizQuestion> custom = repo.random(guildId);
        if (custom.isPresent()) {
            QuizQuestion q = custom.get();
            List<String> opts = new ArrayList<>(List.of(q.correct(), q.wrong1(), q.wrong2(), q.wrong3()));
            Collections.shuffle(opts);
            return new Prepared(q.question(), opts, opts.indexOf(q.correct()));
        }
        QuizBank.Question q = QuizBank.random();
        return new Prepared(q.text(), q.options(), q.correct());
    }
}
