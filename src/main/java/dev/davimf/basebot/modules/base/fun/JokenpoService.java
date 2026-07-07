package dev.davimf.basebot.modules.base.fun;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.util.EmbedColor;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/** Coordena partidas de jokenpo (in-memory, uma por mensagem-desafio). */
public final class JokenpoService {

    /** Partida: escolhas com sincronização no próprio objeto. */
    static final class Match {
        final String challengerId;
        final String targetId;
        JokenpoResult.Choice choiceA;
        JokenpoResult.Choice choiceB;

        Match(String challengerId, String targetId) {
            this.challengerId = challengerId;
            this.targetId = targetId;
        }

        synchronized boolean vote(String userId, JokenpoResult.Choice c) {
            if (userId.equals(challengerId)) {
                choiceA = c;
            } else if (userId.equals(targetId)) {
                choiceB = c;
            } else {
                return false;
            }
            return true;
        }

        synchronized boolean complete() {
            return choiceA != null && choiceB != null;
        }
    }

    private final BotContext ctx;
    private final ConcurrentHashMap<String, Match> matches = new ConcurrentHashMap<>();

    public JokenpoService(BotContext ctx) { this.ctx = ctx; }

    public void register(String messageId, String challengerId, String targetId) {
        matches.put(messageId, new Match(challengerId, targetId));
        ctx.scheduler().once(() -> matches.remove(messageId), 60, TimeUnit.SECONDS);
    }

    public void onPick(ButtonInteractionEvent event, JokenpoResult.Choice choice) {
        String messageId = event.getMessageId();
        Match m = matches.get(messageId);
        if (m == null) {
            Replies.ephemeral(event, ctx, "Esse jogo já encerrou.");
            return;
        }
        String uid = event.getUser().getId();
        if (!uid.equals(m.challengerId) && !uid.equals(m.targetId)) {
            Replies.ephemeral(event, ctx, "Você não faz parte deste jogo.");
            return;
        }
        if (!m.vote(uid, choice)) {
            return;
        }
        Replies.ephemeral(event, ctx, "Você escolheu **" + choice.name().toLowerCase() + "**.");
        if (m.complete() && matches.remove(messageId, m)) {
            reveal(event, m);
        }
    }

    private void reveal(ButtonInteractionEvent event, Match m) {
        int accent = EmbedColor.resolve(ctx.database().guildConfig()
                .findOrEmpty(event.getGuild() == null ? "0" : event.getGuild().getId()));
        JokenpoResult.Outcome o = JokenpoResult.decide(m.choiceA, m.choiceB);
        String line = "<@" + m.challengerId + "> escolheu **" + m.choiceA.name().toLowerCase() + "**\n"
                + "<@" + m.targetId + "> escolheu **" + m.choiceB.name().toLowerCase() + "**\n\n"
                + switch (o) {
                    case WIN_A -> "🏆 <@" + m.challengerId + "> venceu!";
                    case WIN_B -> "🏆 <@" + m.targetId + "> venceu!";
                    case TIE -> "🤝 Empate!";
                };
        if (event.getChannel() instanceof TextChannel ch) {
            ch.editMessageComponentsById(event.getMessageId(), JokenpoView.result(accent, line))
                    .useComponentsV2().queue(ok -> { }, err -> { });
        }
    }
}
