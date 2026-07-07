package dev.davimf.basebot.modules.base.security;

import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.util.Emojis;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;

/** Painel público de verificação (Segurança · Módulo 3): botão Verificar concede o cargo de membro. */
public final class VerificationView {

    private VerificationView() {}

    public static Container panel(int accent) {
        return Panels.container(accent,
                Panels.text("## " + Emojis.of(Emojis.CHECK_YES, "✅") + " Verificação"),
                Panels.divider(),
                Panels.text("> Clique em **Verificar** para liberar seu acesso ao servidor."),
                ActionRow.of(Button.success(ComponentId.of(AntiRaidService.NS, "verify"), "Verificar")
                        .withEmoji(Emojis.button(Emojis.CHECK_YES))));
    }
}
