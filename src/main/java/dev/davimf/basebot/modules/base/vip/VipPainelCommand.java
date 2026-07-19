package dev.davimf.basebot.modules.base.vip;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/** /vip painel — abre o painel ephemeral do VIP ativo do autor (estado + controles). */
public final class VipPainelCommand implements SlashCommand {

    private final VipService vip;

    public VipPainelCommand(VipService vip) {
        this.vip = vip;
    }

    @Override public String name() { return "painel"; }

    @Override public SlashCommandData data() {
        return Commands.slash("painel", "Abre o painel do seu VIP ativo.");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        Guild guild = event.getGuild();
        if (guild == null) {
            Replies.ephemeral(event, ctx, "Use este comando em um servidor.");
            return;
        }
        var grant = vip.activeGrant(guild.getId(), event.getUser().getId()).orElse(null);
        if (grant == null) {
            Replies.ephemeral(event, ctx, "Você não tem um VIP ativo.");
            return;
        }
        var plan = vip.plans().findById(grant.planId()).orElse(null);
        event.replyComponents(VipPanelView.panel(ctx, guild, grant, plan))
                .useComponentsV2().setEphemeral(true).queue();
    }
}
