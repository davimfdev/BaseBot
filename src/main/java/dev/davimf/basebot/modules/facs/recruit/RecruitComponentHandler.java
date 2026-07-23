// [OUTLINE START]
// Package: dev.davimf.basebot.modules.facs.recruit
//
// Class: RecruitComponentHandler
//
// Constructors:
//   - `Constructor` : `public RecruitComponentHandler(RecruitStatsRepository stats, RecruitRequestRepository requests)`
//
// Methods:
//   - `Method` : `public String namespace()`
//   - `Method` : `private static String value(ModalInteractionEvent event, String key)`
//
// Fields:
//   - `Field` : `private static final String ENTRY_ROLE_KEY`
//   - `Field` : `private static final String REVIEW_CHANNEL_KEY`
//   - `Field` : `private final RecruitStatsRepository stats`
//   - `Field` : `private final RecruitRequestRepository requests`
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
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Mentions;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;

import java.util.List;
import java.util.Optional;

/**
 * Recruitment pipeline handler (BOTSPECS Module 4): the apply button opens the form,
 * the form posts a review request to the Set-requests channel, and a manager's Accept
 * grants the entry ("membro") role and credits the chosen recruiter (+1 stat).
 */
public final class RecruitComponentHandler implements ComponentHandler {

    private static final String ENTRY_ROLE_KEY = "membro";
    private static final String REVIEW_CHANNEL_KEY = "log-sets";

    private final RecruitStatsRepository stats;
    private final RecruitRequestRepository requests;

    public RecruitComponentHandler(RecruitStatsRepository stats, RecruitRequestRepository requests) {
        this.stats = stats;
        this.requests = requests;
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
                .queue(msg -> {
                    try {
                        requests.save(msg.getId(), event.getGuild().getId(), event.getUser().getId(),
                                recruiter.getId(), value(event, "idjogo"), value(event, "nome"),
                                value(event, "telefone"));
                        Replies.ephemeral(event, ctx,
                                Emojis.of(Emojis.NOTE, "📝") + " Solicitação enviada para análise.");
                    } catch (RuntimeException persistErr) {
                        // Não deixar botões clicáveis sem persistência: invalida a mensagem.
                        msg.editMessageComponents(RecruitView.resolvedLegacy(
                                        EmbedColor.resolve(cfg), event.getUser().getId(), recruiter.getId(),
                                        Emojis.of(Emojis.CHECK_NO, "❌")
                                                + " Erro ao salvar a solicitação. Reenvie o formulário."))
                                .useComponentsV2().queue(ok -> {}, e -> {});
                        Replies.ephemeral(event, ctx,
                                "Erro ao salvar a solicitação. Reenvie o formulário.");
                    }
                }, err -> Replies.ephemeral(event, ctx, "Falha ao enviar: " + err.getMessage()));
    }

    private void accept(ButtonInteractionEvent event, BotContext ctx, String applicantId, String recruiterId) {
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(event.getGuild().getId());
        if (!ManagerPermissions.can(event.getMember(), cfg, ManagerPermissions.Capability.RECRUTAMENTO)) {
            Replies.ephemeral(event, ctx, "Apenas a gerência de **Recrutamento** pode aceitar solicitações.");
            return;
        }
        int accent = EmbedColor.resolve(cfg);
        String messageId = event.getMessageId();

        boolean claimed = requests.resolvePending(messageId, "ACCEPTED");
        Optional<RecruitRequestRepository.Request> row = requests.find(messageId);
        if (!claimed) {
            if (row.isPresent()) {
                Replies.ephemeral(event, ctx, "Essa solicitação já foi resolvida.");
            } else {
                acceptLegacy(event, ctx, cfg, accent, applicantId, recruiterId);
            }
            return;
        }
        RecruitRequestRepository.Request req = row.orElse(null);

        String roleId = cfg.role(ENTRY_ROLE_KEY);
        Role role = roleId == null ? null : event.getGuild().getRoleById(roleId);
        if (role == null) {
            requests.setStatus(messageId, "PENDING");
            Replies.ephemeral(event, ctx, "Cargo de entrada (Membro) não configurado em /setup → Cargos.");
            return;
        }
        if (!event.getGuild().getSelfMember().canInteract(role)) {
            requests.setStatus(messageId, "PENDING");
            Replies.ephemeral(event, ctx, "Não posso atribuir o cargo de Membro (acima do meu cargo).");
            return;
        }
        // Ack agora: o trabalho a seguir é assíncrono (retrieve + addRole) e pode passar de 3s.
        event.deferEdit().queue();
        event.getGuild().retrieveMemberById(applicantId).queue(member ->
                event.getGuild().addRoleToMember(member, role).reason("Recrutamento aprovado").queue(
                        ok -> afterAccepted(event, ctx, cfg, accent, member, applicantId, recruiterId, req),
                        err -> {
                            requests.setStatus(messageId, "PENDING");
                            Replies.hookEphemeral(event, ctx, "Falha ao atribuir o cargo: " + err.getMessage());
                        }),
                err -> {
                    requests.setStatus(messageId, "PENDING");
                    Replies.hookEphemeral(event, ctx, "Não foi possível encontrar o candidato.");
                });
    }

    /** Passos best-effort após o cargo Membro já ter sido concedido (nunca desfazem o aceite). */
    private void afterAccepted(ButtonInteractionEvent event, BotContext ctx, GuildConfig cfg, int accent,
                               Member member, String applicantId, String recruiterId,
                               RecruitRequestRepository.Request req) {
        removeSemSet(event, cfg, member);
        if (req != null) {
            applyNickname(event, ctx, member, req);
        }
        int total = stats.increment(event.getGuild().getId(), recruiterId);
        ctx.database().actionLogs().log(event.getGuild().getId(), event.getUser().getId(),
                applicantId, "RECRUIT_ACCEPT", recruiterId);
        String status = "" + Emojis.of(Emojis.CHECK_YES, "✅") + " Aceito por <@" + event.getUser().getId() + ">.";
        // Já demos deferEdit em accept(): atualizar a mensagem original via hook.
        if (req != null) {
            event.getHook().editOriginalComponents(RecruitView.resolved(accent, applicantId, recruiterId,
                    req.idJogo(), req.nome(), req.telefone(), status)).useComponentsV2().queue();
        } else {
            event.getHook().editOriginalComponents(RecruitView.resolvedLegacy(accent, applicantId, recruiterId, status))
                    .useComponentsV2().queue();
        }
        FacsLog.post(ctx, event.getGuild().getId(), "log-hierarquia",
                "## " + Emojis.of(Emojis.NOTE, "📝") + " Recrutamento aceito\n---\n"
                        + "" + Emojis.of(Emojis.MEMBER, "👤") + " **Novo membro** · <@" + applicantId + ">\n---\n"
                        + "" + Emojis.of(Emojis.HANDSHAKE, "🤝") + " **Recrutador** · <@" + recruiterId + "> · `" + total + "` recrutas");
    }

    /** Remove o cargo "sem-set" respeitando config/hierarquia/permissão; no-op silencioso senão. */
    private void removeSemSet(ButtonInteractionEvent event, GuildConfig cfg, Member member) {
        String semSetId = cfg.role("sem-set");
        if (semSetId == null) {
            return;
        }
        Role semSet = event.getGuild().getRoleById(semSetId);
        Member self = event.getGuild().getSelfMember();
        if (semSet == null || !member.getRoles().contains(semSet)
                || !self.hasPermission(Permission.MANAGE_ROLES) || !self.canInteract(semSet)) {
            return;
        }
        event.getGuild().removeRoleFromMember(member, semSet).reason("Set aceito").queue(ok -> {}, err -> {});
    }

    /** Renomeia para {Nome} | {ID}; falha vira aviso efêmero, não desfaz o aceite. */
    private void applyNickname(ButtonInteractionEvent event, BotContext ctx, Member member,
                               RecruitRequestRepository.Request req) {
        Member self = event.getGuild().getSelfMember();
        if (!self.hasPermission(Permission.NICKNAME_MANAGE) || !self.canInteract(member)) {
            event.getHook().sendMessage("-# Set aceito, mas não consegui alterar o apelido (permissão/hierarquia).")
                    .setEphemeral(true).queue(ok -> {}, e -> {});
            return;
        }
        member.modifyNickname(RecruitNick.format(req.nome(), req.idJogo())).reason("Set aceito")
                .queue(ok -> {}, err -> event.getHook()
                        .sendMessage("-# Set aceito, mas não consegui alterar o apelido: " + err.getMessage())
                        .setEphemeral(true).queue(o -> {}, e -> {}));
    }

    /** Aceite de mensagem legada (sem linha no banco): só troca de cargo, sem respostas/rename. */
    private void acceptLegacy(ButtonInteractionEvent event, BotContext ctx, GuildConfig cfg, int accent,
                              String applicantId, String recruiterId) {
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
        event.deferEdit().queue();
        event.getGuild().retrieveMemberById(applicantId).queue(member ->
                event.getGuild().addRoleToMember(member, role).reason("Recrutamento aprovado (legado)").queue(ok -> {
                    removeSemSet(event, cfg, member);
                    int total = stats.increment(event.getGuild().getId(), recruiterId);
                    ctx.database().actionLogs().log(event.getGuild().getId(), event.getUser().getId(),
                            applicantId, "RECRUIT_ACCEPT", recruiterId);
                    event.getHook().editOriginalComponents(RecruitView.resolvedLegacy(accent, applicantId, recruiterId,
                            "" + Emojis.of(Emojis.CHECK_YES, "✅") + " Aceito por <@" + event.getUser().getId() + ">."))
                            .useComponentsV2().queue();
                    FacsLog.post(ctx, event.getGuild().getId(), "log-hierarquia",
                            "## " + Emojis.of(Emojis.NOTE, "📝") + " Recrutamento aceito\n---\n"
                                    + "" + Emojis.of(Emojis.MEMBER, "👤") + " **Novo membro** · <@" + applicantId + ">\n---\n"
                                    + "" + Emojis.of(Emojis.HANDSHAKE, "🤝") + " **Recrutador** · <@" + recruiterId + "> · `" + total + "` recrutas");
                }, err -> Replies.hookEphemeral(event, ctx, "Falha ao atribuir o cargo: " + err.getMessage())),
                err -> Replies.hookEphemeral(event, ctx, "Não foi possível encontrar o candidato."));
    }

    private void reject(ButtonInteractionEvent event, BotContext ctx, String applicantId, String recruiterId) {
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(event.getGuild().getId());
        if (!ManagerPermissions.can(event.getMember(), cfg, ManagerPermissions.Capability.RECRUTAMENTO)) {
            Replies.ephemeral(event, ctx, "Apenas a gerência de **Recrutamento** pode recusar solicitações.");
            return;
        }
        int accent = EmbedColor.resolve(cfg);
        String messageId = event.getMessageId();
        boolean claimed = requests.resolvePending(messageId, "REJECTED");
        Optional<RecruitRequestRepository.Request> row = requests.find(messageId);
        String status = "" + Emojis.of(Emojis.CHECK_NO, "❌") + " Recusado por <@" + event.getUser().getId() + ">.";
        if (!claimed) {
            if (row.isPresent()) {
                Replies.ephemeral(event, ctx, "Essa solicitação já foi resolvida.");
            } else {
                event.editComponents(RecruitView.resolvedLegacy(accent, applicantId, recruiterId, status))
                        .useComponentsV2().queue();
                ctx.database().actionLogs().log(event.getGuild().getId(), event.getUser().getId(),
                        applicantId, "RECRUIT_REJECT", recruiterId);
            }
            return;
        }
        RecruitRequestRepository.Request req = row.orElse(null);
        if (req != null) {
            event.editComponents(RecruitView.resolved(accent, applicantId, recruiterId,
                    req.idJogo(), req.nome(), req.telefone(), status)).useComponentsV2().queue();
        } else {
            event.editComponents(RecruitView.resolvedLegacy(accent, applicantId, recruiterId, status))
                    .useComponentsV2().queue();
        }
        ctx.database().actionLogs().log(event.getGuild().getId(), event.getUser().getId(),
                applicantId, "RECRUIT_REJECT", recruiterId);
    }

    private static String value(ModalInteractionEvent event, String key) {
        ModalMapping m = event.getValue(key);
        return m == null ? "" : m.getAsString();
    }
}
