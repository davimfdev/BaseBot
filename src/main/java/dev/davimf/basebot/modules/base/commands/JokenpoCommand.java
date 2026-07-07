package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.modules.base.fun.JokenpoService;
import dev.davimf.basebot.modules.base.fun.JokenpoView;
import dev.davimf.basebot.util.EmbedColor;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.List;

/** /jokenpo @alvo — pedra, papel e tesoura. */
public final class JokenpoCommand implements SlashCommand {
    private final JokenpoService service;

    public JokenpoCommand(JokenpoService service) { this.service = service; }

    @Override public String name() { return "jokenpo"; }

    @Override public SlashCommandData data() {
        return Commands.slash("jokenpo", "Desafia alguém para pedra, papel e tesoura.")
                .addOptions(new OptionData(OptionType.USER, "usuario", "Quem você desafia", true));
    }

    @Override public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null || event.getMember() == null) {
            Replies.ephemeral(event, ctx, "Use este comando em um servidor.");
            return;
        }
        Member alvo = event.getOption("usuario", OptionMapping::getAsMember);
        if (alvo == null || alvo.getUser().isBot() || alvo.getId().equals(event.getUser().getId())) {
            Replies.ephemeral(event, ctx, "Escolha outra pessoa (não bot, não você).");
            return;
        }
        int accent = EmbedColor.resolve(ctx.database().guildConfig().findOrEmpty(event.getGuild().getId()));
        String challenger = event.getMember().getId();
        String target = alvo.getId();
        event.replyComponents(JokenpoView.challenge(accent, event.getMember().getAsMention(), alvo.getAsMention()))
                .useComponentsV2()
                .setAllowedMentions(List.of(Message.MentionType.USER))
                .queue(hook -> hook.retrieveOriginal()
                        .queue(msg -> service.register(msg.getId(), challenger, target), err -> { }));
    }
}
