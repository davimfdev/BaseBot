package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.modules.base.economy.EconomyConfig;
import dev.davimf.basebot.modules.base.economy.JailService;
import dev.davimf.basebot.modules.base.economy.JobService;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/** /economia programar — freela de programação; exige teclado equipado. */
public final class ProgramarCommand implements SlashCommand {
    private final JobService jobs;
    private final JailService jail;
    public ProgramarCommand(JobService jobs, JailService jail) { this.jobs = jobs; this.jail = jail; }

    @Override public String name() { return "programar"; }
    @Override public SlashCommandData data() { return Commands.slash("programar", "Faz um freela de programação com o teclado equipado."); }

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
        if (jail.blockedIfJailed(event, ctx, event.getGuild().getId(), event.getMember().getId())) {
            return;
        }
        Replies.reply(event, ctx, jobs.runProgram(event.getGuild(), event.getMember()));
    }
}
