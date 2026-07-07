package dev.davimf.basebot.modules.facs.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.modules.base.moderation.Moderation;
import dev.davimf.basebot.modules.facs.FacsLog;
import dev.davimf.basebot.modules.facs.perms.ManagerPermissions;
import dev.davimf.basebot.modules.facs.perms.ManagerPermissions.Capability;
import dev.davimf.basebot.util.Emojis;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/**
 * /pd — aplica PD e registra nos logs de PDs e Punições (BOTSPECS Module 4). Com {@code usuario}
 * (membro no Discord), remove o membro (kick, respeitando a hierarquia de cargos). Sem membro —
 * para quem já saiu do Discord — registra o PD via {@code id_jogo} + {@code nome_rp}, sem kick.
 */
public final class PdCommand implements SlashCommand {

    /** Qual fluxo o /pd deve seguir conforme as opções fornecidas. */
    public enum Mode { MEMBER, EXTERNAL, INVALID }

    static Mode mode(boolean hasUsuario, String idJogo, String nomeRp) {
        if (hasUsuario) {
            return Mode.MEMBER;
        }
        if (notBlank(idJogo) && notBlank(nomeRp)) {
            return Mode.EXTERNAL;
        }
        return Mode.INVALID;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    @Override
    public String name() {
        return "pd";
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("pd", "Aplica PD: remove o membro (se estiver no Discord) e registra nos logs.")
                .addOption(OptionType.STRING, "motivo", "Motivo do PD", true)
                .addOption(OptionType.USER, "usuario", "Membro a remover (se estiver no Discord)", false)
                .addOption(OptionType.STRING, "id_jogo", "ID de jogo (para quem já saiu do Discord)", false)
                .addOption(OptionType.STRING, "nome_rp", "Nome no RP (para quem já saiu do Discord)", false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null || event.getMember() == null) {
            Replies.ephemeral(event, ctx, "Use este comando em um servidor.");
            return;
        }
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(event.getGuild().getId());
        if (!ManagerPermissions.can(event.getMember(), cfg, Capability.PUNICOES)) {
            Replies.ephemeral(event, ctx, "Você não tem permissão de **Punições**.");
            return;
        }

        String reason = event.getOption("motivo", OptionMapping::getAsString);
        boolean hasUsuario = event.getOption("usuario") != null;
        String idJogo = event.getOption("id_jogo", OptionMapping::getAsString);
        String nomeRp = event.getOption("nome_rp", OptionMapping::getAsString);
        String guildId = event.getGuild().getId();
        String actorId = event.getUser().getId();

        switch (mode(hasUsuario, idJogo, nomeRp)) {
            case MEMBER -> {
                Member target = event.getOption("usuario", OptionMapping::getAsMember);
                if (target == null) {
                    Replies.ephemeral(event, ctx,
                            "Esse usuário não está mais no servidor. Para registrar mesmo assim, use **id_jogo** + **nome_rp**.");
                    return;
                }
                Member self = event.getGuild().getSelfMember();
                if (!Moderation.canModerate(event.getMember(), target, self)) {
                    Replies.ephemeral(event, ctx,
                            "Hierarquia insuficiente: você ou o bot não estão acima desse membro.");
                    return;
                }
                String targetTag = target.getUser().getAsTag();
                String targetId = target.getId();
                event.getGuild().kick(target)
                        .reason(dev.davimf.basebot.util.ModReason.of(event.getUser(), "PD: " + reason)).queue(ok -> {
                    ctx.database().actionLogs().log(guildId, actorId, targetId, "PD", reason);
                    String memberLine = targetTag + " · `" + targetId + "`";
                    FacsLog.post(ctx, guildId, "log-pds", pdEntry(memberLine, actorId, reason));
                    FacsLog.post(ctx, guildId, "log-punicoes", pdEntry(memberLine, actorId, reason));
                    Replies.reply(event, ctx, Emojis.of(Emojis.SKULL, "💀") + " PD aplicado em " + targetTag + ".");
                }, err -> Replies.ephemeral(event, ctx, "Falha ao aplicar PD: " + err.getMessage()));
            }
            case EXTERNAL -> {
                String memberLine = nomeRp.trim() + " · id de jogo `" + idJogo.trim() + "` · (fora do Discord)";
                ctx.database().actionLogs().log(guildId, actorId, idJogo.trim(), "PD", reason);
                FacsLog.post(ctx, guildId, "log-pds", pdEntry(memberLine, actorId, reason));
                FacsLog.post(ctx, guildId, "log-punicoes", pdEntry(memberLine, actorId, reason));
                Replies.reply(event, ctx,
                        Emojis.of(Emojis.SKULL, "💀") + " PD registrado para **" + nomeRp.trim() + "** (fora do Discord).");
            }
            case INVALID -> Replies.ephemeral(event, ctx,
                    "Informe o **usuário** (membro no Discord) ou o **id_jogo** + **nome_rp** (para quem já saiu).");
        }
    }

    private static String pdEntry(String memberLine, String actorId, String reason) {
        return "## " + Emojis.of(Emojis.SKULL, "💀") + " PD aplicado\n---\n"
                + Emojis.of(Emojis.MEMBER, "👤") + " **Membro** · " + memberLine + "\n---\n"
                + Emojis.of(Emojis.SHIELD, "🛡️") + " **Responsável** · <@" + actorId + ">\n"
                + Emojis.of(Emojis.NOTE, "📝") + " **Motivo** · " + reason;
    }
}
