package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.modules.base.leveling.LevelingService;
import dev.davimf.basebot.modules.base.leveling.RankData;
import dev.davimf.basebot.modules.base.leveling.RankView;
import dev.davimf.basebot.util.EmbedColor;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/** /rank — mostra o nível/XP do usuário (ou de outro membro). */
public final class RankCommand implements SlashCommand {

    private final LevelingService leveling;

    public RankCommand(LevelingService leveling) { this.leveling = leveling; }

    @Override
    public String name() { return "rank"; }

    @Override
    public SlashCommandData data() {
        return Commands.slash("rank", "Mostra seu nível e XP (ou de outro membro).")
                .addOption(OptionType.USER, "usuario", "Membro (opcional)", false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null || event.getMember() == null) {
            Replies.ephemeral(event, ctx, "Use este comando em um servidor.");
            return;
        }
        OptionMapping opt = event.getOption("usuario");
        Member target = opt == null ? event.getMember() : opt.getAsMember();
        if (target == null) {
            Replies.ephemeral(event, ctx, "Esse usuário não está no servidor.");
            return;
        }
        RankData d = leveling.rank(event.getGuild().getId(), target.getId());
        int accent = EmbedColor.resolve(ctx.database().guildConfig().findOrEmpty(event.getGuild().getId()));
        event.replyComponents(RankView.panel(accent, target, d)).useComponentsV2().setEphemeral(true).queue();
    }
}
