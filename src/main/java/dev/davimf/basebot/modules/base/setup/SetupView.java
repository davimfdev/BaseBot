package dev.davimf.basebot.modules.base.setup;

import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.database.model.GuildConfig;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
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
                .addField("📂 Categoria de tickets", channelOrUnset(cfg.channel("tickets-category")), true)
                .addField("👥 Cargos configurados", String.valueOf(cfg.roles().size()), true)
                .addField("🛡️ Staff de tickets", String.valueOf(cfg.staffRoleIds().size()), true)
                .addField("📝 Descrição definida",
                        cfg.setting("ticket-description") == null
                                || cfg.setting("ticket-description").isBlank() ? "Não" : "Sim", true)
                .build();
    }

    public static ActionRow hubRow() {
        StringSelectMenu menu = StringSelectMenu.create(ComponentId.of(NS, "section"))
                .setPlaceholder("Escolha a seção")
                .addOption("Logs", "logs", "Canais de logs gerais e de tickets")
                .addOption("Cargos", "roles", "Mapear cargos lógicos a cargos do servidor")
                .addOption("Tickets", "tickets", "Categoria, staff, descrição e emoji")
                .addOption("Bot", "bot", "Perfil global do bot")
                .build();
        return ActionRow.of(menu);
    }

    private static String channelOrUnset(String channelId) {
        return channelId == null || channelId.isBlank() ? "*Não definido*" : "<#" + channelId + ">";
    }
}
