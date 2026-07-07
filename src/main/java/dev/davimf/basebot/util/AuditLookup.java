package dev.davimf.basebot.util;

import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.audit.AuditLogEntry;
import net.dv8tion.jda.api.entities.Guild;

import java.time.OffsetDateTime;
import java.util.function.Consumer;

/** Busca o autor humano + motivo de uma ação no audit log do Discord, para enriquecer
 *  logs de eventos nativos (movido à força, mute/deafen, pin, kick, ban, etc.).
 *  Requer VIEW_AUDIT_LOG; falha em silêncio. */
public final class AuditLookup {

    public record Actor(String moderatorMention, String reason) {}

    private AuditLookup() {}

    /** Busca a entrada recente (~15s) cujo {@code targetId} bate (ou qualquer entrada do
     *  tipo quando {@code targetId} é nulo). Chama {@code onFound} apenas se achar. */
    public static void lookup(Guild guild, String targetId, ActionType type, Consumer<Actor> onFound) {
        lookup(guild, targetId, type, onFound, () -> { });
    }

    /** Igual a {@link #lookup(Guild, String, ActionType, Consumer)} mas também chama
     *  {@code onAbsent} quando nenhuma entrada recente bate (ou a busca falha) — útil para
     *  distinguir uma ação forçada por moderador de uma ação voluntária do próprio membro. */
    public static void lookup(Guild guild, String targetId, ActionType type,
                              Consumer<Actor> onFound, Runnable onAbsent) {
        guild.retrieveAuditLogs().type(type).limit(6).queue(entries -> {
            AuditLogEntry hit = entries.stream()
                    .filter(e -> targetId == null || targetId.equals(e.getTargetId()))
                    .filter(e -> e.getTimeCreated().isAfter(OffsetDateTime.now().minusSeconds(15)))
                    .findFirst().orElse(null);
            if (hit == null) {
                onAbsent.run();
                return;
            }
            String mod = hit.getUser() == null ? "—" : hit.getUser().getAsMention();
            String reason = (hit.getReason() == null || hit.getReason().isBlank())
                    ? "*sem motivo*" : hit.getReason();
            onFound.accept(new Actor(mod, reason));
        }, err -> onAbsent.run());
    }
}
