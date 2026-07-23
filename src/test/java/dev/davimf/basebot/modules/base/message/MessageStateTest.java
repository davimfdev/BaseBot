package dev.davimf.basebot.modules.base.message;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageStateTest {

    @Test
    void imageBlockHoldsSource() {
        ObjectNode b = MessageState.newImageBlock("https://x/y.png");
        assertEquals(MessageState.BLOCK_IMAGE, MessageState.str(b, "type"));
        assertEquals("https://x/y.png", MessageState.str(b, "src"));
    }

    @Test
    void setTextThumbnailAddsAndClears() {
        ObjectNode t = MessageState.newTextBlock("oi");
        MessageState.setTextThumbnail(t, "https://x/t.png");
        assertEquals("https://x/t.png", MessageState.str(t, "thumbnail"));
        MessageState.setTextThumbnail(t, "  ");
        assertFalse(t.hasNonNull("thumbnail"));
    }

    @Test
    void uploadsAreLazyAndAppendable() {
        ObjectNode s = MessageState.initial();
        MessageState.addUpload(s, "imagem", "https://cdn/a.png");
        assertEquals(1, MessageState.uploads(s).size());
        assertEquals("imagem", MessageState.str((ObjectNode) MessageState.uploads(s).get(0), "label"));
    }

    @Test
    void describeImageAndThumbnailedText() {
        assertTrue(MessageState.describe(MessageState.newImageBlock("u")).startsWith("Imagem"));
        ObjectNode t = MessageState.newTextBlock("oi");
        MessageState.setTextThumbnail(t, "u");
        assertTrue(MessageState.describe(t).contains("thumb"));
    }
}
