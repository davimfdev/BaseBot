package dev.davimf.basebot.modules.base.listeners;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.util.ChannelLog;
import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.audit.AuditLogEntry;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.events.guild.GuildBanEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.utils.TimeFormat;

import java.time.OffsetDateTime;

/**
 * General event logging (BOTSPECS §General Logging). Persists a fast local record to
 * SQLite (action_logs) and posts a Components V2 embed to the matching per-type log
 * channel configured in {@code /setup → Logs} (one channel per event type).
 *
 * <p>Some events require privileged intents to fire (member join/leave → GUILD_MEMBERS;
 * full message content → MESSAGE_CONTENT). The handlers degrade gracefully to whatever
 * data is available and become fully detailed once those intents are enabled.
 */
public final class GeneralLoggingListener extends ListenerAdapter {

    private final BotContext ctx;

    public GeneralLoggingListener(BotContext ctx) {
        this.ctx = ctx;
    }

    // --- commands --------------------------------------------------------------

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getGuild() == null) {
            return; // guild-scoped logging only (Golden Rule: never global state)
        }
        ctx.database().actionLogs().log(event.getGuild().getId(), event.getUser().getId(), null,
                "COMMAND_EXEC", "/" + event.getFullCommandName());
        ChannelLog.post(ctx, event.getGuild().getId(), "log-comandos",
                "## 💬 Comando\n**Comando:** `/" + event.getFullCommandName() + "`\n"
                        + "**Por:** " + event.getUser().getAsMention() + "\n"
                        + "**Canal:** " + (event.getChannel() == null ? "—" : event.getChannel().getAsMention()));
    }

    // --- messages --------------------------------------------------------------

    @Override
    public void onMessageDelete(MessageDeleteEvent event) {
        if (!event.isFromGuild()) {
            return;
        }
        ChannelLog.post(ctx, event.getGuild().getId(), "log-msgdel",
                "## 🗑️ Mensagem apagada\n**Canal:** " + event.getChannel().getAsMention()
                        + "\n**ID:** `" + event.getMessageId() + "`");
    }

    @Override
    public void onMessageUpdate(MessageUpdateEvent event) {
        if (!event.isFromGuild() || event.getAuthor().isBot()) {
            return;
        }
        String content = event.getMessage().getContentDisplay();
        ChannelLog.post(ctx, event.getGuild().getId(), "log-msgedit",
                "## ✏️ Mensagem editada\n**Autor:** " + event.getAuthor().getAsMention()
                        + "\n**Canal:** " + event.getChannel().getAsMention()
                        + (content.isBlank() ? "" : "\n**Agora:** " + trim(content)));
    }

    // --- membership ------------------------------------------------------------

    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        User user = event.getUser();
        ChannelLog.post(ctx, event.getGuild().getId(), "log-entradas",
                "## 📥 Entrou\n**Membro:** " + user.getAsMention() + " (`" + user.getId() + "`)\n"
                        + "**Conta criada:** " + TimeFormat.RELATIVE.format(user.getTimeCreated()));
    }

    @Override
    public void onGuildMemberRemove(GuildMemberRemoveEvent event) {
        User user = event.getUser();
        ChannelLog.post(ctx, event.getGuild().getId(), "log-saidas",
                "## 📤 Saiu\n**Membro:** " + user.getAsTag() + " (`" + user.getId() + "`)");
        // Distinguish a kick from a voluntary leave via the audit log (best-effort).
        logFromAudit(event.getGuild(), user, ActionType.KICK, "log-kicks", "👢 Expulso (kick)");
    }

    // --- moderation ------------------------------------------------------------

    @Override
    public void onGuildBan(GuildBanEvent event) {
        ctx.database().actionLogs().log(event.getGuild().getId(), null, event.getUser().getId(),
                "BAN", null);
        logFromAudit(event.getGuild(), event.getUser(), ActionType.BAN, "log-bans", "🔨 Banido");
    }

    // --- voice -----------------------------------------------------------------

    @Override
    public void onGuildVoiceUpdate(GuildVoiceUpdateEvent event) {
        AudioChannel left = event.getChannelLeft();
        AudioChannel joined = event.getChannelJoined();
        String member = event.getMember().getAsMention();
        String body;
        if (left == null && joined != null) {
            body = "## 🔊 Entrou em call\n" + member + " → " + joined.getAsMention();
        } else if (left != null && joined == null) {
            body = "## 🔇 Saiu da call\n" + member + " ← " + left.getAsMention();
        } else if (left != null) {
            body = "## 🔁 Mudou de call\n" + member + ": " + left.getAsMention() + " → " + joined.getAsMention();
        } else {
            return;
        }
        ChannelLog.post(ctx, event.getGuild().getId(), "log-voz", body);
    }

    // --- helpers ---------------------------------------------------------------

    /**
     * Posts a moderation log entry enriched with the moderator + reason from the audit
     * log when a matching recent entry is found. Requires VIEW_AUDIT_LOGS; fails silently.
     */
    private void logFromAudit(Guild guild, User target, ActionType type, String logKey, String title) {
        guild.retrieveAuditLogs().type(type).limit(6).queue(entries -> {
            AuditLogEntry hit = entries.stream()
                    .filter(e -> target.getId().equals(e.getTargetId()))
                    .filter(e -> e.getTimeCreated().isAfter(OffsetDateTime.now().minusSeconds(15)))
                    .findFirst().orElse(null);
            if (hit == null) {
                return;
            }
            String moderator = hit.getUser() == null ? "—" : hit.getUser().getAsMention();
            String reason = hit.getReason() == null || hit.getReason().isBlank()
                    ? "*sem motivo*" : hit.getReason();
            ChannelLog.post(ctx, guild.getId(), logKey, "## " + title + "\n"
                    + "**Membro:** " + target.getAsTag() + " (`" + target.getId() + "`)\n"
                    + "**Responsável:** " + moderator + "\n**Motivo:** " + reason);
        }, err -> { });
    }

    private static String trim(String s) {
        return s.length() > 500 ? s.substring(0, 500) + "…" : s;
    }
}
