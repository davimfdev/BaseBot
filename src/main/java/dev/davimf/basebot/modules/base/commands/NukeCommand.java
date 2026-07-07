package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.util.Emojis;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.modules.base.moderation.InfractionView;
import dev.davimf.basebot.util.EmbedColor;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/** /nuke — clones and deletes a channel to wipe all its messages (asks for confirmation). */
public final class NukeCommand implements SlashCommand {

    @Override
    public String name() {
        return "nuke";
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("nuke", "Limpa um canal apagando TODAS as mensagens (clona e recria).")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_CHANNEL))
                .addOption(OptionType.CHANNEL, "canal", "Canal (padrão: o atual)", false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null) {
            Replies.ephemeral(event, ctx, "Use este comando em um servidor.");
            return;
        }
        OptionMapping canalOpt = event.getOption("canal");
        TextChannel channel = canalOpt != null && canalOpt.getAsChannel() instanceof TextChannel tc
                ? tc
                : (event.getChannel() instanceof TextChannel cur ? cur : null);
        if (channel == null) {
            Replies.ephemeral(event, ctx, "Selecione um canal de texto.");
            return;
        }
        int accent = EmbedColor.resolve(ctx.database().guildConfig().findOrEmpty(event.getGuild().getId()));
        event.replyComponents(Panels.container(accent,
                        Panels.text("## " + Emojis.of(Emojis.WARN, "⚠️") + " Limpar " + channel.getAsMention() + "?"),
                        Panels.divider(),
                        Panels.text("> Isso vai **apagar todas as mensagens** do canal clonando-o e recriando-o. "
                                + "Esta ação é **irreversível**."),
                        ActionRow.of(Button.danger(
                                ComponentId.of(InfractionView.NS, "nuke", channel.getId()), "Limpar canal")
                                .withEmoji(Emojis.button(Emojis.NUKE)))))
                .useComponentsV2().setEphemeral(true).queue();
    }
}
