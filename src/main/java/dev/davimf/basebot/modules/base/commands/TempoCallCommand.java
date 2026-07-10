package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.modules.base.leveling.VoiceFormat;
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

/** /tempocall [membro] — tempo em call desta semana. */
public final class TempoCallCommand implements SlashCommand {

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
        long week = VoiceWeek.weekStart(System.currentTimeMillis());
        long ms = new VoiceTimeRepository(ctx.database().sqlite()).msOf(guildId, target.getId(), week);
        int accent = EmbedColor.resolve(ctx.database().guildConfig().findOrEmpty(guildId));

        String body = "## " + Emojis.of(Emojis.CLOCK, "🕒") + " Tempo em call\n"
                + "> " + target.getAsMention() + "\n"
                + "**Esta semana** · `" + VoiceFormat.precise(ms) + "`\n"
                + "-# Zera toda segunda 00:00.";
        event.replyComponents(Panels.container(accent, Panels.text(body)))
                .useComponentsV2().setEphemeral(true).queue();
    }
}
