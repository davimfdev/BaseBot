package dev.davimf.basebot.modules.facs.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.modules.facs.economy.FarmItems;
import dev.davimf.basebot.modules.facs.economy.FarmView;
import dev.davimf.basebot.util.EmbedColor;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.List;

/** /farm — pick a pre-configured material from a dropdown and enter the quantity to submit
 *  it for manager approval (BOTSPECS Module 4). Items come from {@code /setup → Farm}. */
public final class FarmCommand implements SlashCommand {

    public FarmCommand() {
    }

    @Override
    public String name() {
        return "farm";
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("farm", "Entrega materiais de farm para aprovação.");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null) {
            Replies.ephemeral(event, ctx, "Use este comando em um servidor.");
            return;
        }
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(event.getGuild().getId());
        List<String> items = FarmItems.list(cfg);
        if (items.isEmpty()) {
            Replies.ephemeral(event, ctx,
                    "Nenhum item de farm configurado. Peça à gerência para configurar em `/setup → Farm`.");
            return;
        }
        event.replyComponents(FarmView.pickItem(EmbedColor.resolve(cfg), items))
                .useComponentsV2().setEphemeral(true).queue();
    }
}
