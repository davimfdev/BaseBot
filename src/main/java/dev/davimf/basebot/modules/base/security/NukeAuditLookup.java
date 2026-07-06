package dev.davimf.basebot.modules.base.security;

import dev.davimf.basebot.core.BotContext;
import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.audit.AuditLogEntry;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.time.OffsetDateTime;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Igual ao {@code AuditLookup}, mas com retry curto: o registro de "quem fez" pode atrasar
 *  alguns ms no audit log. Resolve o ator (User) de uma ação e só então chama {@code onActor}.
 *  Requer VIEW_AUDIT_LOG; desiste em silêncio. */
public final class NukeAuditLookup {

    private NukeAuditLookup() {}

    public static void resolveActor(Guild guild, String targetId, ActionType type,
                                    BotContext ctx, Consumer<User> onActor) {
        attempt(guild, targetId, type, ctx, onActor, 3);
    }

    private static void attempt(Guild guild, String targetId, ActionType type,
                                BotContext ctx, Consumer<User> onActor, int remaining) {
        guild.retrieveAuditLogs().type(type).limit(6).queue(entries -> {
            AuditLogEntry hit = entries.stream()
                    .filter(e -> targetId == null || targetId.equals(e.getTargetId()))
                    .filter(e -> e.getTimeCreated().isAfter(OffsetDateTime.now().minusSeconds(15)))
                    .findFirst().orElse(null);
            if (hit != null && hit.getUser() != null) {
                onActor.accept(hit.getUser());
            } else {
                retry(guild, targetId, type, ctx, onActor, remaining);
            }
        }, err -> retry(guild, targetId, type, ctx, onActor, remaining));
    }

    private static void retry(Guild guild, String targetId, ActionType type,
                              BotContext ctx, Consumer<User> onActor, int remaining) {
        if (remaining > 1) {
            ctx.scheduler().once(() -> attempt(guild, targetId, type, ctx, onActor, remaining - 1),
                    500, TimeUnit.MILLISECONDS);
        }
    }
}
