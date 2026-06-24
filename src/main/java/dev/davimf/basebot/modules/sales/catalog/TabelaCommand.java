package dev.davimf.basebot.modules.sales.catalog;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.util.EmbedColor;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/** /tabela — configuration + display hub for the product catalog (BOTSPECS Module 3). */
public final class TabelaCommand implements SlashCommand {

    private final CatalogRepository catalog;

    public TabelaCommand(CatalogRepository catalog) {
        this.catalog = catalog;
    }

    @Override
    public String name() {
        return "tabela";
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("tabela", "Tabela de preços: categorias e produtos.")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null) {
            event.reply("Use este comando em um servidor.").setEphemeral(true).queue();
            return;
        }
        int accent = EmbedColor.resolve(ctx.database().guildConfig().findOrEmpty(event.getGuild().getId()));
        event.replyComponents(TabelaView.hub(accent, catalog.listCategories(event.getGuild().getId())))
                .useComponentsV2().setEphemeral(true).queue();
    }
}
