// [OUTLINE START]
// Package: dev.davimf.basebot.util
// 
// Class: ImageMediaTest
// [OUTLINE END]



package dev.davimf.basebot.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ImageMediaTest {

    @Test
    void acceptsSupportedImageContentTypes() {
        assertTrue(ImageMedia.isAllowedContentType("image/png"));
        assertTrue(ImageMedia.isAllowedContentType("image/jpeg"));
        assertTrue(ImageMedia.isAllowedContentType("image/gif"));
        assertTrue(ImageMedia.isAllowedContentType("image/webp"));
    }

    @Test
    void toleratesCharsetParamAndCaseAndWhitespace() {
        assertTrue(ImageMedia.isAllowedContentType("IMAGE/PNG; charset=binary"));
    }

    @Test
    void rejectsNonImageOrNull() {
        assertFalse(ImageMedia.isAllowedContentType("text/html"));
        assertFalse(ImageMedia.isAllowedContentType("application/octet-stream"));
        assertFalse(ImageMedia.isAllowedContentType(null));
    }

    @Test
    void derivesExtensionFromContentType() {
        assertEquals("image.png", ImageMedia.fileName("http://x/y", "image/png"));
        assertEquals("image.gif", ImageMedia.fileName("http://x/y", "image/gif"));
        assertEquals("image.webp", ImageMedia.fileName("http://x/y", "image/webp"));
        assertEquals("image.jpg", ImageMedia.fileName("http://x/y", "image/jpeg"));
        assertEquals("image.png", ImageMedia.fileName("http://x/y", null)); // safe default
    }

    @Test
    void eraseZeroesAndDropsBytes() {
        ImageMedia.Image img = ImageMedia.fromBytes(new byte[]{1, 2, 3}, "image.png");
        img.erase();
        assertNull(img.bytes());
    }
}
