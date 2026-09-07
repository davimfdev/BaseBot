package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.modules.base.message.MessageBuilderService;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

/**
 * /mensagem enviar — opens the interactive message builder (BOTSPECS Module 1): pick
 * Embed clássico or Container V2, edit fields/blocks, then send (optionally via webhook).
 */
public final class MensagemCommand implements SlashCommand {

    private final MessageBuilderService service;

    public MensagemCommand(MessageBuilderService service) {
        this.service = service;
    }

    @Override
    public String name() {
        return "mensagem";
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("mensagem", "Construtor de mensagens (embed clássico ou Container V2).")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER))
                .addSubcommands(
                        new SubcommandData("enviar", "Abre o construtor de mensagem.")
                                .addOption(OptionType.ATTACHMENT, "imagem", "Imagem para anexar", false)
                                .addOption(OptionType.ATTACHMENT, "thumbnail", "Thumbnail para anexar", false),
                        new SubcommandData("editar", "Edita uma mensagem do bot no construtor.")
                                .addOption(OptionType.STRING, "mensagem",
                                        "ID ou link da mensagem a editar", true)
                                .addOption(OptionType.ATTACHMENT, "imagem", "Imagem para anexar", false)
                                .addOption(OptionType.ATTACHMENT, "thumbnail", "Thumbnail para anexar", false));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null) {
            Replies.ephemeral(event, ctx, "Use este comando em um servidor.");
            return;
        }
        net.dv8tion.jda.api.entities.Message.Attachment img =
                event.getOption("imagem", OptionMapping::getAsAttachment);
        net.dv8tion.jda.api.entities.Message.Attachment thumb =
                event.getOption("thumbnail", OptionMapping::getAsAttachment);
        if ("editar".equals(event.getSubcommandName())) {
            service.openEdit(event, event.getOption("mensagem", OptionMapping::getAsString), img, thumb);
        } else {
            service.open(event, img, thumb);
        }
    }
}
