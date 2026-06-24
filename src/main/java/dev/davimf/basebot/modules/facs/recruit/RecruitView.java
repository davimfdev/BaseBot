package dev.davimf.basebot.modules.facs.recruit;

import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.core.component.Panels;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu.SelectTarget;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.modals.Modal;

/** Views for the recruitment (Set) pipeline (BOTSPECS Module 4). */
public final class RecruitView {

    public static final String NS = "recruit";

    private RecruitView() {}

    /** Static public panel with the apply button. */
    public static Container panel(int accent, String description) {
        String body = "## 📝 Recrutamento\n"
                + (description == null || description.isBlank()
                        ? "Clique abaixo para solicitar sua entrada na facção." : description);
        return Panels.container(accent,
                Panels.text(body),
                ActionRow.of(Button.success(ComponentId.of(NS, "open"), "Solicitar Entrada")));
    }

    /** The applicant form. */
    public static Modal form() {
        TextInput id = TextInput.create("idjogo", TextInputStyle.SHORT)
                .setPlaceholder("Seu ID no jogo").setRequired(true).setMaxLength(20).build();
        TextInput nome = TextInput.create("nome", TextInputStyle.SHORT)
                .setPlaceholder("Nome do personagem").setRequired(true).setMaxLength(80).build();
        TextInput telefone = TextInput.create("telefone", TextInputStyle.SHORT)
                .setPlaceholder("Telefone in-game").setRequired(true).setMaxLength(20).build();
        EntitySelectMenu recrutador = EntitySelectMenu.create("recrutador", SelectTarget.USER)
                .setPlaceholder("Quem te recrutou").setRequiredRange(1, 1).build();
        return Modal.create(ComponentId.of(NS, "form"), "Solicitação de entrada")
                .addComponents(
                        Label.of("ID no jogo", id),
                        Label.of("Nome", nome),
                        Label.of("Telefone", telefone),
                        Label.of("Recrutador", recrutador))
                .build();
    }

    /** The review request posted to the private channel with accept/reject buttons. */
    public static Container request(int accent, String applicantId, String recruiterId,
                                    String idJogo, String nome, String telefone) {
        String body = "## 📝 Solicitação de entrada\n"
                + "**Candidato:** <@" + applicantId + ">\n"
                + "**ID no jogo:** " + idJogo + "\n"
                + "**Nome:** " + nome + "\n"
                + "**Telefone:** " + telefone + "\n"
                + "**Recrutador:** <@" + recruiterId + ">";
        return Panels.container(accent,
                Panels.text(body),
                ActionRow.of(
                        Button.success(ComponentId.of(NS, "accept", applicantId, recruiterId), "✅ Aceitar"),
                        Button.danger(ComponentId.of(NS, "reject", applicantId, recruiterId), "❌ Recusar")));
    }

    public static Container resolved(int accent, String applicantId, String recruiterId, String statusLine) {
        return Panels.container(accent, Panels.text("## 📝 Solicitação de entrada\n"
                + "**Candidato:** <@" + applicantId + ">\n"
                + "**Recrutador:** <@" + recruiterId + ">\n" + statusLine));
    }
}
