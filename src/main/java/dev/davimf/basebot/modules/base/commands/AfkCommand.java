package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.modules.base.utility.AfkRegistry;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/** /afk [motivo] — marca você como ausente. */
public final class AfkCommand implements SlashCommand {
    private final AfkRegistry registry;
    public AfkCommand(AfkRegistry registry) { this.registry = registry; }

    @Override public String name() { return "afk"; }

    @Override public SlashCommandData data() {
        return Commands.slash("afk", "Marca você como ausente (AFK).")
                .addOptions(new OptionData(OptionType.STRING, "motivo", "Motivo (opcional)", false));
    }

    @Override public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null || event.getMember() == null) {
            Replies.ephemeral(event, ctx, "Use este comando em um servidor.");
            return;
        }
        OptionMapping m = event.getOption("motivo");
        String motivo = m == null || m.getAsString().isBlank() ? "Ausente" : m.getAsString().trim();
        registry.set(event.getGuild().getId(), event.getUser().getId(), motivo, System.currentTimeMillis());
        Replies.reply(event, ctx, "💤 Você está **AFK**: " + motivo);
    }
}
