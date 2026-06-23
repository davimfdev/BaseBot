package dev.davimf.basebot.modules.base.setup;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.component.ComponentHandler;
import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.database.model.GuildConfig;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.modals.Modal;

import java.util.List;

/**
 * Drives the {@code /setup} hub interactions (BOTSPECS Module 1). Each section reads the
 * current {@link GuildConfig}, applies a pure {@link GuildConfigEdits} transform, and
 * persists via the Postgres-backed {@code GuildConfigRepository}.
 */
public final class SetupComponentHandler implements ComponentHandler {

    @Override
    public String namespace() {
        return SetupView.NS;
    }

    @Override
    public void onButton(ButtonInteractionEvent event, ComponentId id, BotContext ctx) {
        if ("ticketinfo".equals(id.action())) {
            openTicketInfoModal(event);
            return;
        }
        if (!"section".equals(id.action())) {
            return;
        }
        switch (String.valueOf(id.arg(0))) {
            case "logs" -> showLogsPanel(event);
            case "roles" -> showRolesPanel(event);
            case "tickets" -> showTicketsPanel(event);
            case "bot" -> placeholder(event, "Bot",
                    "Perfil global do bot via /bot-name e /bot-icon (limite do Discord: 2x por hora).");
            default -> placeholder(event, "Desconhecido", "Seção inválida.");
        }
    }

    @Override
    public void onStringSelect(StringSelectInteractionEvent event, ComponentId id, BotContext ctx) {
        if (!"rolekey".equals(id.action())) {
            return;
        }
        String key = event.getValues().get(0);
        EntitySelectMenu roleMenu = EntitySelectMenu
                .create(ComponentId.of(SetupView.NS, "setrole", key), EntitySelectMenu.SelectTarget.ROLE)
                .setPlaceholder("Cargo para: " + SetupRoleKeys.labelFor(key))
                .setRequiredRange(1, 1)
                .build();
        event.reply("Selecione o cargo do servidor para **" + SetupRoleKeys.labelFor(key) + "**:")
                .addComponents(ActionRow.of(roleMenu))
                .setEphemeral(true)
                .queue();
    }

    @Override
    public void onEntitySelect(EntitySelectInteractionEvent event, ComponentId id, BotContext ctx) {
        switch (id.action()) {
            case "setlog" -> persistLogChannel(event, ctx, false);
            case "setticketlog" -> persistLogChannel(event, ctx, true);
            case "setrole" -> persistRole(event, ctx, id.arg(0));
            case "setcategory" -> persistChannelSetting(event, ctx, "tickets-category", "Categoria de tickets");
            case "setstaff" -> persistStaffRoles(event, ctx);
            default -> { /* not ours */ }
        }
    }

    @Override
    public void onModal(ModalInteractionEvent event, ComponentId id, BotContext ctx) {
        if (!"ticketinfo".equals(id.action()) || event.getGuild() == null) {
            return;
        }
        String desc = event.getValue("desc") == null ? "" : event.getValue("desc").getAsString();
        String emoji = event.getValue("emoji") == null ? "" : event.getValue("emoji").getAsString();
        String guildId = event.getGuild().getId();
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(guildId);
        cfg = GuildConfigEdits.withSetting(cfg, "ticket-description", desc);
        cfg = GuildConfigEdits.withSetting(cfg, "ticket-emoji", emoji);
        ctx.database().guildConfig().save(cfg);
        event.reply("Descrição e emoji dos tickets atualizados.").setEphemeral(true).queue();
    }

    private void showLogsPanel(ButtonInteractionEvent event) {
        EntitySelectMenu logMenu = EntitySelectMenu
                .create(ComponentId.of(SetupView.NS, "setlog"), EntitySelectMenu.SelectTarget.CHANNEL)
                .setChannelTypes(ChannelType.TEXT)
                .setPlaceholder("Canal de logs gerais")
                .setRequiredRange(1, 1)
                .build();
        EntitySelectMenu ticketMenu = EntitySelectMenu
                .create(ComponentId.of(SetupView.NS, "setticketlog"), EntitySelectMenu.SelectTarget.CHANNEL)
                .setChannelTypes(ChannelType.TEXT)
                .setPlaceholder("Canal de logs de tickets")
                .setRequiredRange(1, 1)
                .build();
        event.reply("Selecione os canais de log:")
                .addComponents(ActionRow.of(logMenu), ActionRow.of(ticketMenu))
                .setEphemeral(true)
                .queue();
    }

    private void showRolesPanel(ButtonInteractionEvent event) {
        StringSelectMenu.Builder menu = StringSelectMenu
                .create(ComponentId.of(SetupView.NS, "rolekey"))
                .setPlaceholder("Qual cargo lógico configurar?");
        for (var e : SetupRoleKeys.OPTIONS) {
            menu.addOption(e.getValue(), e.getKey());
        }
        event.reply("Escolha qual função deseja mapear a um cargo:")
                .addComponents(ActionRow.of(menu.build()))
                .setEphemeral(true)
                .queue();
    }

    private void showTicketsPanel(ButtonInteractionEvent event) {
        EntitySelectMenu category = EntitySelectMenu
                .create(ComponentId.of(SetupView.NS, "setcategory"), EntitySelectMenu.SelectTarget.CHANNEL)
                .setChannelTypes(ChannelType.CATEGORY)
                .setPlaceholder("Categoria onde os tickets serão criados")
                .setRequiredRange(1, 1)
                .build();
        EntitySelectMenu staff = EntitySelectMenu
                .create(ComponentId.of(SetupView.NS, "setstaff"), EntitySelectMenu.SelectTarget.ROLE)
                .setPlaceholder("Cargos de staff com acesso aos tickets")
                .setRequiredRange(1, 25)
                .build();
        event.reply("Configuração de tickets:")
                .addComponents(
                        ActionRow.of(category),
                        ActionRow.of(staff),
                        ActionRow.of(Button.secondary(
                                ComponentId.of(SetupView.NS, "ticketinfo"), "Definir descrição/emoji")))
                .setEphemeral(true)
                .queue();
    }

    private void openTicketInfoModal(ButtonInteractionEvent event) {
        TextInput desc = TextInput.create("desc", TextInputStyle.PARAGRAPH)
                .setPlaceholder("Texto exibido no painel de tickets")
                .setRequired(false)
                .setMaxLength(200)
                .build();
        TextInput emoji = TextInput.create("emoji", TextInputStyle.SHORT)
                .setPlaceholder("Ex.: 🎫")
                .setRequired(false)
                .setMaxLength(8)
                .build();
        Modal modal = Modal.create(ComponentId.of(SetupView.NS, "ticketinfo"), "Descrição & Emoji dos Tickets")
                .addComponents(Label.of("Descrição", desc), Label.of("Emoji", emoji))
                .build();
        event.replyModal(modal).queue();
    }

    private void persistLogChannel(EntitySelectInteractionEvent event, BotContext ctx, boolean ticketLog) {
        if (event.getGuild() == null) {
            event.reply("Use em um servidor.").setEphemeral(true).queue();
            return;
        }
        List<GuildChannel> channels = event.getMentions().getChannels();
        if (channels.isEmpty()) {
            event.reply("Nenhum canal selecionado.").setEphemeral(true).queue();
            return;
        }
        String channelId = channels.get(0).getId();
        String guildId = event.getGuild().getId();

        GuildConfig current = ctx.database().guildConfig().findOrEmpty(guildId);
        GuildConfig updated = ticketLog
                ? GuildConfigEdits.withTicketLogChannel(current, channelId)
                : GuildConfigEdits.withLogChannel(current, channelId);
        ctx.database().guildConfig().save(updated);
        ctx.database().actionLogs().log(guildId, event.getUser().getId(), channelId,
                ticketLog ? "SETUP_TICKET_LOG" : "SETUP_LOG", channelId);

        event.reply((ticketLog ? "Canal de logs de tickets" : "Canal de logs gerais")
                + " definido: <#" + channelId + ">").setEphemeral(true).queue();
    }

    private void persistRole(EntitySelectInteractionEvent event, BotContext ctx, String key) {
        if (event.getGuild() == null || key == null) {
            event.reply("Requisição inválida.").setEphemeral(true).queue();
            return;
        }
        List<Role> roles = event.getMentions().getRoles();
        if (roles.isEmpty()) {
            event.reply("Nenhum cargo selecionado.").setEphemeral(true).queue();
            return;
        }
        String roleId = roles.get(0).getId();
        String guildId = event.getGuild().getId();
        GuildConfig updated = GuildConfigEdits.withRole(
                ctx.database().guildConfig().findOrEmpty(guildId), key, roleId);
        ctx.database().guildConfig().save(updated);
        ctx.database().actionLogs().log(guildId, event.getUser().getId(), roleId, "SETUP_ROLE", key);
        event.reply("Cargo de **" + SetupRoleKeys.labelFor(key) + "** definido: <@&" + roleId + ">")
                .setEphemeral(true).queue();
    }

    private void persistChannelSetting(EntitySelectInteractionEvent event, BotContext ctx,
                                       String key, String label) {
        if (event.getGuild() == null) {
            event.reply("Use em um servidor.").setEphemeral(true).queue();
            return;
        }
        List<GuildChannel> channels = event.getMentions().getChannels();
        if (channels.isEmpty()) {
            event.reply("Nada selecionado.").setEphemeral(true).queue();
            return;
        }
        String channelId = channels.get(0).getId();
        String guildId = event.getGuild().getId();
        GuildConfig updated = GuildConfigEdits.withChannel(
                ctx.database().guildConfig().findOrEmpty(guildId), key, channelId);
        ctx.database().guildConfig().save(updated);
        ctx.database().actionLogs().log(guildId, event.getUser().getId(), channelId, "SETUP_CHANNEL", key);
        event.reply(label + " definida: <#" + channelId + ">").setEphemeral(true).queue();
    }

    private void persistStaffRoles(EntitySelectInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null) {
            event.reply("Use em um servidor.").setEphemeral(true).queue();
            return;
        }
        List<String> roleIds = event.getMentions().getRoles().stream().map(Role::getId).toList();
        if (roleIds.isEmpty()) {
            event.reply("Nenhum cargo selecionado.").setEphemeral(true).queue();
            return;
        }
        String guildId = event.getGuild().getId();
        GuildConfig updated = GuildConfigEdits.withStaffRoles(
                ctx.database().guildConfig().findOrEmpty(guildId), roleIds);
        ctx.database().guildConfig().save(updated);
        ctx.database().actionLogs().log(guildId, event.getUser().getId(), null,
                "SETUP_STAFF_ROLES", String.valueOf(roleIds.size()));
        event.reply("Cargos de staff definidos: " + roleIds.size()).setEphemeral(true).queue();
    }

    private void placeholder(ButtonInteractionEvent event, String section, String detail) {
        event.reply("**" + section + "** — " + detail).setEphemeral(true).queue();
    }
}
