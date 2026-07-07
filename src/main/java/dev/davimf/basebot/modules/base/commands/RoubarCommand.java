package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.modules.base.economy.CrimeEconomyService;
import dev.davimf.basebot.modules.base.economy.EconomyConfig;
import dev.davimf.basebot.modules.base.economy.JailService;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/** /roubar — tenta roubar a carteira de outro membro; exige arma equipada. */
public final class RoubarCommand implements SlashCommand {
    private final CrimeEconomyService crime;
    private final JailService jail;
    public RoubarCommand(CrimeEconomyService crime, JailService jail) {
        this.crime = crime;
        this.jail = jail;
    }

    @Override public String name() { return "roubar"; }

    @Override public SlashCommandData data() {
        return Commands.slash("roubar", "Tenta roubar a carteira de outro membro.")
                .addOption(OptionType.USER, "usuario", "Alvo do roubo", true);
    }

    @Override public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null || event.getMember() == null) {
            Replies.ephemeral(event, ctx, "Use este comando em um servidor.");
            return;
        }
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(event.getGuild().getId());
        if (!EconomyConfig.enabled(cfg)) {
            Replies.ephemeral(event, ctx, "Economia desativada neste servidor.");
            return;
        }
        if (jail.blockedIfJailed(event, ctx, event.getGuild().getId(), event.getMember().getId())) {
            return;
        }
        OptionMapping opt = event.getOption("usuario");
        Member target = opt == null ? null : opt.getAsMember();
        if (target == null) {
            Replies.ephemeral(event, ctx, "Alvo inválido ou fora do servidor.");
            return;
        }
        Replies.reply(event, ctx, crime.runRobbery(event.getGuild(), event.getMember(), target));
    }
}
