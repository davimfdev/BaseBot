package dev.davimf.basebot.modules.base.message;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageBuildWebhookTest {

    private static ObjectNode containerWith(ObjectNode... blocks) {
        ObjectNode state = MessageState.initial();
        state.put("type", MessageState.CONTAINER);
        for (ObjectNode b : blocks) {
            MessageState.blocks(state).add(b);
        }
        return MessageState.container(state);
    }

    @Test
    void imageBlockBecomesMediaGalleryType12() {
        ArrayNode arr = MessageBuild.webhookContainer(
                containerWith(MessageState.newImageBlock("https://x/y.png")), 0x000000);
        JsonNode comps = arr.get(0).get("components");
        JsonNode gallery = comps.get(0);
        assertEquals(12, gallery.get("type").asInt());
        assertEquals("https://x/y.png",
                gallery.get("items").get(0).get("media").get("url").asText());
    }

    @Test
    void textWithThumbnailBecomesSectionType9WithAccessory11() {
        ObjectNode t = MessageState.newTextBlock("olá");
        MessageState.setTextThumbnail(t, "https://x/t.png");
        ArrayNode arr = MessageBuild.webhookContainer(containerWith(t), 0x000000);
        JsonNode section = arr.get(0).get("components").get(0);
        assertEquals(9, section.get("type").asInt());
        assertEquals(10, section.get("components").get(0).get("type").asInt());
        assertEquals("olá", section.get("components").get(0).get("content").asText());
        assertEquals(11, section.get("accessory").get("type").asInt());
        assertEquals("https://x/t.png", section.get("accessory").get("media").get("url").asText());
    }

    @Test
    void plainTextStaysType10() {
        ArrayNode arr = MessageBuild.webhookContainer(
                containerWith(MessageState.newTextBlock("puro")), 0x000000);
        assertEquals(10, arr.get(0).get("components").get(0).get("type").asInt());
    }
}
