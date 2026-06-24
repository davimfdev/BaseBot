package dev.davimf.basebot.modules.facs.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.modules.facs.recruit.RecruitView;
import dev.davimf.basebot.util.EmbedColor;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

/** /recrutamento painel — posts the static apply panel for the Set pipeline (BOTSPECS Module 4). */
public final class RecrutamentoCommand implements SlashCommand {

    @Override
    public String name() {
        return "recrutamento";
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("recrutamento", "Recrutamento da facção.")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_ROLES))
                .addSubcommands(new SubcommandData("painel", "Publica o painel de solicitação de entrada.")
                        .addOption(OptionType.STRING, "descricao", "Texto do painel (opcional)", false));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null) {
            event.reply("Use este comando em um servidor.").setEphemeral(true).queue();
            return;
        }
        String description = event.getOption("descricao", OptionMapping::getAsString);
        int accent = EmbedColor.resolve(ctx.database().guildConfig().findOrEmpty(event.getGuild().getId()));
        event.replyComponents(RecruitView.panel(accent, description)).useComponentsV2().queue();
    }
}
