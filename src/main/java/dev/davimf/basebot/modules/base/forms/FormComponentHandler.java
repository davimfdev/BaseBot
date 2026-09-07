package dev.davimf.basebot.modules.base.forms;

import dev.davimf.basebot.util.Emojis;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.component.ComponentHandler;
import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.modules.base.forms.FormRepository.Form;
import dev.davimf.basebot.util.ChannelLog;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;

import java.util.Optional;

/** Routes the {@code /formulario} fill button + submission (BOTSPECS Module 1). */
public final class FormComponentHandler implements ComponentHandler {

    private final FormRepository forms;

    public FormComponentHandler(FormRepository forms) {
        this.forms = forms;
    }

    @Override
    public String namespace() {
        return FormView.NS;
    }

    @Override
    public void onButton(ButtonInteractionEvent event, ComponentId id, BotContext ctx) {
        if (!"fill".equals(id.action())) {
            return;
        }
        Optional<Form> form = forms.find(id.arg(0));
        if (form.isEmpty() || form.get().questions().isEmpty()) {
            Replies.ephemeral(event, ctx, "Esse formulário não está mais disponível.");
            return;
        }
        event.replyModal(FormView.modal(form.get())).queue();
    }

    @Override
    public void onModal(ModalInteractionEvent event, ComponentId id, BotContext ctx) {
        if (!"submit".equals(id.action()) || event.getGuild() == null) {
            return;
        }
        Optional<Form> maybe = forms.find(id.arg(0));
        if (maybe.isEmpty()) {
            Replies.ephemeral(event, ctx, "Formulário indisponível.");
            return;
        }
        Form form = maybe.get();
        StringBuilder body = new StringBuilder("## " + Emojis.of(Emojis.LIST, "📋") + " ").append(form.title())
                .append("\n---\n" + Emojis.of(Emojis.MEMBER, "👤") + " **De** · <@").append(event.getUser().getId()).append(">\n---\n");
        for (int i = 0; i < form.questions().size(); i++) {
            ModalMapping answer = event.getValue("q" + i);
            body.append("\n**").append(form.questions().get(i)).append("**\n> ")
                    .append(answer == null ? "—" : answer.getAsString());
        }
        ChannelLog.post(ctx, event.getGuild().getId(), "log-formularios", body.toString());
        ctx.database().actionLogs().log(event.getGuild().getId(), event.getUser().getId(),
                form.id(), "FORM_SUBMIT", form.title());
        Replies.ephemeral(event, ctx, Emojis.of(Emojis.CHECK_YES, "✅") + " Formulário enviado. Obrigado!");
    }
}
