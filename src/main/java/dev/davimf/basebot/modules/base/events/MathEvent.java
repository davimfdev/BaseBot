package dev.davimf.basebot.modules.base.events;

import java.util.Random;

/** Gera um problema de matemática simples e sua resposta. */
public final class MathEvent {
    public record Problem(String prompt, String answer) {}

    private static final char[] OPS = {'+', '-', '×'};

    private MathEvent() {}

    public static Problem generate(Random r) {
        char op = OPS[r.nextInt(OPS.length)];
        long a = 2 + r.nextInt(11);
        long b = 2 + r.nextInt(11);
        if (op == '-' && b > a) {
            long tmp = a; a = b; b = tmp;
        }
        long ans = compute(a, op, b);
        return new Problem(a + " " + op + " " + b, String.valueOf(ans));
    }

    public static long compute(long a, char op, long b) {
        return switch (op) {
            case '+' -> a + b;
            case '-' -> a - b;
            default -> a * b;
        };
    }
}
