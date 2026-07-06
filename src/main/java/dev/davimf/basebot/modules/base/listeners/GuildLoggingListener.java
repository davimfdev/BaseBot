package dev.davimf.basebot.modules.base.listeners;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.util.ChannelLog;
import dev.davimf.basebot.util.Emojis;
import net.dv8tion.jda.api.entities.ScheduledEvent;
import net.dv8tion.jda.api.entities.Invite;
import net.dv8tion.jda.api.events.channel.ChannelCreateEvent;
import net.dv8tion.jda.api.events.channel.ChannelDeleteEvent;
import net.dv8tion.jda.api.events.channel.update.ChannelUpdateArchivedEvent;
import net.dv8tion.jda.api.events.emoji.EmojiAddedEvent;
import net.dv8tion.jda.api.events.emoji.EmojiRemovedEvent;
import net.dv8tion.jda.api.events.emoji.update.EmojiUpdateNameEvent;
import net.dv8tion.jda.api.events.guild.invite.GuildInviteCreateEvent;
import net.dv8tion.jda.api.events.guild.invite.GuildInviteDeleteEvent;
import net.dv8tion.jda.api.events.guild.scheduledevent.ScheduledEventCreateEvent;
import net.dv8tion.jda.api.events.guild.scheduledevent.ScheduledEventDeleteEvent;
import net.dv8tion.jda.api.events.guild.scheduledevent.update.ScheduledEventUpdateStatusEvent;
import net.dv8tion.jda.api.events.guild.update.GuildUpdateBannerEvent;
import net.dv8tion.jda.api.events.guild.update.GuildUpdateIconEvent;
import net.dv8tion.jda.api.events.guild.update.GuildUpdateNameEvent;
import net.dv8tion.jda.api.events.sticker.GuildStickerAddedEvent;
import net.dv8tion.jda.api.events.sticker.GuildStickerRemovedEvent;
import net.dv8tion.jda.api.events.sticker.update.GuildStickerUpdateNameEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/** Logs do servidor em log-servidor: nome/foto/banner, emojis, stickers, convites, eventos
 *  agendados e threads. */
public final class GuildLoggingListener extends ListenerAdapter {

    private final BotContext ctx;

    public GuildLoggingListener(BotContext ctx) {
        this.ctx = ctx;
    }

    private void log(String guildId, String markdown) {
        ChannelLog.post(ctx, guildId, "log-servidor", markdown);
    }

    // --- servidor --------------------------------------------------------------

    @Override
    public void onGuildUpdateName(GuildUpdateNameEvent event) {
        log(event.getGuild().getId(), "## " + Emojis.of(Emojis.SERVER, "🏛️") + " Nome do servidor alterado\n---\n"
                + "**Antes** · `" + event.getOldValue() + "`\n**Depois** · `" + event.getNewValue() + "`");
    }

    @Override
    public void onGuildUpdateIcon(GuildUpdateIconEvent event) {
        log(event.getGuild().getId(), "## " + Emojis.of(Emojis.SERVER_ICON, "🖼️") + " Foto do servidor alterada");
    }

    @Override
    public void onGuildUpdateBanner(GuildUpdateBannerEvent event) {
        log(event.getGuild().getId(), "## " + Emojis.of(Emojis.BANNER, "🎌") + " Banner do servidor alterado");
    }

    // --- convites --------------------------------------------------------------

    @Override
    public void onGuildInviteCreate(GuildInviteCreateEvent event) {
        Invite inv = event.getInvite();
        log(event.getGuild().getId(), "## " + Emojis.of(Emojis.LINK, "🔗") + " Convite criado\n---\n"
                + "**Código** · `" + event.getCode() + "`"
                + "\n" + Emojis.of(Emojis.LOCATION, "📍") + " **Canal** · " + event.getChannel().getAsMention()
                + "\n" + Emojis.of(Emojis.MEMBER, "👤") + " **Por** · " + (inv.getInviter() == null ? "—" : inv.getInviter().getAsMention())
                + "\n---\n" + Emojis.of(Emojis.HOURGLASS, "⏳") + " **Expira** · " + (inv.getMaxAge() == 0 ? "nunca" : "em " + inv.getMaxAge() + "s")
                + "\n" + Emojis.of(Emojis.HASH, "🔢") + " **Usos máx.** · " + (inv.getMaxUses() == 0 ? "ilimitado" : inv.getMaxUses()));
    }

    @Override
    public void onGuildInviteDelete(GuildInviteDeleteEvent event) {
        log(event.getGuild().getId(), "## " + Emojis.of(Emojis.LINK, "🔗") + " Convite removido\n---\n"
                + "**Código** · `" + event.getCode() + "`");
    }

    // --- emojis ----------------------------------------------------------------

    @Override
    public void onEmojiAdded(EmojiAddedEvent event) {
        log(event.getGuild().getId(), "## " + Emojis.of(Emojis.EMOJI_ADD, "😀") + " Emoji adicionado\n---\n"
                + "**Nome** · `" + event.getEmoji().getName() + "`");
    }

    @Override
    public void onEmojiRemoved(EmojiRemovedEvent event) {
        log(event.getGuild().getId(), "## " + Emojis.of(Emojis.EMOJI_REMOVE, "😶") + " Emoji removido\n---\n"
                + "**Nome** · `" + event.getEmoji().getName() + "`");
    }

    @Override
    public void onEmojiUpdateName(EmojiUpdateNameEvent event) {
        log(event.getGuild().getId(), "## " + Emojis.of(Emojis.EDIT, "✏️") + " Emoji renomeado\n---\n"
                + "**Antes** · `" + event.getOldValue() + "`\n**Depois** · `" + event.getNewValue() + "`");
    }

    // --- stickers --------------------------------------------------------------

    @Override
    public void onGuildStickerAdded(GuildStickerAddedEvent event) {
        log(event.getGuild().getId(), "## " + Emojis.of(Emojis.NICKNAME, "🏷️") + " Sticker adicionado\n---\n"
                + "**Nome** · `" + event.getSticker().getName() + "`");
    }

    @Override
    public void onGuildStickerRemoved(GuildStickerRemovedEvent event) {
        log(event.getGuild().getId(), "## " + Emojis.of(Emojis.NICKNAME, "🏷️") + " Sticker removido\n---\n"
                + "**Nome** · `" + event.getSticker().getName() + "`");
    }

    @Override
    public void onGuildStickerUpdateName(GuildStickerUpdateNameEvent event) {
        log(event.getGuild().getId(), "## " + Emojis.of(Emojis.EDIT, "✏️") + " Sticker renomeado\n---\n"
                + "**Antes** · `" + event.getOldValue() + "`\n**Depois** · `" + event.getNewValue() + "`");
    }

    // --- eventos agendados -----------------------------------------------------

    @Override
    public void onScheduledEventCreate(ScheduledEventCreateEvent event) {
        log(event.getGuild().getId(), "## " + Emojis.of(Emojis.CALENDAR, "📅") + " Evento agendado criado\n---\n"
                + "**Nome** · `" + event.getScheduledEvent().getName() + "`");
    }

    @Override
    public void onScheduledEventDelete(ScheduledEventDeleteEvent event) {
        log(event.getGuild().getId(), "## " + Emojis.of(Emojis.CALENDAR, "📅") + " Evento agendado removido\n---\n"
                + "**Nome** · `" + event.getScheduledEvent().getName() + "`");
    }

    @Override
    public void onScheduledEventUpdateStatus(ScheduledEventUpdateStatusEvent event) {
        ScheduledEvent.Status status = event.getNewStatus();
        String label = switch (status) {
            case ACTIVE -> "iniciado";
            case COMPLETED -> "concluído";
            case CANCELED -> "cancelado";
            default -> "atualizado";
        };
        log(event.getGuild().getId(), "## " + Emojis.of(Emojis.CALENDAR, "📅") + " Evento agendado " + label + "\n---\n"
                + "**Nome** · `" + event.getScheduledEvent().getName() + "`");
    }

    // --- threads ---------------------------------------------------------------

    @Override
    public void onChannelCreate(ChannelCreateEvent event) {
        if (!event.getChannel().getType().isThread()) {
            return;
        }
        log(event.getGuild().getId(), "## " + Emojis.of(Emojis.THREAD, "🧵") + " Thread criada\n---\n"
                + "**Thread** · " + event.getChannel().getAsMention());
    }

    @Override
    public void onChannelDelete(ChannelDeleteEvent event) {
        if (!event.getChannel().getType().isThread()) {
            return;
        }
        log(event.getGuild().getId(), "## " + Emojis.of(Emojis.THREAD, "🧵") + " Thread deletada\n---\n"
                + "**Nome** · `" + event.getChannel().getName() + "`");
    }

    @Override
    public void onChannelUpdateArchived(ChannelUpdateArchivedEvent event) {
        if (!event.getChannel().getType().isThread()) {
            return;
        }
        boolean archived = Boolean.TRUE.equals(event.getNewValue());
        log(event.getGuild().getId(), "## " + Emojis.of(Emojis.THREAD, "🧵") + (archived ? " Thread arquivada" : " Thread desarquivada")
                + "\n---\n**Thread** · " + event.getChannel().getAsMention());
    }
}
