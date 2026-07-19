package dev.davimf.basebot.modules.base.vip;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.util.EmbedColor;
import dev.davimf.basebot.util.Emojis;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.List;

/** /vip revogar — revoga o VIP ativo de um membro (BOTSPECS VIPs). */
public final class VipRevogarCommand implements SlashCommand {

    private final VipService vip;

    public VipRevogarCommand(VipService vip) {
        this.vip = vip;
    }

    @Override public String name() { return "revogar"; }

    @Override public SlashCommandData data() {
        return Commands.slash("revogar", "Revoga o VIP ativo de um membro.")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_ROLES))
                .addOption(OptionType.USER, "membro", "Membro a ter o VIP revogado", true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null || event.getMember() == null) {
            Replies.ephemeral(event, ctx, "Use este comando em um servidor.");
            return;
        }
        User target = event.getOption("membro", OptionMapping::getAsUser);
        if (target == null) {
            Replies.ephemeral(event, ctx, "Membro inválido.");
            return;
        }
        Guild guild = event.getGuild();
        String userId = target.getId();
        String actorId = event.getUser().getId();

        event.deferReply(true).queue();
        ctx.scheduler().executor().execute(() -> {
            boolean hadActive = vip.activeGrant(guild.getId(), userId).isPresent();
            vip.revoke(guild, userId, actorId, false);
            String msg = hadActive
                    ? Emojis.of(Emojis.GEM, "💎") + " VIP de " + target.getAsMention() + " revogado."
                    : target.getAsMention() + " não tem um VIP ativo.";
            int accent = EmbedColor.resolve(ctx.database().guildConfig().findOrEmpty(guild.getId()));
            event.getHook().editOriginalComponents(Panels.container(accent, Panels.text(msg)))
                    .useComponentsV2().setAllowedMentions(List.of()).queue(ok -> { }, err -> { });
        });
    }
}
