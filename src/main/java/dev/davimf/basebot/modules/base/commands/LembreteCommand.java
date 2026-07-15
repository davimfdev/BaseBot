package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.AutocompleteCommand;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.modules.base.utility.Reminder;
import dev.davimf.basebot.modules.base.utility.ReminderService;
import dev.davimf.basebot.util.Durations;
import dev.davimf.basebot.util.Emojis;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;

/** /lembrete criar|listar|cancelar. */
public final class LembreteCommand implements SlashCommand, AutocompleteCommand {

    /** Teto de lembretes pendentes por usuário (anti-abuso / anti-spam de DM). */
    private static final int MAX_PENDING = 25;

    private final ReminderService service;
    public LembreteCommand(ReminderService service) { this.service = service; }

    @Override public String name() { return "lembrete"; }

    @Override public SlashCommandData data() {
        return Commands.slash("lembrete", "Cria lembretes que o bot te envia por DM.")
                .addSubcommands(
                        new SubcommandData("criar", "Cria um lembrete")
                                .addOptions(new OptionData(OptionType.STRING, "tempo", "Ex.: 2h, 30m, 1d", true))
                                .addOptions(new OptionData(OptionType.STRING, "mensagem", "O que lembrar", true)),
                        new SubcommandData("listar", "Mostra seus lembretes ativos"),
                        new SubcommandData("cancelar", "Cancela um lembrete")
                                .addOptions(new OptionData(OptionType.STRING, "id", "O lembrete a cancelar", true)
                                        .setAutoComplete(true)));
    }

    @Override public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null) {
            Replies.ephemeral(event, ctx, "Use este comando em um servidor.");
            return;
        }
        switch (event.getSubcommandName() == null ? "" : event.getSubcommandName()) {
            case "criar" -> criar(event, ctx);
            case "listar" -> listar(event, ctx);
            case "cancelar" -> cancelar(event, ctx);
            default -> Replies.ephemeral(event, ctx, "Subcomando inválido.");
        }
    }

    private void criar(SlashCommandInteractionEvent event, BotContext ctx) {
        OptionalLong dur = Durations.parse(event.getOption("tempo", "", OptionMapping::getAsString));
        if (dur.isEmpty()) {
            Replies.ephemeral(event, ctx, "Tempo inválido. Ex.: `2h`, `30m`, `1d`.");
            return;
        }
        if (service.repo().listByUser(event.getGuild().getId(), event.getUser().getId()).size() >= MAX_PENDING) {
            Replies.ephemeral(event, ctx, "Você já tem " + MAX_PENDING
                    + " lembretes ativos. Cancele algum com `/lembrete cancelar` antes de criar outro.");
            return;
        }
        String msg = event.getOption("mensagem", "", OptionMapping::getAsString).trim();
        if (msg.length() > 500) {
            msg = msg.substring(0, 500);
        }
        long remindAt = System.currentTimeMillis() + dur.getAsLong();
        service.repo().create(event.getGuild().getId(), event.getUser().getId(), event.getChannel().getId(),
                msg, remindAt);
        Replies.reply(event, ctx, Emojis.of(Emojis.CLOCK, "⏰")
                + " Lembrete criado! Vou te avisar <t:" + (remindAt / 1000) + ":R>.");
    }

    private void listar(SlashCommandInteractionEvent event, BotContext ctx) {
        List<Reminder> mine = service.repo().listByUser(event.getGuild().getId(), event.getUser().getId());
        if (mine.isEmpty()) {
            Replies.ephemeral(event, ctx, "Você não tem lembretes ativos.");
            return;
        }
        StringBuilder sb = new StringBuilder("## " + Emojis.of(Emojis.CLOCK, "⏰") + " Seus lembretes\n");
        for (Reminder r : mine) {
            String m = r.message().length() > 60 ? r.message().substring(0, 60) + "…" : r.message();
            sb.append("\n`").append(r.id()).append("` · <t:").append(r.remindAt() / 1000).append(":R> · ").append(m);
        }
        Replies.ephemeral(event, ctx, sb.toString());
    }

    private void cancelar(SlashCommandInteractionEvent event, BotContext ctx) {
        String id = event.getOption("id", "", OptionMapping::getAsString);
        boolean ok = service.repo().cancel(id, event.getUser().getId());
        Replies.ephemeral(event, ctx, ok ? "Lembrete cancelado." : "Lembrete não encontrado (ou não é seu).");
    }

    /** Sugere os lembretes ativos do próprio usuário (só a opção {@code cancelar → id}). */
    @Override public void onAutocomplete(CommandAutoCompleteInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null || !"id".equals(event.getFocusedOption().getName())) {
            event.replyChoices(List.of()).queue();
            return;
        }
        String focused = event.getFocusedOption().getValue().toLowerCase(Locale.ROOT);
        long now = System.currentTimeMillis();
        List<Command.Choice> choices = new ArrayList<>();
        for (Reminder r : service.repo().listByUser(event.getGuild().getId(), event.getUser().getId())) {
            String label = "em " + Durations.format(Math.max(0, r.remindAt() - now)) + " · " + r.message();
            if (label.length() > 100) {
                label = label.substring(0, 99) + "…";
            }
            if (focused.isEmpty() || r.message().toLowerCase(Locale.ROOT).contains(focused)) {
                choices.add(new Command.Choice(label, r.id()));
            }
            if (choices.size() >= 25) {
                break;
            }
        }
        event.replyChoices(choices).queue();
    }
}
