package dev.davimf.basebot.modules.base.setup;

import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.database.model.GuildConfig;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;

/** Builds the {@code /setup} hub as a Components V2 container (summary + section select). */
public final class SetupView {

    public static final String NS = "setup";

    private SetupView() {}

    public static Container hubContainer(GuildConfig cfg) {
        String body = "## ⚙️ Configuração do Servidor\n"
                + "Escolha uma seção para configurar. As alterações são salvas na hora.\n\n"
                + "📋 **Logs gerais:** " + channelOrUnset(cfg.logChannelId()) + "\n"
                + "🎫 **Logs de tickets:** " + channelOrUnset(cfg.ticketLogChannelId()) + "\n"
                + "📂 **Categoria de tickets:** " + channelOrUnset(cfg.channel("tickets-category")) + "\n"
                + "👥 **Cargos configurados:** " + cfg.roles().size() + "\n"
                + "🛡️ **Staff de tickets:** " + cfg.staffRoleIds().size() + "\n"
                + "📝 **Descrição de tickets:** " + (hasText(cfg.setting("ticket-description")) ? "Sim" : "Não");

        StringSelectMenu menu = StringSelectMenu.create(ComponentId.of(NS, "section"))
                .setPlaceholder("Escolha a seção")
                .addOption("Logs", "logs", "Canais de logs gerais e de tickets")
                .addOption("Cargos", "roles", "Mapear cargos lógicos a cargos do servidor")
                .addOption("Tickets", "tickets", "Categoria, staff, descrição e emoji")
                .addOption("Bot", "bot", "Perfil global do bot")
                .build();

        return Panels.container(Panels.BLURPLE,
                Panels.text(body),
                Panels.divider(),
                ActionRow.of(menu));
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private static String channelOrUnset(String channelId) {
        return (channelId == null || channelId.isBlank()) ? "*Não definido*" : "<#" + channelId + ">";
    }
}
