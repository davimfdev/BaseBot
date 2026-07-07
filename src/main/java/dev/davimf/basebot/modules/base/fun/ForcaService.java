package dev.davimf.basebot.modules.base.fun;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.util.EmbedColor;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.concurrent.ConcurrentHashMap;

/** Coordena jogos de forca (in-memory, um por canal). */
public final class ForcaService {

    private static final class Game {
        volatile String messageId;
        HangmanState state;
        final String theme;
        Game(HangmanState state, String theme) { this.state = state; this.theme = theme; }
    }

    private final BotContext ctx;
    private final ConcurrentHashMap<String, Game> games = new ConcurrentHashMap<>();

    public ForcaService(BotContext ctx) { this.ctx = ctx; }

    public void start(SlashCommandInteractionEvent event) {
        if (!(event.getChannel() instanceof TextChannel channel)) {
            Replies.ephemeral(event, ctx, "Use num canal de texto.");
            return;
        }
        String chId = channel.getId();
        if (games.containsKey(chId)) {
            Replies.ephemeral(event, ctx, "Já tem uma forca rolando neste canal.");
            return;
        }
        HangmanBank.Entry entry = HangmanBank.random();
        Game game = new Game(HangmanState.start(entry.word()), entry.theme());
        games.put(chId, game);
        int accent = accent(event.getGuild() == null ? "0" : event.getGuild().getId());
        event.replyComponents(ForcaView.panel(accent, game.state, game.theme)).useComponentsV2()
                .queue(hook -> hook.retrieveOriginal().queue(msg -> game.messageId = msg.getId(), err -> { }));
    }

    public void onMessage(MessageReceivedEvent event) {
        if (!event.isFromGuild() || event.getAuthor().isBot()) {
            return;
        }
        String chId = event.getChannel().getId();
        Game game = games.get(chId);
        if (game == null || !(event.getChannel() instanceof TextChannel channel)) {
            return;
        }
        String content = event.getMessage().getContentRaw().trim();
        if (content.isEmpty()) {
            return;
        }
        boolean changed;
        synchronized (game) {
            HangmanState before = game.state;
            if (content.length() == 1 && Character.isLetter(content.charAt(0))) {
                game.state = game.state.guessLetter(content.charAt(0));
            } else if (content.chars().allMatch(Character::isLetter)) {
                game.state = game.state.guessWord(content);
            }
            changed = game.state != before;
        }
        if (!changed) {
            return;
        }
        int accent = accent(event.getGuild().getId());
        HangmanState s = game.state;
        if (s.won() || s.lost()) {
            games.remove(chId);
            if (game.messageId != null) {
                channel.editMessageComponentsById(game.messageId, ForcaView.ended(accent, s, s.won()))
                        .useComponentsV2().queue(ok -> { }, err -> { });
            }
        } else if (game.messageId != null) {
            channel.editMessageComponentsById(game.messageId, ForcaView.panel(accent, s, game.theme))
                    .useComponentsV2().queue(ok -> { }, err -> { });
        }
    }

    private int accent(String guildId) {
        return EmbedColor.resolve(ctx.database().guildConfig().findOrEmpty(guildId));
    }
}
