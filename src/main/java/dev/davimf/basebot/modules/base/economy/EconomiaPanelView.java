package dev.davimf.basebot.modules.base.economy;

import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.util.Emojis;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.entities.Member;

import java.util.ArrayList;
import java.util.List;

/** Painel de leitura do /economia. */
public final class EconomiaPanelView {

    public record ActionLine(String command, ActionStatus status, String unlockHint, String equippedLabel) {}

    private EconomiaPanelView() {}

    public static Container panel(int accent, Member m, WalletRepository.Wallet w, JailService.Status jail,
                                  boolean fichaSuja, List<ActionLine> acoes, boolean notifyOn, GuildConfig cfg) {
        boolean preso = jail.kind() == JailService.Kind.PRESO;
        StringBuilder body = new StringBuilder();
        body.append("## ").append(Emojis.of(Emojis.MONEY, "🪙")).append(" Economia de ").append(m.getEffectiveName()).append('\n');
        long total = w.cash() + w.bank();
        body.append('\n').append(Emojis.of(Emojis.CASH, "💵")).append(" Carteira · ").append(EconomyFormat.format(w.cash(), cfg));
        body.append('\n').append(Emojis.of(Emojis.BANK, "🏦")).append(" Banco · ").append(EconomyFormat.format(w.bank(), cfg));
        body.append('\n').append(Emojis.of(Emojis.GEM, "💠")).append(" Total · ").append(EconomyFormat.formatNamed(total, cfg));

        if (preso) {
            body.append('\n').append('\n').append(Emojis.of(Emojis.LOCK, "🔒"))
                    .append(" **Preso** — sai <t:").append(jail.presoAte() / 1000).append(":R>. Pague `/economia fianca` pra sair já.");
        } else if (fichaSuja) {
            body.append('\n').append('\n').append(Emojis.of(Emojis.WARN, "⚠️"))
                    .append(" **Ficha suja** — −15% de chance em crimes. `/economia limparficha` limpa.");
        }

        StringBuilder acts = new StringBuilder();
        for (ActionLine a : acoes) {
            acts.append('\n').append(lineOf(a));
        }

        StringBuilder tips = new StringBuilder();
        if (preso) {
            tips.append('\n').append(Emojis.of(Emojis.KEY, "🔓")).append(" `/economia fianca` — sair da cadeia por ")
                    .append(EconomyFormat.format(EconomyDefaults.BAIL_BASE, cfg));
        } else if (fichaSuja) {
            tips.append('\n').append(Emojis.of(Emojis.BROOM, "🧼")).append(" `/economia limparficha` — limpar a ficha por ")
                    .append(EconomyFormat.format(EconomyDefaults.EXPUNGE, cfg));
        }

        String footer = "-# Roubo pode ter cooldown separado por alvo."
                + (preso ? " Preso? Só `/economia daily` e eventos rendem." : "");

        List<ContainerChildComponent> kids = new ArrayList<>();
        kids.add(Panels.text(body.toString()));
        kids.add(Panels.divider());
        kids.add(Panels.text("### Ações" + acts));
        if (!tips.isEmpty()) {
            kids.add(Panels.divider());
            kids.add(Panels.text("### Ações úteis" + tips));
        }
        kids.add(Panels.divider());
        kids.add(Panels.text(footer));
        String toggleLabel = notifyOn ? "Avisos de trabalho: Ligado" : "Avisos de trabalho: Desligado";
        Button toggle = notifyOn
                ? Button.success(ComponentId.of("ecopanel", "notify"), toggleLabel)
                : Button.secondary(ComponentId.of("ecopanel", "notify"), toggleLabel);
        kids.add(ActionRow.of(toggle));
        return Panels.container(accent, kids.toArray(new ContainerChildComponent[0]));
    }

    private static String lineOf(ActionLine a) {
        String cmd = "`/" + a.command() + "`";
        String eq = a.equippedLabel() == null ? "" : " · " + a.equippedLabel();
        return switch (a.status().kind()) {
            case READY -> Emojis.of(Emojis.CHECK_YES, "✅") + " " + cmd + " — disponível agora" + eq;
            case COOLDOWN -> Emojis.of(Emojis.HOURGLASS, "⏳") + " " + cmd + " — <t:" + (a.status().readyAt() / 1000) + ":R>" + eq;
            case LOCKED -> Emojis.of(Emojis.LOCK, "🔒") + " " + cmd + " — " + a.unlockHint();
            case JAILED -> Emojis.of(Emojis.LOCK, "🔒") + " " + cmd + " — preso";
        };
    }
}
