// [OUTLINE START]
// Package: dev.davimf.basebot.util
// 
// Class: MoneyTest
// [OUTLINE END]



package dev.davimf.basebot.util;

import org.junit.jupiter.api.Test;

import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MoneyTest {

    @Test
    void parsesPlainInteger() {
        assertEquals(1000_00L, Money.parse("1000").orElseThrow());
        assertEquals(10_00L, Money.parse("10").orElseThrow());
    }

    @Test
    void parsesBrazilianDecimal() {
        assertEquals(10_50L, Money.parse("10,50").orElseThrow());
        assertEquals(1_234_56L, Money.parse("1.234,56").orElseThrow());
        assertEquals(1_500_00L, Money.parse("R$ 1.500,00").orElseThrow());
    }

    @Test
    void parsesDotDecimal() {
        assertEquals(10_50L, Money.parse("10.50").orElseThrow());
    }

    @Test
    void treatsThreeTrailingDigitsAsGrouping() {
        // "1.500" is one thousand five hundred reais, not 1 real 50 cents.
        assertEquals(1_500_00L, Money.parse("1.500").orElseThrow());
    }

    @Test
    void singleDecimalDigitScalesToCents() {
        assertEquals(10_50L, Money.parse("10,5").orElseThrow());
    }

    @Test
    void rejectsNonNumeric() {
        assertEquals(OptionalLong.empty(), Money.parse("abc"));
        assertEquals(OptionalLong.empty(), Money.parse(""));
        assertEquals(OptionalLong.empty(), Money.parse(null));
    }

    @Test
    void formatsBrazilianStyle() {
        assertEquals("R$ 10,50", Money.format(10_50L));
        assertEquals("R$ 1.234,56", Money.format(1_234_56L));
        assertEquals("R$ 0,00", Money.format(0L));
        assertEquals("R$ 1.500,00", Money.format(1_500_00L));
    }
}
