// [OUTLINE START]
// Package: dev.davimf.basebot.modules.base.message
// 
// Class: MessageBuild
// 
// Constructors:
//   - `Constructor` : `private MessageBuild()`
// 
// Methods:
//   - `Method` : `public static MessageEmbed jdaEmbed(ObjectNode classic, int defaultAccent)`
//   - `Method` : `public static Container jdaContainer(ObjectNode container, int defaultAccent)`
//   - `Method` : `public static ObjectNode webhookEmbed(ObjectNode classic, int defaultAccent)`
//   - `Method` : `public static ArrayNode webhookContainer(ObjectNode container, int defaultAccent)`
//   - `Method` : `private static ButtonStyle buttonStyle(String name)`
//   - `Method` : `private static int styleNumber(String name)`
//   - `Method` : `private static boolean valid(String label, String url)`
// [OUTLINE END]



package dev.davimf.basebot.modules.base.message;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.util.EmbedColor;
import dev.davimf.basebot.util.WebhookSender;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.entities.MessageEmbed;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders a {@link MessageState} into the actual message — both the JDA objects for a
 * normal send and the raw JSON for a webhook send (BOTSPECS Module 1 — /mensagem).
 */
public final class MessageBuild {

    private MessageBuild() {}

    // --- JDA (normal send) -----------------------------------------------------

    public static MessageEmbed jdaEmbed(ObjectNode classic, int defaultAccent) {
        EmbedBuilder eb = new EmbedBuilder();
        eb.setColor(EmbedColor.parse(MessageState.str(classic, "color")).orElse(defaultAccent));
        set(MessageState.str(classic, "title"), eb::setTitle);
        set(MessageState.str(classic, "description"), eb::setDescription);
        set(MessageState.str(classic, "author"), n -> eb.setAuthor(n));
        set(MessageState.str(classic, "image"), eb::setImage);
        set(MessageState.str(classic, "thumbnail"), eb::setThumbnail);
        set(MessageState.str(classic, "footer"), f -> eb.setFooter(f));
        return eb.build();
    }

    public static Container jdaContainer(ObjectNode container, int defaultAccent) {
        int accent = EmbedColor.parse(MessageState.str(container, "color")).orElse(defaultAccent);
        List<ContainerChildComponent> kids = new ArrayList<>();
        for (var node : (ArrayNode) container.get("blocks")) {
            ObjectNode b = (ObjectNode) node;
            switch (MessageState.str(b, "type")) {
                case MessageState.BLOCK_TEXT -> {
                    String t = MessageState.str(b, "text");
                    if (t != null && !t.isBlank()) {
                        kids.add(Panels.text(t));
                    }
                }
                case MessageState.BLOCK_BUTTONS -> {
                    List<Button> buttons = new ArrayList<>();
                    for (var bn : (ArrayNode) b.get("buttons")) {
                        ObjectNode btn = (ObjectNode) bn;
                        String label = MessageState.str(btn, "label");
                        if (MessageState.isInteraction(btn)) {
                            buttons.add(Button.of(buttonStyle(MessageState.str(btn, "style")),
                                    MessageState.str(btn, "customId"), label == null ? "" : label));
                        } else {
                            String url = MessageState.str(btn, "url");
                            if (valid(label, url)) {
                                buttons.add(Button.link(url, label));
                            }
                        }
                    }
                    if (!buttons.isEmpty()) {
                        kids.add(ActionRow.of(buttons));
                    }
                }
                case MessageState.BLOCK_SEPARATOR -> kids.add(b.path("divider").asBoolean()
                        ? Separator.createDivider(Separator.Spacing.SMALL)
                        : Separator.create(false, Separator.Spacing.SMALL));
                default -> { /* unknown block */ }
            }
        }
        if (kids.isEmpty()) {
            kids.add(Panels.text("​")); // containers need at least one child
        }
        return Panels.container(accent, kids.toArray(new ContainerChildComponent[0]));
    }

    // --- Webhook (raw JSON) ----------------------------------------------------

    public static ObjectNode webhookEmbed(ObjectNode classic, int defaultAccent) {
        ObjectNode e = WebhookSender.mapper().createObjectNode();
        e.put("color", EmbedColor.parse(MessageState.str(classic, "color")).orElse(defaultAccent) & 0xFFFFFF);
        putIf(e, "title", MessageState.str(classic, "title"));
        putIf(e, "description", MessageState.str(classic, "description"));
        String author = MessageState.str(classic, "author");
        if (author != null && !author.isBlank()) {
            e.putObject("author").put("name", author);
        }
        String footer = MessageState.str(classic, "footer");
        if (footer != null && !footer.isBlank()) {
            e.putObject("footer").put("text", footer);
        }
        String image = MessageState.str(classic, "image");
        if (image != null && !image.isBlank()) {
            e.putObject("image").put("url", image);
        }
        String thumb = MessageState.str(classic, "thumbnail");
        if (thumb != null && !thumb.isBlank()) {
            e.putObject("thumbnail").put("url", thumb);
        }
        return e;
    }

    /** A one-element components array holding the Container V2 (Discord component type 17). */
    public static ArrayNode webhookContainer(ObjectNode container, int defaultAccent) {
        int accent = EmbedColor.parse(MessageState.str(container, "color")).orElse(defaultAccent);
        ObjectNode c = WebhookSender.mapper().createObjectNode();
        c.put("type", 17);
        c.put("accent_color", accent & 0xFFFFFF);
        ArrayNode children = c.putArray("components");
        for (var node : (ArrayNode) container.get("blocks")) {
            ObjectNode b = (ObjectNode) node;
            switch (MessageState.str(b, "type")) {
                case MessageState.BLOCK_TEXT -> {
                    String t = MessageState.str(b, "text");
                    if (t != null && !t.isBlank()) {
                        children.addObject().put("type", 10).put("content", t);
                    }
                }
                case MessageState.BLOCK_BUTTONS -> {
                    ArrayNode row = WebhookSender.mapper().createArrayNode();
                    for (var bn : (ArrayNode) b.get("buttons")) {
                        ObjectNode src = (ObjectNode) bn;
                        String label = MessageState.str(src, "label");
                        if (MessageState.isInteraction(src)) {
                            ObjectNode btn = row.addObject();
                            btn.put("type", 2);
                            btn.put("style", styleNumber(MessageState.str(src, "style")));
                            btn.put("label", label == null ? "" : label);
                            btn.put("custom_id", MessageState.str(src, "customId"));
                        } else {
                            String url = MessageState.str(src, "url");
                            if (valid(label, url)) {
                                ObjectNode btn = row.addObject();
                                btn.put("type", 2);
                                btn.put("style", 5);
                                btn.put("label", label);
                                btn.put("url", url);
                            }
                        }
                    }
                    if (!row.isEmpty()) {
                        ObjectNode ar = children.addObject();
                        ar.put("type", 1);
                        ar.set("components", row);
                    }
                }
                case MessageState.BLOCK_SEPARATOR -> children.addObject()
                        .put("type", 14)
                        .put("divider", b.path("divider").asBoolean())
                        .put("spacing", 1);
                default -> { /* unknown */ }
            }
        }
        ArrayNode arr = WebhookSender.mapper().createArrayNode();
        arr.add(c);
        return arr;
    }

    // --- helpers ---------------------------------------------------------------

    private static ButtonStyle buttonStyle(String name) {
        try {
            return name == null ? ButtonStyle.PRIMARY : ButtonStyle.valueOf(name);
        } catch (IllegalArgumentException e) {
            return ButtonStyle.PRIMARY;
        }
    }

    private static int styleNumber(String name) {
        return switch (name == null ? "PRIMARY" : name) {
            case "SECONDARY" -> 2;
            case "SUCCESS" -> 3;
            case "DANGER" -> 4;
            default -> 1; // PRIMARY
        };
    }

    private static boolean valid(String label, String url) {
        return label != null && !label.isBlank() && url != null
                && (url.startsWith("http://") || url.startsWith("https://"));
    }

    private static void set(String value, java.util.function.Consumer<String> setter) {
        if (value != null && !value.isBlank()) {
            setter.accept(value);
        }
    }

    private static void putIf(ObjectNode node, String field, String value) {
        if (value != null && !value.isBlank()) {
            node.put(field, value);
        }
    }
}
