package dev.davimf.basebot.modules.sales.pix;

import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.database.model.PixKey;
import dev.davimf.basebot.util.Emojis;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu.SelectTarget;
import net.dv8tion.jda.api.components.selections.SelectOption;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;

import java.util.ArrayList;
import java.util.List;

/** Render das telas efêmeras do painel Pix (namespace "pix"). */
public final class PixPanelView {

    public static final String NS = "pix";

    private PixPanelView() {}

    /** Painel raiz: enviar cobrança ou gerenciar chaves. */
    public static Container root(int accent, int keyCount) {
        String body = "## " + Emojis.of(Emojis.GEM, "💠") + " Pix\n"
                + "-# Você tem `" + keyCount + "` chave(s) cadastrada(s).";
        return Panels.container(accent,
                Panels.text(body),
                Panels.divider(),
                ActionRow.of(
                        Button.success(ComponentId.of(NS, "send"), "Enviar cobrança")
                                .withEmoji(Emojis.button(Emojis.CASH)),
                        Button.secondary(ComponentId.of(NS, "manage"), "Gerenciar chaves")
                                .withEmoji(Emojis.button(Emojis.KEY))));
    }

    /** Tela de gerenciamento: lista de chaves + editar/remover + cadastrar nova. */
    public static Container manage(int accent, List<PixKey> keys) {
        StringBuilder sb = new StringBuilder("## " + Emojis.of(Emojis.KEY, "🔑") + " Suas chaves Pix\n");
        List<ActionRow> rows = new ArrayList<>();
        if (keys.isEmpty()) {
            sb.append("-# Nenhuma chave ainda. Cadastre a primeira.");
        } else {
            for (PixKey k : keys) {
                sb.append("\n• `").append(k.keyType()).append("` ").append(mask(k.keyValue()))
                        .append(" · ").append(k.merchantName());
                rows.add(ActionRow.of(
                        Button.secondary(ComponentId.of(NS, "edit", String.valueOf(k.id())),
                                "Editar: " + mask(k.keyValue())).withEmoji(Emojis.button(Emojis.EDIT)),
                        Button.danger(ComponentId.of(NS, "del", String.valueOf(k.id())), "Remover")));
                if (rows.size() >= 4) {
                    break;
                }
            }
        }
        List<ContainerChildComponent> kids = new ArrayList<>();
        kids.add(Panels.text(sb.toString()));
        kids.add(Panels.divider());
        kids.addAll(rows);
        kids.add(ActionRow.of(
                Button.primary(ComponentId.of(NS, "new"), "Cadastrar nova")
                        .withEmoji(Emojis.button(Emojis.PLUS)),
                Button.secondary(ComponentId.of(NS, "root"), "◀ Voltar")));
        return Panels.container(accent, kids.toArray(new ContainerChildComponent[0]));
    }

    /** Select do tipo da nova chave. */
    public static Container newType(int accent) {
        StringSelectMenu menu = StringSelectMenu.create(ComponentId.of(NS, "typenew"))
                .setPlaceholder("Tipo da nova chave…")
                .addOptions(
                        SelectOption.of("CPF", "CPF"),
                        SelectOption.of("CNPJ", "CNPJ"),
                        SelectOption.of("E-mail", "EMAIL"),
                        SelectOption.of("Telefone", "PHONE"),
                        SelectOption.of("Aleatória", "RANDOM"))
                .build();
        return Panels.container(accent,
                Panels.text("## " + Emojis.of(Emojis.PLUS, "➕") + " Nova chave"),
                Panels.divider(),
                Panels.text("-# Escolha o tipo; em seguida informe a chave e o nome do recebedor."),
                ActionRow.of(menu),
                ActionRow.of(Button.secondary(ComponentId.of(NS, "manage"), "◀ Voltar")));
    }

    /**
     * Tela de envio: um select de chave (valor da opção = id da chave) e um select de cliente
     * opcional. O cliente escolhido é encodado no id do select de chave ({@code sendkey:<clientId>})
     * para viajar até o modal de valor.
     */
    public static Container sendPicker(int accent, List<PixKey> keys, String clientId) {
        boolean hasClient = clientId != null && !clientId.isBlank();
        StringSelectMenu.Builder menu = StringSelectMenu.create(ComponentId.of(NS, "sendkey", hasClient ? clientId : ""))
                .setPlaceholder("Escolha a chave para a cobrança…")
                .setMinValues(1).setMaxValues(1);
        for (PixKey k : keys) {
            menu.addOptions(SelectOption.of(k.keyType() + " · " + mask(k.keyValue()), String.valueOf(k.id()))
                    .withDescription(k.merchantName()));
        }
        EntitySelectMenu.Builder client = EntitySelectMenu.create(ComponentId.of(NS, "sendclient"), SelectTarget.USER)
                .setPlaceholder("Cliente (opcional)…")
                .setRequiredRange(0, 1);
        if (hasClient) {
            client.setDefaultValues(EntitySelectMenu.DefaultValue.user(clientId));
        }
        String clientLine = hasClient
                ? Emojis.of(Emojis.MEMBER, "🧑") + " **Cliente** · <@" + clientId + ">"
                : "-# Sem cliente atribuído (opcional).";
        return Panels.container(accent,
                Panels.text("## " + Emojis.of(Emojis.CASH, "💵") + " Enviar cobrança"),
                Panels.divider(),
                Panels.text("-# Escolha a chave; opcionalmente atribua um cliente. Depois informe um valor (opcional)."),
                Panels.text(clientLine),
                ActionRow.of(client.build()),
                ActionRow.of(menu.build()),
                ActionRow.of(Button.secondary(ComponentId.of(NS, "root"), "◀ Voltar")));
    }

    static String mask(String value) {
        if (value == null || value.length() <= 4) {
            return value == null ? "" : value;
        }
        return "…" + value.substring(value.length() - 4);
    }
}
