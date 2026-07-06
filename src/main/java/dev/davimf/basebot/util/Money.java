// [OUTLINE START]
// Package: dev.davimf.basebot.util
// 
// Class: Money
// 
// Constructors:
//   - `Constructor` : `private Money()`
// 
// Methods:
//   - `Method` : `public static OptionalLong parse(String input)`
//   - `Method` : `public static String format(long cents)`
// [OUTLINE END]



package dev.davimf.basebot.util;

import java.util.OptionalLong;

/**
 * Parses and formats Brazilian Real money values as integer cents. Accepts the common
 * ways a user types a price ({@code 10}, {@code 10,50}, {@code R$ 1.234,56},
 * {@code 10.50}) and renders the canonical {@code R$ 1.234,56} form.
 */
public final class Money {

    private Money() {}

    /** Parses a BRL string to cents. The last {@code ,} or {@code .} is the decimal mark. */
    public static OptionalLong parse(String input) {
        if (input == null) {
            return OptionalLong.empty();
        }
        String x = input.replaceAll("[^0-9.,]", "");
        if (x.isEmpty()) {
            return OptionalLong.empty();
        }
        int sep = Math.max(x.lastIndexOf(','), x.lastIndexOf('.'));
        String intPart;
        String fracPart;
        if (sep >= 0) {
            String frac = x.substring(sep + 1);
            if (frac.length() == 1 || frac.length() == 2) {
                // Trailing 1-2 digits → decimal part; strip any grouping marks from the rest.
                intPart = x.substring(0, sep).replaceAll("[.,]", "");
                fracPart = frac;
            } else {
                // 3+ trailing digits → that separator was a thousands mark, not a decimal.
                intPart = x.replaceAll("[.,]", "");
                fracPart = "";
            }
        } else {
            intPart = x;
            fracPart = "";
        }
        if (intPart.isEmpty()) {
            intPart = "0";
        }
        try {
            long cents = Long.parseLong(intPart) * 100;
            if (fracPart.length() == 1) {
                cents += Long.parseLong(fracPart) * 10;
            } else if (fracPart.length() >= 2) {
                cents += Long.parseLong(fracPart.substring(0, 2));
            }
            return OptionalLong.of(cents);
        } catch (NumberFormatException e) {
            return OptionalLong.empty();
        }
    }

    /** Formats cents as {@code R$ 1.234,56} (BR grouping + decimal marks). */
    public static String format(long cents) {
        boolean negative = cents < 0;
        long abs = Math.abs(cents);
        long reais = abs / 100;
        long centavos = abs % 100;
        String grouped = String.format("%,d", reais).replace(',', '.');
        return "R$ " + (negative ? "-" : "") + grouped + "," + String.format("%02d", centavos);
    }
}
