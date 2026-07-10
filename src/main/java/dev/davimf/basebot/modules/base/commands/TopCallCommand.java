package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.modules.base.leveling.TopCallView;
import dev.davimf.basebot.modules.base.leveling.VoiceTimeRepository;
import dev.davimf.basebot.modules.base.leveling.VoiceWeek;
import dev.davimf.basebot.util.EmbedColor;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/** /topcall — ranking semanal de tempo em call (paginado). */
public final class TopCallCommand implements SlashCommand {

    @Override public String name() { return "topcall"; }

    @Override
    public SlashCommandData data() {
        return Commands.slash("topcall", "Ranking de tempo em call desta semana.");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null) {
            Replies.ephemeral(event, ctx, "Use este comando em um servidor.");
            return;
        }
        String guildId = event.getGuild().getId();
        long week = VoiceWeek.weekStart(System.currentTimeMillis());
        VoiceTimeRepository repo = new VoiceTimeRepository(ctx.database().sqlite());
        int total = repo.count(guildId, week);
        var entries = repo.topPage(guildId, week, TopCallView.PAGE, 0);
        int accent = EmbedColor.resolve(ctx.database().guildConfig().findOrEmpty(guildId));
        event.replyComponents(TopCallView.panel(accent, entries, 0, total))
                .useComponentsV2().setEphemeral(true).queue();
    }
}
