package dev.davimf.basebot.modules.facs.recruit;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.component.ComponentHandler;
import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.modules.facs.FacsLog;
import dev.davimf.basebot.util.EmbedColor;
import net.dv8tion.jda.api.Permission;
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
            event.reply("Canal de análise (Solicitações de Set) não configurado em /setup → Logs.")
                    .setEphemeral(true).queue();
            return;
        }
        ModalMapping rec = event.getValue("recrutador");
        Mentions mentions = rec == null ? null : rec.getAsMentions();
        User recruiter = mentions == null || mentions.getUsers().isEmpty() ? null : mentions.getUsers().get(0);
        if (recruiter == null) {
            event.reply("Selecione um recrutador válido.").setEphemeral(true).queue();
            return;
        }
        review.sendMessageComponents(RecruitView.request(EmbedColor.resolve(cfg),
                        event.getUser().getId(), recruiter.getId(),
                        value(event, "idjogo"), value(event, "nome"), value(event, "telefone")))
                .useComponentsV2()
                .setAllowedMentions(List.of())
                .queue(msg -> event.reply("📝 Solicitação enviada para análise.").setEphemeral(true).queue(),
                        err -> event.reply("Falha ao enviar: " + err.getMessage()).setEphemeral(true).queue());
    }

    private void accept(ButtonInteractionEvent event, BotContext ctx, String applicantId, String recruiterId) {
        if (event.getMember() == null || !event.getMember().hasPermission(Permission.MANAGE_ROLES)) {
            event.reply("Apenas a gerência pode aceitar solicitações.").setEphemeral(true).queue();
            return;
        }
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(event.getGuild().getId());
        int accent = EmbedColor.resolve(cfg);
        String roleId = cfg.role(ENTRY_ROLE_KEY);
        Role role = roleId == null ? null : event.getGuild().getRoleById(roleId);
        if (role == null) {
            event.reply("Cargo de entrada (Membro) não configurado em /setup → Cargos.")
                    .setEphemeral(true).queue();
            return;
        }
        if (!event.getGuild().getSelfMember().canInteract(role)) {
            event.reply("Não posso atribuir o cargo de Membro (acima do meu cargo).")
                    .setEphemeral(true).queue();
            return;
        }
        event.getGuild().retrieveMemberById(applicantId).queue(member ->
                event.getGuild().addRoleToMember(member, role).reason("Recrutamento aprovado").queue(ok -> {
                    int total = stats.increment(event.getGuild().getId(), recruiterId);
                    event.editComponents(RecruitView.resolved(accent, applicantId, recruiterId,
                            "✅ Aceito por <@" + event.getUser().getId() + ">.")).useComponentsV2().queue();
                    ctx.database().actionLogs().log(event.getGuild().getId(), event.getUser().getId(),
                            applicantId, "RECRUIT_ACCEPT", recruiterId);
                    FacsLog.post(ctx, event.getGuild().getId(), "log-hierarquia",
                            "## 📝 Recrutamento aceito\n<@" + applicantId + "> entrou na facção · recrutador <@"
                                    + recruiterId + "> (" + total + " recrutas).");
                }, err -> event.reply("Falha ao atribuir o cargo: " + err.getMessage())
                        .setEphemeral(true).queue()),
                err -> event.reply("Não foi possível encontrar o candidato.").setEphemeral(true).queue());
    }

    private void reject(ButtonInteractionEvent event, BotContext ctx, String applicantId, String recruiterId) {
        if (event.getMember() == null || !event.getMember().hasPermission(Permission.MANAGE_ROLES)) {
            event.reply("Apenas a gerência pode recusar solicitações.").setEphemeral(true).queue();
            return;
        }
        int accent = EmbedColor.resolve(ctx.database().guildConfig().findOrEmpty(event.getGuild().getId()));
        event.editComponents(RecruitView.resolved(accent, applicantId, recruiterId,
                "❌ Recusado por <@" + event.getUser().getId() + ">.")).useComponentsV2().queue();
        ctx.database().actionLogs().log(event.getGuild().getId(), event.getUser().getId(),
                applicantId, "RECRUIT_REJECT", recruiterId);
    }

    private static String value(ModalInteractionEvent event, String key) {
        ModalMapping m = event.getValue(key);
        return m == null ? "" : m.getAsString();
    }
}
