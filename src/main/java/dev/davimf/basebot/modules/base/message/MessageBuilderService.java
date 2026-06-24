package dev.davimf.basebot.modules.base.message;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.modules.base.message.MessageBuilderView.Field;
import dev.davimf.basebot.util.EmbedColor;
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

    public void onStringSelect(StringSelectInteractionEvent event, String action) {
        ObjectNode state = load(event.getUser().getId());
        switch (action) {
            case "type" -> {
                state.put("type", event.getValues().get(0));
                saveAndRender(event, state);
            }
            case "manage" -> render(event, MessageBuilderView.blockPanel(state,
                    Integer.parseInt(event.getValues().get(0))));
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

    public void onButton(ButtonInteractionEvent event, String action, String arg) {
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
            case "cancel" -> { drafts.delete(event.getUser().getId()); render(event, Panels.container(EmbedColor.DEFAULT, Panels.text("Construtor cancelado."))); }
            case "send" -> send(event);
            default -> { /* not ours */ }
        }
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

    public void onModal(ModalInteractionEvent event, String action, String arg) {
        ObjectNode state = load(event.getUser().getId());
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
        String channelId = MessageState.str(state, "channelId");
        TextChannel channel = channelId == null ? null : event.getGuild().getTextChannelById(channelId);
        if (channel == null) {
            event.reply("Selecione um canal de destino primeiro.").setEphemeral(true).queue();
            return;
        }
        int accent = EmbedColor.resolve(ctx.database().guildConfig().findOrEmpty(event.getGuild().getId()));
        boolean container = MessageState.isContainer(state);
        boolean webhook = state.path("webhook").asBoolean();
        event.deferEdit().queue();

        if (webhook) {
            sendWebhook(event, channel, state, accent, container);
            return;
        }
        if (container) {
            channel.sendMessageComponents(MessageBuild.jdaContainer(MessageState.container(state), accent))
                    .useComponentsV2().queue(m -> done(event, channel), err -> fail(event, err));
        } else {
            var embed = MessageBuild.jdaEmbed(MessageState.classic(state), accent);
            String content = MessageState.str(MessageState.classic(state), "content");
            var action = (content != null && !content.isBlank())
                    ? channel.sendMessage(content).setEmbeds(embed)
                    : channel.sendMessageEmbeds(embed);
            action.queue(m -> done(event, channel), err -> fail(event, err));
        }
    }

    private void sendWebhook(ButtonInteractionEvent event, TextChannel channel, ObjectNode state,
                             int accent, boolean container) {
        String name = MessageState.str(state, "webhookName");
        String avatar = MessageState.str(state, "webhookAvatar");
        webhook(channel).thenAcceptAsync(wh -> {
            ObjectNode body = WebhookSender.mapper().createObjectNode();
            if (container) {
                body.put("flags", WebhookSender.IS_COMPONENTS_V2);
                body.set("components", MessageBuild.webhookContainer(MessageState.container(state), accent));
            } else {
                String content = MessageState.str(MessageState.classic(state), "content");
                if (content != null && !content.isBlank()) {
                    body.put("content", content);
                }
                body.putArray("embeds").add(MessageBuild.webhookEmbed(MessageState.classic(state), accent));
            }
            WebhookSender.post(wh.getUrl(), name, avatar, body);
        }, ctx.scheduler().executor())
                .whenComplete((v, ex) -> {
                    if (ex != null) {
                        event.getHook().sendMessage("Falha ao enviar via webhook: " + root(ex)
                                + "\n-# O bot precisa da permissão **Gerenciar Webhooks** no canal.")
                                .setEphemeral(true).queue();
                    } else {
                        done(event, channel);
                    }
                });
    }

    private CompletableFuture<Webhook> webhook(TextChannel channel) {
        return channel.retrieveWebhooks().submit().thenCompose(list -> list.stream()
                .filter(w -> WEBHOOK_NAME.equals(w.getName()) && w.getToken() != null)
                .findFirst()
                .map(CompletableFuture::completedFuture)
                .orElseGet(() -> channel.createWebhook(WEBHOOK_NAME).reason("/mensagem").submit()));
    }

    private void done(ButtonInteractionEvent event, TextChannel channel) {
        drafts.delete(event.getUser().getId());
        event.getHook().editOriginalComponents(Panels.container(EmbedColor.DEFAULT,
                        Panels.text("✅ Mensagem enviada em " + channel.getAsMention() + ".")))
                .useComponentsV2().queue(ok -> {}, e -> {});
    }

    private void fail(ButtonInteractionEvent event, Throwable err) {
        event.getHook().sendMessage("Falha ao enviar: " + root(err)).setEphemeral(true).queue();
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
