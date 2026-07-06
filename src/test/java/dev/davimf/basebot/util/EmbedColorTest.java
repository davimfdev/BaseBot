// [OUTLINE START]
// Package: dev.davimf.basebot.util
// 
// Class: EmbedColorTest
// [OUTLINE END]



package dev.davimf.basebot.util;

import dev.davimf.basebot.database.model.GuildConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.*;

class EmbedColorTest {

    @Test
    void parsesHexWithVariousPrefixes() {
        assertEquals(0x5865F2, EmbedColor.parse("#5865F2").orElseThrow());
        assertEquals(0x5865F2, EmbedColor.parse("5865F2").orElseThrow());
        assertEquals(0x5865F2, EmbedColor.parse("0x5865F2").orElseThrow());
        assertEquals(0x5865F2, EmbedColor.parse("  #5865f2 ").orElseThrow());
    }

    @Test
    void expandsThreeDigitShorthand() {
        assertEquals(0xFF0000, EmbedColor.parse("f00").orElseThrow());
        assertEquals(0xFFFFFF, EmbedColor.parse("#fff").orElseThrow());
    }

    @Test
    void rejectsInvalid() {
        assertEquals(OptionalInt.empty(), EmbedColor.parse("xyz"));
        assertEquals(OptionalInt.empty(), EmbedColor.parse("12345"));   // 5 digits
        assertEquals(OptionalInt.empty(), EmbedColor.parse(null));
        assertEquals(OptionalInt.empty(), EmbedColor.parse(""));
    }

    @Test
    void hexFormatsBackToString() {
        assertEquals("#5865F2", EmbedColor.hex(0x5865F2));
        assertEquals("#FF0000", EmbedColor.hex(0xFF0000));
    }

    @Test
    void resolveUsesConfiguredColorOrDefault() {
        GuildConfig withColor = new GuildConfig("g", null, null, Map.of(), Map.of(), Map.of(),
                List.of(), Map.of(EmbedColor.SETTING_KEY, "#FF0000"));
        assertEquals(0xFF0000, EmbedColor.resolve(withColor));
        assertEquals(EmbedColor.DEFAULT, EmbedColor.resolve(GuildConfig.empty("g")));
    }
}
