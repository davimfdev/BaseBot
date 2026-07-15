package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.modules.base.economy.EconomiaPanelBuilder;
import dev.davimf.basebot.modules.base.economy.EconomyConfig;
import dev.davimf.basebot.modules.base.economy.JailService;
import dev.davimf.basebot.modules.base.economy.JobNotifyRepository;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/** /economia painel — o que dá pra fazer + cooldowns + saldo + cadeia + toggle de aviso. */
public final class EconomiaCommand implements SlashCommand {

    private final JailService jail;
    private final JobNotifyRepository notify;

    public EconomiaCommand(JailService jail, JobNotifyRepository notify) {
        this.jail = jail;
        this.notify = notify;
    }

    @Override public String name() { return "painel"; }

    @Override public SlashCommandData data() {
        return Commands.slash("painel", "Mostra o que você pode fazer, seus cooldowns e saldo.");
    }

    @Override public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null || event.getMember() == null) {
            Replies.ephemeral(event, ctx, "Use este comando em um servidor.");
            return;
        }
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(event.getGuild().getId());
        if (!EconomyConfig.enabled(cfg)) {
            Replies.ephemeral(event, ctx, "Economia desativada neste servidor.");
            return;
        }
        event.replyComponents(EconomiaPanelBuilder.build(ctx, event.getGuild(), event.getMember(), jail, notify))
                .useComponentsV2().setEphemeral(true).queue();
    }
}
