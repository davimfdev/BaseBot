package dev.davimf.basebot.modules.base.fun;

/** Lógica pura do jokenpo. */
public final class JokenpoResult {

    public enum Choice { PEDRA, PAPEL, TESOURA }
    public enum Outcome { WIN_A, WIN_B, TIE }

    private JokenpoResult() {}

    public static Outcome decide(Choice a, Choice b) {
        if (a == b) {
            return Outcome.TIE;
        }
        boolean aWins = (a == Choice.PEDRA && b == Choice.TESOURA)
                || (a == Choice.PAPEL && b == Choice.PEDRA)
                || (a == Choice.TESOURA && b == Choice.PAPEL);
        return aWins ? Outcome.WIN_A : Outcome.WIN_B;
    }
}
