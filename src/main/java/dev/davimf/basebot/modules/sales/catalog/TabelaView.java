package dev.davimf.basebot.modules.sales.catalog;

import dev.davimf.basebot.util.Emojis;

import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.database.model.CatalogCategory;
import dev.davimf.basebot.database.model.CatalogProduct;
import dev.davimf.basebot.util.Money;
import dev.davimf.basebot.util.Paginator;
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

/** Components V2 views for the {@code /tabela} catalog hub (BOTSPECS Module 3). */
public final class TabelaView {

    public static final String NS = "tabela";

    /** Products listed per page in a category view. */
    public static final int PAGE_SIZE = 8;

    private TabelaView() {}

    // --- Hub (category list) ---------------------------------------------------

    public static Container hub(int accent, List<CatalogCategory> categories) {
        List<ContainerChildComponent> kids = new ArrayList<>();
        kids.add(Panels.text("## " + Emojis.of(Emojis.RECEIPT, "🧾") + " Tabela de Preços"));
        kids.add(Panels.divider());
        kids.add(Panels.text("> " + (categories.isEmpty()
                ? "Nenhuma categoria cadastrada ainda. Crie a primeira abaixo."
                : "Selecione uma categoria para ver os produtos ou gerencie o catálogo.")));
        if (!categories.isEmpty()) {
            StringSelectMenu.Builder menu = StringSelectMenu.create(ComponentId.of(NS, "catview"))
                    .setPlaceholder("Ver categoria");
            for (CatalogCategory cat : categories) {
                menu.addOption(trim(cat.name(), 100), cat.id());
            }
            kids.add(ActionRow.of(menu.build()));
        }
        kids.add(ActionRow.of(Button.success(ComponentId.of(NS, "catnew"), "Nova categoria").withEmoji(Emojis.button(Emojis.PLUS))));
        return Panels.container(accent, kids.toArray(new ContainerChildComponent[0]));
    }

    // --- Category view (products, paginated) -----------------------------------

    public static Container category(int accent, CatalogCategory cat,
                                     List<CatalogProduct> products, int pageIndex) {
        int pages = Paginator.pageCount(products.size(), PAGE_SIZE);
        int page = Math.max(0, Math.min(pageIndex, pages - 1));
        List<CatalogProduct> slice = Paginator.page(products, page, PAGE_SIZE);

        StringBuilder body = new StringBuilder();
        if (products.isEmpty()) {
            body.append("-# *Nenhum produto nesta categoria.*");
        } else {
            for (CatalogProduct p : slice) {
                if (body.length() > 0) {
                    body.append('\n');
                }
                body.append("**").append(p.name()).append("** · `")
                        .append(Money.format(p.priceCents())).append('`');
                if (p.description() != null && !p.description().isBlank()) {
                    body.append("\n-# ").append(p.description());
                }
            }
            body.append("\n\n-# Página ").append(page + 1).append('/').append(pages);
        }

        List<ContainerChildComponent> kids = new ArrayList<>();
        kids.add(Panels.text("## " + Emojis.of(Emojis.RECEIPT, "🧾") + " " + cat.name()));
        kids.add(Panels.divider());
        kids.add(Panels.text(body.toString()));
        kids.add(ActionRow.of(
                Button.secondary(ComponentId.of(NS, "prodnav", cat.id(), String.valueOf(page - 1)), "◀")
                        .withDisabled(page <= 0),
                Button.secondary(ComponentId.of(NS, "prodnav", cat.id(), String.valueOf(page + 1)), "▶")
                        .withDisabled(page >= pages - 1),
                Button.secondary(ComponentId.of(NS, "hub"), "◀ Voltar")));
        kids.add(ActionRow.of(
                Button.success(ComponentId.of(NS, "prodnew", cat.id()), "Novo produto").withEmoji(Emojis.button(Emojis.PLUS)),
                Button.danger(ComponentId.of(NS, "catdel", cat.id()), "Remover categoria").withEmoji(Emojis.button(Emojis.TRASH))));
        if (!slice.isEmpty()) {
            StringSelectMenu.Builder del = StringSelectMenu.create(ComponentId.of(NS, "proddel", cat.id()))
                    .setPlaceholder("Remover um produto desta página");
            for (CatalogProduct p : slice) {
                del.addOption(trim(p.name() + " — " + Money.format(p.priceCents()), 100), p.id());
            }
            kids.add(ActionRow.of(del.build()));
        }
        return Panels.container(accent, kids.toArray(new ContainerChildComponent[0]));
    }

    // --- Modals ----------------------------------------------------------------

    public static Modal categoryModal() {
        TextInput nome = TextInput.create("nome", TextInputStyle.SHORT)
                .setPlaceholder("Ex: Veículos").setRequired(true).setMaxLength(80).build();
        return Modal.create(ComponentId.of(NS, "catform"), "Nova categoria")
                .addComponents(Label.of("Nome", nome))
                .build();
    }

    public static Modal productModal(String categoryId) {
        TextInput nome = TextInput.create("nome", TextInputStyle.SHORT)
                .setPlaceholder("Ex: Adder").setRequired(true).setMaxLength(80).build();
        TextInput preco = TextInput.create("preco", TextInputStyle.SHORT)
                .setPlaceholder("Ex: 1.500,00").setRequired(true).setMaxLength(20).build();
        TextInput descricao = TextInput.create("descricao", TextInputStyle.PARAGRAPH)
                .setPlaceholder("Descrição (opcional)").setRequired(false).setMaxLength(200).build();
        return Modal.create(ComponentId.of(NS, "prodform", categoryId), "Novo produto")
                .addComponents(
                        Label.of("Nome", nome),
                        Label.of("Preço", preco),
                        Label.of("Descrição", descricao))
                .build();
    }

    private static String trim(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }
}
