package dev.davimf.basebot.modules.base.fun;

import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.util.Emojis;
import net.dv8tion.jda.api.components.container.Container;

public final class ForcaView {
    private ForcaView() {}

    public static Container panel(int accent, HangmanState s, String theme) {
        String body = "## " + Emojis.of(Emojis.GAME, "🔤") + " Forca\n"
                + Emojis.of(Emojis.INFO, "💡") + " Tema · **" + theme + "**\n"
                + "`" + s.masked() + "`\n"
                + Emojis.of(Emojis.HEART, "❤️") + " Vidas: `" + s.lives() + "/" + HangmanState.MAX_LIVES + "`\n"
                + "-# Digite uma **letra** ou a **palavra inteira** no chat.";
        return Panels.container(accent, Panels.text(body));
    }

    public static Container ended(int accent, HangmanState s, boolean won) {
        String head = won ? Emojis.of(Emojis.CHECK_YES, "🎉") + " Acertaram!"
                : Emojis.of(Emojis.CHECK_NO, "💀") + " Fim de jogo!";
        return Panels.container(accent,
                Panels.text("## " + head),
                Panels.divider(),
                Panels.text("A palavra era: **" + s.word() + "**"));
    }
}
