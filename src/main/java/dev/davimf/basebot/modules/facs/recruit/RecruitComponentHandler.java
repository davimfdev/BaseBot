// [OUTLINE START]
// Package: dev.davimf.basebot.modules.facs.recruit
// 
// Class: RecruitComponentHandler
// 
// Constructors:
//   - `Constructor` : `public RecruitComponentHandler(RecruitStatsRepository stats)`
// 
// Methods:
//   - `Method` : `public String namespace()`
//   - `Method` : `private static String value(ModalInteractionEvent event, String key)`
// 
// Fields:
//   - `Field` : `private static final String ENTRY_ROLE_KEY`
//   - `Field` : `private static final String REVIEW_CHANNEL_KEY`
//   - `Field` : `private final RecruitStatsRepository stats`
// [OUTLINE END]



package dev.davimf.basebot.modules.facs.recruit;

import dev.davimf.basebot.util.Emojis;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.component.ComponentHandler;
import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.modules.facs.FacsLog;
import dev.davimf.basebot.modules.facs.perms.ManagerPermissions;
import dev.davimf.basebot.util.EmbedColor;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.entities.Mentions;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;

import java.util.List;

/**
 * Recruitment pipeline handler (BOTSPECS Module 4): the apply button opens the form,
 * the form posts a review request to the Set-requests channel, and a manager's Accept
 * grants the entry ("membro") role and credits the chosen recruiter (+1 stat).
 */
public final class RecruitComponentHandler implements ComponentHandler {

    private static final String ENTRY_ROLE_KEY = "membro";
    private static final String REVIEW_CHANNEL_KEY = "log-sets";

    private final RecruitStatsRepository stats;

    public RecruitComponentHandler(RecruitStatsRepository stats) {
        this.stats = stats;
    }

    @Override
    public String namespace() {
        return RecruitView.NS;
    }

    @Override
    public void onButton(ButtonInteractionEvent event, ComponentId id, BotContext ctx) {
        if (event.getGuild() == null) {
            return;
        }
        switch (id.action()) {
            case "open" -> event.replyModal(RecruitView.form()).queue();
            case "accept" -> accept(event, ctx, id.arg(0), id.arg(1));
            case "reject" -> reject(event, ctx, id.arg(0), id.arg(1));
            default -> { /* not ours */ }
        }
    }

    @Override
    public void onModal(ModalInteractionEvent event, ComponentId id, BotContext ctx) {
        if (!"form".equals(id.action()) || event.getGuild() == null) {
            return;
        }
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(event.getGuild().getId());
        String channelId = cfg.channel(REVIEW_CHANNEL_KEY);
        TextChannel review = channelId == null ? null : event.getGuild().getTextChannelById(channelId);
        if (review == null) {
            Replies.ephemeral(event, ctx,
                    "Canal de análise (Solicitações de Set) não configurado em /setup → Logs.");
            return;
        }
        ModalMapping rec = event.getValue("recrutador");
        Mentions mentions = rec == null ? null : rec.getAsMentions();
        User recruiter = mentions == null || mentions.getUsers().isEmpty() ? null : mentions.getUsers().get(0);
        if (recruiter == null) {
            Replies.ephemeral(event, ctx, "Selecione um recrutador válido.");
            return;
        }
        review.sendMessageComponents(RecruitView.request(EmbedColor.resolve(cfg),
                        event.getUser().getId(), recruiter.getId(),
                        value(event, "idjogo"), value(event, "nome"), value(event, "telefone")))
                .useComponentsV2()
                .setAllowedMentions(List.of())
                .queue(msg -> Replies.ephemeral(event, ctx, Emojis.of(Emojis.NOTE, "📝") + " Solicitação enviada para análise."),
                        err -> Replies.ephemeral(event, ctx, "Falha ao enviar: " + err.getMessage()));
    }

    private void accept(ButtonInteractionEvent event, BotContext ctx, String applicantId, String recruiterId) {
        GuildConfig gcfg = ctx.database().guildConfig().findOrEmpty(event.getGuild().getId());
        if (!ManagerPermissions.can(event.getMember(), gcfg, ManagerPermissions.Capability.RECRUTAMENTO)) {
            Replies.ephemeral(event, ctx, "Apenas a gerência de **Recrutamento** pode aceitar solicitações.");
            return;
        }
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(event.getGuild().getId());
        int accent = EmbedColor.resolve(cfg);
        String roleId = cfg.role(ENTRY_ROLE_KEY);
        Role role = roleId == null ? null : event.getGuild().getRoleById(roleId);
        if (role == null) {
            Replies.ephemeral(event, ctx, "Cargo de entrada (Membro) não configurado em /setup → Cargos.");
            return;
        }
        if (!event.getGuild().getSelfMember().canInteract(role)) {
            Replies.ephemeral(event, ctx, "Não posso atribuir o cargo de Membro (acima do meu cargo).");
            return;
        }
        event.getGuild().retrieveMemberById(applicantId).queue(member ->
                event.getGuild().addRoleToMember(member, role).reason("Recrutamento aprovado").queue(ok -> {
                    int total = stats.increment(event.getGuild().getId(), recruiterId);
                    event.editComponents(RecruitView.resolved(accent, applicantId, recruiterId,
                            "" + Emojis.of(Emojis.CHECK_YES, "✅") + " Aceito por <@" + event.getUser().getId() + ">.")).useComponentsV2().queue();
                    ctx.database().actionLogs().log(event.getGuild().getId(), event.getUser().getId(),
                            applicantId, "RECRUIT_ACCEPT", recruiterId);
                    FacsLog.post(ctx, event.getGuild().getId(), "log-hierarquia",
                            "## " + Emojis.of(Emojis.NOTE, "📝") + " Recrutamento aceito\n---\n"
                                    + "" + Emojis.of(Emojis.MEMBER, "👤") + " **Novo membro** · <@" + applicantId + ">\n---\n"
                                    + "" + Emojis.of(Emojis.HANDSHAKE, "🤝") + " **Recrutador** · <@" + recruiterId + "> · `" + total + "` recrutas");
                }, err -> Replies.ephemeral(event, ctx, "Falha ao atribuir o cargo: " + err.getMessage())),
                err -> Replies.ephemeral(event, ctx, "Não foi possível encontrar o candidato."));
    }

    private void reject(ButtonInteractionEvent event, BotContext ctx, String applicantId, String recruiterId) {
        GuildConfig gcfg = ctx.database().guildConfig().findOrEmpty(event.getGuild().getId());
        if (!ManagerPermissions.can(event.getMember(), gcfg, ManagerPermissions.Capability.RECRUTAMENTO)) {
            Replies.ephemeral(event, ctx, "Apenas a gerência de **Recrutamento** pode recusar solicitações.");
            return;
        }
        int accent = EmbedColor.resolve(ctx.database().guildConfig().findOrEmpty(event.getGuild().getId()));
        event.editComponents(RecruitView.resolved(accent, applicantId, recruiterId,
                "" + Emojis.of(Emojis.CHECK_NO, "❌") + " Recusado por <@" + event.getUser().getId() + ">.")).useComponentsV2().queue();
        ctx.database().actionLogs().log(event.getGuild().getId(), event.getUser().getId(),
                applicantId, "RECRUIT_REJECT", recruiterId);
    }

    private static String value(ModalInteractionEvent event, String key) {
        ModalMapping m = event.getValue(key);
        return m == null ? "" : m.getAsString();
    }
}
