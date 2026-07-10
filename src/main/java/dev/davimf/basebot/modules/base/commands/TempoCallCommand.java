package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.modules.base.leveling.VoiceFormat;
import dev.davimf.basebot.modules.base.leveling.VoiceGate;
import dev.davimf.basebot.modules.base.leveling.VoiceLive;
import dev.davimf.basebot.modules.base.leveling.VoiceSessionRepository;
import dev.davimf.basebot.modules.base.leveling.VoiceTimeRepository;
import dev.davimf.basebot.modules.base.leveling.VoiceWeek;
import dev.davimf.basebot.util.EmbedColor;
import dev.davimf.basebot.util.Emojis;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/** /tempocall [membro] — tempo em call desta semana, incluindo a sessão em curso. */
public final class TempoCallCommand implements SlashCommand {

    private final VoiceGate gate;

    public TempoCallCommand(VoiceGate gate) { this.gate = gate; }

    @Override public String name() { return "tempocall"; }

    @Override
    public SlashCommandData data() {
        return Commands.slash("tempocall", "Tempo em call nesta semana.")
                .addOptions(new OptionData(OptionType.USER, "membro", "Membro (opcional)", false));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null) {
            Replies.ephemeral(event, ctx, "Use este comando em um servidor.");
            return;
        }
        OptionMapping option = event.getOption("membro");
        User target = option == null ? event.getUser() : option.getAsUser();
        String guildId = event.getGuild().getId();
        long now = System.currentTimeMillis();
        long week = VoiceWeek.weekStart(now);
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(guildId);

        VoiceLive.Snapshot live = VoiceLive.forUser(event.getGuild(), target.getId(), cfg, gate,
                new VoiceSessionRepository(ctx.database().sqlite()),
                new VoiceTimeRepository(ctx.database().sqlite()), now, week);

        StringBuilder body = new StringBuilder("## " + Emojis.of(Emojis.CLOCK, "🕒") + " Tempo em call\n")
                .append("> ").append(target.getAsMention()).append("\n")
                .append("**Salvo** · `").append(VoiceFormat.precise(live.savedMs())).append("`\n");
        if (live.inCall()) {
            String andamento = live.pauseReason() != null
                    ? "pausado (" + live.pauseReason() + ")"
                    : VoiceFormat.precise(live.pendingMs());
            body.append("**Em andamento** · `").append(andamento).append("`\n")
                    .append("**Total** · `").append(VoiceFormat.precise(live.totalMs())).append("`\n");
        }
        body.append("-# Zera toda segunda 00:00.");

        event.replyComponents(Panels.container(EmbedColor.resolve(cfg), Panels.text(body.toString())))
                .useComponentsV2().setEphemeral(true).queue();
    }
}
