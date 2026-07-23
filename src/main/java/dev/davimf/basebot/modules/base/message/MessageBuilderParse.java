// [OUTLINE START]
// Package: dev.davimf.basebot.modules.base.message
// 
// Class: MessageBuilderParse
// 
// Constructors:
//   - `Constructor` : `private MessageBuilderParse()`
// 
// Methods:
//   - `Method` : `public static ObjectNode fromMessage(Message message)`
// [OUTLINE END]



package dev.davimf.basebot.modules.base.message;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.davimf.basebot.util.EmbedColor;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;

/**
 * Reads an existing message back into a {@link MessageState} so {@code /mensagem editar}
 * can load it into the builder. Handles the two shapes the builder produces: a classic
 * embed (+ content) or a Components V2 container of text/buttons/separator blocks.
 */
public final class MessageBuilderParse {

    private MessageBuilderParse() {}

    public static ObjectNode fromMessage(Message message) {
        ObjectNode state = MessageState.initial();
        Container container = message.getComponents().stream()
                .filter(c -> c instanceof Container).map(c -> (Container) c)
                .findFirst().orElse(null);
        if (container != null) {
            parseContainer(state, container);
        } else {
            parseClassic(state, message);
        }
        return state;
    }

    private static void parseContainer(ObjectNode state, Container container) {
        state.put("type", MessageState.CONTAINER);
        ObjectNode c = MessageState.container(state);
        java.awt.Color accent = container.getAccentColor();
        if (accent != null) {
            c.put("color", EmbedColor.hex(accent.getRGB()));
        }
        ArrayNode blocks = MessageState.blocks(state);
        container.getComponents().forEach(child -> {
            if (child instanceof net.dv8tion.jda.api.components.mediagallery.MediaGallery gal) {
                if (!gal.getItems().isEmpty()) {
                    blocks.add(MessageState.newImageBlock(gal.getItems().get(0).getUrl()));
                }
                return;
            }
            if (child instanceof net.dv8tion.jda.api.components.section.Section sec) {
                String text = sec.getContentComponents().stream()
                        .filter(cc -> cc instanceof net.dv8tion.jda.api.components.textdisplay.TextDisplay)
                        .map(cc -> ((net.dv8tion.jda.api.components.textdisplay.TextDisplay) cc).getContent())
                        .findFirst().orElse("");
                ObjectNode tb = MessageState.newTextBlock(text);
                var acc = sec.getAccessory();
                if (acc instanceof net.dv8tion.jda.api.components.thumbnail.Thumbnail th) {
                    String url = th.getUrl();
                    if (url != null && !url.startsWith("attachment://")) {
                        MessageState.setTextThumbnail(tb, url);
                    }
                }
                blocks.add(tb);
                return;
            }
            if (child instanceof TextDisplay td) {
                blocks.add(MessageState.newTextBlock(td.getContent()));
            } else if (child instanceof ActionRow row) {
                ObjectNode block = MessageState.newButtonsBlock();
                boolean any = false;
                for (Button b : row.getButtons()) {
                    if (b.getStyle() == ButtonStyle.LINK && b.getUrl() != null) {
                        MessageState.addButton(block, b.getLabel(), b.getUrl());
                        any = true;
                    } else if (b.getCustomId() != null) {
                        // Interaction button: keep its custom id so it stays wired up.
                        MessageState.addInteractionButton(block, b.getLabel(), b.getCustomId(), b.getStyle().name());
                        any = true;
                    }
                }
                if (any) {
                    blocks.add(block);
                }
            } else if (child instanceof Separator sep) {
                blocks.add(MessageState.newSeparatorBlock(sep.isDivider()));
            }
        });
    }

    private static void parseClassic(ObjectNode state, Message message) {
        state.put("type", MessageState.CLASSIC);
        ObjectNode classic = MessageState.classic(state);
        if (!message.getContentRaw().isBlank()) {
            classic.put("content", message.getContentRaw());
        }
        if (message.getEmbeds().isEmpty()) {
            return;
        }
        MessageEmbed e = message.getEmbeds().get(0);
        put(classic, "title", e.getTitle());
        put(classic, "description", e.getDescription());
        if (e.getAuthor() != null) {
            put(classic, "author", e.getAuthor().getName());
        }
        if (e.getFooter() != null) {
            put(classic, "footer", e.getFooter().getText());
        }
        if (e.getImage() != null) {
            put(classic, "image", e.getImage().getUrl());
        }
        if (e.getThumbnail() != null) {
            put(classic, "thumbnail", e.getThumbnail().getUrl());
        }
        if (e.getColor() != null) {
            classic.put("color", EmbedColor.hex(e.getColorRaw()));
        }
    }

    private static void put(ObjectNode node, String field, String value) {
        if (value != null && !value.isBlank()) {
            node.put(field, value);
        }
    }
}
