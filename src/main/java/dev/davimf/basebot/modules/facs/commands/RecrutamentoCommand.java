// [OUTLINE START]
// Package: dev.davimf.basebot.modules.facs.commands
// 
// Class: RecrutamentoCommand
// 
// Methods:
//   - `Method` : `public String name()`
//   - `Method` : `public SlashCommandData data()`
// [OUTLINE END]



package dev.davimf.basebot.modules.facs.commands;

import dev.davimf.basebot.util.Emojis;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.modules.facs.perms.ManagerPermissions;
import dev.davimf.basebot.modules.facs.perms.ManagerPermissions.Capability;
import dev.davimf.basebot.modules.facs.recruit.RecruitView;
import dev.davimf.basebot.util.EmbedColor;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
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
                .addSubcommands(new SubcommandData("painel", "Publica o painel de solicitação de entrada.")
                        .addOption(OptionType.STRING, "descricao", "Texto do painel (opcional)", false));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null) {
            Replies.ephemeral(event, ctx, "Use este comando em um servidor.");
            return;
        }
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(event.getGuild().getId());
        if (!ManagerPermissions.can(event.getMember(), cfg, Capability.RECRUTAMENTO)) {
            Replies.ephemeral(event, ctx, "Você não tem permissão de **Recrutamento** para publicar o painel.");
            return;
        }
        String description = event.getOption("descricao", OptionMapping::getAsString);
        int accent = EmbedColor.resolve(ctx.database().guildConfig().findOrEmpty(event.getGuild().getId()));
        // Post as a normal channel message (not an interaction response) so it can be
        // cleanly edited later via /mensagem editar; confirm to the user ephemerally.
        event.getChannel().sendMessageComponents(RecruitView.panel(accent, description)).useComponentsV2().queue(
                msg -> Replies.ephemeral(event, ctx, Emojis.of(Emojis.NOTE, "📝") + " Painel de recrutamento publicado."),
                err -> Replies.ephemeral(event, ctx, "Falha ao publicar: " + err.getMessage()));
    }
}
