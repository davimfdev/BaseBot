package dev.davimf.basebot.modules.base.setup;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.component.ComponentHandler;
import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.database.model.TicketCategory;
import dev.davimf.basebot.util.EmbedColor;
import dev.davimf.basebot.util.TicketEmoji;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.callbacks.IMessageEditCallback;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * Drives the {@code /setup} wizard (Components V2). Navigation edits the single ephemeral
 * message; the Tickets section is a multi-category CRUD (list → detail → create/edit via
 * a modal that includes channel/role selects). Selects save on change.
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
        String guildId = event.getGuild().getId();
        switch (id.action()) {
            case "nav" -> edit(event, "tickets".equals(id.arg(0))
                    ? ticketsScreen(ctx, guildId) : hubScreen(ctx, guildId));
            case "logpage" -> edit(event, SetupView.logsPage(config(ctx, guildId), parseInt(id.arg(0))));
            case "rolepage" -> edit(event, SetupView.cargos(config(ctx, guildId), parseInt(id.arg(0))));
            case "ticketnew" -> event.replyModal(SetupView.ticketModal("new", null)).queue();
            case "ticketedit" -> {
                Optional<TicketCategory> cat = ctx.database().ticketCategories().find(id.arg(0));
                if (cat.isPresent()) {
                    event.replyModal(SetupView.ticketModal(cat.get().id(), cat.get())).queue();
                } else {
                    event.reply("Categoria não encontrada.").setEphemeral(true).queue();
                }
            }
            case "ticketdel" -> {
                ctx.database().ticketCategories().delete(id.arg(0));
                ctx.database().actionLogs().log(guildId, event.getUser().getId(), id.arg(0),
                        "TICKET_CATEGORY_DELETE", null);
                edit(event, ticketsScreen(ctx, guildId));
            }
            case "botcolor" -> event.replyModal(
                    SetupView.colorModal(EmbedColor.hex(EmbedColor.resolve(config(ctx, guildId))))).queue();
            default -> { /* not ours */ }
        }
    }

    @Override
    public void onStringSelect(StringSelectInteractionEvent event, ComponentId id, BotContext ctx) {
        if (event.getGuild() == null) {
            return;
        }
        String guildId = event.getGuild().getId();
        switch (id.action()) {
            case "section" -> {
                GuildConfig cfg = config(ctx, guildId);
                Container screen = switch (event.getValues().get(0)) {
                    case "logs" -> SetupView.logsPage(cfg, 0);
                    case "roles" -> SetupView.cargos(cfg, 0);
                    case "tickets" -> ticketsScreen(ctx, guildId);
                    case "bot" -> SetupView.bot(EmbedColor.resolve(cfg),
                            event.getJDA().getSelfUser().getName(), event.getJDA().getSelfUser().getId());
                    default -> hubScreen(ctx, guildId);
                };
                edit(event, screen);
            }
            case "ticketcat" -> ctx.database().ticketCategories().find(event.getValues().get(0))
                    .ifPresent(cat -> edit(event,
                            SetupView.ticketDetail(EmbedColor.resolve(config(ctx, guildId)), cat)));
            default -> { /* not ours */ }
        }
    }

    @Override
    public void onEntitySelect(EntitySelectInteractionEvent event, ComponentId id, BotContext ctx) {
        switch (id.action()) {
            case "setlogchannel" -> { saveChannel(event, ctx, id.arg(0), firstChannelId(event)); ack(event); }
            case "setrole" -> { saveRole(event, ctx, id.arg(0), firstRoleId(event)); ack(event); }
            default -> { /* not ours */ }
        }
    }

    @Override
    public void onModal(ModalInteractionEvent event, ComponentId id, BotContext ctx) {
        if (event.getGuild() == null) {
            return;
        }
        if ("botcolorform".equals(id.action())) {
            saveColor(event, ctx);
            return;
        }
        if (!"ticketform".equals(id.action())) {
            return;
        }
        String guildId = event.getGuild().getId();
        String nome = value(event, "nome");
        String emoji = TicketEmoji.channelSafe(value(event, "emoji"));
        String descricao = value(event, "descricao");

        ModalMapping catMap = event.getValue("categoria");
        ModalMapping cargosMap = event.getValue("cargos");
        List<GuildChannel> cats = catMap == null ? List.of() : catMap.getAsMentions().getChannels();
        List<Role> roles = cargosMap == null ? List.of() : cargosMap.getAsMentions().getRoles();

        if (nome == null || nome.isBlank() || cats.isEmpty() || roles.isEmpty()) {
            event.reply("Preencha o nome, a categoria do Discord e ao menos um cargo que atende.")
                    .setEphemeral(true).queue();
            return;
        }

        String catId = "new".equals(id.arg(0)) ? newId() : id.arg(0);
        TicketCategory tc = new TicketCategory(catId, guildId, nome.trim(),
                emoji.isBlank() ? null : emoji,
                (descricao == null || descricao.isBlank()) ? null : descricao.trim(),
                cats.get(0).getId(), roles.stream().map(Role::getId).toList());
        ctx.database().ticketCategories().upsert(tc);
        ctx.database().actionLogs().log(guildId, event.getUser().getId(), catId, "TICKET_CATEGORY_SAVE", nome);

        event.replyComponents(ticketsScreen(ctx, guildId)).useComponentsV2().setEphemeral(true).queue();
    }

    private void saveColor(ModalInteractionEvent event, BotContext ctx) {
        String guildId = event.getGuild().getId();
        OptionalInt parsed = EmbedColor.parse(value(event, "cor"));
        if (parsed.isEmpty()) {
            event.reply("Cor inválida. Use um hex como `#5865F2`.").setEphemeral(true).queue();
            return;
        }
        int color = parsed.getAsInt();
        GuildConfig cfg = GuildConfigEdits.withSetting(
                config(ctx, guildId), EmbedColor.SETTING_KEY, EmbedColor.hex(color));
        ctx.database().guildConfig().save(cfg);
        ctx.database().actionLogs().log(guildId, event.getUser().getId(), null,
                "SETUP_COLOR", EmbedColor.hex(color));
        event.replyComponents(SetupView.bot(color, event.getJDA().getSelfUser().getName(),
                event.getJDA().getSelfUser().getId())).useComponentsV2().setEphemeral(true).queue();
    }

    // --- screens ---------------------------------------------------------------

    private Container hubScreen(BotContext ctx, String guildId) {
        return SetupView.hub(config(ctx, guildId), ctx.database().ticketCategories().count(guildId));
    }

    private Container ticketsScreen(BotContext ctx, String guildId) {
        return SetupView.ticketsList(EmbedColor.resolve(config(ctx, guildId)),
                ctx.database().ticketCategories().listByGuild(guildId));
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

    // --- helpers ---------------------------------------------------------------

    private void edit(IMessageEditCallback event, Container screen) {
        event.editComponents(screen).useComponentsV2().queue();
    }

    private void ack(EntitySelectInteractionEvent event) {
        event.deferEdit().queue();
    }

    private GuildConfig config(BotContext ctx, String guildId) {
        return ctx.database().guildConfig().findOrEmpty(guildId);
    }

    private static String value(ModalInteractionEvent event, String key) {
        ModalMapping m = event.getValue(key);
        return m == null ? null : m.getAsString();
    }

    private static String newId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
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
}
