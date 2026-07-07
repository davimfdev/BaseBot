package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.modules.base.leveling.LevelingService;
import dev.davimf.basebot.modules.base.leveling.TopView;
import dev.davimf.basebot.util.EmbedColor;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/** /top — ranking de nível do servidor (paginado). */
public final class TopCommand implements SlashCommand {

    private final LevelingService leveling;

    public TopCommand(LevelingService leveling) { this.leveling = leveling; }

    @Override
    public String name() { return "top"; }

    @Override
    public SlashCommandData data() {
        return Commands.slash("top", "Ranking de nível do servidor.");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null) {
            Replies.ephemeral(event, ctx, "Use este comando em um servidor.");
            return;
        }
        String guildId = event.getGuild().getId();
        int total = leveling.users().count(guildId);
        var entries = leveling.users().topPage(guildId, TopView.PAGE, 0);
        int accent = EmbedColor.resolve(ctx.database().guildConfig().findOrEmpty(guildId));
        event.replyComponents(TopView.panel(accent, entries, 0, total)).useComponentsV2().setEphemeral(true).queue();
    }
}
