package dev.davimf.basebot.modules.sales.budget;

import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.database.model.BudgetItem;
import dev.davimf.basebot.database.model.CatalogCategory;
import dev.davimf.basebot.database.model.CatalogProduct;
import dev.davimf.basebot.util.Money;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.modals.Modal;

import java.util.ArrayList;
import java.util.List;

/** Components V2 views for the {@code /orçamento} builder + approval (BOTSPECS Module 3). */
public final class BudgetView {

    public static final String NS = "orcamento";

    private BudgetView() {}

    // --- Seller builder (ephemeral) --------------------------------------------

    public static Container builder(int accent, String budgetId, String clientId,
                                    List<BudgetItem> items, List<CatalogCategory> categories) {
        List<ContainerChildComponent> kids = new ArrayList<>();
        kids.add(Panels.text(cart("## 🧮 Novo orçamento\n**Cliente:** <@" + clientId + ">", items)));

        if (categories.isEmpty()) {
            kids.add(Panels.text("\n*Nenhuma categoria no catálogo. Crie produtos com `/tabela` primeiro.*"));
        } else {
            StringSelectMenu.Builder menu = StringSelectMenu.create(ComponentId.of(NS, "pickcat", budgetId))
                    .setPlaceholder("Adicionar item: escolha a categoria");
            for (CatalogCategory cat : categories) {
                menu.addOption(trim(cat.name(), 100), cat.id());
            }
            kids.add(ActionRow.of(menu.build()));
        }
        kids.add(ActionRow.of(
                Button.success(ComponentId.of(NS, "send", budgetId), "📨 Enviar ao cliente")
                        .withDisabled(items.isEmpty()),
                Button.danger(ComponentId.of(NS, "cancel", budgetId), "Cancelar")));
        return Panels.container(accent, kids.toArray(new ContainerChildComponent[0]));
    }

    public static Container productPicker(int accent, String budgetId, CatalogCategory category,
                                          List<CatalogProduct> products) {
        List<ContainerChildComponent> kids = new ArrayList<>();
        kids.add(Panels.text("## 🧮 " + category.name() + "\nEscolha um produto para adicionar."));
        if (products.isEmpty()) {
            kids.add(Panels.text("\n*Sem produtos nesta categoria.*"));
        } else {
            StringSelectMenu.Builder menu = StringSelectMenu
                    .create(ComponentId.of(NS, "pickprod", budgetId, category.id()))
                    .setPlaceholder("Selecione um produto");
            for (CatalogProduct p : products) {
                menu.addOption(trim(p.name() + " — " + Money.format(p.priceCents()), 100), p.id());
            }
            kids.add(ActionRow.of(menu.build()));
        }
        kids.add(ActionRow.of(Button.secondary(ComponentId.of(NS, "back", budgetId), "◀ Voltar")));
        return Panels.container(accent, kids.toArray(new ContainerChildComponent[0]));
    }

    public static Modal quantityModal(String budgetId, String productId, String productName) {
        TextInput qtd = TextInput.create("qtd", TextInputStyle.SHORT)
                .setPlaceholder("Ex: 2").setRequired(true).setMaxLength(6).setValue("1").build();
        return Modal.create(ComponentId.of(NS, "qty", budgetId, productId),
                        trim("Quantidade: " + productName, 45))
                .addComponents(Label.of("Quantidade", qtd))
                .build();
    }

    // --- Client approval (public) ----------------------------------------------

    public static Container approval(int accent, String budgetId, String sellerId, String clientId,
                                     List<BudgetItem> items) {
        String header = "## 🧾 Orçamento\n**Vendedor:** <@" + sellerId + ">\n**Cliente:** <@" + clientId + ">";
        return Panels.container(accent,
                Panels.text(cart(header, items)),
                Panels.text("-# Expira em 24h se não for respondido."),
                ActionRow.of(
                        Button.success(ComponentId.of(NS, "approve", budgetId), "✅ Aprovar"),
                        Button.danger(ComponentId.of(NS, "reject", budgetId), "❌ Recusar")));
    }

    /** Final, button-less state of the approval message (approved/rejected/expired). */
    public static Container resolved(int accent, String sellerId, String clientId,
                                     List<BudgetItem> items, String statusLine) {
        String header = "## 🧾 Orçamento\n**Vendedor:** <@" + sellerId + ">\n**Cliente:** <@" + clientId + ">";
        return Panels.container(accent,
                Panels.text(cart(header, items)),
                Panels.text(statusLine));
    }

    // --- helpers ---------------------------------------------------------------

    /** Renders the item list + total under {@code header}. */
    private static String cart(String header, List<BudgetItem> items) {
        StringBuilder sb = new StringBuilder(header).append("\n");
        if (items.isEmpty()) {
            sb.append("\n*Nenhum item adicionado ainda.*");
            return sb.toString();
        }
        long total = 0;
        for (BudgetItem it : items) {
            total += it.subtotalCents();
            sb.append("\n• ").append(it.quantity()).append("x **").append(it.productName())
                    .append("** — ").append(Money.format(it.subtotalCents()));
        }
        sb.append("\n\n**Total:** ").append(Money.format(total));
        return sb.toString();
    }

    static long total(List<BudgetItem> items) {
        long total = 0;
        for (BudgetItem it : items) {
            total += it.subtotalCents();
        }
        return total;
    }

    private static String trim(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }
}
