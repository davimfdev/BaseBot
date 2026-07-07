// [OUTLINE START]
// Package: dev.davimf.basebot.modules.base.commands
// 
// Class: FormularioCommand
// 
// Constructors:
//   - `Constructor` : `public FormularioCommand(FormRepository forms)`
// 
// Methods:
//   - `Method` : `public String name()`
//   - `Method` : `public SlashCommandData data()`
// 
// Fields:
//   - `Field` : `private final FormRepository forms`
// [OUTLINE END]



package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.util.Emojis;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.modules.base.forms.FormRepository;
import dev.davimf.basebot.modules.base.forms.FormRepository.Form;
import dev.davimf.basebot.modules.base.forms.FormView;
import dev.davimf.basebot.util.EmbedColor;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * /formulario — define configurable forms and dispatch them as a fill panel
 * (BOTSPECS Module 1). Up to 5 questions (Discord modal limit).
 */
public final class FormularioCommand implements SlashCommand {

    private final FormRepository forms;

    public FormularioCommand(FormRepository forms) {
        this.forms = forms;
    }

    @Override
    public String name() {
        return "formulario";
    }

    @Override
    public SlashCommandData data() {
        SubcommandData criar = new SubcommandData("criar", "Cria um formulário (até 5 perguntas).")
                .addOption(OptionType.STRING, "titulo", "Título do formulário", true)
                .addOption(OptionType.STRING, "p1", "Pergunta 1", true)
                .addOption(OptionType.STRING, "p2", "Pergunta 2", false)
                .addOption(OptionType.STRING, "p3", "Pergunta 3", false)
                .addOption(OptionType.STRING, "p4", "Pergunta 4", false)
                .addOption(OptionType.STRING, "p5", "Pergunta 5", false);
        SubcommandData enviar = new SubcommandData("enviar", "Publica o painel de um formulário neste canal.")
                .addOption(OptionType.STRING, "id", "ID do formulário (veja /formulario lista)", true);
        SubcommandData lista = new SubcommandData("lista", "Lista os formulários cadastrados.");
        return Commands.slash("formulario", "Formulários configuráveis via modal.")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER))
                .addSubcommands(criar, enviar, lista);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null) {
            Replies.ephemeral(event, ctx, "Use este comando em um servidor.");
            return;
        }
        switch (event.getSubcommandName() == null ? "" : event.getSubcommandName()) {
            case "criar" -> criar(event, ctx);
            case "enviar" -> enviar(event, ctx);
            case "lista" -> lista(event, ctx);
            default -> Replies.ephemeral(event, ctx, "Subcomando inválido.");
        }
    }

    private void criar(SlashCommandInteractionEvent event, BotContext ctx) {
        String titulo = event.getOption("titulo", OptionMapping::getAsString).trim();
        List<String> questions = new ArrayList<>();
        for (String key : List.of("p1", "p2", "p3", "p4", "p5")) {
            String q = event.getOption(key, OptionMapping::getAsString);
            if (q != null && !q.isBlank()) {
                questions.add(q.trim());
            }
        }
        String id = forms.create(event.getGuild().getId(), titulo, event.getUser().getId(), questions);
        Replies.ephemeral(event, ctx, "" + Emojis.of(Emojis.LIST, "📋") + " Formulário criado (`" + questions.size() + "` perguntas).\n"
                + "Publique com `/formulario enviar id:" + id + "`");
    }

    private void enviar(SlashCommandInteractionEvent event, BotContext ctx) {
        String id = event.getOption("id", OptionMapping::getAsString).trim();
        Optional<Form> form = forms.find(id);
        if (form.isEmpty() || !event.getGuild().getId().equals(form.get().guildId())) {
            Replies.ephemeral(event, ctx, "Formulário não encontrado.");
            return;
        }
        int accent = EmbedColor.resolve(ctx.database().guildConfig().findOrEmpty(event.getGuild().getId()));
        // Post as a normal channel message (editable via /mensagem editar); confirm ephemerally.
        event.getChannel().sendMessageComponents(FormView.panel(accent, form.get())).useComponentsV2().queue(
                msg -> Replies.ephemeral(event, ctx, Emojis.of(Emojis.LIST, "📋") + " Formulário publicado."),
                err -> Replies.ephemeral(event, ctx, "Falha ao publicar: " + err.getMessage()));
    }

    private void lista(SlashCommandInteractionEvent event, BotContext ctx) {
        List<Form> all = forms.list(event.getGuild().getId());
        if (all.isEmpty()) {
            Replies.ephemeral(event, ctx, "Nenhum formulário cadastrado.");
            return;
        }
        StringBuilder sb = new StringBuilder("## " + Emojis.of(Emojis.LIST, "📋") + " Formulários\n");
        for (Form f : all) {
            sb.append("\n• **").append(f.title()).append("** · `").append(f.id()).append("` · ")
                    .append(f.questions().size()).append(" perguntas");
        }
        Replies.ephemeral(event, ctx, sb.toString());
    }
}
