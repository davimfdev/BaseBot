package dev.davimf.basebot.modules.base.setup;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.database.model.GuildConfig;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;

import java.util.Map;

/** Setup rápido dos cargos: para cada função em {@link SetupRoleKeys} ainda não mapeada,
 *  reusa um cargo existente com o mesmo nome ou cria um novo, e grava na chave. Idempotente:
 *  pula funções já mapeadas a um cargo vivo. BLOQUEANTE — chame fora da thread do JDA. */
public final class QuickRoleSetup {

    public record Summary(int created, int skipped) {}

    private QuickRoleSetup() {}

    public static Summary run(Guild guild, BotContext ctx) {
        int created = 0;
        int skipped = 0;
        for (Map.Entry<String, String> slot : SetupRoleKeys.OPTIONS) {
            GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(guild.getId());
            String existing = cfg.role(slot.getKey());
            if (existing != null && guild.getRoleById(existing) != null) {
                skipped++;
                continue; // já mapeado e vivo
            }
            Role role = guild.getRolesByName(slot.getValue(), true).stream().findFirst()
                    .orElseGet(() -> guild.createRole().setName(slot.getValue()).complete());
            ctx.database().guildConfig().save(GuildConfigEdits.withRole(cfg, slot.getKey(), role.getId()));
            created++;
        }
        return new Summary(created, skipped);
    }
}
