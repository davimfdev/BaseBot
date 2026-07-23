// [OUTLINE START]
// Package: dev.davimf.basebot.modules.base.message
// 
// Class: MessageState
// 
// Constructors:
//   - `Constructor` : `private MessageState()`
// 
// Methods:
//   - `Method` : `public static ObjectNode initial()`
//   - `Method` : `public static ObjectNode parse(String json)`
//   - `Method` : `public static String stringify(ObjectNode state)`
//   - `Method` : `public static ObjectNode classic(ObjectNode state)`
//   - `Method` : `public static ObjectNode container(ObjectNode state)`
//   - `Method` : `public static ArrayNode blocks(ObjectNode state)`
//   - `Method` : `public static String str(ObjectNode obj, String field)`
//   - `Method` : `public static boolean isContainer(ObjectNode state)`
//   - `Method` : `public static ObjectNode newTextBlock(String text)`
//   - `Method` : `public static ObjectNode newButtonsBlock()`
//   - `Method` : `public static ObjectNode newSeparatorBlock(boolean divider)`
//   - `Method` : `public static boolean isInteraction(ObjectNode button)`
//   - `Method` : `public static ArrayNode buttons(ObjectNode buttonsBlock)`
//   - `Method` : `public static String describeButton(ObjectNode button)`
//   - `Method` : `public static String describe(ObjectNode block)`
// 
// Fields:
//   - `Field` : `public static final String CLASSIC`
//   - `Field` : `public static final String CONTAINER`
//   - `Field` : `public static final String BLOCK_TEXT`
//   - `Field` : `public static final String BLOCK_BUTTONS`
//   - `Field` : `public static final String BLOCK_SEPARATOR`
//   - `Field` : `public static final String[] STYLES`
// [OUTLINE END]



package dev.davimf.basebot.modules.base.message;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * The interactive message builder's state (BOTSPECS Module 1 — /mensagem), stored as a
 * JSON blob per user. Either a classic embed (typed fields) or a Components V2 container
 * (an ordered list of blocks: text, link buttons, separators). Helpers keep the JSON
 * shape in one place.
 */
public final class MessageState {

    public static final String CLASSIC = "CLASSIC";
    public static final String CONTAINER = "CONTAINER";

    public static final String BLOCK_TEXT = "TEXT";
    public static final String BLOCK_BUTTONS = "BUTTONS";
    public static final String BLOCK_SEPARATOR = "SEPARATOR";
    public static final String BLOCK_IMAGE = "IMAGE";

    private static final ObjectMapper JSON = new ObjectMapper();

    private MessageState() {}

    public static ObjectNode initial() {
        ObjectNode root = JSON.createObjectNode();
        root.put("type", CLASSIC);
        root.put("webhook", false);
        root.putNull("webhookName");
        root.putNull("webhookAvatar");
        root.putNull("channelId");
        ObjectNode classic = root.putObject("classic");
        for (String f : new String[]{"content", "title", "author", "description", "image", "thumbnail", "footer"}) {
            classic.putNull(f);
        }
        classic.putNull("color");
        ObjectNode container = root.putObject("container");
        container.putNull("color");
        container.putArray("blocks");
        return root;
    }

    public static ObjectNode parse(String json) {
        try {
            return (ObjectNode) JSON.readTree(json);
        } catch (Exception e) {
            return initial();
        }
    }

    public static String stringify(ObjectNode state) {
        return state.toString();
    }

    public static ObjectNode classic(ObjectNode state) {
        return (ObjectNode) state.get("classic");
    }

    public static ObjectNode container(ObjectNode state) {
        return (ObjectNode) state.get("container");
    }

    public static ArrayNode blocks(ObjectNode state) {
        return (ArrayNode) container(state).get("blocks");
    }

    /** Reads a possibly-null string field; returns null when absent/json-null. */
    public static String str(ObjectNode obj, String field) {
        return obj.hasNonNull(field) ? obj.get(field).asText() : null;
    }

    public static boolean isContainer(ObjectNode state) {
        return CONTAINER.equals(str(state, "type"));
    }

    public static ObjectNode newTextBlock(String text) {
        ObjectNode b = JSON.createObjectNode();
        b.put("type", BLOCK_TEXT);
        b.put("text", text);
        return b;
    }

    public static ObjectNode newButtonsBlock() {
        ObjectNode b = JSON.createObjectNode();
        b.put("type", BLOCK_BUTTONS);
        b.putArray("buttons");
        return b;
    }

    public static ObjectNode newSeparatorBlock(boolean divider) {
        ObjectNode b = JSON.createObjectNode();
        b.put("type", BLOCK_SEPARATOR);
        b.put("divider", divider);
        return b;
    }

    public static ObjectNode newImageBlock(String src) {
        ObjectNode b = JSON.createObjectNode();
        b.put("type", BLOCK_IMAGE);
        b.put("src", src == null ? "" : src);
        return b;
    }

    /** Define/limpa a thumbnail (acessório de Section) de um bloco de texto. */
    public static void setTextThumbnail(ObjectNode textBlock, String src) {
        if (src == null || src.isBlank()) {
            textBlock.remove("thumbnail");
        } else {
            textBlock.put("thumbnail", src);
        }
    }

    /** Uploads da sessão do builder (URLs de anexos do comando). Criado lazy. */
    public static ArrayNode uploads(ObjectNode state) {
        if (!state.has("uploads") || !state.get("uploads").isArray()) {
            state.putArray("uploads");
        }
        return (ArrayNode) state.get("uploads");
    }

    public static void addUpload(ObjectNode state, String label, String url) {
        ObjectNode u = JSON.createObjectNode();
        u.put("label", label);
        u.put("url", url);
        uploads(state).add(u);
    }

    /** Interaction button styles (colours) a "normal" button can cycle through. */
    public static final String[] STYLES = {"PRIMARY", "SECONDARY", "SUCCESS", "DANGER"};

    /** A link button (style LINK), built by the user from scratch. */
    public static void addButton(ObjectNode buttonsBlock, String label, String url) {
        ObjectNode btn = JSON.createObjectNode();
        btn.put("label", label);
        btn.put("url", url);
        ((ArrayNode) buttonsBlock.get("buttons")).add(btn);
    }

    /** An interaction button (preserved from an edited message — its customId is never changed). */
    public static void addInteractionButton(ObjectNode buttonsBlock, String label, String customId, String style) {
        ObjectNode btn = JSON.createObjectNode();
        btn.put("label", label == null ? "" : label);
        btn.put("customId", customId);
        btn.put("style", style);
        ((ArrayNode) buttonsBlock.get("buttons")).add(btn);
    }

    /** True for an interaction (non-link) button — has a customId, editable colour/label only. */
    public static boolean isInteraction(ObjectNode button) {
        return button.hasNonNull("customId");
    }

    public static ArrayNode buttons(ObjectNode buttonsBlock) {
        return (ArrayNode) buttonsBlock.get("buttons");
    }

    /** Short label for a button in the management lists. */
    public static String describeButton(ObjectNode button) {
        String label = str(button, "label");
        String shown = label == null || label.isBlank() ? "(sem texto)" : label;
        if (isInteraction(button)) {
            return shown + " · " + str(button, "style");
        }
        return shown + " · link";
    }

    private static String trimForDescribe(String s) {
        return s.length() > 60 ? s.substring(0, 59) + "…" : s;
    }

    /** Short human label for a block, used in the builder lists. */
    public static String describe(ObjectNode block) {
        return switch (str(block, "type")) {
            case BLOCK_TEXT -> {
                String t = str(block, "text");
                String base = "Texto: " + (t == null ? "" : t.replace("\n", " "));
                yield block.hasNonNull("thumbnail") ? base + " · com thumb" : base;
            }
            case BLOCK_IMAGE -> {
                String s = str(block, "src");
                yield "Imagem: " + (s == null || s.isBlank() ? "(sem fonte)" : trimForDescribe(s));
            }
            case BLOCK_BUTTONS -> "Botões (" + ((ArrayNode) block.get("buttons")).size() + ")";
            case BLOCK_SEPARATOR -> "Separador (" + (block.path("divider").asBoolean() ? "com linha" : "sem linha") + ")";
            default -> str(block, "type");
        };
    }
}
