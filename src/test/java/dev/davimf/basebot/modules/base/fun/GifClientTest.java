package dev.davimf.basebot.modules.base.fun;

import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class GifClientTest {
    @Test
    void extractsUrlFromNekosBestJson() {
        String json = "{\"results\":[{\"artist_href\":\"a\",\"source_url\":\"s\",\"url\":\"https://nekos.best/x.gif\"}]}";
        assertEquals(Optional.of("https://nekos.best/x.gif"), GifClient.extractUrl(json));
    }

    @Test
    void emptyOnBadJson() {
        assertEquals(Optional.empty(), GifClient.extractUrl("nope"));
        assertEquals(Optional.empty(), GifClient.extractUrl("{\"results\":[]}"));
    }
}
