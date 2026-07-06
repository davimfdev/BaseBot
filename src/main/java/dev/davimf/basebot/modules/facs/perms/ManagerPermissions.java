package dev.davimf.basebot.modules.facs.perms;

import dev.davimf.basebot.database.model.GuildConfig;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Role-based faction-management permissions (design 2026-06-28). Each {@link Capability} is
 * one management area; a guild grants it to "principals" — hierarchy role keys and/or
 * {@code role:<id>} raw ids — stored as a CSV in guild_config.settings under
 * {@code perm:<capability>}. Discord Administrator always bypasses; an absent setting falls
 * back to the capability's coded defaults.
 */
public final class ManagerPermissions {

    /** Top management — in every capability's default set (editable). */
    private static final List<String> LEADERSHIP = List.of("lider", "sub-lider", "gerente-geral");

    /** Management categories shown in /setup, in hierarchy order. */
    public static final List<String> CATEGORY_KEYS = List.of(
            "lider", "sub-lider", "gerente-geral", "gerente-vendas",
            "gerente-elite", "gerente-elite-feminina", "gerente-recrutamento", "gerente-farm");

    public enum Capability {
        ACOES("acoes", "Ações / Escalações", "Ações", "gerente-elite", "gerente-elite-feminina"),
        FINANCEIRO("financeiro", "Financeiro", "Financeiro", "gerente-vendas"),
        FARM("farm", "Farm & Produção", "Farm", "gerente-farm"),
        RECRUTAMENTO("recrutamento", "Recrutamento & Sets", "Recrut./Sets", "gerente-recrutamento"),
        PUNICOES("punicoes", "Punições", "Punições");

        private final String key;
        private final String label;
        private final String shortLabel;
        private final List<String> domainOwners;

        Capability(String key, String label, String shortLabel, String... domainOwners) {
            this.key = key;
            this.label = label;
            this.shortLabel = shortLabel;
            this.domainOwners = List.of(domainOwners);
        }

        public String key() { return key; }
        public String label() { return label; }
        public String shortLabel() { return shortLabel; }
        public String settingKey() { return "perm:" + key; }

        public List<String> defaultPrincipals() {
            List<String> out = new ArrayList<>(domainOwners);
            out.addAll(LEADERSHIP);
            return out;
        }

        public static Capability fromKey(String key) {
            for (Capability c : values()) {
                if (c.key.equals(key)) {
                    return c;
                }
            }
            return null;
        }
    }

    private ManagerPermissions() {}

    public static List<String> principals(GuildConfig cfg, Capability cap) {
        String raw = cfg.setting(cap.settingKey());
        if (raw == null) {
            return cap.defaultPrincipals();
        }
        List<String> out = new ArrayList<>();
        for (String part : raw.split(",")) {
            String t = part.trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }

    public static boolean grants(GuildConfig cfg, Capability cap, String principal) {
        return principals(cfg, cap).contains(principal);
    }

    public static Set<String> allowedRoleIds(GuildConfig cfg, Capability cap) {
        Set<String> ids = new LinkedHashSet<>();
        for (String p : principals(cfg, cap)) {
            if (p.startsWith("role:")) {
                String id = p.substring("role:".length());
                if (!id.isEmpty()) {
                    ids.add(id);
                }
            } else {
                String id = cfg.role(p);
                if (id != null) {
                    ids.add(id);
                }
            }
        }
        return ids;
    }

    public static boolean isAllowed(Set<String> memberRoleIds, boolean isAdministrator, Set<String> allowed) {
        if (isAdministrator) {
            return true;
        }
        for (String id : memberRoleIds) {
            if (allowed.contains(id)) {
                return true;
            }
        }
        return false;
    }

    public static boolean can(Member member, GuildConfig cfg, Capability cap) {
        if (member == null) {
            return false;
        }
        if (member.hasPermission(Permission.ADMINISTRATOR)) {
            return true;
        }
        Set<String> allowed = allowedRoleIds(cfg, cap);
        for (Role r : member.getRoles()) {
            if (allowed.contains(r.getId())) {
                return true;
            }
        }
        return false;
    }

    public static String grant(GuildConfig cfg, Capability cap, String principal) {
        List<String> ps = new ArrayList<>(principals(cfg, cap));
        if (!ps.contains(principal)) {
            ps.add(principal);
        }
        return String.join(",", ps);
    }

    public static String revoke(GuildConfig cfg, Capability cap, String principal) {
        List<String> ps = new ArrayList<>(principals(cfg, cap));
        ps.remove(principal);
        return String.join(",", ps);
    }

    public static List<String> freeRolePrincipals(GuildConfig cfg) {
        Set<String> out = new LinkedHashSet<>();
        for (Capability c : Capability.values()) {
            for (String p : principals(cfg, c)) {
                if (p.startsWith("role:")) {
                    out.add(p);
                }
            }
        }
        return new ArrayList<>(out);
    }

    /** Custom-id-safe token for a principal (the ComponentId separator is ':'). */
    public static String customIdToken(String principal) {
        return principal.startsWith("role:") ? "role-" + principal.substring("role:".length()) : principal;
    }

    public static String principalFromToken(String token) {
        return token.startsWith("role-") ? "role:" + token.substring("role-".length()) : token;
    }
}
