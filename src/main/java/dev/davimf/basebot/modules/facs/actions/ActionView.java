// [OUTLINE START]
// Package: dev.davimf.basebot.modules.facs.actions
// 
// Class: ActionView
// 
// Constructors:
//   - `Constructor` : `private ActionView()`
// 
// Methods:
//   - `Method` : `public static Container managementPanel(int accent)`
//   - `Method` : `public static Container registerPickType(int accent, List<ActionType> types)`
//   - `Method` : `public static Container registerPastFuture(int accent, String typeId, String typeName)`
//   - `Method` : `public static Modal futureModal(String typeId)`
//   - `Method` : `public static Modal pastModal(String typeId)`
//   - `Method` : `public static Modal timeModal(String actionId, Action existing)`
//   - `Method` : `public static Container panel(int accent, Action action, List<Participant> confirmed, List<Participant> reserve)`
//   - `Method` : `public static Container configPanel(int accent, Action action)`
//   - `Method` : `public static Container manageList(int accent, List<Action> openActions)`
//   - `Method` : `public static String typeSummary(ActionType t)`
//   - `Method` : `private static boolean isResolved(String status)`
//   - `Method` : `private static String memberList(List<Participant> list)`
//   - `Method` : `private static String whenValue(Action action)`
//   - `Method` : `private static String statusLabel(Action action)`
//   - `Method` : `private static String trim(String s, int max)`
// 
// Fields:
//   - `Field` : `public static final String NS`
//   - `Field` : `private static final int MAX_OPTIONS`
// [OUTLINE END]



package dev.davimf.basebot.modules.facs.actions;

import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.database.postgres.ActionTypeRepository.ActionType;
import dev.davimf.basebot.modules.facs.actions.ActionRepository.Action;
import dev.davimf.basebot.modules.facs.actions.ActionRepository.Participant;
import dev.davimf.basebot.util.Emojis;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu.SelectTarget;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.modals.Modal;

import java.util.ArrayList;
import java.util.List;

/** Views for the {@code /painel-acoes} action + reservation system (BOTSPECS Module 4). */
public final class ActionView {

    public static final String NS = "acao";

    /** Discord allows at most 25 options in a select menu. */
    private static final int MAX_OPTIONS = 25;

    private ActionView() {}

    // --- Management panel (posted by /painel-acoes) ----------------------------

    public static Container managementPanel(int accent) {
        return Panels.container(accent,
                Panels.text("## Painel de Ações "+Emojis.of(Emojis.WEAPON, "🔫")+"\n"
                        + "-# Registre uma nova ação ou edite uma já registrada."),
                Panels.divider(),
                ActionRow.of(
                        Button.success(ComponentId.of(NS, "register"), "Registrar ação")
                                .withEmoji(Emojis.button(Emojis.NOTE)),
                        Button.secondary(ComponentId.of(NS, "manage"), "Editar ações")
                                .withEmoji(Emojis.button(Emojis.EDIT))));
    }

    // --- Registration flow (ephemeral) -----------------------------------------

    /**
     * Step 1: pick which saved action type to register. {@code types} must be non-empty.
     *
     * <p>Um menu por categoria em vez de um só: o Discord aceita no máximo 25 opções por
     * select, e antes o excedente era simplesmente cortado — as ações depois da 25ª em
     * ordem alfabética não apareciam para ninguém, sem erro nem aviso.
     */
    public static Container registerPickType(int accent, List<ActionType> types) {
        List<ContainerChildComponent> kids = new ArrayList<>();
        kids.add(Panels.text("## " + Emojis.of(Emojis.NOTE, "📝") + " Registrar ação\n-# Escolha qual ação registrar."));
        kids.add(Panels.divider());

        List<ActionTypeGroups.Group> groups = ActionTypeGroups.chunked(types);
        for (int i = 0; i < groups.size(); i++) {
            ActionTypeGroups.Group g = groups.get(i);
            StringSelectMenu.Builder menu = StringSelectMenu
                    .create(ComponentId.of(NS, "regtype", String.valueOf(i)))
                    .setPlaceholder(g.label());
            for (ActionType t : g.types()) {
                menu.addOption(trim(t.name(), 100), t.id(), trim(typeSummary(t), 100));
            }
            kids.add(ActionRow.of(menu.build()));
        }
        return Panels.container(accent, kids.toArray(new ContainerChildComponent[0]));
    }

    /** Step 2: choose whether the action already happened or is scheduled. */
    public static Container registerPastFuture(int accent, String typeId, String typeName) {
        return Panels.container(accent,
                Panels.text("## " + Emojis.of(Emojis.NOTE, "📝") + " " + typeName + "\n-# Essa ação já aconteceu ou ainda vai acontecer?"),
                Panels.divider(),
                ActionRow.of(
                        Button.primary(ComponentId.of(NS, "regfut", typeId), "Vai acontecer")
                                .withEmoji(Emojis.button(Emojis.CALENDAR)),
                        Button.secondary(ComponentId.of(NS, "regpast", typeId), "Já aconteceu")
                                .withEmoji(Emojis.button(Emojis.CHECK_YES))));
    }

    /** Future-action modal: schedule date + time. */
    public static Modal futureModal(String typeId) {
        TextInput hora = TextInput.create("hora", TextInputStyle.SHORT)
                .setPlaceholder("HH:mm").setRequired(true).setMaxLength(5).build();
        TextInput data = TextInput.create("data", TextInputStyle.SHORT)
                .setPlaceholder("dd/mm/yy · dd/mm · dd/mm/yyyy").setRequired(true).setMaxLength(10).build();
        return Modal.create(ComponentId.of(NS, "createfut", typeId), "Agendar ação")
                .addComponents(Label.of("Hora", hora), Label.of("Data", data))
                .build();
    }

    /** Past-action modal: user-select the members who participated. */
    public static Modal pastModal(String typeId) {
        EntitySelectMenu membros = EntitySelectMenu.create("membros", SelectTarget.USER)
                .setPlaceholder("Quem participou").setRequiredRange(1, 25).build();
        return Modal.create(ComponentId.of(NS, "createpast", typeId), "Registrar ação realizada")
                .addComponents(Label.of("Participantes", membros))
                .build();
    }

    /** Modal to change a scheduled action's date + time. */
    public static Modal timeModal(String actionId, Action existing) {
        TextInput hora = TextInput.create("hora", TextInputStyle.SHORT)
                .setPlaceholder("HH:mm").setRequired(true).setMaxLength(5).build();
        TextInput data = TextInput.create("data", TextInputStyle.SHORT)
                .setPlaceholder("dd/mm/yy · dd/mm · dd/mm/yyyy").setRequired(true).setMaxLength(10).build();
        return Modal.create(ComponentId.of(NS, "settime", actionId), "Mudar horário")
                .addComponents(Label.of("Hora", hora), Label.of("Data", data))
                .build();
    }

    // --- Public action embed ---------------------------------------------------

    public static Container panel(int accent, Action action,
                                  List<Participant> confirmed, List<Participant> reserve) {
        boolean resolved = isResolved(action.status());
        boolean reachedMin = action.minContingent() <= 0 || confirmed.size() >= action.minContingent();
        String denom = !reachedMin ? String.valueOf(action.minContingent())
                : (action.capacity() == 0 ? "∞" : String.valueOf(action.capacity()));
        String stage = !reachedMin ? " — mínimo" : "";
        String title = action.actionName() == null || action.actionName().isBlank()
                ? "Ação" : action.actionName();

        List<ContainerChildComponent> kids = new ArrayList<>();
        kids.add(Panels.text("## " + Emojis.of(Emojis.WEAPON, "🔫") + " " + title));
        kids.add(Panels.divider());

        // Status / Quando / Recompensa — the action's metadata, separate from the roster.
        StringBuilder meta = new StringBuilder("**Status** · ").append(statusLabel(action));
        String when = whenValue(action);
        if (when != null) {
            meta.append("\n" + Emojis.of(Emojis.CLOCK, "🕒") + " **Quando** · ").append(when);
        }
        if (action.dirtyMoney() > 0) {
            meta.append("\n" + Emojis.of(Emojis.EXPENSE, "💸") + " **Recompensa** · `").append(action.dirtyMoney()).append("` sujo");
        }
        kids.add(Panels.text(meta.toString()));
        kids.add(Panels.divider());

        // Confirmados — one member per line.
        StringBuilder conf = new StringBuilder(Emojis.of(Emojis.MEMBER, "👤"))
                .append(" **Confirmados** `").append(confirmed.size()).append('/').append(denom).append('`')
                .append(stage);
        if (action.minContingent() > 0 && !reachedMin) {
            conf.append('\n').append(Emojis.of(Emojis.WARN, "⚠️")).append(" *Abaixo do contingente mínimo.*");
        }
        conf.append('\n').append(memberList(confirmed));
        kids.add(Panels.text(conf.toString()));

        // Reservas — one member per line (scheduled actions only).
        if (!action.isPast()) {
            kids.add(Panels.divider());
            kids.add(Panels.text(Emojis.of(Emojis.MEMBER, "👤") + " **Reservas** `" + reserve.size() + "`\n"
                    + memberList(reserve)));
        }

        kids.add(Panels.divider());
        Button config = Button.secondary(ComponentId.of(NS, "config", action.id()), "Configurar")
                .withEmoji(Emojis.button(Emojis.GEAR));
        // Once a result is declared (Vitória/Derrota/Encerrada) only Configurar remains.
        if (resolved) {
            kids.add(ActionRow.of(config));
        } else if (action.isPast() || action.dueNotified()) {
            // The action has already happened: drop Entrar/Sair/Alinhamento, keep Configurar + result.
            kids.add(ActionRow.of(config));
            kids.add(ActionRow.of(
                    Button.success(ComponentId.of(NS, "victory", action.id()), "Vitória")
                            .withEmoji(Emojis.button(Emojis.TROPHY)),
                    Button.danger(ComponentId.of(NS, "defeat", action.id()), "Derrota")
                            .withEmoji(Emojis.button(Emojis.SKULL))));
        } else {
            // Still upcoming: full participation controls.
            kids.add(ActionRow.of(
                    Button.success(ComponentId.of(NS, "join", action.id()), "Entrar")
                            .withEmoji(Emojis.button(Emojis.CHECK_YES))
                            .withDisabled(!action.entriesOpen()),
                    Button.secondary(ComponentId.of(NS, "leave", action.id()), "Sair")
                            .withEmoji(Emojis.button(Emojis.CHECK_NO)),
                    Button.primary(ComponentId.of(NS, "align", action.id()), "Alinhamento")
                            .withEmoji(Emojis.button(Emojis.VOICE)),
                    config));
        }
        return Panels.container(accent, kids.toArray(new ContainerChildComponent[0]));
    }

    // --- Configure panel (ephemeral, manager only) -----------------------------

    public static Container configPanel(int accent, Action action) {
        EntitySelectMenu add = EntitySelectMenu.create(
                        ComponentId.of(NS, "backfill", action.id()), SelectTarget.USER)
                .setPlaceholder("Adicionar membros").setRequiredRange(1, 25).build();
        EntitySelectMenu rem = EntitySelectMenu.create(
                        ComponentId.of(NS, "removemember", action.id()), SelectTarget.USER)
                .setPlaceholder("Remover membros").setRequiredRange(1, 25).build();

        String title = action.actionName() == null || action.actionName().isBlank()
                ? "Ação" : action.actionName();
        List<ContainerChildComponent> kids = new ArrayList<>();
        kids.add(Panels.text("## " + Emojis.of(Emojis.GEAR, "⚙️") + " Configurar · " + title
                + "\n-# Gerencie os membros e o resultado da ação."));
        kids.add(Panels.divider());
        kids.add(Panels.text("-# " + Emojis.of(Emojis.MEMBER, "👤") + " Membros"));
        kids.add(ActionRow.of(add));
        kids.add(ActionRow.of(rem));
        if (!action.isPast()) {
            kids.add(ActionRow.of(
                    Button.secondary(ComponentId.of(NS, "togglentry", action.id()),
                                    action.entriesOpen() ? "Bloquear entradas" : "Liberar entradas")
                            .withEmoji(action.entriesOpen() ? Emojis.button(Emojis.LOCK) : Emojis.button(Emojis.UNLOCK)),
                    Button.secondary(ComponentId.of(NS, "changetime", action.id()), "Mudar horário")
                            .withEmoji(Emojis.button(Emojis.CLOCK))));
        }
        kids.add(Panels.divider());
        kids.add(Panels.text("-# " + Emojis.of(Emojis.FINISH, "🏁") + " Resultado & encerramento"));
        kids.add(ActionRow.of(
                Button.success(ComponentId.of(NS, "victory", action.id()), "Vitória")
                        .withEmoji(Emojis.button(Emojis.TROPHY)),
                Button.danger(ComponentId.of(NS, "defeat", action.id()), "Derrota")
                        .withEmoji(Emojis.button(Emojis.SKULL)),
                Button.secondary(ComponentId.of(NS, "close", action.id()), "Encerrar")
                        .withEmoji(Emojis.button(Emojis.LOCK))));
        return Panels.container(accent, kids.toArray(new ContainerChildComponent[0]));
    }

    // --- "Editar ações" picker (ephemeral) -------------------------------------

    public static Container manageList(int accent, List<Action> openActions) {
        List<ContainerChildComponent> kids = new ArrayList<>();
        kids.add(Panels.text("## " + Emojis.of(Emojis.EDIT, "✏️") + " Editar ações\n-# Selecione uma ação aberta para configurar."));
        kids.add(Panels.divider());
        if (openActions.isEmpty()) {
            kids.add(Panels.text("-# *Nenhuma ação aberta no momento.*"));
        } else {
            StringSelectMenu.Builder menu = StringSelectMenu.create(ComponentId.of(NS, "managepick"))
                    .setPlaceholder("Ações abertas");
            openActions.stream().limit(MAX_OPTIONS).forEach(a -> {
                String name = a.actionName() == null || a.actionName().isBlank() ? "Ação" : a.actionName();
                String when = a.whenText() == null || a.whenText().isBlank()
                        ? (a.isPast() ? "já realizada" : "sem horário") : a.whenText();
                menu.addOption(trim(name, 100), a.id(), trim(when, 100));
            });
            kids.add(ActionRow.of(menu.build()));
        }
        return Panels.container(accent, kids.toArray(new ContainerChildComponent[0]));
    }

    // --- helpers ---------------------------------------------------------------

    /** "máx X · mín Y · sujo Z" — shown as the saved-type select option. */
    public static String typeSummary(ActionType t) {
        String max = t.maxContingent() == 0 ? "∞" : String.valueOf(t.maxContingent());
        return "máx " + max + " · mín " + t.minContingent() + " · sujo " + t.dirtyMoney();
    }

    private static boolean isResolved(String status) {
        return "VICTORY".equals(status) || "DEFEAT".equals(status) || "CLOSED".equals(status);
    }

    /** One participant per line as a numbered list, or a subtle placeholder when empty. */
    private static String memberList(List<Participant> list) {
        if (list.isEmpty()) {
            return "-# *ninguém ainda*";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(i + 1).append(". <@").append(list.get(i).userId()).append('>');
        }
        return sb.toString();
    }

    /** Scheduled time as a Discord timestamp (absolute + relative), the raw text, or null. */
    private static String whenValue(Action action) {
        if (action.dueAt() > 0) {
            long secs = action.dueAt() / 1000;
            return "<t:" + secs + ":F> • <t:" + secs + ":R>";
        }
        if (action.whenText() != null && !action.whenText().isBlank()) {
            return "`" + action.whenText() + "`";
        }
        return null;
    }

    private static String statusLabel(Action action) {
        return switch (action.status()) {
            case "OPEN" -> action.isPast() ? Emojis.of(Emojis.CHECK_YES, "✅") + " Realizada" : Emojis.of(Emojis.DOT, "⚪") + " Aberta";
            case "CLOSED" -> Emojis.of(Emojis.LOCK, "🔒") + " Encerrada";
            case "VICTORY" -> "" + Emojis.of(Emojis.TROPHY, "🏆") + " Vitória";
            case "DEFEAT" -> "" + Emojis.of(Emojis.SKULL, "💀") + " Derrota";
            default -> action.status();
        };
    }

    private static String trim(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() > max ? s.substring(0, max) : s;
    }
}
