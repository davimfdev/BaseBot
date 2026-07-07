// [OUTLINE START]
// Package: dev.davimf.basebot.config
// 
// Class: DotEnvTest
// [OUTLINE END]



package dev.davimf.basebot.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DotEnvTest {

    @Test
    void parsesKeyValueIgnoringCommentsAndBlanks() {
        Map<String, String> m = DotEnv.parse("""
                # comment
                BOT_TOKEN=abc123

                POSTGRES_USER=basebot
                """);
        assertEquals("abc123", m.get("BOT_TOKEN"));
        assertEquals("basebot", m.get("POSTGRES_USER"));
        assertEquals(2, m.size());
    }

    @Test
    void stripsQuotesAndExportPrefixAndKeepsInnerEquals() {
        Map<String, String> m = DotEnv.parse("""
                export TICKET_VIEW_BASE="https://davimf.dev/ticket"
                POSTGRES_URL=jdbc:postgresql://h/db?sslmode=require
                EMPTY=''
                """);
        assertEquals("https://davimf.dev/ticket", m.get("TICKET_VIEW_BASE"));
        assertEquals("jdbc:postgresql://h/db?sslmode=require", m.get("POSTGRES_URL"));
        assertEquals("", m.get("EMPTY"));
    }

    @Test
    void getReturnsFileValueWhenNotInProcessEnv(@TempDir Path dir) throws Exception {
        Path f = dir.resolve(".env");
        Files.writeString(f, "BASEBOT_UNLIKELY_KEY_42=hello\n");
        assertEquals("hello", DotEnv.load(f).get("BASEBOT_UNLIKELY_KEY_42"));
    }

    @Test
    void loadMissingFileYieldsEmptyLookup(@TempDir Path dir) {
        assertNull(DotEnv.load(dir.resolve("nope.env")).get("BASEBOT_UNLIKELY_KEY_42"));
    }
}
