package dev.davimf.basebot.modules.base.setup;

import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.database.model.GuildConfig;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;

/** Builds the {@code /setup} hub: a summary embed + the section buttons (BOTSPECS Module 1). */
public final class SetupView {

    public static final String NS = "setup";

    private SetupView() {}

    public static MessageEmbed hubEmbed(GuildConfig cfg) {
        return new EmbedBuilder()
                .setTitle("⚙️ Configuração do Servidor")
                .setColor(0x5865F2)
                .setDescription("Use os botões abaixo para configurar cada seção. "
                        + "Todas as configurações são específicas deste servidor.")
                .addField("📋 Logs gerais", channelOrUnset(cfg.logChannelId()), true)
                .addField("🎫 Logs de tickets", channelOrUnset(cfg.ticketLogChannelId()), true)
                .addField("👥 Cargos configurados", String.valueOf(cfg.roles().size()), true)
                .addField("🛡️ Cargos de staff (tickets)", String.valueOf(cfg.staffRoleIds().size()), true)
                .build();
    }

    public static ActionRow hubRow() {
        return ActionRow.of(
                Button.primary(ComponentId.of(NS, "section", "logs"), "Logs"),
                Button.primary(ComponentId.of(NS, "section", "roles"), "Cargos"),
                Button.primary(ComponentId.of(NS, "section", "tickets"), "Tickets"),
                Button.secondary(ComponentId.of(NS, "section", "bot"), "Bot")
        );
    }

    private static String channelOrUnset(String channelId) {
        return channelId == null || channelId.isBlank() ? "*Não definido*" : "<#" + channelId + ">";
    }
}
