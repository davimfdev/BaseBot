package dev.davimf.basebot.modules.base.economy;

import dev.davimf.basebot.database.model.GuildConfig;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class EconomyFormatTest {
    private static GuildConfig cfg(Map<String, String> settings) {
        return new GuildConfig("g1", null, null, Map.of(), Map.of(), Map.of(), List.of(), settings);
    }

    @Test
    void groupsThousandsBr() {
        GuildConfig c = cfg(Map.of(EconomyConfig.KEY_CURRENCY_EMOJI, "$"));
        assertEquals("$ 1.234", EconomyFormat.format(1234, c));
        assertEquals("$ 0", EconomyFormat.format(0, c));
    }

    @Test
    void namedAppendsCurrencyName() {
        GuildConfig c = cfg(Map.of(EconomyConfig.KEY_CURRENCY_EMOJI, "$", EconomyConfig.KEY_CURRENCY_NAME, "dols"));
        assertEquals("$ 1.234 dols", EconomyFormat.formatNamed(1234, c));
    }
}
