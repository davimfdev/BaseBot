package dev.davimf.basebot.modules.base.message;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MessageRehostTest {

    @Test
    void collectsClassicImageAndThumbnailDistinct() {
        ObjectNode s = MessageState.initial();
        MessageState.classic(s).put("image", "https://a.png");
        MessageState.classic(s).put("thumbnail", "https://a.png"); // igual -> dedup
        assertEquals(List.of("https://a.png"), MessageRehost.collectSources(s));
    }

    @Test
    void collectsContainerImageBlocksAndTextThumbnailsDedup() {
        ObjectNode s = MessageState.initial();
        s.put("type", MessageState.CONTAINER);
        MessageState.blocks(s).add(MessageState.newImageBlock("https://img1.png"));
        MessageState.blocks(s).add(MessageState.newImageBlock("https://img1.png")); // dup
        ObjectNode t = MessageState.newTextBlock("oi");
        MessageState.setTextThumbnail(t, "https://thumb.png");
        MessageState.blocks(s).add(t);
        assertEquals(List.of("https://img1.png", "https://thumb.png"), MessageRehost.collectSources(s));
    }

    @Test
    void ignoresBlankSources() {
        ObjectNode s = MessageState.initial();
        MessageState.classic(s).put("image", "   ");
        assertEquals(List.of(), MessageRehost.collectSources(s));
    }
}
