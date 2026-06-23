package dev.davimf.basebot.modules.base.setup;

import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.database.model.GuildConfig;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.entities.channel.ChannelType;

/**
 * Builds every {@code /setup} screen as a Components V2 container. The flow is a wizard
 * that edits a single message: hub → section → sub-screen, each non-hub screen carrying
 * a "◀ Voltar" button back to the previous screen.
 */
public final class SetupView {

    public static final String NS = "setup";

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

    // --- Logs (one channel per type) ------------------------------------------

    public static Container logsList() {
        StringSelectMenu.Builder menu = StringSelectMenu.create(ComponentId.of(NS, "logpick"))
                .setPlaceholder("Selecione o tipo de log");
        for (SetupLogTypes.LogType t : SetupLogTypes.ALL) {
            menu.addOption(t.label(), t.key(), "Módulo " + t.module());
        }
        return Panels.container(Panels.BLURPLE,
                Panels.text("### 📋 Logs\nCada tipo de log tem o **seu próprio canal**. "
                        + "Selecione um tipo para definir o canal:"),
                ActionRow.of(menu.build()),
                backRow("hub"));
    }

    public static Container logChannelPicker(String logKey) {
        EntitySelectMenu channel = EntitySelectMenu
                .create(ComponentId.of(NS, "setlogchannel", logKey), EntitySelectMenu.SelectTarget.CHANNEL)
                .setChannelTypes(ChannelType.TEXT)
                .setPlaceholder("Canal para este log")
                .setRequiredRange(1, 1)
                .build();
        return Panels.container(Panels.BLURPLE,
                Panels.text("### 📋 Log: " + SetupLogTypes.labelFor(logKey)
                        + "\nSelecione o canal onde este log será enviado:"),
                ActionRow.of(channel),
                backRow("logs"));
    }

    // --- Cargos ----------------------------------------------------------------

    public static Container cargos() {
        StringSelectMenu.Builder menu = StringSelectMenu.create(ComponentId.of(NS, "rolekey"))
                .setPlaceholder("Qual cargo lógico configurar?");
        for (var e : SetupRoleKeys.OPTIONS) {
            menu.addOption(e.getValue(), e.getKey());
        }
        return Panels.container(Panels.BLURPLE,
                Panels.text("### 👥 Cargos\nMapeie cada função a um cargo do servidor:"),
                ActionRow.of(menu.build()),
                backRow("hub"));
    }

    public static Container rolePicker(String roleKey) {
        EntitySelectMenu roleMenu = EntitySelectMenu
                .create(ComponentId.of(NS, "setrole", roleKey), EntitySelectMenu.SelectTarget.ROLE)
                .setPlaceholder("Cargo para: " + SetupRoleKeys.labelFor(roleKey))
                .setRequiredRange(1, 1)
                .build();
        return Panels.container(Panels.BLURPLE,
                Panels.text("### 👥 Cargo: " + SetupRoleKeys.labelFor(roleKey)
                        + "\nSelecione o cargo do servidor:"),
                ActionRow.of(roleMenu),
                backRow("cargos"));
    }

    // --- Tickets ---------------------------------------------------------------

    public static Container tickets() {
        EntitySelectMenu category = EntitySelectMenu
                .create(ComponentId.of(NS, "setcategory"), EntitySelectMenu.SelectTarget.CHANNEL)
                .setChannelTypes(ChannelType.CATEGORY)
                .setPlaceholder("Categoria onde os tickets serão criados")
                .setRequiredRange(1, 1)
                .build();
        EntitySelectMenu staff = EntitySelectMenu
                .create(ComponentId.of(NS, "setstaff"), EntitySelectMenu.SelectTarget.ROLE)
                .setPlaceholder("Cargos de staff com acesso aos tickets")
                .setRequiredRange(1, 25)
                .build();
        return Panels.container(Panels.BLURPLE,
                Panels.text("### 🎫 Tickets\nConfigure a categoria, os cargos de staff e os textos do painel."),
                ActionRow.of(category),
                ActionRow.of(staff),
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
