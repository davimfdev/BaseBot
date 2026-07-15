package dev.davimf.basebot.modules.base.events;

import java.util.Random;

/**
 * Gera um problema de matemática desafiador e sua resposta.
 * Sempre resulta em um inteiro não-negativo (respondível digitando no chat).
 */
public final class MathEvent {
    public record Problem(String prompt, String answer) {}

    private MathEvent() {}

    public static Problem generate(Random r) {
        return switch (r.nextInt(6)) {
            case 0 -> {                                    // a × b + c × d
                long a = rng(r, 11, 29), b = rng(r, 11, 29);
                long c = rng(r, 6, 19), d = rng(r, 6, 19);
                yield problem(a + " × " + b + " + " + c + " × " + d, a * b + c * d);
            }
            case 1 -> {                                    // (a + b) × c − d
                long a = rng(r, 20, 60), b = rng(r, 20, 60);
                long c = rng(r, 3, 9), d = rng(r, 5, 40);
                yield problem("(" + a + " + " + b + ") × " + c + " − " + d, (a + b) * c - d);
            }
            case 2 -> {                                    // a × b − c × d (maior menos menor)
                long a = rng(r, 15, 40), b = rng(r, 11, 25);
                long c = rng(r, 6, 18), d = rng(r, 4, 14);
                long p1 = a * b, p2 = c * d;
                String left = a + " × " + b, right = c + " × " + d;
                if (p2 > p1) {
                    long t = p1; p1 = p2; p2 = t;
                    String ts = left; left = right; right = ts;
                }
                yield problem(left + " − " + right, p1 - p2);
            }
            case 3 -> {                                    // a × b (multiplicação "feia")
                long a = rng(r, 23, 98), b = rng(r, 12, 49);
                yield problem(a + " × " + b, a * b);
            }
            case 4 -> {                                    // a² + b
                long a = rng(r, 13, 39), b = rng(r, 10, 99);
                yield problem(a + "² + " + b, a * a + b);
            }
            default -> {                                   // a × b + c
                long a = rng(r, 20, 80), b = rng(r, 11, 40), c = rng(r, 50, 300);
                yield problem(a + " × " + b + " + " + c, a * b + c);
            }
        };
    }

    private static Problem problem(String prompt, long answer) {
        return new Problem(prompt, String.valueOf(answer));
    }

    /** Inteiro aleatório em [min, max]. */
    private static long rng(Random r, int min, int max) {
        return min + r.nextInt(max - min + 1);
    }

    /** Operação binária simples (mantida para compatibilidade e testes). */
    public static long compute(long a, char op, long b) {
        return switch (op) {
            case '+' -> a + b;
            case '-' -> a - b;
            default -> a * b;
        };
    }
}
