package dev.davimf.basebot.modules.facs.economy;

import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.util.Emojis;
import dev.davimf.basebot.util.Money;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu.SelectTarget;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.modals.Modal;

/** Views for the {@code /painel-financeiro} treasury panel (BOTSPECS Module 4). */
public final class FinanceView {

    public static final String NS = "fin";

    private FinanceView() {}

    public static Container panel(int accent, long balanceCents,
                                  boolean lavagem, int lavagemPct, boolean desmanche, int desmanchePct) {
        String body = "" + Emojis.of(Emojis.BANK, "🏦") + " **Saldo** · `" + Money.format(balanceCents) + "`\n"
                + "" + Emojis.of(Emojis.BROOM, "🧼") + " **Lavagem** · " + (lavagem ? Emojis.of(Emojis.CHECK_YES, "✅") + " ativada" : Emojis.of(Emojis.CHECK_NO, "❌") + " desativada")
                + " · `" + lavagemPct + "%`\n"
                + "" + Emojis.of(Emojis.WRENCH, "🔧") + " **Desmanche** · " + (desmanche ? Emojis.of(Emojis.CHECK_YES, "✅") + " ativado" : Emojis.of(Emojis.CHECK_NO, "❌") + " desativado")
                + " · `" + desmanchePct + "%`";
        return Panels.container(accent,
                Panels.text("## " + Emojis.of(Emojis.MONEY, "💰") + " Painel Financeiro"),
                Panels.divider(),
                Panels.text(body),
                Panels.divider(),
                ActionRow.of(
                        Button.success(ComponentId.of(NS, "deposit"), "Depósito"),
                        Button.danger(ComponentId.of(NS, "withdraw"), "Saque"),
                        Button.primary(ComponentId.of(NS, "transfer"), "Transferência")),
                ActionRow.of(
                        Button.secondary(ComponentId.of(NS, "togglelav"),
                                "Lavagem: " + (lavagem ? "ON" : "OFF")).withEmoji(Emojis.button(Emojis.BROOM)),
                        Button.secondary(ComponentId.of(NS, "toggledesm"),
                                "Desmanche: " + (desmanche ? "ON" : "OFF")).withEmoji(Emojis.button(Emojis.WRENCH)),
                        Button.secondary(ComponentId.of(NS, "setpct"), "Definir %")));
    }

    public static Modal amountModal(String action, String title, String label) {
        TextInput valor = TextInput.create("valor", TextInputStyle.SHORT)
                .setPlaceholder("Ex: 1.500,00").setRequired(true).setMaxLength(20).build();
        return Modal.create(ComponentId.of(NS, action), title)
                .addComponents(Label.of(label, valor))
                .build();
    }

    public static Modal transferModal() {
        TextInput valor = TextInput.create("valor", TextInputStyle.SHORT)
                .setPlaceholder("Ex: 1.500,00").setRequired(true).setMaxLength(20).build();
        EntitySelectMenu destino = EntitySelectMenu.create("destino", SelectTarget.USER)
                .setPlaceholder("Destinatário").setRequiredRange(1, 1).build();
        return Modal.create(ComponentId.of(NS, "traform"), "Transferência")
                .addComponents(Label.of("Valor", valor), Label.of("Destinatário", destino))
                .build();
    }

    public static Modal pctModal(int lavagemPct, int desmanchePct) {
        TextInput lav = TextInput.create("lavagem", TextInputStyle.SHORT)
                .setPlaceholder("0-100").setRequired(true).setMaxLength(3)
                .setValue(String.valueOf(lavagemPct)).build();
        TextInput desm = TextInput.create("desmanche", TextInputStyle.SHORT)
                .setPlaceholder("0-100").setRequired(true).setMaxLength(3)
                .setValue(String.valueOf(desmanchePct)).build();
        return Modal.create(ComponentId.of(NS, "pctform"), "Percentuais")
                .addComponents(Label.of("Lavagem (%)", lav), Label.of("Desmanche (%)", desm))
                .build();
    }
}
