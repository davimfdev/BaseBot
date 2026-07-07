package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.modules.base.economy.OrgCrimeService;
import dev.davimf.basebot.modules.base.economy.OrgCrimeView;
import dev.davimf.basebot.util.EmbedColor;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.concurrent.TimeUnit;

/** /crimeorganizado — abre um lobby público (5-10 pessoas armadas) de crime organizado. */
public final class CrimeOrganizadoCommand implements SlashCommand {

    private final OrgCrimeService svc;

    public CrimeOrganizadoCommand(OrgCrimeService svc) { this.svc = svc; }

    @Override public String name() { return "crimeorganizado"; }

    @Override public SlashCommandData data() {
        return Commands.slash("crimeorganizado", "Monta um crime organizado (5-10 pessoas armadas).");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null || event.getMember() == null) {
            Replies.ephemeral(event, ctx, "Use este comando em um servidor.");
            return;
        }
        String guildId = event.getGuild().getId();
        String userId = event.getMember().getId();
        // open() já valida eco/preso/arma/cooldown do líder — o comando não repete essas checagens.
        String err = svc.open(guildId, userId);
        if (err != null) {
            Replies.ephemeral(event, ctx, err);
            return;
        }
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(guildId);
        int accent = EmbedColor.resolve(cfg);
        // Captura a instância aberta AGORA: o timer só expira ESTE lobby, nunca um futuro reaberto na mesma guild.
        OrgCrimeService.Lobby opened = svc.lobby(guildId);
        event.replyComponents(OrgCrimeView.panel(accent, opened, cfg))
                .useComponentsV2()
                .queue(hook -> ctx.scheduler().once(() -> {
                    if (svc.lobby(guildId) == opened && !opened.started()) {
                        svc.cancel(guildId);
                        hook.editOriginalComponents(OrgCrimeView.expired(accent)).useComponentsV2().queue(null, e -> {});
                    }
                }, 5, TimeUnit.MINUTES));
    }
}
