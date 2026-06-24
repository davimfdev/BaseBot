package dev.davimf.basebot.modules.base.embed;

import dev.davimf.basebot.core.component.ComponentId;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.modals.Modal;

/** Modals for the {@code /embed} and {@code /editembed} builders (BOTSPECS Module 1). */
public final class EmbedView {

    public static final String NS = "embed";

    private EmbedView() {}

    /** Compose modal: content + optional webhook impersonation identity. */
    public static Modal compose() {
        return Modal.create(ComponentId.of(NS, "send"), "Criar embed")
                .addComponents(
                        Label.of("Título", shortInput("titulo", "Título (opcional)", false, 256)),
                        Label.of("Descrição", paragraph("descricao", "Texto do embed", true, 4000)),
                        Label.of("Cor (hex)", shortInput("cor", "Ex: #5865F2 (opcional)", false, 9)),
                        Label.of("Nome (webhook)", shortInput("nome", "Personificar nome (opcional)", false, 80)),
                        Label.of("Avatar (URL)", shortInput("avatar", "URL do avatar (opcional)", false, 400)))
                .build();
    }

    /** Edit modal: re-enter the embed content for an existing message id. */
    public static Modal edit(String messageId) {
        return Modal.create(ComponentId.of(NS, "editform", messageId), "Editar embed")
                .addComponents(
                        Label.of("Título", shortInput("titulo", "Título (opcional)", false, 256)),
                        Label.of("Descrição", paragraph("descricao", "Novo texto do embed", true, 4000)),
                        Label.of("Cor (hex)", shortInput("cor", "Ex: #5865F2 (opcional)", false, 9)))
                .build();
    }

    private static TextInput shortInput(String id, String placeholder, boolean required, int max) {
        return TextInput.create(id, TextInputStyle.SHORT)
                .setPlaceholder(placeholder).setRequired(required).setMaxLength(max).build();
    }

    private static TextInput paragraph(String id, String placeholder, boolean required, int max) {
        return TextInput.create(id, TextInputStyle.PARAGRAPH)
                .setPlaceholder(placeholder).setRequired(required).setMaxLength(max).build();
    }
}
