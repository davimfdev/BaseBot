package dev.davimf.basebot.modules.facs.hierarchy;

import dev.davimf.basebot.util.Emojis;

import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.database.model.GuildConfig;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Builds the {@code /hierarquia} panel (BOTSPECS Module 4): members grouped by rank. */
public final class HierarchyView {

    private HierarchyView() {}

    /**
     * Renders the chain of command top → bottom. Each member is listed under their
     * highest configured rank only. Unconfigured levels are skipped. Mentions should be
     * suppressed when sending so the panel never pings.
     */
    public static Container panel(int accent, Guild guild, GuildConfig cfg) {
        StringBuilder body = new StringBuilder();
        Set<String> seen = new HashSet<>();

        for (FacHierarchy.Level level : FacHierarchy.LEVELS) {
            String roleId = cfg.role(level.key());
            if (roleId == null || roleId.isBlank()) {
                continue;
            }
            Role role = guild.getRoleById(roleId);
            if (role == null) {
                continue;
            }
            List<Member> members = guild.getMembersWithRoles(role).stream()
                    .filter(m -> seen.add(m.getId()))
                    .toList();
            if (body.length() > 0) {
                body.append('\n');
            }
            body.append("### ").append(level.label()).append(" · <@&").append(roleId)
                    .append("> `").append(members.size()).append("`\n");
            if (members.isEmpty()) {
                body.append("-# *ninguém*\n");
            } else {
                body.append(members.stream().map(Member::getAsMention).collect(Collectors.joining(" ")))
                        .append('\n');
            }
        }
        if (seen.isEmpty()) {
            body.append("-# Nenhum cargo da hierarquia configurado em `/setup → Cargos`.");
        }
        return Panels.container(accent,
                Panels.text("# " + Emojis.of(Emojis.SERVER, "🏛️") + " Hierarquia · " + guild.getName()),
                Panels.divider(),
                Panels.text(body.toString()));
    }
}
