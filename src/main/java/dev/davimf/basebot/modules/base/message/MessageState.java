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

    public static void addButton(ObjectNode buttonsBlock, String label, String url) {
        ObjectNode btn = JSON.createObjectNode();
        btn.put("label", label);
        btn.put("url", url);
        ((ArrayNode) buttonsBlock.get("buttons")).add(btn);
    }

    /** Short human label for a block, used in the builder lists. */
    public static String describe(ObjectNode block) {
        return switch (str(block, "type")) {
            case BLOCK_TEXT -> {
                String t = str(block, "text");
                yield "Texto: " + (t == null ? "" : t.replace("\n", " "));
            }
            case BLOCK_BUTTONS -> "Botões (" + ((ArrayNode) block.get("buttons")).size() + ")";
            case BLOCK_SEPARATOR -> "Separador (" + (block.path("divider").asBoolean() ? "com linha" : "sem linha") + ")";
            default -> str(block, "type");
        };
    }
}
