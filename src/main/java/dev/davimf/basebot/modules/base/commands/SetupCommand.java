package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.modules.base.setup.SetupView;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/** /setup — opens the guild configuration hub (BOTSPECS Module 1). */
public final class SetupCommand implements SlashCommand {

    @Override
    public String name() {
        return "setup";
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("setup", "Abre o painel de configuração do servidor.")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null) {
            event.reply("Use este comando em um servidor.").setEphemeral(true).queue();
            return;
        }
        String guildId = event.getGuild().getId();
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(guildId);
        int ticketCats = ctx.database().ticketCategories().count(guildId);
        event.replyComponents(SetupView.hub(cfg, ticketCats))
                .useComponentsV2()
                .setEphemeral(true)
                .queue();
    }
}
