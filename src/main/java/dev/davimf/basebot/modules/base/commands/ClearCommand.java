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
 * Message purge (BOTSPECS Module 1), skipping any message 14 days or older (Discord can't
 * bulk-delete those). Two flavours, by name:
 * <ul>
 *   <li>{@code /clear} — deletes the last N messages from anyone (moderation).</li>
 *   <li>{@code /cl} — deletes only the executor's own recent messages (self-cleanup).</li>
 * </ul>
 */
public final class ClearCommand implements SlashCommand {

    /** How many recent messages to scan when filtering to the executor's own. */
    private static final int SCAN_WINDOW = 100;

    private final String name;
    private final boolean onlyOwn;

    public ClearCommand(String name, boolean onlyOwn) {
        this.name = name;
        this.onlyOwn = onlyOwn;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public SlashCommandData data() {
        if (onlyOwn) {
            // No options: always clears all of the executor's messages in the last 100.
            return Commands.slash(name, "Apaga todas as suas mensagens (últimas 100, até 14 dias).")
                    .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MESSAGE_MANAGE));
        }
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
        // /cl: scan the last 100 and delete ALL the executor's. /clear: the last N from anyone.
        int amount = onlyOwn ? SCAN_WINDOW
                : (int) Math.max(1, Math.min(100, event.getOption("quantidade", 0L, OptionMapping::getAsLong)));
        int fetch = onlyOwn ? SCAN_WINDOW : amount;
        String authorId = event.getUser().getId();

        event.deferReply(true).queue();
        channel.getHistory().retrievePast(fetch).queue(messages -> {
            long now = System.currentTimeMillis();
            List<Message> deletable = messages.stream()
                    .filter(m -> !onlyOwn || m.getAuthor().getId().equals(authorId))
                    .filter(m -> now - m.getTimeCreated().toInstant().toEpochMilli()
                            < MessagePurge.BULK_MAX_AGE_MILLIS)
                    .limit(amount)
                    .toList();

            if (deletable.isEmpty()) {
                event.getHook().sendMessage(onlyOwn
                        ? "Nenhuma mensagem sua (até 14 dias) encontrada para apagar."
                        : "Nada para apagar (todas as mensagens têm 14+ dias).").queue();
                return;
            }
            channel.purgeMessages(deletable);
            ctx.database().actionLogs().log(event.getGuild().getId(),
                    event.getUser().getId(), channel.getId(),
                    onlyOwn ? "CLEAR_OWN" : "CLEAR", "deleted=" + deletable.size());
            event.getHook().sendMessage("Apagadas: " + deletable.size()
                    + (onlyOwn ? " (somente suas mensagens)." : ".")).queue();
        }, err -> event.getHook().sendMessage("Falha ao buscar mensagens: " + err.getMessage()).queue());
    }
}
