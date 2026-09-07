package dev.davimf.basebot.modules.base.forms;

import dev.davimf.basebot.util.Emojis;

import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.modules.base.forms.FormRepository.Form;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.modals.Modal;

/** Views for the {@code /formulario} pipeline (BOTSPECS Module 1). */
public final class FormView {

    public static final String NS = "form";

    private FormView() {}

    /** Public panel with the fill button. */
    public static Container panel(int accent, Form form) {
        return Panels.container(accent,
                Panels.text("## " + Emojis.of(Emojis.LIST, "📋") + " " + form.title()),
                Panels.divider(),
                Panels.text("> Clique no botão abaixo para preencher este formulário."),
                ActionRow.of(Button.primary(ComponentId.of(NS, "fill", form.id()), "Preencher")
                        .withEmoji(Emojis.button(Emojis.EDIT))));
    }

    /** The form modal built from the stored questions (≤5). */
    public static Modal modal(Form form) {
        Modal.Builder builder = Modal.create(ComponentId.of(NS, "submit", form.id()), trim(form.title(), 45));
        for (int i = 0; i < form.questions().size(); i++) {
            String label = form.questions().get(i);
            TextInput input = TextInput.create("q" + i, TextInputStyle.PARAGRAPH)
                    .setPlaceholder(trim(label, 100)).setRequired(true).setMaxLength(1000).build();
            builder.addComponents(Label.of(trim(label, 45), input));
        }
        return builder.build();
    }

    private static String trim(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }
}
