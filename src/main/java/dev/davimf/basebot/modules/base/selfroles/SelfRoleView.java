package dev.davimf.basebot.modules.base.selfroles;

import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.core.component.Panels;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.selections.SelectOption;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.entities.emoji.Emoji;

import java.util.ArrayList;
import java.util.List;

/** Render do painel público de self-roles (botões ou menu). */
public final class SelfRoleView {

    public static final String NS = "selfrole";

    private SelfRoleView() {}

    public static Container panel(int accent, SelfRolePanel p) {
        List<ContainerChildComponent> kids = new ArrayList<>();
        kids.add(Panels.text("## " + p.title()));
        if (p.description() != null && !p.description().isBlank()) {
            kids.add(Panels.divider());
            kids.add(Panels.text(p.description()));
        }
        kids.add(Panels.divider());
        if (SelfRolePanel.STYLE_MENU.equals(p.style())) {
            kids.add(ActionRow.of(menu(p)));
        } else {
            kids.addAll(buttonRows(p));
        }
        return Panels.container(accent, kids.toArray(new ContainerChildComponent[0]));
    }

    private static StringSelectMenu menu(SelfRolePanel p) {
        StringSelectMenu.Builder b = StringSelectMenu.create(ComponentId.of(NS, "select", p.id()))
                .setPlaceholder("Escolha seus cargos…")
                .setMinValues(0)
                .setMaxValues(p.unique() ? 1 : Math.max(1, p.options().size()));
        for (SelfRolePanel.Option o : p.options()) {
            SelectOption opt = SelectOption.of(o.label(), o.roleId());
            if (o.emoji() != null && !o.emoji().isBlank()) {
                opt = opt.withEmoji(Emoji.fromFormatted(o.emoji()));
            }
            b.addOptions(opt);
        }
        return b.build();
    }

    private static List<ActionRow> buttonRows(SelfRolePanel p) {
        List<Button> buttons = new ArrayList<>();
        for (SelfRolePanel.Option o : p.options()) {
            Button btn = Button.secondary(ComponentId.of(NS, "toggle", p.id(), o.roleId()), o.label());
            if (o.emoji() != null && !o.emoji().isBlank()) {
                btn = btn.withEmoji(Emoji.fromFormatted(o.emoji()));
            }
            buttons.add(btn);
        }
        List<ActionRow> rows = new ArrayList<>();
        for (int i = 0; i < buttons.size(); i += 5) {
            rows.add(ActionRow.of(buttons.subList(i, Math.min(i + 5, buttons.size()))));
        }
        return rows;
    }
}
