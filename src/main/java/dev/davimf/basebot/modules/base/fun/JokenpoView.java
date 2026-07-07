package dev.davimf.basebot.modules.base.fun;

import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.util.Emojis;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;

public final class JokenpoView {
    public static final String NS = "jkp";

    private JokenpoView() {}

    public static Container challenge(int accent, String challengerMention, String targetMention) {
        return Panels.container(accent,
                Panels.text("## " + Emojis.of(Emojis.GAME, "✊") + " Jokenpô\n" + challengerMention + " desafiou "
                        + targetMention + "!\nOs dois escolhem em segredo:"),
                Panels.divider(),
                ActionRow.of(
                        Button.secondary(ComponentId.of(NS, "pick", "PEDRA"), "Pedra"),
                        Button.secondary(ComponentId.of(NS, "pick", "PAPEL"), "Papel"),
                        Button.secondary(ComponentId.of(NS, "pick", "TESOURA"), "Tesoura")));
    }

    public static Container result(int accent, String line) {
        return Panels.container(accent,
                Panels.text("## " + Emojis.of(Emojis.GAME, "✊") + " Jokenpô — resultado"),
                Panels.divider(),
                Panels.text(line));
    }
}
