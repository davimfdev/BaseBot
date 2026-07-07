// [OUTLINE START]
// Package: dev.davimf.basebot.modules.base.message
// 
// Class: MessageBuilderService
// 
// Constructors:
//   - `Constructor` : `public MessageBuilderService(BotContext ctx, MessageDraftRepository drafts)`
// 
// Methods:
//   - `Method` : `private static ArrayNode buttonsOf(ObjectNode state, String bi)`
//   - `Method` : `private static ObjectNode buttonAt(ObjectNode state, String bi, String ji)`
//   - `Method` : `private static ObjectNode webhookBody(ObjectNode state, int accent, boolean container)`
//   - `Method` : `private int accent(net.dv8tion.jda.api.interactions.Interaction event)`
//   - `Method` : `private static String[] parseRef(SlashCommandInteractionEvent event, String ref)`
//   - `Method` : `private CompletableFuture<Webhook> webhook(TextChannel channel)`
//   - `Method` : `private ObjectNode load(String userId)`
//   - `Method` : `private static String value(ModalInteractionEvent event, String key)`
//   - `Method` : `private static String id(String action, String arg)`
//   - `Method` : `private static String userId(IMessageEditCallback cb)`
//   - `Method` : `private static String guildId(IMessageEditCallback cb)`
//   - `Method` : `private static String root(Throwable err)`
// 
// Fields:
//   - `Field` : `private static final String WEBHOOK_NAME`
//   - `Field` : `private final BotContext ctx`
//   - `Field` : `private final MessageDraftRepository drafts`
// [OUTLINE END]



package dev.davimf.basebot.modules.base.message;

import dev.davimf.basebot.util.Emojis;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.modules.base.message.MessageBuilderView.Field;
import dev.davimf.basebot.util.EmbedColor;
import dev.davimf.basebot.util.Replies;
import dev.davimf.basebot.util.WebhookSender;
import net.dv8tion.jda.api.entities.Webhook;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.callbacks.IMessageEditCallback;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;

import java.util.concurrent.CompletableFuture;

/**
 * The interactive message builder behind {@code /mensagem} (BOTSPECS Module 1). Keeps a
 * per-user draft, re-rendering the ephemeral panel after every edit, and sends the final
 * message either normally or through a managed channel webhook (with name/avatar).
 */
public final class MessageBuilderService {

    private static final String WEBHOOK_NAME = "BaseBot Mensagem";

    private final BotContext ctx;
    private final MessageDraftRepository drafts;

    public MessageBuilderService(BotContext ctx, MessageDraftRepository drafts) {
        this.ctx = ctx;
        this.drafts = drafts;
    }

    public void open(SlashCommandInteractionEvent event) {
        ObjectNode state = MessageState.initial();
        drafts.save(event.getUser().getId(), event.getGuild().getId(), MessageState.stringify(state));
        event.replyComponents(MessageBuilderView.panel(state)).useComponentsV2().setEphemeral(true).queue();
    }

    // --- selects ---------------------------------------------------------------

    public void onStringSelect(StringSelectInteractionEvent event, ComponentId id) {
        ObjectNode state = load(event.getUser().getId());
        switch (id.action()) {
            case "type" -> {
                state.put("type", event.getValues().get(0));
                saveAndRender(event, state);
            }
            case "manage" -> render(event, MessageBuilderView.blockPanel(state,
                    Integer.parseInt(event.getValues().get(0))));
            case "managebtn" -> render(event, MessageBuilderView.buttonPanel(state,
                    Integer.parseInt(id.arg(0)), Integer.parseInt(event.getValues().get(0))));
            case "addblock" -> addBlock(event, state, event.getValues().get(0));
            default -> { /* not ours */ }
        }
    }

    public void onEntitySelect(EntitySelectInteractionEvent event, String action) {
        if (!"channel".equals(action)) {
            return;
        }
        ObjectNode state = load(event.getUser().getId());
        state.put("channelId", event.getValues().isEmpty() ? null : event.getValues().get(0).getId());
        saveAndRender(event, state);
    }

    private void addBlock(StringSelectInteractionEvent event, ObjectNode state, String kind) {
        switch (kind) {
            case "text" -> event.replyModal(MessageBuilderView.textBlockModal(
                    dev.davimf.basebot.core.component.ComponentId.of(MessageBuilderView.NS, "addtext"), null)).queue();
            case "buttons" -> event.replyModal(MessageBuilderView.buttonModal(
                    dev.davimf.basebot.core.component.ComponentId.of(MessageBuilderView.NS, "addbtn"))).queue();
            case "sep-line" -> { MessageState.blocks(state).add(MessageState.newSeparatorBlock(true)); saveAndRender(event, state); }
            case "sep-plain" -> { MessageState.blocks(state).add(MessageState.newSeparatorBlock(false)); saveAndRender(event, state); }
            default -> { /* ignore */ }
        }
    }

    // --- buttons ---------------------------------------------------------------

    public void onButton(ButtonInteractionEvent event, ComponentId id) {
        String action = id.action();
        String arg = id.arg(0);
        switch (action) {
            case "fld" -> {
                Field f = MessageBuilderView.field(arg);
                if (f != null) {
                    String current = MessageState.str(MessageState.classic(load(event.getUser().getId())), f.stateKey());
                    event.replyModal(MessageBuilderView.fieldModal(f, current)).queue();
                }
            }
            case "ccolor" -> event.replyModal(MessageBuilderView.colorModal(
                    MessageState.str(MessageState.container(load(event.getUser().getId())), "color"))).queue();
            case "webhook" -> {
                ObjectNode state = load(event.getUser().getId());
                state.put("webhook", !state.path("webhook").asBoolean());
                saveAndRender(event, state);
            }
            case "whname" -> event.replyModal(MessageBuilderView.textModal("whnameform", "Nome do webhook",
                    "Nome", "Como o webhook aparece", MessageState.str(load(event.getUser().getId()), "webhookName"))).queue();
            case "whavatar" -> event.replyModal(MessageBuilderView.textModal("whavatarform", "Avatar do webhook",
                    "Avatar (URL)", "https://…", MessageState.str(load(event.getUser().getId()), "webhookAvatar"))).queue();
            case "bedit" -> blockEdit(event, arg);
            case "bdel" -> { ObjectNode s = load(event.getUser().getId()); MessageState.blocks(s).remove(Integer.parseInt(arg)); saveAndRender(event, s); }
            case "bup" -> moveBlock(event, arg, -1);
            case "bdown" -> moveBlock(event, arg, 1);
            case "back" -> saveAndRender(event, load(event.getUser().getId()));
            case "btback" -> render(event, MessageBuilderView.blockPanel(
                    load(event.getUser().getId()), Integer.parseInt(arg)));
            case "btype" -> cycleButtonStyle(event, arg, id.arg(1));
            case "btlabel" -> openButtonModal(event, "btlabelform", arg, id.arg(1), "Título", "label");
            case "blink" -> openButtonModal(event, "blinkform", arg, id.arg(1), "Link (URL)", "url");
            case "btup" -> moveButton(event, arg, id.arg(1), -1);
            case "btdown" -> moveButton(event, arg, id.arg(1), 1);
            case "btdel" -> removeButton(event, arg, id.arg(1));
            case "cancel" -> { drafts.delete(event.getUser().getId()); render(event, Panels.container(EmbedColor.DEFAULT, Panels.text("Construtor cancelado."))); }
            case "send" -> send(event);
            default -> { /* not ours */ }
        }
    }

    // --- per-button editing ----------------------------------------------------

    private void cycleButtonStyle(ButtonInteractionEvent event, String bi, String ji) {
        ObjectNode state = load(event.getUser().getId());
        ObjectNode btn = buttonAt(state, bi, ji);
        if (btn != null && MessageState.isInteraction(btn)) {
            String[] styles = MessageState.STYLES;
            int idx = 0;
            String cur = MessageState.str(btn, "style");
            for (int i = 0; i < styles.length; i++) {
                if (styles[i].equals(cur)) {
                    idx = i;
                }
            }
            btn.put("style", styles[(idx + 1) % styles.length]);
        }
        saveButton(event, state, bi, ji);
    }

    private void openButtonModal(ButtonInteractionEvent event, String form, String bi, String ji,
                                 String label, String field) {
        ObjectNode btn = buttonAt(load(event.getUser().getId()), bi, ji);
        String current = btn == null ? "" : MessageState.str(btn, field);
        event.replyModal(MessageBuilderView.inputModal(
                dev.davimf.basebot.core.component.ComponentId.of(MessageBuilderView.NS, form, bi, ji),
                label, label, label, current)).queue();
    }

    private void moveButton(ButtonInteractionEvent event, String bi, String ji, int dir) {
        ObjectNode state = load(event.getUser().getId());
        ArrayNode buttons = buttonsOf(state, bi);
        if (buttons == null) {
            saveAndRender(event, state);
            return;
        }
        int i = Integer.parseInt(ji);
        int j = i + dir;
        if (i >= 0 && j >= 0 && i < buttons.size() && j < buttons.size()) {
            var moved = buttons.remove(i);
            buttons.insert(j, moved);
            drafts.save(event.getUser().getId(), guildId(event), MessageState.stringify(state));
            render(event, MessageBuilderView.buttonPanel(state, Integer.parseInt(bi), j));
        } else {
            saveAndRender(event, state);
        }
    }

    private void removeButton(ButtonInteractionEvent event, String bi, String ji) {
        ObjectNode state = load(event.getUser().getId());
        ArrayNode buttons = buttonsOf(state, bi);
        if (buttons != null) {
            buttons.remove(Integer.parseInt(ji));
        }
        drafts.save(event.getUser().getId(), guildId(event), MessageState.stringify(state));
        render(event, MessageBuilderView.blockPanel(state, Integer.parseInt(bi)));
    }

    private void saveButton(ButtonInteractionEvent event, ObjectNode state, String bi, String ji) {
        drafts.save(event.getUser().getId(), guildId(event), MessageState.stringify(state));
        render(event, MessageBuilderView.buttonPanel(state, Integer.parseInt(bi), Integer.parseInt(ji)));
    }

    private static ArrayNode buttonsOf(ObjectNode state, String bi) {
        ArrayNode blocks = MessageState.blocks(state);
        int i = Integer.parseInt(bi);
        if (i < 0 || i >= blocks.size()) {
            return null;
        }
        ObjectNode block = (ObjectNode) blocks.get(i);
        return MessageState.BLOCK_BUTTONS.equals(MessageState.str(block, "type"))
                ? MessageState.buttons(block) : null;
    }

    private static ObjectNode buttonAt(ObjectNode state, String bi, String ji) {
        ArrayNode buttons = buttonsOf(state, bi);
        int j = Integer.parseInt(ji);
        return buttons != null && j >= 0 && j < buttons.size() ? (ObjectNode) buttons.get(j) : null;
    }

    private void blockEdit(ButtonInteractionEvent event, String arg) {
        ObjectNode state = load(event.getUser().getId());
        int i = Integer.parseInt(arg);
        ArrayNode blocks = MessageState.blocks(state);
        if (i < 0 || i >= blocks.size()) {
            saveAndRender(event, state);
            return;
        }
        ObjectNode block = (ObjectNode) blocks.get(i);
        switch (MessageState.str(block, "type")) {
            case MessageState.BLOCK_TEXT -> event.replyModal(MessageBuilderView.textBlockModal(
                    id("edittext", arg), MessageState.str(block, "text"))).queue();
            case MessageState.BLOCK_BUTTONS -> event.replyModal(MessageBuilderView.buttonModal(id("editbtn", arg))).queue();
            case MessageState.BLOCK_SEPARATOR -> {
                block.put("divider", !block.path("divider").asBoolean());
                drafts.save(event.getUser().getId(), guildId(event), MessageState.stringify(state));
                render(event, MessageBuilderView.blockPanel(state, i));
            }
            default -> saveAndRender(event, state);
        }
    }

    private void moveBlock(ButtonInteractionEvent event, String arg, int dir) {
        ObjectNode state = load(event.getUser().getId());
        ArrayNode blocks = MessageState.blocks(state);
        int i = Integer.parseInt(arg);
        int j = i + dir;
        if (i >= 0 && j >= 0 && i < blocks.size() && j < blocks.size()) {
            var moved = blocks.remove(i);
            blocks.insert(j, moved);
            drafts.save(event.getUser().getId(), guildId(event), MessageState.stringify(state));
            render(event, MessageBuilderView.blockPanel(state, j));
        } else {
            saveAndRender(event, state);
        }
    }

    // --- modals ----------------------------------------------------------------

    public void onModal(ModalInteractionEvent event, ComponentId id) {
        String action = id.action();
        String arg = id.arg(0);
        ObjectNode state = load(event.getUser().getId());
        // Per-button edits re-render the button panel, not the main builder.
        if ("btlabelform".equals(action) || "blinkform".equals(action)) {
            ObjectNode btn = buttonAt(state, arg, id.arg(1));
            if (btn != null) {
                btn.put("btlabelform".equals(action) ? "label" : "url", value(event, "v"));
            }
            drafts.save(event.getUser().getId(), guildId(event), MessageState.stringify(state));
            render(event, MessageBuilderView.buttonPanel(state, Integer.parseInt(arg),
                    Integer.parseInt(id.arg(1))));
            return;
        }
        switch (action) {
            case "fldform" -> {
                Field f = MessageBuilderView.field(arg);
                if (f != null) {
                    putOrNull(MessageState.classic(state), f.stateKey(), value(event, "v"));
                }
            }
            case "ccolorform" -> putOrNull(MessageState.container(state), "color", value(event, "v"));
            case "whnameform" -> putOrNull(state, "webhookName", value(event, "v"));
            case "whavatarform" -> putOrNull(state, "webhookAvatar", value(event, "v"));
            case "addtext" -> MessageState.blocks(state).add(MessageState.newTextBlock(value(event, "v")));
            case "addbtn" -> {
                ObjectNode block = MessageState.newButtonsBlock();
                MessageState.addButton(block, value(event, "label"), value(event, "url"));
                MessageState.blocks(state).add(block);
            }
            case "edittext" -> ((ObjectNode) MessageState.blocks(state).get(Integer.parseInt(arg)))
                    .put("text", value(event, "v"));
            case "editbtn" -> MessageState.addButton(
                    (ObjectNode) MessageState.blocks(state).get(Integer.parseInt(arg)),
                    value(event, "label"), value(event, "url"));
            default -> { /* not ours */ }
        }
        saveAndRender(event, state);
    }

    // --- send ------------------------------------------------------------------

    private void send(ButtonInteractionEvent event) {
        ObjectNode state = load(event.getUser().getId());
        if (state.hasNonNull("editMessageId")) {
            editExisting(event, state);
            return;
        }
        String channelId = MessageState.str(state, "channelId");
        TextChannel channel = channelId == null ? null : event.getGuild().getTextChannelById(channelId);
        if (channel == null) {
            Replies.ephemeral(event, ctx, "Selecione um canal de destino primeiro.");
            return;
        }
        int accent = accent(event);
        boolean container = MessageState.isContainer(state);
        boolean webhook = state.path("webhook").asBoolean();
        event.deferEdit().queue();

        if (webhook) {
            String name = MessageState.str(state, "webhookName");
            String avatar = MessageState.str(state, "webhookAvatar");
            webhook(channel)
                    .thenAcceptAsync(wh -> WebhookSender.post(wh.getUrl(), name, avatar,
                            webhookBody(state, accent, container)), ctx.scheduler().executor())
                    .whenComplete((v, ex) -> finishWebhook(event, channel, ex, false));
            return;
        }
        if (container) {
            channel.sendMessageComponents(MessageBuild.jdaContainer(MessageState.container(state), accent))
                    .useComponentsV2().queue(m -> done(event, channel, false), err -> fail(event, err));
        } else {
            var embed = MessageBuild.jdaEmbed(MessageState.classic(state), accent);
            String content = MessageState.str(MessageState.classic(state), "content");
            var action = (content != null && !content.isBlank())
                    ? channel.sendMessage(content).setEmbeds(embed)
                    : channel.sendMessageEmbeds(embed);
            action.queue(m -> done(event, channel, false), err -> fail(event, err));
        }
    }

    // --- edit existing ---------------------------------------------------------

    public void openEdit(SlashCommandInteractionEvent event, String ref) {
        String[] loc = parseRef(event, ref);
        TextChannel channel = event.getGuild().getTextChannelById(loc[0]);
        if (channel == null) {
            Replies.ephemeral(event, ctx, "Canal da mensagem não encontrado.");
            return;
        }
        event.deferReply(true).queue();
        channel.retrieveMessageById(loc[1]).queue(msg -> {
            // The bot's own messages (incl. interaction responses, which carry a webhook_id)
            // are edited via JDA. Only a real channel-webhook message — author != the bot —
            // is edited through the webhook API.
            boolean mine = event.getJDA().getSelfUser().getId().equals(msg.getAuthor().getId());
            boolean webhookMsg = msg.isWebhookMessage() && !mine;
            if (!mine && !webhookMsg) {
                Replies.hook(event, ctx, "Só posso editar mensagens enviadas por mim ou pelo meu webhook.");
                return;
            }
            ObjectNode state = MessageBuilderParse.fromMessage(msg);
            state.put("channelId", channel.getId());
            state.put("editChannelId", channel.getId());
            state.put("editMessageId", msg.getId());
            if (webhookMsg) {
                state.put("editWebhook", true);
                state.put("editWebhookId", msg.getAuthor().getId());
                state.put("webhook", true);
            }
            drafts.save(event.getUser().getId(), event.getGuild().getId(), MessageState.stringify(state));
            event.getHook().sendMessageComponents(MessageBuilderView.panel(state)).useComponentsV2().queue();
        }, err -> Replies.hook(event, ctx, "Mensagem não encontrada nesse canal."));
    }

    private void editExisting(ButtonInteractionEvent event, ObjectNode state) {
        String channelId = MessageState.str(state, "editChannelId");
        String messageId = MessageState.str(state, "editMessageId");
        TextChannel channel = channelId == null ? null : event.getGuild().getTextChannelById(channelId);
        if (channel == null) {
            Replies.ephemeral(event, ctx, "O canal da mensagem não existe mais.");
            return;
        }
        int accent = accent(event);
        boolean container = MessageState.isContainer(state);
        event.deferEdit().queue();

        if (state.path("editWebhook").asBoolean()) {
            String whId = MessageState.str(state, "editWebhookId");
            channel.retrieveWebhooks().submit().thenAcceptAsync(list -> {
                Webhook wh = list.stream()
                        .filter(w -> w.getId().equals(whId) && w.getToken() != null)
                        .findFirst().orElseThrow(() -> new IllegalStateException(
                                "sem acesso ao webhook desta mensagem"));
                WebhookSender.patch(wh.getUrl(), messageId, webhookBody(state, accent, container));
            }, ctx.scheduler().executor())
                    .whenComplete((v, ex) -> finishWebhook(event, channel, ex, true));
            return;
        }
        if (container) {
            channel.editMessageComponentsById(messageId,
                            MessageBuild.jdaContainer(MessageState.container(state), accent))
                    .useComponentsV2().queue(m -> done(event, channel, true), err -> fail(event, err));
        } else {
            var embed = MessageBuild.jdaEmbed(MessageState.classic(state), accent);
            String content = MessageState.str(MessageState.classic(state), "content");
            var action = (content != null && !content.isBlank())
                    ? channel.editMessageById(messageId, content).setEmbeds(embed)
                    : channel.editMessageEmbedsById(messageId, embed);
            action.queue(m -> done(event, channel, true), err -> fail(event, err));
        }
    }

    /** Builds the webhook request body (content/embeds or V2 components) for send + edit. */
    private static ObjectNode webhookBody(ObjectNode state, int accent, boolean container) {
        ObjectNode body = WebhookSender.mapper().createObjectNode();
        if (container) {
            body.put("flags", WebhookSender.IS_COMPONENTS_V2);
            body.set("components", MessageBuild.webhookContainer(MessageState.container(state), accent));
        } else {
            String content = MessageState.str(MessageState.classic(state), "content");
            body.put("content", content == null ? "" : content);
            body.putArray("embeds").add(MessageBuild.webhookEmbed(MessageState.classic(state), accent));
        }
        return body;
    }

    private void finishWebhook(ButtonInteractionEvent event, TextChannel channel, Throwable ex, boolean edit) {
        if (ex != null) {
            Replies.hookEphemeral(event, ctx, "Falha via webhook: " + root(ex)
                    + "\n-# O bot precisa da permissão **Gerenciar Webhooks** no canal.");
        } else {
            done(event, channel, edit);
        }
    }

    private int accent(net.dv8tion.jda.api.interactions.Interaction event) {
        return EmbedColor.resolve(ctx.database().guildConfig().findOrEmpty(event.getGuild().getId()));
    }

    private static String[] parseRef(SlashCommandInteractionEvent event, String ref) {
        String r = ref == null ? "" : ref.trim();
        if (r.contains("/channels/")) {
            String[] p = r.split("/");
            return new String[]{p[p.length - 2], p[p.length - 1]};
        }
        return new String[]{event.getChannelId(), r};
    }

    private CompletableFuture<Webhook> webhook(TextChannel channel) {
        return channel.retrieveWebhooks().submit().thenCompose(list -> list.stream()
                .filter(w -> WEBHOOK_NAME.equals(w.getName()) && w.getToken() != null)
                .findFirst()
                .map(CompletableFuture::completedFuture)
                .orElseGet(() -> channel.createWebhook(WEBHOOK_NAME).reason("/mensagem").submit()));
    }

    private void done(ButtonInteractionEvent event, TextChannel channel, boolean edit) {
        drafts.delete(event.getUser().getId());
        event.getHook().editOriginalComponents(Panels.container(EmbedColor.DEFAULT,
                        Panels.text("" + Emojis.of(Emojis.CHECK_YES, "✅") + " Mensagem " + (edit ? "editada" : "enviada") + " em "
                                + channel.getAsMention() + ".")))
                .useComponentsV2().queue(ok -> {}, e -> {});
    }

    private void fail(ButtonInteractionEvent event, Throwable err) {
        Replies.hookEphemeral(event, ctx, "Falha ao enviar: " + root(err));
    }

    // --- helpers ---------------------------------------------------------------

    private ObjectNode load(String userId) {
        return drafts.find(userId).map(MessageState::parse).orElseGet(MessageState::initial);
    }

    private void saveAndRender(IMessageEditCallback cb, ObjectNode state) {
        drafts.save(userId(cb), guildId(cb), MessageState.stringify(state));
        render(cb, MessageBuilderView.panel(state));
    }

    private void render(IMessageEditCallback cb, net.dv8tion.jda.api.components.container.Container panel) {
        cb.editComponents(panel).useComponentsV2().queue();
    }

    private static void putOrNull(ObjectNode node, String field, String value) {
        if (value == null || value.isBlank()) {
            node.putNull(field);
        } else {
            node.put(field, value);
        }
    }

    private static String value(ModalInteractionEvent event, String key) {
        ModalMapping m = event.getValue(key);
        return m == null ? null : m.getAsString();
    }

    private static String id(String action, String arg) {
        return dev.davimf.basebot.core.component.ComponentId.of(MessageBuilderView.NS, action, arg);
    }

    private static String userId(IMessageEditCallback cb) {
        return ((net.dv8tion.jda.api.interactions.Interaction) cb).getUser().getId();
    }

    private static String guildId(IMessageEditCallback cb) {
        var guild = ((net.dv8tion.jda.api.interactions.Interaction) cb).getGuild();
        return guild == null ? "0" : guild.getId();
    }

    private static String root(Throwable err) {
        Throwable c = err;
        while (c.getCause() != null) {
            c = c.getCause();
        }
        return c.getMessage() == null ? c.getClass().getSimpleName() : c.getMessage();
    }
}
