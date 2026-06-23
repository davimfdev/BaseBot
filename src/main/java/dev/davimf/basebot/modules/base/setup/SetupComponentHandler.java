package dev.davimf.basebot.modules.base.setup;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.component.ComponentHandler;
import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.database.model.GuildConfig;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.callbacks.IMessageEditCallback;
import net.dv8tion.jda.api.modals.Modal;

import java.util.List;

/**
 * Drives the {@code /setup} wizard. Navigation edits the single ephemeral message
 * (hub → section, with the Logs section paginated by module). Each select saves to
 * {@code guild_config} and is acknowledged silently (the choice stays selected); the
 * value is re-shown as the select's default when the screen is rebuilt.
 */
public final class SetupComponentHandler implements ComponentHandler {

    @Override
    public String namespace() {
        return SetupView.NS;
    }

    @Override
    public void onButton(ButtonInteractionEvent event, ComponentId id, BotContext ctx) {
        if (event.getGuild() == null) {
            return;
        }
        switch (id.action()) {
            case "nav" -> edit(event, SetupView.hub(config(ctx, event.getGuild().getId())));
            case "logpage" -> edit(event, SetupView.logsPage(
                    config(ctx, event.getGuild().getId()), parseInt(id.arg(0))));
            case "ticketinfo" -> event.replyModal(ticketInfoModal()).queue();
            default -> { /* not ours */ }
        }
    }

    @Override
    public void onStringSelect(StringSelectInteractionEvent event, ComponentId id, BotContext ctx) {
        if (!"section".equals(id.action()) || event.getGuild() == null) {
            return;
        }
        GuildConfig cfg = config(ctx, event.getGuild().getId());
        Container screen = switch (event.getValues().get(0)) {
            case "logs" -> SetupView.logsPage(cfg, 0);
            case "roles" -> SetupView.cargos(cfg);
            case "tickets" -> SetupView.tickets(cfg);
            case "bot" -> SetupView.bot(event.getJDA().getSelfUser().getName(),
                    event.getJDA().getSelfUser().getId());
            default -> SetupView.hub(cfg);
        };
        edit(event, screen);
    }

    @Override
    public void onEntitySelect(EntitySelectInteractionEvent event, ComponentId id, BotContext ctx) {
        switch (id.action()) {
            case "setlogchannel" -> { saveChannel(event, ctx, id.arg(0), firstChannelId(event)); ack(event); }
            case "setcategory" -> { saveChannel(event, ctx, "tickets-category", firstChannelId(event)); ack(event); }
            case "setrole" -> { saveRole(event, ctx, id.arg(0), firstRoleId(event)); ack(event); }
            case "setstaff" -> { saveStaff(event, ctx); ack(event); }
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
        GuildConfig cfg = config(ctx, event.getGuild().getId());
        cfg = GuildConfigEdits.withSetting(cfg, "ticket-description", desc);
        cfg = GuildConfigEdits.withSetting(cfg, "ticket-emoji", emoji);
        ctx.database().guildConfig().save(cfg);
        event.reply("Descrição e emoji dos tickets atualizados.").setEphemeral(true).queue();
    }

    // --- persistence -----------------------------------------------------------

    private void saveChannel(EntitySelectInteractionEvent event, BotContext ctx, String key, String channelId) {
        if (channelId == null || event.getGuild() == null) {
            return;
        }
        String guildId = event.getGuild().getId();
        GuildConfig updated = GuildConfigEdits.withChannel(
                ctx.database().guildConfig().findOrEmpty(guildId), key, channelId);
        ctx.database().guildConfig().save(updated);
        ctx.database().actionLogs().log(guildId, event.getUser().getId(), channelId, "SETUP_CHANNEL", key);
    }

    private void saveRole(EntitySelectInteractionEvent event, BotContext ctx, String key, String roleId) {
        if (roleId == null || key == null || event.getGuild() == null) {
            return;
        }
        String guildId = event.getGuild().getId();
        GuildConfig updated = GuildConfigEdits.withRole(
                ctx.database().guildConfig().findOrEmpty(guildId), key, roleId);
        ctx.database().guildConfig().save(updated);
        ctx.database().actionLogs().log(guildId, event.getUser().getId(), roleId, "SETUP_ROLE", key);
    }

    private void saveStaff(EntitySelectInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null) {
            return;
        }
        List<String> roleIds = event.getMentions().getRoles().stream().map(Role::getId).toList();
        String guildId = event.getGuild().getId();
        GuildConfig updated = GuildConfigEdits.withStaffRoles(
                ctx.database().guildConfig().findOrEmpty(guildId), roleIds);
        ctx.database().guildConfig().save(updated);
        ctx.database().actionLogs().log(guildId, event.getUser().getId(), null,
                "SETUP_STAFF_ROLES", String.valueOf(roleIds.size()));
    }

    // --- helpers ---------------------------------------------------------------

    /** Edits the wizard message to a new V2 screen (V1 default would reject Container). */
    private void edit(IMessageEditCallback event, Container screen) {
        event.editComponents(screen).useComponentsV2().queue();
    }

    /** Acknowledges a select without changing the message — the choice stays selected. */
    private void ack(EntitySelectInteractionEvent event) {
        event.deferEdit().queue();
    }

    private GuildConfig config(BotContext ctx, String guildId) {
        return ctx.database().guildConfig().findOrEmpty(guildId);
    }

    private static int parseInt(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String firstChannelId(EntitySelectInteractionEvent event) {
        List<GuildChannel> channels = event.getMentions().getChannels();
        return channels.isEmpty() ? null : channels.get(0).getId();
    }

    private static String firstRoleId(EntitySelectInteractionEvent event) {
        List<Role> roles = event.getMentions().getRoles();
        return roles.isEmpty() ? null : roles.get(0).getId();
    }

    private static Modal ticketInfoModal() {
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
        return Modal.create(ComponentId.of(SetupView.NS, "ticketinfo"), "Descrição & Emoji dos Tickets")
                .addComponents(Label.of("Descrição", desc), Label.of("Emoji", emoji))
                .build();
    }
}
