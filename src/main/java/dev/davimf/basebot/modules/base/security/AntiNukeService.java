package dev.davimf.basebot.modules.base.security;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.modules.base.moderation.ModerationService;
import dev.davimf.basebot.util.ChannelLog;
import dev.davimf.basebot.util.EmbedColor;
import dev.davimf.basebot.util.Emojis;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import java.util.List;

/** Resposta do anti-nuke: remove os cargos do ator (os que o bot consegue tocar) e
 *  alerta o dono no modlog. Não desfaz canais/cargos/bans já apagados. */
public final class AntiNukeService {

    private AntiNukeService() {}

    public static void neutralize(BotContext ctx, Guild guild, Member actor, int count) {
        List<Role> removable = actor.getRoles().stream()
                .filter(r -> !r.isManaged() && guild.getSelfMember().canInteract(r))
                .toList();
        for (Role r : removable) {
            guild.removeRoleFromMember(actor, r).reason("Anti-nuke: ações destrutivas em massa").queue(ok -> {}, err -> {});
        }
        boolean impotent = !guild.getSelfMember().canInteract(actor);

        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(guild.getId());
        String body = "## " + Emojis.of(Emojis.SHIELD, "🛡️") + " Anti-nuke acionado\n"
                + "<@" + guild.getOwnerIdLong() + ">\n---\n"
                + "**Ator** · " + actor.getAsMention() + " (`" + actor.getId() + "`)\n"
                + "**Ações destrutivas** · `" + count + "` em janela curta\n"
                + (impotent
                    ? "-# " + Emojis.of(Emojis.WARN, "⚠️") + " O ator está **acima do meu cargo** — não consegui remover os cargos. Intervenha manualmente."
                    : "-# Cargos removidos. Canais/cargos/bans já feitos **não** são desfeitos — verifique os danos.");

        TextChannel modlog = modlog(ctx, guild);
        if (modlog != null) {
            modlog.sendMessageComponents(Panels.container(EmbedColor.resolve(cfg), Panels.text(body)))
                    .useComponentsV2()
                    .setAllowedMentions(List.of(Message.MentionType.USER))
                    .queue(ok -> {}, err -> {});
        } else {
            ChannelLog.post(ctx, guild.getId(), ModerationService.MODLOG_KEY, body);
        }
    }

    private static TextChannel modlog(BotContext ctx, Guild guild) {
        String id = ctx.database().guildConfig().findOrEmpty(guild.getId()).channel(ModerationService.MODLOG_KEY);
        return id == null ? null : guild.getTextChannelById(id);
    }
}
