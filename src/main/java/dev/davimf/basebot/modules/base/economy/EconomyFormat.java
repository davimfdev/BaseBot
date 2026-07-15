package dev.davimf.basebot.modules.base.economy;

import dev.davimf.basebot.database.model.GuildConfig;

/** Formata quantias da economia: emoji + número agrupado (BR) + nome opcional. Puro. */
public final class EconomyFormat {

    private EconomyFormat() {}

    public static String format(long amount, GuildConfig cfg) {
        return EconomyConfig.currencyEmoji(cfg) + " " + grouped(amount);
    }

    public static String formatNamed(long amount, GuildConfig cfg) {
        return format(amount, cfg) + " " + EconomyConfig.currencyName(cfg);
    }

    /** Como {@link #format}, mas seguro para textos que não renderizam emoji custom
     *  (ex.: descrições de opções de select menu): usa o fallback Unicode da moeda. */
    public static String formatPlain(long amount, GuildConfig cfg) {
        return EconomyConfig.currencyEmojiPlain(cfg) + " " + grouped(amount);
    }

    private static String grouped(long amount) {
        boolean neg = amount < 0;
        String digits = String.format("%,d", Math.abs(amount)).replace(',', '.');
        return (neg ? "-" : "") + digits;
    }
}
