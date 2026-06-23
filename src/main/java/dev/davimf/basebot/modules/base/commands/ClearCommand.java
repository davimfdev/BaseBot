package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.modules.base.moderation.MessagePurge;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.List;

/**
 * /clear (and alias /cl) — bulk-deletes recent messages, skipping any 14 days or older
 * (Discord cannot bulk-delete those). Registered twice under the two names.
 */
public final class ClearCommand implements SlashCommand {

    private final String name;

    public ClearCommand(String name) {
        this.name = name;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash(name, "Apaga mensagens recentes do canal (até 14 dias).")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MESSAGE_MANAGE))
                .addOption(OptionType.INTEGER, "quantidade", "Quantas mensagens (1-100)", true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null || !(event.getChannel() instanceof TextChannel channel)) {
            event.reply("Use este comando em um canal de texto do servidor.").setEphemeral(true).queue();
            return;
        }
        long requested = event.getOption("quantidade", 0L, OptionMapping::getAsLong);
        int amount = (int) Math.max(1, Math.min(100, requested));

        event.deferReply(true).queue();
        channel.getHistory().retrievePast(amount).queue(messages -> {
            long now = System.currentTimeMillis();
            MessagePurge.Partition p = MessagePurge.partitionByAge(
                    messages.stream().map(m -> m.getTimeCreated().toInstant().toEpochMilli()).toList(),
                    now);

            List<Message> deletable = messages.stream()
                    .filter(m -> now - m.getTimeCreated().toInstant().toEpochMilli()
                            < MessagePurge.BULK_MAX_AGE_MILLIS)
                    .toList();

            int skipped = p.tooOld().size();
            if (deletable.isEmpty()) {
                event.getHook().sendMessage("Nada para apagar (todas as mensagens têm 14+ dias). "
                        + "Ignoradas: " + skipped).queue();
                return;
            }
            channel.purgeMessages(deletable);
            ctx.database().actionLogs().log(event.getGuild().getId(),
                    event.getUser().getId(), channel.getId(), "CLEAR",
                    "deleted=" + deletable.size() + " skipped=" + skipped);
            event.getHook().sendMessage("Apagadas: " + deletable.size()
                    + (skipped > 0 ? " | Ignoradas (14+ dias): " + skipped : "")).queue();
        }, err -> event.getHook().sendMessage("Falha ao buscar mensagens: " + err.getMessage()).queue());
    }
}
