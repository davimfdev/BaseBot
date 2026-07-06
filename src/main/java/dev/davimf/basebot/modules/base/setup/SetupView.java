// [OUTLINE START]
// Package: dev.davimf.basebot.modules.base.setup
// 
// Class: SetupView
// 
// Constructors:
//   - `Constructor` : `private SetupView()`
// 
// Methods:
//   - `Method` : `public static Container hub(GuildConfig cfg, int ticketCategoryCount, int actionTypeCount)`
//   - `Method` : `public static Container logsPage(GuildConfig cfg, int pageIndex)`
//   - `Method` : `public static Container cargos(GuildConfig cfg, int pageIndex)`
//   - `Method` : `public static Container ticketsList(int accent, List<TicketCategory> categories)`
//   - `Method` : `public static Container ticketDetail(int accent, TicketCategory cat)`
//   - `Method` : `public static Modal ticketModal(String id, TicketCategory existing)`
//   - `Method` : `public static Container actionTypesList(GuildConfig cfg, List<ActionType> types)`
//   - `Method` : `public static Container actionTypeDetail(int accent, ActionType t)`
//   - `Method` : `public static Modal actionTypeModal(String id, ActionType existing)`
//   - `Method` : `private static String actionSummary(ActionType t)`
//   - `Method` : `public static Modal colorModal(String currentHex)`
//   - `Method` : `private static String trim(String s, int max)`
//   - `Method` : `public static Container bot(int accent, String botName, String botId)`
//   - `Method` : `public static Container permissionsHub(GuildConfig cfg, Guild guild)`
//   - `Method` : `public static Container permissionsDetail(GuildConfig cfg, String principal, String label)`
//   - `Method` : `private static EntitySelectMenu channelSelect(String action, String key, String placeholder, String currentId)`
//   - `Method` : `private static EntitySelectMenu roleSelect(String action, String key, String placeholder, String currentId)`
// 
// Fields:
//   - `Field` : `public static final String NS`
//   - `Field` : `private static final int PER_PAGE`
//   - `Field` : `private static final String CH_ESCALACOES`
//   - `Field` : `private static final String CH_ALINHAMENTOS`
// [OUTLINE END]



package dev.davimf.basebot.modules.base.setup;

import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.database.model.TicketCategory;
import dev.davimf.basebot.database.sqlite.ActionTypeRepository.ActionType;
import dev.davimf.basebot.modules.base.moderation.ModerationConfig;
import dev.davimf.basebot.modules.base.moderation.ModerationConfig.EscalationRule;
import dev.davimf.basebot.modules.base.moderation.ModerationService;
import dev.davimf.basebot.modules.base.setup.SetupLogTypes.LogType;
import dev.davimf.basebot.modules.base.welcome.WelcomeConfig;
import dev.davimf.basebot.modules.facs.perms.ManagerPermissions;
import dev.davimf.basebot.util.EmbedColor;
import dev.davimf.basebot.util.Emojis;
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
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.emoji.Emoji;
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

    public static Container hub(GuildConfig cfg, int ticketCategoryCount, int actionTypeCount) {
        long logsSet = SetupLogTypes.ALL.stream().filter(t -> cfg.channel(t.key()) != null).count();
        String overview = "" + Emojis.of(Emojis.LIST, "📋") + " **Logs** · `" + logsSet + "/" + SetupLogTypes.ALL.size() + "`\n"
                + "" + Emojis.of(Emojis.TICKET, "🎫") + " **Categorias de ticket** · `" + ticketCategoryCount + "`\n"
                + "" + Emojis.of(Emojis.SWORDS, "⚔️") + " **Ações salvas** · `" + actionTypeCount + "`\n"
                + "" + Emojis.of(Emojis.MEMBERS, "👥") + " **Cargos configurados** · `" + cfg.roles().size() + "`";

        StringSelectMenu menu = StringSelectMenu.create(ComponentId.of(NS, "section"))
                .setPlaceholder("Escolha a seção para configurar")
                .addOption("Logs", "logs", "Um canal próprio para cada tipo de log")
                .addOption("Cargos", "roles", "Mapear cargos lógicos a cargos do servidor")
                .addOption("Farm", "farm", "Itens entregáveis no /farm e usados nas receitas")
                .addOption("Tickets", "tickets", "Categoria, staff, descrição e emoji")
                .addOption("Ações", "acoes", "Tipos de ação: nome, contingente e dinheiro sujo")
                .addOption("Moderação", "moderacao", "Escalonamento de avisos, DM e exigir motivo")
                .addOption("Segurança", "seguranca", "AutoMod, anti-raid, verificação e anti-nuke")
                .addOption("Boas-vindas", "boasvindas", "Boas-vindas, despedida, autorole e imagem")
                .addOption("Auto-cargos", "autocargos", "Painéis de auto-atribuição de cargos")
                .addOption("Bot", "bot", "Perfil e cor das embeds do bot")
                .addOption("Permissões", "permissoes", "O que cada categoria de gerência pode fazer")
                .build();

        return Panels.container(EmbedColor.resolve(cfg),
                Panels.text("## " + Emojis.of(Emojis.GEAR, "⚙️") + " Configuração do Servidor"),
                Panels.divider(),
                Panels.text(overview),
                Panels.divider(),
                Panels.text("> Escolha uma seção abaixo para configurar — tudo é salvo automaticamente."),
                ActionRow.of(menu));
    }

    // --- Logs (many selects per screen, paginated by module) ------------------

    public static Container logsPage(GuildConfig cfg, int pageIndex) {
        List<List<LogType>> pages = SetupLogTypes.pages(PER_PAGE);
        int total = pages.size();
        int idx = Math.max(0, Math.min(pageIndex, total - 1));
        List<LogType> page = pages.get(idx);
        String module = page.get(0).module();

        List<ContainerChildComponent> kids = new ArrayList<>();
        kids.add(Panels.text("## " + Emojis.of(Emojis.LIST, "📋") + " Logs · " + module + " `" + (idx + 1) + "/" + total + "`"));
        kids.add(Panels.divider());
        kids.add(Panels.text("> Selecione o canal de cada tipo de log. Salva automaticamente ao selecionar."));
        for (LogType t : page) {
            kids.add(Panels.text("**" + t.label() + "**"));
            kids.add(ActionRow.of(channelSelect("setlogchannel", t.key(),
                    "Selecionar canal", cfg.channel(t.key()))));
        }
        kids.add(Panels.divider());
        kids.add(ActionRow.of(
                Button.primary(ComponentId.of(NS, "logquicksetup"), "Setup rápido").withEmoji(Emojis.button(Emojis.BOLT))));
        kids.add(ActionRow.of(
                Button.secondary(ComponentId.of(NS, "logpage", String.valueOf(idx - 1)), "◀")
                        .withDisabled(idx <= 0),
                Button.secondary(ComponentId.of(NS, "logpage", String.valueOf(idx + 1)), "▶")
                        .withDisabled(idx >= total - 1),
                Button.secondary(ComponentId.of(NS, "nav", "hub"), "◀ Voltar")));
        kids.add(moduleNav("logs"));

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
        kids.add(Panels.text("## " + Emojis.of(Emojis.MEMBERS, "👥") + " Cargos `" + (idx + 1) + "/" + total + "`"));
        kids.add(Panels.divider());
        kids.add(Panels.text("> Mapeie cada função a um cargo do servidor. Salva automaticamente ao selecionar."));
        for (var e : options.subList(from, to)) {
            kids.add(Panels.text("**" + e.getValue() + "**"));
            kids.add(ActionRow.of(roleSelect("setrole", e.getKey(),
                    "Selecionar cargo", cfg.role(e.getKey()))));
        }
        kids.add(Panels.divider());
        kids.add(ActionRow.of(
                Button.primary(ComponentId.of(NS, "rolequicksetup"), "Setup rápido").withEmoji(Emojis.button(Emojis.BOLT))));
        kids.add(ActionRow.of(
                Button.secondary(ComponentId.of(NS, "rolepage", String.valueOf(idx - 1)), "◀")
                        .withDisabled(idx <= 0),
                Button.secondary(ComponentId.of(NS, "rolepage", String.valueOf(idx + 1)), "▶")
                        .withDisabled(idx >= total - 1),
                Button.secondary(ComponentId.of(NS, "nav", "hub"), "◀ Voltar")));
        kids.add(moduleNav("roles"));
        return Panels.container(EmbedColor.resolve(cfg), kids.toArray(new ContainerChildComponent[0]));
    }

    // --- Tickets ---------------------------------------------------------------

    public static Container ticketsList(int accent, List<TicketCategory> categories) {
        List<ContainerChildComponent> kids = new ArrayList<>();
        kids.add(Panels.text("## " + Emojis.of(Emojis.TICKET, "🎫") + " Tickets"));
        kids.add(Panels.divider());
        kids.add(Panels.text("> Selecione uma categoria para editar ou remover, ou crie uma nova."));
        if (categories.isEmpty()) {
            kids.add(Panels.text("-# *Nenhuma categoria de ticket ainda.*"));
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
                Button.success(ComponentId.of(NS, "ticketnew"), "Nova categoria").withEmoji(Emojis.button(Emojis.PLUS)),
                Button.secondary(ComponentId.of(NS, "nav", "hub"), "◀ Voltar")));
        kids.add(moduleNav("tickets"));
        return Panels.container(accent, kids.toArray(new ContainerChildComponent[0]));
    }

    public static Container ticketDetail(int accent, TicketCategory cat) {
        String roles = cat.staffRoleIds().isEmpty() ? "*nenhum*"
                : String.join(" ", cat.staffRoleIds().stream().map(r -> "<@&" + r + ">").toList());
        String title = "## " + Emojis.of(Emojis.TICKET, "🎫") + " " + (cat.emoji() != null && !cat.emoji().isBlank() ? cat.emoji() + " " : "")
                + cat.name();
        String details = "" + Emojis.of(Emojis.FOLDER_OPEN, "📂") + " **Categoria Discord** · <#" + cat.discordCategoryId() + ">\n"
                + "" + Emojis.of(Emojis.SHIELD, "🛡️") + " **Cargos que atendem** · " + roles;
        List<ContainerChildComponent> kids = new ArrayList<>();
        kids.add(Panels.text(title));
        kids.add(Panels.divider());
        if (cat.description() != null && !cat.description().isBlank()) {
            kids.add(Panels.text("> " + cat.description()));
        }
        kids.add(Panels.text(details));
        kids.add(ActionRow.of(
                Button.primary(ComponentId.of(NS, "ticketedit", cat.id()), "Editar").withEmoji(Emojis.button(Emojis.EDIT)),
                Button.danger(ComponentId.of(NS, "ticketdel", cat.id()), "Remover").withEmoji(Emojis.button(Emojis.TRASH)),
                Button.secondary(ComponentId.of(NS, "nav", "tickets"), "◀ Voltar")));
        kids.add(moduleNav("tickets"));
        return Panels.container(accent, kids.toArray(new ContainerChildComponent[0]));
    }

    /** The create/edit form. {@code existing} is null for a new category. */
    public static Modal ticketModal(String id, TicketCategory existing) {
        TextInput nome = TextInput.create("nome", TextInputStyle.SHORT)
                .setPlaceholder("Ex: Suporte").setRequired(true).setMaxLength(80)
                .setValue(existing == null ? null : existing.name()).build();
        TextInput emoji = TextInput.create("emoji", TextInputStyle.SHORT)
                .setPlaceholder("Ex: " + Emojis.of(Emojis.WRENCH, "🛠️") + " (opcional)").setRequired(false).setMaxLength(8)
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

    // --- Ações (saved action types) --------------------------------------------

    /** Action keys stored in guild_config.channels (mirror of ActionService constants). */
    private static final String CH_ESCALACOES = "acoes-escalacoes";
    private static final String CH_ALINHAMENTOS = "acoes-alinhamentos";

    public static Container actionTypesList(GuildConfig cfg, List<ActionType> types) {
        int accent = EmbedColor.resolve(cfg);
        List<ContainerChildComponent> kids = new ArrayList<>();
        kids.add(Panels.text("## " + Emojis.of(Emojis.SWORDS, "⚔️") + " Ações"));
        kids.add(Panels.divider());
        kids.add(Panels.text("> Defina os canais e as ações salvas usadas em `/painel-acoes`."));
        kids.add(Panels.text("**" + Emojis.of(Emojis.MEGAPHONE, "📣") + " Canal de escalações**\n-# Onde os painéis de ação são postados."));
        kids.add(ActionRow.of(channelSelect("setlogchannel", CH_ESCALACOES,
                "Selecionar canal", cfg.channel(CH_ESCALACOES))));
        kids.add(Panels.text("**" + Emojis.of(Emojis.BELL, "🔔") + " Canal de alinhamentos**\n-# Onde os pings de alinhamento são enviados."));
        kids.add(ActionRow.of(channelSelect("setlogchannel", CH_ALINHAMENTOS,
                "Selecionar canal", cfg.channel(CH_ALINHAMENTOS))));
        kids.add(Panels.divider());
        kids.add(Panels.text("### Ações salvas\n-# Selecione uma ação para editar ou remover, ou crie uma nova."));
        if (types.isEmpty()) {
            kids.add(Panels.text("-# *Nenhuma ação salva ainda.*"));
        } else {
            StringSelectMenu.Builder menu = StringSelectMenu.create(ComponentId.of(NS, "actiontype"))
                    .setPlaceholder("Ações existentes");
            for (ActionType t : types) {
                menu.addOption(trim(t.name(), 100), t.id(), trim(actionSummary(t), 100));
            }
            kids.add(ActionRow.of(menu.build()));
        }
        kids.add(ActionRow.of(
                Button.success(ComponentId.of(NS, "actionnew"), "Nova ação").withEmoji(Emojis.button(Emojis.PLUS)),
                Button.secondary(ComponentId.of(NS, "nav", "hub"), "◀ Voltar")));
        kids.add(moduleNav("acoes"));
        return Panels.container(accent, kids.toArray(new ContainerChildComponent[0]));
    }

    public static Container actionTypeDetail(int accent, ActionType t) {
        String max = t.maxContingent() == 0 ? "`∞` ilimitado" : "`" + t.maxContingent() + "`";
        String body = "" + Emojis.of(Emojis.MEMBERS, "👥") + " **Contingente máximo** · " + max + "\n"
                + "" + Emojis.of(Emojis.ARROW_DOWN, "🔻") + " **Contingente mínimo** · `" + t.minContingent() + "`\n"
                + "" + Emojis.of(Emojis.CASH, "💵") + " **Dinheiro sujo** · `" + t.dirtyMoney() + "`";
        return Panels.container(accent,
                Panels.text("## " + Emojis.of(Emojis.SWORDS, "⚔️") + " " + t.name()),
                Panels.divider(),
                Panels.text(body),
                ActionRow.of(
                        Button.primary(ComponentId.of(NS, "actionedit", t.id()), "Editar").withEmoji(Emojis.button(Emojis.EDIT)),
                        Button.danger(ComponentId.of(NS, "actiondel", t.id()), "Remover").withEmoji(Emojis.button(Emojis.TRASH)),
                        Button.secondary(ComponentId.of(NS, "nav", "acoes"), "◀ Voltar")),
                moduleNav("acoes"));
    }

    /** The create/edit form. {@code existing} is null for a new action type. */
    public static Modal actionTypeModal(String id, ActionType existing) {
        TextInput nome = TextInput.create("nome", TextInputStyle.SHORT)
                .setPlaceholder("Ex: Assalto ao banco").setRequired(true).setMaxLength(80)
                .setValue(existing == null ? null : existing.name()).build();
        TextInput maximo = TextInput.create("maximo", TextInputStyle.SHORT)
                .setPlaceholder("Contingente máximo (0 = ilimitado)").setRequired(true).setMaxLength(4)
                .setValue(existing == null ? null : String.valueOf(existing.maxContingent())).build();
        TextInput minimo = TextInput.create("minimo", TextInputStyle.SHORT)
                .setPlaceholder("Contingente mínimo").setRequired(true).setMaxLength(4)
                .setValue(existing == null ? null : String.valueOf(existing.minContingent())).build();
        TextInput sujo = TextInput.create("sujo", TextInputStyle.SHORT)
                .setPlaceholder("Dinheiro sujo ganho").setRequired(true).setMaxLength(9)
                .setValue(existing == null ? null : String.valueOf(existing.dirtyMoney())).build();
        return Modal.create(ComponentId.of(NS, "actionform", id),
                        existing == null ? "Nova ação salva" : "Editar ação")
                .addComponents(
                        Label.of("Nome", nome),
                        Label.of("Contingente máximo", maximo),
                        Label.of("Contingente mínimo", minimo),
                        Label.of("Dinheiro sujo", sujo))
                .build();
    }

    private static String actionSummary(ActionType t) {
        String max = t.maxContingent() == 0 ? "∞" : String.valueOf(t.maxContingent());
        return "máx " + max + " · mín " + t.minContingent() + " · sujo " + t.dirtyMoney();
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
                Panels.text("## " + Emojis.of(Emojis.GEAR, "🤖") + " Perfil do Bot"),
                Panels.divider(),
                Panels.text("" + Emojis.of(Emojis.NICKNAME, "🏷️") + " **Nome** · `" + botName + "`\n"
                        + "" + Emojis.of(Emojis.ID, "🆔") + " **ID** · `" + botId + "`\n"
                        + "" + Emojis.of(Emojis.PALETTE, "🎨") + " **Cor das embeds** · `" + EmbedColor.hex(accent) + "`"),
                Panels.divider(),
                Panels.text("> O **nome** e o **avatar** são globais (afetam o bot em todos os servidores) e o "
                        + "Discord limita a **2 alterações por hora**.\n"
                        + "-# Use `/bot-name` e `/bot-icon` para alterá-los, e `/bot-nick` para o apelido "
                        + "apenas neste servidor."),
                ActionRow.of(
                        Button.primary(ComponentId.of(NS, "botcolor"), "Definir cor").withEmoji(Emojis.button(Emojis.PALETTE)),
                        Button.secondary(ComponentId.of(NS, "nav", "hub"), "◀ Voltar")),
                moduleNav("bot"));
    }

    // --- Permissões ------------------------------------------------------------

    public static Container permissionsHub(GuildConfig cfg, Guild guild) {
        int accent = EmbedColor.resolve(cfg);
        List<ContainerChildComponent> kids = new ArrayList<>();
        kids.add(Panels.text("## " + Emojis.of(Emojis.PERMS, "🔐") + " Permissões de Gerência"));
        kids.add(Panels.divider());
        kids.add(Panels.text("> Escolha uma categoria de gerência para definir o que ela pode fazer."));

        StringSelectMenu.Builder menu = StringSelectMenu.create(ComponentId.of(NS, "permcat"))
                .setPlaceholder("Escolha a categoria de gerência");
        for (String key : ManagerPermissions.CATEGORY_KEYS) {
            menu.addOption(SetupRoleKeys.labelFor(key), key);
        }
        for (String principal : ManagerPermissions.freeRolePrincipals(cfg)) {
            String id = principal.substring("role:".length());
            Role r = guild == null ? null : guild.getRoleById(id);
            menu.addOption(trim(r == null ? "Cargo " + id : "Cargo: " + r.getName(), 100),
                    ManagerPermissions.customIdToken(principal));
        }
        kids.add(ActionRow.of(menu.build()));
        kids.add(ActionRow.of(
                Button.success(ComponentId.of(NS, "permaddrole"), "Adicionar outro cargo").withEmoji(Emojis.button(Emojis.PLUS)),
                Button.secondary(ComponentId.of(NS, "nav", "hub"), "◀ Voltar")));
        kids.add(moduleNav("permissoes"));
        return Panels.container(accent, kids.toArray(new ContainerChildComponent[0]));
    }

    public static Container permissionsDetail(GuildConfig cfg, String principal, String label) {
        int accent = EmbedColor.resolve(cfg);
        String roleId = principal.startsWith("role:") ? principal.substring("role:".length())
                : cfg.role(principal);

        List<ContainerChildComponent> kids = new ArrayList<>();
        kids.add(Panels.text("## " + Emojis.of(Emojis.PERMS, "🔐") + " " + label));
        kids.add(Panels.divider());
        kids.add(Panels.text(roleId != null
                ? "" + Emojis.of(Emojis.ROLES, "🎭") + " **Cargo** · <@&" + roleId + ">"
                : "-# Cargo não configurado em `/setup → Cargos` — configure-o para que esta categoria valha."));
        kids.add(Panels.divider());
        kids.add(Panels.text("-# Toque para ligar/desligar cada área:"));

        String token = ManagerPermissions.customIdToken(principal);
        List<Button> buttons = new ArrayList<>();
        for (ManagerPermissions.Capability cap : ManagerPermissions.Capability.values()) {
            boolean on = ManagerPermissions.grants(cfg, cap, principal);
            Button b = on
                    ? Button.success(ComponentId.of(NS, "permtoggle", token, cap.key()), cap.shortLabel())
                            .withEmoji(Emojis.button(Emojis.CHECK_YES))
                    : Button.secondary(ComponentId.of(NS, "permtoggle", token, cap.key()), cap.shortLabel())
                            .withEmoji(Emojis.button(Emojis.CHECK_NO));
            buttons.add(b);
        }
        kids.add(ActionRow.of(buttons));
        kids.add(ActionRow.of(Button.secondary(ComponentId.of(NS, "nav", "permissoes"), "◀ Voltar")));
        kids.add(moduleNav("permissoes"));
        return Panels.container(accent, kids.toArray(new ContainerChildComponent[0]));
    }

    // --- Moderação -------------------------------------------------------------

    public static Container moderation(GuildConfig cfg) {
        int accent = EmbedColor.resolve(cfg);
        int ttl = ModerationConfig.warnTtlDays(cfg);
        java.util.List<EscalationRule> rules = ModerationConfig.escalation(cfg);
        boolean dm = ModerationConfig.dmOnAction(cfg);
        boolean rr = ModerationConfig.requireReason(cfg);
        String modlog = cfg.channel(ModerationService.MODLOG_KEY);

        String overview = "" + Emojis.of(Emojis.HOURGLASS, "⏳") + " **Expiração de avisos** · `" + (ttl == 0 ? "nunca" : ttl + "d") + "`\n"
                + "" + Emojis.of(Emojis.GROWTH, "📈") + " **Escalonamento** · " + (rules.isEmpty() ? "*nenhum*"
                        : "`" + ModerationConfig.serialize(rules) + "`") + "\n"
                + "" + Emojis.of(Emojis.DM, "✉️") + " **DM ao infrator** · " + (dm ? "" + Emojis.of(Emojis.ONLINE, "🟢") + " ligado" : "" + Emojis.of(Emojis.DOT, "⚪") + " desligado") + "\n"
                + "" + Emojis.of(Emojis.NOTE, "📝") + " **Exigir motivo** · " + (rr ? "" + Emojis.of(Emojis.ONLINE, "🟢") + " ligado" : "" + Emojis.of(Emojis.DOT, "⚪") + " desligado") + "\n"
                + "" + Emojis.of(Emojis.LIST, "📋") + " **Canal de modlog** · " + (modlog == null ? "*não configurado*" : "<#" + modlog + ">");

        return Panels.container(accent,
                Panels.text("## " + Emojis.of(Emojis.SHIELD, "🛡️") + " Moderação"),
                Panels.divider(),
                Panels.text(overview),
                Panels.text("-# O canal de modlog é definido em `/setup → Logs`."),
                Panels.divider(),
                ActionRow.of(
                        Button.primary(ComponentId.of(NS, "modrules"), "Editar regras").withEmoji(Emojis.button(Emojis.GROWTH)),
                        Button.secondary(ComponentId.of(NS, "modtoggle", "dm"), "DM: " + (dm ? "on" : "off")).withEmoji(Emojis.button(Emojis.DM)),
                        Button.secondary(ComponentId.of(NS, "modtoggle", "reason"),
                                "Motivo: " + (rr ? "on" : "off")).withEmoji(Emojis.button(Emojis.NOTE))),
                ActionRow.of(Button.secondary(ComponentId.of(NS, "nav", "hub"), "◀ Voltar")),
                moduleNav("moderacao"));
    }

    public static Modal moderationRulesModal(GuildConfig cfg) {
        TextInput.Builder escalationB = TextInput.create("escalation", TextInputStyle.SHORT)
                .setPlaceholder("Ex: 3=timeout:1h,5=kick,7=ban").setRequired(false).setMaxLength(200);
        String escVal = ModerationConfig.serialize(ModerationConfig.escalation(cfg));
        if (escVal != null && !escVal.isBlank()) {
            escalationB.setValue(escVal); // setValue rejects blank — only set when there are rules
        }
        TextInput escalation = escalationB.build();
        int ttl = ModerationConfig.warnTtlDays(cfg);
        TextInput ttlInput = TextInput.create("ttl", TextInputStyle.SHORT)
                .setPlaceholder("Dias até um aviso expirar (0 = nunca)").setRequired(false).setMaxLength(4)
                .setValue(String.valueOf(ttl)).build();
        return Modal.create(ComponentId.of(NS, "modrulesform"), "Regras de moderação")
                .addComponents(Label.of("Escalonamento", escalation), Label.of("Expiração de avisos (dias)", ttlInput))
                .build();
    }

    // --- Farm (configurable stock item list) -----------------------------------

    public static Container farmScreen(GuildConfig cfg) {
        int accent = EmbedColor.resolve(cfg);
        List<String> items = dev.davimf.basebot.modules.facs.economy.FarmItems.list(cfg);
        List<ContainerChildComponent> kids = new ArrayList<>();
        kids.add(Panels.text("## " + Emojis.of(Emojis.FARM, "🌾") + " Itens de Farm"));
        kids.add(Panels.divider());
        kids.add(Panels.text(items.isEmpty()
                ? "> Nenhum item configurado. Adicione os materiais que podem ser entregues no `/farm` e produzidos no `/produzir`."
                : "> " + items.stream().map(i -> "`" + i + "`").collect(java.util.stream.Collectors.joining("  ·  "))));
        kids.add(Panels.divider());
        if (!items.isEmpty()) {
            StringSelectMenu.Builder rem = StringSelectMenu.create(ComponentId.of(NS, "farmremove"))
                    .setPlaceholder("Remover um item…");
            for (String i : items) {
                rem.addOption(i, i);
            }
            kids.add(ActionRow.of(rem.build()));
        }
        kids.add(ActionRow.of(
                Button.success(ComponentId.of(NS, "farmadd"), "Adicionar item").withEmoji(Emojis.button(Emojis.PLUS)),
                Button.secondary(ComponentId.of(NS, "nav", "hub"), "◀ Voltar")));
        kids.add(moduleNav("farm"));
        return Panels.container(accent, kids.toArray(new ContainerChildComponent[0]));
    }

    public static Modal farmItemModal() {
        TextInput nome = TextInput.create("nome", TextInputStyle.SHORT)
                .setPlaceholder("Nome do material (ex.: Madeira)").setRequired(true).setMaxLength(50).build();
        return Modal.create(ComponentId.of(NS, "farmaddform"), "Adicionar item de farm")
                .addComponents(Label.of("Item", nome))
                .build();
    }

    // --- Boas-vindas -----------------------------------------------------------

    public static Container welcomeScreen(GuildConfig cfg) {
        int accent = EmbedColor.resolve(cfg);
        boolean on = WelcomeConfig.enabled(cfg);
        boolean dm = WelcomeConfig.dm(cfg);
        boolean fw = WelcomeConfig.farewellEnabled(cfg);
        String autorole = cfg.role(WelcomeConfig.KEY_AUTOROLE);
        String autoroleLabel = autorole != null ? "<@&" + autorole + ">"
                : (cfg.role(WelcomeConfig.FALLBACK_AUTOROLE_KEY) != null ? "Sem Set (facs)" : "nenhum");
        String overview = Emojis.of(Emojis.MEMBER, "👋") + " **Boas-vindas** · " + (on ? "ligado" : "desligado") + "\n"
                + Emojis.of(Emojis.MEMBER, "✉️") + " **Também no DM** · " + (dm ? "sim" : "não") + "\n"
                + Emojis.of(Emojis.MEMBERS, "🏷️") + " **Autorole** · " + autoroleLabel + "\n"
                + Emojis.of(Emojis.MEMBER, "👋") + " **Despedida** · " + (fw ? "ligada" : "desligada");
        return Panels.container(accent,
                Panels.text("## " + Emojis.of(Emojis.MEMBER, "👋") + " Boas-vindas"),
                Panels.divider(),
                Panels.text(overview),
                Panels.text("-# Placeholders: `{user}` `{mention}` `{server}` `{count}`. Requer o intent **GUILD_MEMBERS**."),
                Panels.divider(),
                ActionRow.of(channelSelect("welcomechan", WelcomeConfig.KEY_CHANNEL, "Canal de boas-vindas…",
                        cfg.channel(WelcomeConfig.KEY_CHANNEL))),
                ActionRow.of(roleSelect("welcomerole", WelcomeConfig.KEY_AUTOROLE, "Cargo automático (autorole)…", autorole)),
                ActionRow.of(channelSelect("welcomechan", WelcomeConfig.KEY_FAREWELL_CHANNEL, "Canal de despedida…",
                        cfg.channel(WelcomeConfig.KEY_FAREWELL_CHANNEL))),
                ActionRow.of(
                        Button.secondary(ComponentId.of(NS, "welctoggle", "enabled"), "Boas-vindas: " + (on ? "on" : "off")).withEmoji(Emojis.button(Emojis.MEMBER)),
                        Button.secondary(ComponentId.of(NS, "welctoggle", "dm"), "DM: " + (dm ? "on" : "off")).withEmoji(Emojis.button(Emojis.MEMBER)),
                        Button.secondary(ComponentId.of(NS, "welctoggle", "farewell"), "Despedida: " + (fw ? "on" : "off")).withEmoji(Emojis.button(Emojis.MEMBER))),
                ActionRow.of(
                        Button.primary(ComponentId.of(NS, "welcedit"), "Editar mensagens").withEmoji(Emojis.button(Emojis.EDIT)),
                        Button.secondary(ComponentId.of(NS, "nav", "hub"), "◀ Voltar")),
                moduleNav("boasvindas"));
    }

    public static Modal welcomeModal(GuildConfig cfg) {
        TextInput.Builder msg = TextInput.create("message", TextInputStyle.PARAGRAPH)
                .setPlaceholder("Mensagem de boas-vindas (use os placeholders)").setRequired(false).setMaxLength(1000);
        msg.setValue(WelcomeConfig.message(cfg));
        TextInput.Builder fw = TextInput.create("farewell", TextInputStyle.PARAGRAPH)
                .setPlaceholder("Mensagem de despedida").setRequired(false).setMaxLength(1000);
        fw.setValue(WelcomeConfig.farewellMessage(cfg));
        TextInput.Builder img = TextInput.create("image", TextInputStyle.SHORT)
                .setPlaceholder("URL da imagem/banner (PNG/JPG/GIF/WEBP) — vazio remove").setRequired(false).setMaxLength(500);
        return Modal.create(ComponentId.of(NS, "welcomeform"), "Boas-vindas — mensagens")
                .addComponents(Label.of("Boas-vindas", msg.build()), Label.of("Despedida", fw.build()),
                        Label.of("Imagem (URL)", img.build()))
                .build();
    }

    // --- Auto-cargos -----------------------------------------------------------

    public static Modal selfRoleDetailsModal(String panelId, String title, String description) {
        TextInput.Builder t = TextInput.create("title", TextInputStyle.SHORT)
                .setPlaceholder("Título do painel").setRequired(true).setMaxLength(80);
        if (title != null && !title.isBlank()) { t.setValue(title); }
        TextInput.Builder d = TextInput.create("description", TextInputStyle.PARAGRAPH)
                .setPlaceholder("Descrição (opcional)").setRequired(false).setMaxLength(400);
        if (description != null && !description.isBlank()) { d.setValue(description); }
        return Modal.create(ComponentId.of(NS, "srdetailsform", panelId), "Painel de cargos")
                .addComponents(Label.of("Título", t.build()), Label.of("Descrição", d.build()))
                .build();
    }

    // --- Segurança (AutoMod) ---------------------------------------------------

    public static Container securityScreen(GuildConfig cfg) {
        int accent = EmbedColor.resolve(cfg);
        boolean on = dev.davimf.basebot.modules.base.security.SecurityConfig.automod(cfg);
        boolean warn = dev.davimf.basebot.modules.base.security.SecurityConfig.automodWarn(cfg);
        boolean inv = dev.davimf.basebot.modules.base.security.SecurityConfig.blockInvites(cfg);
        boolean raid = dev.davimf.basebot.modules.base.security.SecurityConfig.antiraid(cfg);
        boolean verify = dev.davimf.basebot.modules.base.security.SecurityConfig.verify(cfg);
        boolean nuke = dev.davimf.basebot.modules.base.security.SecurityConfig.antinuke(cfg);
        String overview = Emojis.of(Emojis.SHIELD, "🛡️") + " **AutoMod** · " + (on ? "ligado" : "desligado") + "\n"
                + Emojis.of(Emojis.WARN, "⚠️") + " **Warn na violação** · " + (warn ? "sim" : "não")
                + " · `" + dev.davimf.basebot.modules.base.security.SecurityConfig.warnPer(cfg) + "/violação`\n"
                + Emojis.of(Emojis.LINK, "🔗") + " **Bloquear convites** · " + (inv ? "sim" : "não") + "\n"
                + Emojis.of(Emojis.MEMBERS, "👥") + " **Limite de menções** · `"
                + dev.davimf.basebot.modules.base.security.SecurityConfig.mentionLimit(cfg) + "`\n"
                + Emojis.of(Emojis.SHIELD, "🛡️") + " **Anti-raid** · " + (raid ? "ligado" : "desligado")
                + " · `" + dev.davimf.basebot.modules.base.security.SecurityConfig.raidJoins(cfg) + "/"
                + dev.davimf.basebot.modules.base.security.SecurityConfig.raidWindowSeconds(cfg) + "s` · lock `"
                + dev.davimf.basebot.modules.base.security.SecurityConfig.raidLockLevel(cfg) + "`\n"
                + Emojis.of(Emojis.CHECK_YES, "✅") + " **Verificação** · " + (verify ? "ligada" : "desligada") + "\n"
                + Emojis.of(Emojis.SHIELD, "🛡️") + " **Anti-nuke** · " + (nuke ? "ligado" : "desligado")
                + " · `" + dev.davimf.basebot.modules.base.security.SecurityConfig.antinukeMax(cfg) + "/"
                + dev.davimf.basebot.modules.base.security.SecurityConfig.antinukeWindowSeconds(cfg) + "s`";
        return Panels.container(accent,
                Panels.text("## " + Emojis.of(Emojis.SHIELD, "🛡️") + " Segurança"),
                Panels.divider(),
                Panels.text(overview),
                Panels.text("-# " + Emojis.of(Emojis.WARN, "⚠️") + " O anti-nuke só neutraliza atores **abaixo** do meu cargo. Mantenha meu cargo no topo."),
                Panels.divider(),
                ActionRow.of(
                        Button.secondary(ComponentId.of(NS, "sectoggle", "automod"), "AutoMod: " + (on ? "on" : "off"))
                                .withEmoji(Emojis.button(Emojis.SHIELD)),
                        Button.secondary(ComponentId.of(NS, "sectoggle", "warn"), "Warn: " + (warn ? "on" : "off"))
                                .withEmoji(Emojis.button(Emojis.WARN)),
                        Button.secondary(ComponentId.of(NS, "sectoggle", "invites"), "Convites: " + (inv ? "block" : "off"))
                                .withEmoji(Emojis.button(Emojis.LINK))),
                ActionRow.of(
                        Button.secondary(ComponentId.of(NS, "sectoggle", "antiraid"), "Anti-raid: " + (raid ? "on" : "off"))
                                .withEmoji(Emojis.button(Emojis.SHIELD)),
                        Button.primary(ComponentId.of(NS, "raidedit"), "Editar anti-raid").withEmoji(Emojis.button(Emojis.EDIT))),
                ActionRow.of(
                        Button.secondary(ComponentId.of(NS, "sectoggle", "verify"), "Verificação: " + (verify ? "on" : "off"))
                                .withEmoji(Emojis.button(Emojis.CHECK_YES)),
                        Button.primary(ComponentId.of(NS, "verifypanel"), "Publicar painel").withEmoji(Emojis.button(Emojis.SEND))),
                ActionRow.of(
                        Button.secondary(ComponentId.of(NS, "sectoggle", "antinuke"), "Anti-nuke: " + (nuke ? "on" : "off"))
                                .withEmoji(Emojis.button(Emojis.SHIELD)),
                        Button.primary(ComponentId.of(NS, "nukeedit"), "Editar anti-nuke").withEmoji(Emojis.button(Emojis.EDIT))),
                ActionRow.of(
                        Button.primary(ComponentId.of(NS, "secedit"), "Editar AutoMod").withEmoji(Emojis.button(Emojis.EDIT)),
                        Button.secondary(ComponentId.of(NS, "nav", "hub"), "◀ Voltar")),
                moduleNav("seguranca"));
    }

    public static Modal antiraidModal(GuildConfig cfg) {
        TextInput joins = TextInput.create("joins", TextInputStyle.SHORT)
                .setPlaceholder("Entradas para disparar (ex.: 8)").setRequired(false).setMaxLength(3)
                .setValue(String.valueOf(dev.davimf.basebot.modules.base.security.SecurityConfig.raidJoins(cfg))).build();
        TextInput window = TextInput.create("window", TextInputStyle.SHORT)
                .setPlaceholder("Janela em segundos (ex.: 10)").setRequired(false).setMaxLength(4)
                .setValue(String.valueOf(dev.davimf.basebot.modules.base.security.SecurityConfig.raidWindowSeconds(cfg))).build();
        TextInput minage = TextInput.create("minage", TextInputStyle.SHORT)
                .setPlaceholder("Idade mínima da conta em dias (ex.: 7)").setRequired(false).setMaxLength(4)
                .setValue(String.valueOf(dev.davimf.basebot.modules.base.security.SecurityConfig.raidMinAgeDays(cfg))).build();
        TextInput level = TextInput.create("locklevel", TextInputStyle.SHORT)
                .setPlaceholder("Nível no lockdown: LOW, MEDIUM, HIGH, VERY_HIGH").setRequired(false).setMaxLength(10)
                .setValue(dev.davimf.basebot.modules.base.security.SecurityConfig.raidLockLevel(cfg)).build();
        return Modal.create(ComponentId.of(NS, "antiraidform"), "Anti-raid — limiares")
                .addComponents(Label.of("Entradas p/ disparar", joins), Label.of("Janela (s)", window),
                        Label.of("Idade mínima (dias)", minage), Label.of("Nível de lockdown", level))
                .build();
    }

    public static Modal antinukeModal(GuildConfig cfg) {
        TextInput max = TextInput.create("max", TextInputStyle.SHORT)
                .setPlaceholder("Ações destrutivas p/ disparar (ex.: 5)").setRequired(false).setMaxLength(3)
                .setValue(String.valueOf(dev.davimf.basebot.modules.base.security.SecurityConfig.antinukeMax(cfg))).build();
        TextInput window = TextInput.create("window", TextInputStyle.SHORT)
                .setPlaceholder("Janela em segundos (ex.: 60)").setRequired(false).setMaxLength(4)
                .setValue(String.valueOf(dev.davimf.basebot.modules.base.security.SecurityConfig.antinukeWindowSeconds(cfg))).build();
        TextInput.Builder wlB = TextInput.create("whitelist", TextInputStyle.PARAGRAPH)
                .setPlaceholder("Isentos: user:<id>, role:<id> (separados por vírgula)").setRequired(false).setMaxLength(500);
        String wl = String.join(", ", dev.davimf.basebot.modules.base.security.SecurityConfig.antinukeWhitelist(cfg));
        if (!wl.isBlank()) {
            wlB.setValue(wl);
        }
        return Modal.create(ComponentId.of(NS, "antinukeform"), "Anti-nuke — limiares")
                .addComponents(Label.of("Ações p/ disparar", max), Label.of("Janela (s)", window),
                        Label.of("Whitelist (user:/role:)", wlB.build()))
                .build();
    }

    public static Modal securityModal(GuildConfig cfg) {
        TextInput mention = TextInput.create("mention", TextInputStyle.SHORT)
                .setPlaceholder("Limite de menções por mensagem (ex.: 5)").setRequired(false).setMaxLength(3)
                .setValue(String.valueOf(dev.davimf.basebot.modules.base.security.SecurityConfig.mentionLimit(cfg))).build();
        TextInput warnPer = TextInput.create("warnper", TextInputStyle.SHORT)
                .setPlaceholder("Violações por warn (ex.: 1)").setRequired(false).setMaxLength(3)
                .setValue(String.valueOf(dev.davimf.basebot.modules.base.security.SecurityConfig.warnPer(cfg))).build();
        TextInput.Builder kwB = TextInput.create("keywords", TextInputStyle.PARAGRAPH)
                .setPlaceholder("Palavras/padrões bloqueados, separados por vírgula").setRequired(false).setMaxLength(500);
        String curKw = String.join(", ", dev.davimf.basebot.modules.base.security.SecurityConfig.keywords(cfg));
        if (!curKw.isBlank()) {
            kwB.setValue(curKw);
        }
        return Modal.create(ComponentId.of(NS, "securityform"), "AutoMod — limites e keywords")
                .addComponents(Label.of("Limite de menções", mention), Label.of("Violações por warn", warnPer),
                        Label.of("Keywords (CSV)", kwB.build()))
                .build();
    }

    /** The persistent module picker added to every setup screen. */
    public static ActionRow moduleNav(String current) {
        StringSelectMenu.Builder menu = StringSelectMenu.create(ComponentId.of(NS, "section"))
                .setPlaceholder("Ir para a seção…");
        addNav(menu, current, "Visão geral", "hub");
        addNav(menu, current, "Logs", "logs");
        addNav(menu, current, "Cargos", "roles");
        addNav(menu, current, "Farm", "farm");
        addNav(menu, current, "Tickets", "tickets");
        addNav(menu, current, "Ações", "acoes");
        addNav(menu, current, "Moderação", "moderacao");
        addNav(menu, current, "Segurança", "seguranca");
        addNav(menu, current, "Boas-vindas", "boasvindas");
        addNav(menu, current, "Auto-cargos", "autocargos");
        addNav(menu, current, "Bot", "bot");
        addNav(menu, current, "Permissões", "permissoes");
        return ActionRow.of(menu.build());
    }

    private static void addNav(StringSelectMenu.Builder menu, String current, String label, String value) {
        menu.addOption(label, value, value.equals(current) ? "Você está aqui" : null);
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
}
