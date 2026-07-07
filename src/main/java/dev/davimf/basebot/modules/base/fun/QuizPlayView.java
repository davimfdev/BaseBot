package dev.davimf.basebot.modules.base.fun;

import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.util.Emojis;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;

import java.util.ArrayList;
import java.util.List;

public final class QuizPlayView {
    public static final String NS = "funquiz";

    private QuizPlayView() {}

    public static Container panel(int accent, String question, List<String> options) {
        List<Button> buttons = new ArrayList<>();
        for (int i = 0; i < options.size(); i++) {
            buttons.add(Button.secondary(ComponentId.of(NS, "ans", String.valueOf(i)), trim(options.get(i))));
        }
        return Panels.container(accent,
                Panels.text("## " + Emojis.of(Emojis.INFO, "❓") + " Quiz\n" + question),
                Panels.divider(),
                ActionRow.of(buttons));
    }

    public static Container answered(int accent, String question, String winnerMention, String correct) {
        return Panels.container(accent,
                Panels.text("## " + Emojis.of(Emojis.CHECK_YES, "🎉") + " Quiz respondido"),
                Panels.divider(),
                Panels.text(question + "\n\n" + winnerMention + " acertou! Resposta: **" + correct + "**"));
    }

    private static String trim(String s) {
        return s.length() > 78 ? s.substring(0, 78) : s;
    }
}
