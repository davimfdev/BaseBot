package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.util.Emojis;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.modules.base.moderation.MessagePurge;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/** /purge — filtered cleanup (by author, only bots/links/attachments/humans, or contains text). */
public final class PurgeCommand implements SlashCommand {

    private static final Pattern URL = Pattern.compile("https?://", Pattern.CASE_INSENSITIVE);
    private static final int SCAN = 100;

    @Override
    public String name() {
        return "purge";
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("purge", "Apaga mensagens recentes com filtros (até 100, até 14 dias).")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MESSAGE_MANAGE))
                .addOptions(new OptionData(OptionType.INTEGER, "quantidade", "Quantas apagar (1-100)", false)
                        .setMinValue(1).setMaxValue(100))
                .addOption(OptionType.USER, "de", "Apagar apenas mensagens deste usuário", false)
                .addOptions(new OptionData(OptionType.STRING, "apenas", "Filtrar por tipo", false)
                        .addChoice("Bots", "bots").addChoice("Humanos", "humanos")
                        .addChoice("Links", "links").addChoice("Anexos", "anexos"))
                .addOption(OptionType.STRING, "contem", "Apagar apenas mensagens que contenham este texto", false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null || !(event.getChannel() instanceof TextChannel channel)) {
            Replies.ephemeral(event, ctx, "Use este comando em um canal de texto do servidor.");
            return;
        }
        int amount = (int) Math.max(1, Math.min(100, event.getOption("quantidade", 50L, OptionMapping::getAsLong)));
        User from = event.getOption("de", OptionMapping::getAsUser);
        String apenas = event.getOption("apenas", OptionMapping::getAsString);
        String contem = event.getOption("contem", OptionMapping::getAsString);

        Predicate<Message> filter = m -> true;
        if (from != null) {
            filter = filter.and(m -> m.getAuthor().getId().equals(from.getId()));
        }
        if (apenas != null) {
            filter = switch (apenas) {
                case "bots" -> filter.and(m -> m.getAuthor().isBot());
                case "humanos" -> filter.and(m -> !m.getAuthor().isBot());
                case "links" -> filter.and(m -> URL.matcher(m.getContentRaw()).find());
                case "anexos" -> filter.and(m -> !m.getAttachments().isEmpty());
                default -> filter;
            };
        }
        if (contem != null && !contem.isBlank()) {
            String needle = contem.toLowerCase();
            filter = filter.and(m -> m.getContentRaw().toLowerCase().contains(needle));
        }

        Predicate<Message> matches = filter;
        event.deferReply(true).queue();
        long now = System.currentTimeMillis();
        channel.getHistory().retrievePast(SCAN).queue(messages -> {
            List<Message> deletable = messages.stream()
                    .filter(m -> now - m.getTimeCreated().toInstant().toEpochMilli() < MessagePurge.BULK_MAX_AGE_MILLIS)
                    .filter(matches)
                    .limit(amount)
                    .toList();
            if (deletable.isEmpty()) {
                Replies.hook(event, ctx, "Nenhuma mensagem correspondente (até 14 dias) encontrada.");
                return;
            }
            channel.purgeMessages(deletable);
            ctx.database().actionLogs().log(event.getGuild().getId(), event.getUser().getId(),
                    channel.getId(), "PURGE", "deleted=" + deletable.size());
            Replies.hook(event, ctx, "" + Emojis.of(Emojis.BROOM, "🧹") + " Apagadas: `" + deletable.size() + "` mensagens.");
        }, err -> Replies.hook(event, ctx, "Falha ao buscar mensagens: " + err.getMessage()));
    }
}
