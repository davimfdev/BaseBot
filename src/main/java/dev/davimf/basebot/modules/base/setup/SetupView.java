package dev.davimf.basebot.modules.base.setup;

import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.database.model.TicketCategory;
import dev.davimf.basebot.modules.base.setup.SetupLogTypes.LogType;
import dev.davimf.basebot.util.EmbedColor;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu.DefaultValue;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu.SelectTarget;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.modals.Modal;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds every {@code /setup} screen as a Components V2 container. To minimise steps,
 * each screen packs as many channel/role selects as fit (each pre-filled with the
 * current value); the Logs screen is paginated by module with a navigator. Every
 * non-hub screen has a "◀ Voltar" button.
 */
public final class SetupView {

    public static final String NS = "setup";

    /** Max selects per screen — kept within Discord's component limit (matches the target UI). */
    private static final int PER_PAGE = 8;

    private SetupView() {}

    // --- Hub -------------------------------------------------------------------

    public static Container hub(GuildConfig cfg, int ticketCategoryCount) {
        long logsSet = SetupLogTypes.ALL.stream().filter(t -> cfg.channel(t.key()) != null).count();
        String body = "## ⚙️ Configuração do Servidor\n"
                + "Escolha uma seção para configurar. As alterações são salvas na hora.\n\n"
                + "📋 **Logs configurados:** " + logsSet + "/" + SetupLogTypes.ALL.size() + "\n"
                + "🎫 **Categorias de ticket:** " + ticketCategoryCount + "\n"
                + "👥 **Cargos configurados:** " + cfg.roles().size();

        StringSelectMenu menu = StringSelectMenu.create(ComponentId.of(NS, "section"))
                .setPlaceholder("Escolha a seção")
                .addOption("Logs", "logs", "Um canal próprio para cada tipo de log")
                .addOption("Cargos", "roles", "Mapear cargos lógicos a cargos do servidor")
                .addOption("Tickets", "tickets", "Categoria, staff, descrição e emoji")
                .addOption("Bot", "bot", "Perfil e cor das embeds do bot")
                .build();

        return Panels.container(EmbedColor.resolve(cfg),
                Panels.text(body), Panels.divider(), ActionRow.of(menu));
    }

    // --- Logs (many selects per screen, paginated by module) ------------------

    public static Container logsPage(GuildConfig cfg, int pageIndex) {
        List<List<LogType>> pages = SetupLogTypes.pages(PER_PAGE);
        int total = pages.size();
        int idx = Math.max(0, Math.min(pageIndex, total - 1));
        List<LogType> page = pages.get(idx);
        String module = page.get(0).module();

        List<ContainerChildComponent> kids = new ArrayList<>();
        kids.add(Panels.text("## 📋 Logs — " + module + "  (" + (idx + 1) + "/" + total + ")\n"
                + "Selecione o canal de cada log. Salva ao selecionar."));
        for (LogType t : page) {
            kids.add(Panels.text("**" + t.label() + "**"));
            kids.add(ActionRow.of(channelSelect("setlogchannel", t.key(),
                    "Selecionar canal", cfg.channel(t.key()))));
        }
        kids.add(ActionRow.of(
                Button.secondary(ComponentId.of(NS, "logpage", String.valueOf(idx - 1)), "◀")
                        .withDisabled(idx <= 0),
                Button.secondary(ComponentId.of(NS, "logpage", String.valueOf(idx + 1)), "▶")
                        .withDisabled(idx >= total - 1),
                Button.secondary(ComponentId.of(NS, "nav", "hub"), "◀ Voltar")));

        return Panels.container(EmbedColor.resolve(cfg), kids.toArray(new ContainerChildComponent[0]));
    }

    // --- Cargos (role selects, paginated to stay within component limits) ------

    public static Container cargos(GuildConfig cfg, int pageIndex) {
        var options = SetupRoleKeys.OPTIONS;
        int total = Math.max(1, (options.size() + PER_PAGE - 1) / PER_PAGE);
        int idx = Math.max(0, Math.min(pageIndex, total - 1));
        int from = idx * PER_PAGE;
        int to = Math.min(options.size(), from + PER_PAGE);

        List<ContainerChildComponent> kids = new ArrayList<>();
        kids.add(Panels.text("## 👥 Cargos  (" + (idx + 1) + "/" + total + ")\n"
                + "Mapeie cada função a um cargo do servidor. Salva ao selecionar."));
        for (var e : options.subList(from, to)) {
            kids.add(Panels.text("**" + e.getValue() + "**"));
            kids.add(ActionRow.of(roleSelect("setrole", e.getKey(),
                    "Selecionar cargo", cfg.role(e.getKey()))));
        }
        kids.add(ActionRow.of(
                Button.secondary(ComponentId.of(NS, "rolepage", String.valueOf(idx - 1)), "◀")
                        .withDisabled(idx <= 0),
                Button.secondary(ComponentId.of(NS, "rolepage", String.valueOf(idx + 1)), "▶")
                        .withDisabled(idx >= total - 1),
                Button.secondary(ComponentId.of(NS, "nav", "hub"), "◀ Voltar")));
        return Panels.container(EmbedColor.resolve(cfg), kids.toArray(new ContainerChildComponent[0]));
    }

    // --- Tickets ---------------------------------------------------------------

    public static Container ticketsList(int accent, List<TicketCategory> categories) {
        List<ContainerChildComponent> kids = new ArrayList<>();
        kids.add(Panels.text("## 🎫 Tickets\nSelecione uma categoria para editar ou remover, "
                + "ou crie uma nova."));
        if (categories.isEmpty()) {
            kids.add(Panels.text("*Nenhuma categoria de ticket ainda.*"));
        } else {
            StringSelectMenu.Builder menu = StringSelectMenu.create(ComponentId.of(NS, "ticketcat"))
                    .setPlaceholder("Categorias existentes");
            for (TicketCategory cat : categories) {
                String label = (cat.emoji() != null && !cat.emoji().isBlank() ? cat.emoji() + " " : "")
                        + cat.name();
                menu.addOption(trim(label, 100), cat.id(),
                        cat.description() == null ? null : trim(cat.description(), 100));
            }
            kids.add(ActionRow.of(menu.build()));
        }
        kids.add(ActionRow.of(
                Button.success(ComponentId.of(NS, "ticketnew"), "➕ Nova categoria"),
                Button.secondary(ComponentId.of(NS, "nav", "hub"), "◀ Voltar")));
        return Panels.container(accent, kids.toArray(new ContainerChildComponent[0]));
    }

    public static Container ticketDetail(int accent, TicketCategory cat) {
        String roles = cat.staffRoleIds().isEmpty() ? "*nenhum*"
                : String.join(" ", cat.staffRoleIds().stream().map(r -> "<@&" + r + ">").toList());
        String body = "## 🎫 " + (cat.emoji() != null && !cat.emoji().isBlank() ? cat.emoji() + " " : "")
                + cat.name() + "\n"
                + (cat.description() == null || cat.description().isBlank()
                        ? "" : cat.description() + "\n")
                + "\n📂 **Categoria Discord:** <#" + cat.discordCategoryId() + ">\n"
                + "🛡️ **Cargos que atendem:** " + roles;
        return Panels.container(accent,
                Panels.text(body),
                ActionRow.of(
                        Button.primary(ComponentId.of(NS, "ticketedit", cat.id()), "✏️ Editar"),
                        Button.danger(ComponentId.of(NS, "ticketdel", cat.id()), "🗑️ Remover"),
                        Button.secondary(ComponentId.of(NS, "nav", "tickets"), "◀ Voltar")));
    }

    /** The create/edit form. {@code existing} is null for a new category. */
    public static Modal ticketModal(String id, TicketCategory existing) {
        TextInput nome = TextInput.create("nome", TextInputStyle.SHORT)
                .setPlaceholder("Ex: Suporte").setRequired(true).setMaxLength(80)
                .setValue(existing == null ? null : existing.name()).build();
        TextInput emoji = TextInput.create("emoji", TextInputStyle.SHORT)
                .setPlaceholder("Ex: 🛠️ (opcional)").setRequired(false).setMaxLength(8)
                .setValue(existing == null ? null : existing.emoji()).build();
        TextInput descricao = TextInput.create("descricao", TextInputStyle.PARAGRAPH)
                .setPlaceholder("Descrição curta no painel (opcional)").setRequired(false).setMaxLength(200)
                .setValue(existing == null ? null : existing.description()).build();

        EntitySelectMenu.Builder categoria = EntitySelectMenu
                .create("categoria", SelectTarget.CHANNEL)
                .setChannelTypes(ChannelType.CATEGORY)
                .setPlaceholder("Categoria Discord destino")
                .setRequiredRange(1, 1);
        if (existing != null && existing.discordCategoryId() != null) {
            categoria.setDefaultValues(DefaultValue.channel(existing.discordCategoryId()));
        }
        EntitySelectMenu.Builder cargos = EntitySelectMenu
                .create("cargos", SelectTarget.ROLE)
                .setPlaceholder("Cargos que podem atender")
                .setRequiredRange(1, 20);
        if (existing != null && !existing.staffRoleIds().isEmpty()) {
            cargos.setDefaultValues(existing.staffRoleIds().stream().map(DefaultValue::role).toList());
        }

        return Modal.create(ComponentId.of(NS, "ticketform", id),
                        existing == null ? "Nova categoria de ticket" : "Editar categoria")
                .addComponents(
                        Label.of("Nome", nome),
                        Label.of("Emoji", emoji),
                        Label.of("Descrição", descricao),
                        Label.of("Categoria Discord", categoria.build()),
                        Label.of("Cargos que atendem", cargos.build()))
                .build();
    }

    /** Modal to set the guild's embed accent color (hex). */
    public static Modal colorModal(String currentHex) {
        TextInput cor = TextInput.create("cor", TextInputStyle.SHORT)
                .setPlaceholder("Ex: #5865F2").setRequired(true).setMinLength(3).setMaxLength(9)
                .setValue(currentHex).build();
        return Modal.create(ComponentId.of(NS, "botcolorform"), "Cor das embeds")
                .addComponents(Label.of("Cor (hex)", cor))
                .build();
    }

    private static String trim(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }

    // --- Bot -------------------------------------------------------------------

    public static Container bot(int accent, String botName, String botId) {
        return Panels.container(accent,
                Panels.text("## 🤖 Perfil do Bot\n"
                        + "**Nome atual:** " + botName + "\n"
                        + "**ID:** " + botId + "\n"
                        + "🎨 **Cor das embeds:** `" + EmbedColor.hex(accent) + "`\n\n"
                        + "O **nome** e o **avatar** são globais (afetam o bot em todos os servidores) e o "
                        + "Discord limita a **2 alterações por hora**. Use `/bot-name` e `/bot-icon` para "
                        + "alterá-los, e `/bot-nick` para o apelido apenas neste servidor."),
                ActionRow.of(
                        Button.primary(ComponentId.of(NS, "botcolor"), "🎨 Definir cor"),
                        Button.secondary(ComponentId.of(NS, "nav", "hub"), "◀ Voltar")));
    }

    // --- helpers ---------------------------------------------------------------

    private static EntitySelectMenu channelSelect(String action, String key, String placeholder, String currentId) {
        EntitySelectMenu.Builder b = EntitySelectMenu
                .create(ComponentId.of(NS, action, key), SelectTarget.CHANNEL)
                .setChannelTypes(ChannelType.TEXT)
                .setPlaceholder(placeholder)
                .setRequiredRange(1, 1);
        if (currentId != null) {
            b.setDefaultValues(DefaultValue.channel(currentId));
        }
        return b.build();
    }

    private static EntitySelectMenu roleSelect(String action, String key, String placeholder, String currentId) {
        EntitySelectMenu.Builder b = EntitySelectMenu
                .create(ComponentId.of(NS, action, key), SelectTarget.ROLE)
                .setPlaceholder(placeholder)
                .setRequiredRange(1, 1);
        if (currentId != null) {
            b.setDefaultValues(DefaultValue.role(currentId));
        }
        return b.build();
    }

    private static ActionRow backRow(String navTarget) {
        return ActionRow.of(Button.secondary(ComponentId.of(NS, "nav", navTarget), "◀ Voltar"));
    }
}
