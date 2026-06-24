package dev.davimf.basebot.modules.facs.actions;

import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.modules.facs.actions.ActionRepository.Action;
import dev.davimf.basebot.modules.facs.actions.ActionRepository.Participant;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu.SelectTarget;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.modals.Modal;

import java.util.List;
import java.util.stream.Collectors;

/** Views for the {@code /painel-acoes} action + reservation system (BOTSPECS Module 4). */
public final class ActionView {

    public static final String NS = "acao";

    private ActionView() {}

    public static Modal createModal() {
        TextInput quando = TextInput.create("quando", TextInputStyle.SHORT)
                .setPlaceholder("Ex: Hoje 21:00").setRequired(true).setMaxLength(80).build();
        TextInput vagas = TextInput.create("vagas", TextInputStyle.SHORT)
                .setPlaceholder("Vagas confirmadas (0 = ilimitado)").setRequired(true).setMaxLength(4)
                .setValue("0").build();
        return Modal.create(ComponentId.of(NS, "create"), "Nova ação")
                .addComponents(Label.of("Data/Hora", quando), Label.of("Vagas", vagas))
                .build();
    }

    public static Container panel(int accent, Action action,
                                  List<Participant> confirmed, List<Participant> reserve) {
        boolean open = "OPEN".equals(action.status());
        String cap = action.capacity() == 0 ? "∞" : String.valueOf(action.capacity());
        StringBuilder body = new StringBuilder("## ⚔️ Ação\n")
                .append("**Quando:** ").append(action.whenText()).append('\n')
                .append("**Status:** ").append(statusLabel(action.status())).append('\n')
                .append("**Confirmados (").append(confirmed.size()).append('/').append(cap).append("):** ")
                .append(mentions(confirmed))
                .append("\n**Reservas (").append(reserve.size()).append("):** ").append(mentions(reserve));
        return Panels.container(accent,
                Panels.text(body.toString()),
                ActionRow.of(
                        Button.success(ComponentId.of(NS, "join", action.id()), "Entrar").withDisabled(!open),
                        Button.secondary(ComponentId.of(NS, "leave", action.id()), "Sair").withDisabled(!open),
                        Button.primary(ComponentId.of(NS, "align", action.id()), "Alinhamento"),
                        Button.secondary(ComponentId.of(NS, "config", action.id()), "Configurar")));
    }

    public static Container configPanel(int accent, String actionId) {
        EntitySelectMenu backfill = EntitySelectMenu.create(
                        ComponentId.of(NS, "backfill", actionId), SelectTarget.USER)
                .setPlaceholder("Adicionar presença (atrasados)").setRequiredRange(1, 10).build();
        return Panels.container(accent,
                Panels.text("## ⚙️ Configurar ação\nAdicione atrasados, registre o resultado ou encerre."),
                ActionRow.of(backfill),
                ActionRow.of(
                        Button.success(ComponentId.of(NS, "victory", actionId), "🏆 Vitória"),
                        Button.danger(ComponentId.of(NS, "defeat", actionId), "💀 Derrota"),
                        Button.secondary(ComponentId.of(NS, "close", actionId), "🔒 Encerrar")));
    }

    private static String mentions(List<Participant> list) {
        return list.isEmpty() ? "*ninguém*"
                : list.stream().map(p -> "<@" + p.userId() + ">").collect(Collectors.joining(" "));
    }

    private static String statusLabel(String status) {
        return switch (status) {
            case "OPEN" -> "🟢 Aberta";
            case "CLOSED" -> "🔒 Encerrada";
            case "VICTORY" -> "🏆 Vitória";
            case "DEFEAT" -> "💀 Derrota";
            default -> status;
        };
    }
}
