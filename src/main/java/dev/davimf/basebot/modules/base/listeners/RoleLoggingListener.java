package dev.davimf.basebot.modules.base.listeners;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.util.ChannelLog;
import dev.davimf.basebot.util.Emojis;
import dev.davimf.basebot.util.PermissionNames;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.role.RoleCreateEvent;
import net.dv8tion.jda.api.events.role.RoleDeleteEvent;
import net.dv8tion.jda.api.events.role.update.RoleUpdateColorEvent;
import net.dv8tion.jda.api.events.role.update.RoleUpdateNameEvent;
import net.dv8tion.jda.api.events.role.update.RoleUpdatePermissionsEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.EnumSet;
import java.util.stream.Collectors;

/** Logs de cargos em log-cargos: criado, deletado, atualizado (nome/cor/permissões). */
public final class RoleLoggingListener extends ListenerAdapter {

    private final BotContext ctx;

    public RoleLoggingListener(BotContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void onRoleCreate(RoleCreateEvent event) {
        ChannelLog.post(ctx, event.getGuild().getId(), "log-cargos",
                "## " + Emojis.of(Emojis.ROLES, "🎭") + " Cargo criado\n---\n"
                        + "**Cargo** · " + event.getRole().getAsMention());
    }

    @Override
    public void onRoleDelete(RoleDeleteEvent event) {
        ChannelLog.post(ctx, event.getGuild().getId(), "log-cargos",
                "## " + Emojis.of(Emojis.TRASH, "🗑️") + " Cargo deletado\n---\n"
                        + "**Nome** · `" + event.getRole().getName() + "`");
    }

    @Override
    public void onRoleUpdateName(RoleUpdateNameEvent event) {
        ChannelLog.post(ctx, event.getGuild().getId(), "log-cargos",
                "## " + Emojis.of(Emojis.EDIT, "✏️") + " Cargo renomeado\n---\n"
                        + "**Antes** · `" + event.getOldValue() + "`\n**Depois** · `" + event.getNewValue() + "`");
    }

    @Override
    public void onRoleUpdateColor(RoleUpdateColorEvent event) {
        ChannelLog.post(ctx, event.getGuild().getId(), "log-cargos",
                "## " + Emojis.of(Emojis.PALETTE, "🎨") + " Cor do cargo alterada\n---\n"
                        + "**Cargo** · " + event.getRole().getAsMention());
    }

    @Override
    public void onRoleUpdatePermissions(RoleUpdatePermissionsEvent event) {
        EnumSet<Permission> added = EnumSet.noneOf(Permission.class);
        added.addAll(event.getNewPermissions());
        added.removeAll(event.getOldPermissions());
        EnumSet<Permission> removed = EnumSet.noneOf(Permission.class);
        removed.addAll(event.getOldPermissions());
        removed.removeAll(event.getNewPermissions());

        StringBuilder body = new StringBuilder("## " + Emojis.of(Emojis.PERMS, "🔐") + " Permissões do cargo alteradas\n---\n"
                + "**Cargo** · " + event.getRole().getAsMention());
        String addStr = PermissionNames.names(added);
        String remStr = PermissionNames.names(removed);
        if (!addStr.isEmpty() || !remStr.isEmpty()) {
            body.append("\n---\n");
            if (!addStr.isEmpty()) {
                body.append(Emojis.of(Emojis.PLUS, "➕")).append(" **Concedidas** · ").append(addStr);
            }
            if (!remStr.isEmpty()) {
                body.append(addStr.isEmpty() ? "" : "\n")
                        .append(Emojis.of(Emojis.MINUS, "➖")).append(" **Removidas** · ").append(remStr);
            }
        }
        ChannelLog.post(ctx, event.getGuild().getId(), "log-cargos", body.toString());
    }
}
