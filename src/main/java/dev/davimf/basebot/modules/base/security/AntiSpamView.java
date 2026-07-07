package dev.davimf.basebot.modules.base.security;

import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.util.Emojis;
import net.dv8tion.jda.api.components.container.Container;

/** Aviso permanente do canal anti-spam: deixa claro que qualquer mensagem = expulsão. */
public final class AntiSpamView {

    private AntiSpamView() {}

    public static Container panel(int accent) {
        return Panels.container(accent,
                Panels.text("## " + Emojis.of(Emojis.WARN, "⚠️") + " Canal monitorado"),
                Panels.divider(),
                Panels.text("**NÃO envie mensagens neste canal.**"),
                Panels.text("> Qualquer mensagem aqui resulta em **expulsão automática** do servidor "
                        + "e remoção das suas mensagens recentes."));
    }
}
