package dev.davimf.basebot.modules.base.setup;

import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.modules.base.setup.SetupLogTypes.LogType;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu.DefaultValue;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu.SelectTarget;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.entities.channel.ChannelType;

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

    public static Container hub(GuildConfig cfg) {
        long logsSet = SetupLogTypes.ALL.stream().filter(t -> cfg.channel(t.key()) != null).count();
        String body = "## ⚙️ Configuração do Servidor\n"
                + "Escolha uma seção para configurar. As alterações são salvas na hora.\n\n"
                + "📋 **Logs configurados:** " + logsSet + "/" + SetupLogTypes.ALL.size() + "\n"
                + "📂 **Categoria de tickets:** " + channelOrUnset(cfg.channel("tickets-category")) + "\n"
                + "👥 **Cargos configurados:** " + cfg.roles().size() + "\n"
                + "🛡️ **Staff de tickets:** " + cfg.staffRoleIds().size() + "\n"
                + "📝 **Descrição de tickets:** " + (hasText(cfg.setting("ticket-description")) ? "Sim" : "Não");

        StringSelectMenu menu = StringSelectMenu.create(ComponentId.of(NS, "section"))
                .setPlaceholder("Escolha a seção")
                .addOption("Logs", "logs", "Um canal próprio para cada tipo de log")
                .addOption("Cargos", "roles", "Mapear cargos lógicos a cargos do servidor")
                .addOption("Tickets", "tickets", "Categoria, staff, descrição e emoji")
                .addOption("Bot", "bot", "Perfil global do bot")
                .build();

        return Panels.container(Panels.BLURPLE, Panels.text(body), Panels.divider(), ActionRow.of(menu));
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

        return Panels.container(Panels.BLURPLE, kids.toArray(new ContainerChildComponent[0]));
    }

    // --- Cargos (all role selects on one screen) -------------------------------

    public static Container cargos(GuildConfig cfg) {
        List<ContainerChildComponent> kids = new ArrayList<>();
        kids.add(Panels.text("## 👥 Cargos\nMapeie cada função a um cargo do servidor. Salva ao selecionar."));
        for (var e : SetupRoleKeys.OPTIONS) {
            kids.add(Panels.text("**" + e.getValue() + "**"));
            kids.add(ActionRow.of(roleSelect("setrole", e.getKey(),
                    "Selecionar cargo", cfg.role(e.getKey()))));
        }
        kids.add(backRow("hub"));
        return Panels.container(Panels.BLURPLE, kids.toArray(new ContainerChildComponent[0]));
    }

    // --- Tickets ---------------------------------------------------------------

    public static Container tickets(GuildConfig cfg) {
        EntitySelectMenu.Builder category = EntitySelectMenu
                .create(ComponentId.of(NS, "setcategory"), SelectTarget.CHANNEL)
                .setChannelTypes(ChannelType.CATEGORY)
                .setPlaceholder("Categoria onde os tickets serão criados")
                .setRequiredRange(1, 1);
        if (cfg.channel("tickets-category") != null) {
            category.setDefaultValues(DefaultValue.channel(cfg.channel("tickets-category")));
        }
        EntitySelectMenu.Builder staff = EntitySelectMenu
                .create(ComponentId.of(NS, "setstaff"), SelectTarget.ROLE)
                .setPlaceholder("Cargos de staff com acesso aos tickets")
                .setRequiredRange(1, 25);
        if (!cfg.staffRoleIds().isEmpty()) {
            staff.setDefaultValues(cfg.staffRoleIds().stream().map(DefaultValue::role).toList());
        }
        return Panels.container(Panels.BLURPLE,
                Panels.text("## 🎫 Tickets\nConfigure a categoria, os cargos de staff e os textos do painel."),
                Panels.text("**Categoria dos tickets**"),
                ActionRow.of(category.build()),
                Panels.text("**Cargos de staff**"),
                ActionRow.of(staff.build()),
                ActionRow.of(
                        Button.secondary(ComponentId.of(NS, "ticketinfo"), "Definir descrição/emoji"),
                        Button.secondary(ComponentId.of(NS, "nav", "hub"), "◀ Voltar")));
    }

    // --- Bot -------------------------------------------------------------------

    public static Container bot(String botName, String botId) {
        return Panels.container(Panels.BLURPLE,
                Panels.text("## 🤖 Perfil do Bot\n"
                        + "**Nome atual:** " + botName + "\n"
                        + "**ID:** " + botId + "\n\n"
                        + "O **nome** e o **avatar** são globais (afetam o bot em todos os servidores) e o "
                        + "Discord limita a **2 alterações por hora**. Use `/bot-name` e `/bot-icon` para "
                        + "alterá-los, e `/bot-nick` para o apelido apenas neste servidor."),
                backRow("hub"));
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

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private static String channelOrUnset(String channelId) {
        return (channelId == null || channelId.isBlank()) ? "*Não definido*" : "<#" + channelId + ">";
    }
}
