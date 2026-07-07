// [OUTLINE START]
// Package: dev.davimf.basebot.modules.base.commands
// 
// Class: ListaCargoCommand
// 
// Methods:
//   - `Method` : `public String name()`
//   - `Method` : `public SlashCommandData data()`
// [OUTLINE END]



package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.modules.base.listacargo.ListaCargoView;
import dev.davimf.basebot.ratelimit.BatchThrottler;
import dev.davimf.basebot.util.EmbedColor;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.List;
import java.util.concurrent.TimeUnit;

/** /listacargo — paginated list of members holding a role (BOTSPECS Module 1). */
public final class ListaCargoCommand implements SlashCommand {

    @Override
    public String name() {
        return "listacargo";
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("listacargo", "Lista os membros de um cargo (paginado).")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MESSAGE_MANAGE))
                .addOption(OptionType.ROLE, "cargo", "Cargo a listar", true)
                .addOption(OptionType.BOOLEAN, "ghost_ping", "Mencionar os membros em lotes (5 a cada 30s)", false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null) {
            Replies.ephemeral(event, ctx, "Use este comando em um servidor.");
            return;
        }
        Role role = event.getOption("cargo", OptionMapping::getAsRole);
        if (role == null) {
            Replies.ephemeral(event, ctx, "Cargo inválido.");
            return;
        }
        List<Member> members = event.getGuild().getMembersWithRoles(role);
        int accent = EmbedColor.resolve(ctx.database().guildConfig().findOrEmpty(event.getGuild().getId()));
        event.replyComponents(ListaCargoView.container(accent, role, members, 0))
                .useComponentsV2()
                .setAllowedMentions(java.util.Collections.emptyList()) // list members without pinging them
                .queue();

        boolean ghostPing = Boolean.TRUE.equals(event.getOption("ghost_ping", OptionMapping::getAsBoolean));
        if (ghostPing && !members.isEmpty() && event.getChannel() instanceof MessageChannel channel) {
            dispatchGhostPings(ctx, event, role, members, channel);
        }
    }

    /**
     * Pings members in throttled batches (5 every 30s by config) to avoid Discord
     * anti-spam flags. Each batch is a mention message deleted shortly after, so a
     * notification fires without leaving a visible message (BOTSPECS §API Limitations).
     */
    private void dispatchGhostPings(BotContext ctx, SlashCommandInteractionEvent event,
                                    Role role, List<Member> members, MessageChannel channel) {
        BatchThrottler throttler = new BatchThrottler(
                ctx.scheduler().executor(),
                ctx.config().rateLimit().ghostPingBatchSize(),
                ctx.config().rateLimit().ghostPingBatchIntervalSeconds(),
                TimeUnit.SECONDS);
        ctx.database().actionLogs().log(event.getGuild().getId(), event.getUser().getId(),
                role.getId(), "GHOST_PING", String.valueOf(members.size()));
        int accent = EmbedColor.resolve(ctx.database().guildConfig().findOrEmpty(event.getGuild().getId()));
        throttler.run(members, batch -> {
            StringBuilder mentions = new StringBuilder();
            for (Member m : batch) {
                mentions.append(m.getAsMention()).append(' ');
            }
            channel.sendMessageComponents(Panels.container(accent, Panels.text(mentions.toString().trim())))
                    .useComponentsV2()
                    .queue(msg -> msg.delete().queueAfter(2, TimeUnit.SECONDS, x -> {}, x -> {}));
        });
    }
}
