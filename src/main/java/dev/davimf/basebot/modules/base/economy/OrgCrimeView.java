package dev.davimf.basebot.modules.base.economy;

import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.util.Emojis;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;

import java.util.ArrayList;
import java.util.List;

/** Render dos painéis do crime organizado (namespace "org"). */
public final class OrgCrimeView {

    public static final String NS = "org";

    private OrgCrimeView() {}

    /** Painel público do lobby: contagem de participantes + botões Entrar/Iniciar. */
    public static Container panel(int accent, OrgCrimeService.Lobby lobby, GuildConfig cfg) {
        StringBuilder head = new StringBuilder("## " + Emojis.of(Emojis.MONEY, "💰") + " Crime Organizado\n");
        head.append(Emojis.of(Emojis.SALES, "🛒")).append(" Participantes: `")
                .append(lobby.participants().size()).append("/").append(EconomyDefaults.ORG_MAX).append("`\n");
        head.append("Mín. `").append(EconomyDefaults.ORG_MIN).append("` armados pra iniciar. Toda a arma equipada dos "
                + "participantes é revalidada quando o líder inicia.");

        StringBuilder names = new StringBuilder();
        for (String uid : lobby.participants()) {
            names.append("<@").append(uid).append("> ");
        }

        List<ContainerChildComponent> kids = new ArrayList<>();
        kids.add(Panels.text(head.toString()));
        kids.add(Panels.divider());
        kids.add(Panels.text(names.toString().strip().isEmpty() ? "-# *ninguém ainda*" : names.toString().strip()));
        kids.add(Panels.text("-# líder: <@" + lobby.leaderId() + ">"));
        kids.add(Panels.divider());
        kids.add(ActionRow.of(
                Button.success(ComponentId.of(NS, "join"), "Entrar"),
                Button.primary(ComponentId.of(NS, "start"), "Iniciar")));
        return Panels.container(accent, kids.toArray(new ContainerChildComponent[0]));
    }

    /** Painel de resultado (sucesso/falha/cancelamento) — sem botões. */
    public static Container result(int accent, String msg) {
        return Panels.container(accent,
                Panels.text("## " + Emojis.of(Emojis.MONEY, "💰") + " Crime Organizado — Resultado"),
                Panels.divider(),
                Panels.text(msg));
    }

    /** Painel de lobby expirado (5 min sem iniciar) — sem botões. */
    public static Container expired(int accent) {
        return Panels.container(accent,
                Panels.text("## " + Emojis.of(Emojis.MONEY, "💰") + " Crime Organizado"),
                Panels.divider(),
                Panels.text("O lobby expirou sem ser iniciado."));
    }
}
