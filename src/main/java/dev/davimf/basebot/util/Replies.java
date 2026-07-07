package dev.davimf.basebot.util;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.component.Panels;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Wraps interaction replies in Components V2 containers so every bot message renders as an
 * accented panel instead of plain text (project-wide rule: no bot message is ever plain text).
 * Panels are tinted with the guild's configured embed colour. Mentions are always suppressed —
 * these are replies/confirmations, never pings.
 *
 * <p>For channel posts and DMs, build a {@link Panels#container} and send it with
 * {@code sendMessageComponents(...).useComponentsV2()} directly.
 */
public final class Replies {

    /** Public command-confirmation replies self-delete after this many seconds to avoid clutter. */
    private static final long AUTO_DELETE_SECONDS = 8;

    private Replies() {}

    /** Ephemeral container reply tinted with the guild's configured colour. */
    public static void ephemeral(IReplyCallback event, BotContext ctx, String markdown) {
        ephemeral(event, accent(event, ctx), markdown);
    }

    /** Ephemeral container reply with an explicit accent colour. */
    public static void ephemeral(IReplyCallback event, int accent, String markdown) {
        event.replyComponents(panel(accent, markdown)).useComponentsV2()
                .setEphemeral(true).setAllowedMentions(List.of()).queue();
    }

    /** Visible container reply tinted with the guild's configured colour. */
    public static void reply(IReplyCallback event, BotContext ctx, String markdown) {
        reply(event, accent(event, ctx), markdown);
    }

    /** Visible container reply with an explicit accent colour. These are command confirmations,
     *  so the message auto-deletes after {@link #AUTO_DELETE_SECONDS}s to keep channels clean. */
    public static void reply(IReplyCallback event, int accent, String markdown) {
        event.replyComponents(panel(accent, markdown)).useComponentsV2()
                .setAllowedMentions(List.of())
                .queue(ok -> event.getHook().deleteOriginal()
                        .queueAfter(AUTO_DELETE_SECONDS, TimeUnit.SECONDS, null, err -> { }));
    }

    /** Container follow-up sent through the deferred-interaction hook (after deferReply). */
    public static void hook(IReplyCallback event, BotContext ctx, String markdown) {
        event.getHook().sendMessageComponents(panel(accent(event, ctx), markdown))
                .useComponentsV2().setAllowedMentions(List.of()).queue();
    }

    /** Ephemeral container follow-up through the deferred-interaction hook. */
    public static void hookEphemeral(IReplyCallback event, BotContext ctx, String markdown) {
        event.getHook().sendMessageComponents(panel(accent(event, ctx), markdown))
                .useComponentsV2().setEphemeral(true).setAllowedMentions(List.of()).queue();
    }

    /** Resolves the guild's configured embed colour, falling back to the global default. */
    public static int accent(IReplyCallback event, BotContext ctx) {
        String guildId = event.getGuild() == null ? "0" : event.getGuild().getId();
        return EmbedColor.resolve(ctx.database().guildConfig().findOrEmpty(guildId));
    }

    private static Container panel(int accent, String markdown) {
        return Panels.container(accent, Panels.text(markdown));
    }
}
