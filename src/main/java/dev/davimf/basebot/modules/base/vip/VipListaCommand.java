package dev.davimf.basebot.modules.base.vip;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.util.EmbedColor;
import dev.davimf.basebot.util.Emojis;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.List;

/** /vip lista — lista os VIPs ativos do servidor (BOTSPECS VIPs). */
public final class VipListaCommand implements SlashCommand {

    private final VipService vip;

    public VipListaCommand(VipService vip) {
        this.vip = vip;
    }

    @Override public String name() { return "lista"; }

    @Override public SlashCommandData data() {
        return Commands.slash("lista", "Lista os VIPs ativos do servidor.")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_ROLES));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null) {
            Replies.ephemeral(event, ctx, "Use este comando em um servidor.");
            return;
        }
        String guildId = event.getGuild().getId();
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(guildId);
        int accent = EmbedColor.resolve(cfg);
        List<VipGrant> active = vip.grants().listActiveByGuild(guildId);
        if (active.isEmpty()) {
            event.replyComponents(Panels.container(accent, Panels.text("Nenhum VIP ativo neste servidor.")))
                    .useComponentsV2().setEphemeral(true).queue();
            return;
        }

        StringBuilder body = new StringBuilder();
        for (VipGrant g : active) {
            VipPlan plan = vip.plans().findById(g.planId()).orElse(null);
            String planName = plan != null ? plan.name() : g.planId();
            String expiry = g.expiresAt() == null
                    ? "permanente" : "<t:" + g.expiresAt().getEpochSecond() + ":R>";
            body.append("**").append(planName).append("** · <@").append(g.userId())
                    .append("> · expira ").append(expiry)
                    .append(" · `").append(g.provisionStatus().db()).append("`\n");
        }

        event.replyComponents(Panels.container(accent,
                        Panels.text("## " + Emojis.of(Emojis.GEM, "💎") + " VIPs ativos `" + active.size() + "`"),
                        Panels.divider(),
                        Panels.text(body.toString())))
                .useComponentsV2().setEphemeral(true).setAllowedMentions(List.of()).queue();
    }
}
