package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.modules.base.leveling.TopCallView;
import dev.davimf.basebot.modules.base.leveling.VoiceGate;
import dev.davimf.basebot.modules.base.leveling.VoiceLive;
import dev.davimf.basebot.modules.base.leveling.VoiceSessionRepository;
import dev.davimf.basebot.modules.base.leveling.VoiceTimeRepository;
import dev.davimf.basebot.modules.base.leveling.VoiceWeek;
import dev.davimf.basebot.util.EmbedColor;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.List;

/** /topcall — ranking semanal de tempo em call, já com a sessão em curso somada. */
public final class TopCallCommand implements SlashCommand {

    private final VoiceGate gate;

    public TopCallCommand(VoiceGate gate) { this.gate = gate; }

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
        long now = System.currentTimeMillis();
        long week = VoiceWeek.weekStart(now);
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(guildId);

        List<VoiceTimeRepository.Entry> all = VoiceLive.ranking(event.getGuild(), cfg, gate,
                new VoiceSessionRepository(ctx.database().sqlite()),
                new VoiceTimeRepository(ctx.database().sqlite()), now, week);
        List<VoiceTimeRepository.Entry> page = all.subList(0, Math.min(TopCallView.PAGE, all.size()));

        event.replyComponents(TopCallView.panel(EmbedColor.resolve(cfg), page, 0, all.size()))
                .useComponentsV2().setEphemeral(true).queue();
    }
}
