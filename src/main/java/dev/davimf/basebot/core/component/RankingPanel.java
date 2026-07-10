package dev.davimf.basebot.core.component;

import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * Moldura compartilhada dos leaderboards paginados (/top, /topcall): cabeçalho, linhas já
 * formatadas pelo chamador, divisor, rodapé e os botões ◀ ▶.
 *
 * <p>Os botões usam a ação {@code "top"} no namespace do chamador, então cada ranking mantém o
 * seu próprio {@code ComponentHandler}.
 */
public final class RankingPanel {

    private RankingPanel() {}

    /** Ao menos 1, mesmo com ranking vazio. */
    public static int pageCount(int total, int pageSize) {
        return Math.max(1, (total + pageSize - 1) / pageSize);
    }

    /**
     * @param heading      título markdown, ex. {@code "## 🏆 Ranking de nível"}
     * @param emptyLine    exibido quando {@code lines} está vazia
     * @param lines        linhas já numeradas e formatadas
     * @param namespace    namespace do {@code ComponentHandler} que pagina este painel
     * @param footerSuffix acrescentado ao rodapé, ou {@code null}
     */
    public static Container of(int accent, String heading, String emptyLine, List<String> lines,
                               String namespace, int page, int total, int pageSize, String footerSuffix) {
        int pages = pageCount(total, pageSize);

        List<ContainerChildComponent> kids = new ArrayList<>();
        kids.add(Panels.text(body(heading, emptyLine, lines)));
        kids.add(Panels.divider());
        kids.add(Panels.text("-# Página " + (page + 1) + "/" + pages
                + (footerSuffix == null ? "" : " · " + footerSuffix)));
        if (pages > 1) {
            kids.add(ActionRow.of(
                    Button.secondary(ComponentId.of(namespace, "top", String.valueOf(page - 1)), "◀")
                            .withDisabled(page <= 0),
                    Button.secondary(ComponentId.of(namespace, "top", String.valueOf(page + 1)), "▶")
                            .withDisabled(page >= pages - 1)));
        }
        return Panels.container(accent, kids.toArray(new ContainerChildComponent[0]));
    }

    /** Corpo do painel: cabeçalho, linha em branco, e as linhas já formatadas. Puro, para teste. */
    static String body(String heading, String emptyLine, List<String> lines) {
        StringBuilder sb = new StringBuilder(heading);
        sb.append("\n");
        if (lines.isEmpty()) {
            sb.append(emptyLine);
        } else {
            lines.forEach(line -> sb.append("\n").append(line));
        }
        return sb.toString();
    }
}
