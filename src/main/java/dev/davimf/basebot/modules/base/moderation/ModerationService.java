package dev.davimf.basebot.modules.base.moderation;

import dev.davimf.basebot.util.Emojis;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.modules.base.moderation.ModerationConfig.EscalationRule;
import dev.davimf.basebot.modules.base.voice.MuteRepository;
import dev.davimf.basebot.util.ChannelLog;
import dev.davimf.basebot.util.Durations;
import dev.davimf.basebot.util.EmbedColor;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.UserSnowflake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * The single orchestrator for the base moderation case system (design 2026-06-29). Every
 * action records a numbered case, DMs the target (unless disabled / a note), posts to the
 * {@code log-moderacao} channel, and — for warns — runs the configured auto-escalation.
 * Also sweeps expired cases (warn decay + tempban lift) on a schedule.
 */
public final class ModerationService {

    public static final String MODLOG_KEY = "log-moderacao";

    private static final Logger log = LoggerFactory.getLogger(ModerationService.class);

    private final BotContext ctx;
    private final InfractionRepository repo;

    public ModerationService(BotContext ctx) {
        this.ctx = ctx;
        this.repo = new InfractionRepository(ctx.database().sqlite());
    }

    public InfractionRepository repository() {
        return repo;
    }

    // --- recording -------------------------------------------------------------

    /**
     * Records a case for an action the caller already applied (ban/kick/mute/timeout/…),
     * DMs the target (respecting config, never for notes) and posts to the modlog.
     */
    public Infraction record(Guild guild, InfractionType type, User target, String modId,
                             String reason, Long expiresAt, Long durationMs) {
        long now = System.currentTimeMillis();
        Infraction inf = repo.create(guild.getId(), target.getId(), modId, type.name(),
                blankToNull(reason), now, expiresAt, durationMs);

        ChannelLog.post(ctx, guild.getId(), MODLOG_KEY, modlogMarkdown(inf, modId, durationMs));
        ctx.database().actionLogs().log(guild.getId(), modId, target.getId(), "MOD_" + type.name(), reason);

        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(guild.getId());
        if (type != InfractionType.NOTE && ModerationConfig.dmOnAction(cfg)) {
            sendDm(guild, target, inf, EmbedColor.resolve(cfg));
        }
        return inf;
    }

    /** Records a WARN, then applies the matching escalation step (if any) as a system case. */
    public Infraction warn(Guild guild, Member target, String modId, String reason) {
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(guild.getId());
        long ttl = ModerationConfig.warnTtlMillis(cfg);
        Long expiresAt = ttl > 0 ? System.currentTimeMillis() + ttl : null;

        Infraction warnCase = record(guild, InfractionType.WARN, target.getUser(), modId, reason,
                expiresAt, null);

        int activeWarns = repo.countActiveWarns(guild.getId(), target.getId(), System.currentTimeMillis());
        EscalationRule rule = ModerationConfig.escalationFor(cfg, activeWarns);
        if (rule != null) {
            applyEscalation(guild, target, rule);
        }
        return warnCase;
    }

    /** Marks the most recent active case of a type inactive (used by /unban, /unmute, /untimeout…). */
    public void deactivateLatest(String guildId, String userId, InfractionType type) {
        repo.deactivateLatest(guildId, userId, type.name());
    }

    /** Revokes a case by number. True if a row changed. */
    public boolean revoke(String guildId, int caseNumber) {
        return repo.revoke(guildId, caseNumber);
    }

    // --- auto-escalation -------------------------------------------------------

    private void applyEscalation(Guild guild, Member target, EscalationRule rule) {
        Member self = guild.getSelfMember();
        String reason = "Escalonamento automático (limiar de " + rule.threshold() + " avisos)";
        long now = System.currentTimeMillis();
        Long expires = rule.durationMs() == null ? null : now + rule.durationMs();

        if (!self.canInteract(target) && !rule.action().equals(ModerationConfig.ACTION_BAN)) {
            skipEscalation(guild, target.getUser(), rule, "cargo do alvo acima do bot");
            return;
        }
        try {
            switch (rule.action()) {
                case ModerationConfig.ACTION_TIMEOUT -> {
                    target.timeoutFor(Duration.ofMillis(rule.durationMs())).reason(reason).queue();
                    record(guild, InfractionType.TIMEOUT, target.getUser(), "system", reason,
                            expires, rule.durationMs());
                }
                case ModerationConfig.ACTION_KICK -> {
                    guild.kick(target).reason(reason).queue();
                    record(guild, InfractionType.KICK, target.getUser(), "system", reason, null, null);
                }
                case ModerationConfig.ACTION_BAN -> {
                    guild.ban(target.getUser(), 0, TimeUnit.SECONDS).reason(reason).queue();
                    record(guild, InfractionType.BAN, target.getUser(), "system", reason, null, null);
                }
                case ModerationConfig.ACTION_TEMPBAN -> {
                    guild.ban(target.getUser(), 0, TimeUnit.SECONDS).reason(reason).queue();
                    record(guild, InfractionType.TEMPBAN, target.getUser(), "system", reason,
                            expires, rule.durationMs());
                }
                case ModerationConfig.ACTION_MUTE -> applyMuteEscalation(guild, target, reason, expires, rule);
                default -> { /* unknown — ignored at parse time */ }
            }
        } catch (RuntimeException e) {
            log.warn("Escalation {} failed for {}/{}", rule.action(), guild.getId(), target.getId(), e);
            skipEscalation(guild, target.getUser(), rule, e.getMessage());
        }
    }

    private void applyMuteEscalation(Guild guild, Member target, String reason, Long expires,
                                     EscalationRule rule) {
        String roleId = ctx.database().guildConfig().findOrEmpty(guild.getId()).role("mutado");
        Role role = roleId == null ? null : guild.getRoleById(roleId);
        if (role == null || !guild.getSelfMember().canInteract(role)) {
            skipEscalation(guild, target.getUser(), rule, "cargo de mutado não configurado/inacessível");
            return;
        }
        guild.addRoleToMember(target, role).reason(reason).queue();
        ctx.database().mutes().add(guild.getId(), target.getId(), MuteRepository.TEXT,
                System.currentTimeMillis() + rule.durationMs());
        record(guild, InfractionType.MUTE, target.getUser(), "system", reason, expires, rule.durationMs());
    }

    private void skipEscalation(Guild guild, User target, EscalationRule rule, String why) {
        ChannelLog.post(ctx, guild.getId(), MODLOG_KEY, "## " + Emojis.of(Emojis.WARN, "⚠️") + " Escalonamento não aplicado\n---\n"
                + "" + Emojis.of(Emojis.MEMBER, "👤") + " **Membro** · <@" + target.getId() + ">\n"
                + "" + Emojis.of(Emojis.GROWTH, "📈") + " **Regra** · `" + rule.describe() + "`\n"
                + "-# Motivo: " + (why == null ? "desconhecido" : why));
    }

    // --- scheduled sweep -------------------------------------------------------

    /** Decays expired warns and lifts expired tempbans. Runs on a schedule + on boot. */
    public void sweepExpired() {
        if (ctx.jda() == null) {
            return;
        }
        List<Infraction> due = repo.listExpiredActive(System.currentTimeMillis());
        for (Infraction inf : due) {
            if (InfractionType.TEMPBAN.name().equals(inf.type())) {
                Guild guild = ctx.jda().getGuildById(inf.guildId());
                if (guild != null) {
                    guild.unban(UserSnowflake.fromId(inf.userId())).reason("Tempban expirado")
                            .queue(ok -> {}, err -> {});
                    ctx.database().actionLogs().log(inf.guildId(), "system", inf.userId(),
                            "TEMPBAN_LIFT_AUTO", null);
                }
            }
            repo.deactivate(inf.id());
        }
        if (!due.isEmpty()) {
            log.info("Swept {} expired infraction(s).", due.size());
        }
    }

    // --- helpers ---------------------------------------------------------------

    private String modlogMarkdown(Infraction inf, String modId, Long durationMs) {
        InfractionType type = inf.kind();
        String head = type == null ? "Caso" : type.emoji() + " " + type.label();
        StringBuilder sb = new StringBuilder("## " + head + " · Caso #" + inf.caseNumber() + "\n---\n")
                .append("" + Emojis.of(Emojis.MEMBER, "👤") + " **Membro** · <@").append(inf.userId()).append("> · `").append(inf.userId()).append("`\n---\n")
                .append("" + Emojis.of(Emojis.SHIELD, "🛡️") + " **Moderador** · ")
                .append("system".equals(modId) ? "`automático`" : "<@" + modId + ">").append('\n')
                .append("" + Emojis.of(Emojis.NOTE, "📝") + " **Motivo** · ")
                .append(inf.reason() == null ? "*não informado*" : inf.reason());
        if (durationMs != null) {
            sb.append("\n" + Emojis.of(Emojis.HOURGLASS, "⏳") + " **Duração** · `").append(Durations.format(durationMs)).append('`');
        }
        return sb.toString();
    }

    private void sendDm(Guild guild, User target, Infraction inf, int accent) {
        InfractionType type = inf.kind();
        String label = type == null ? "Moderação" : type.label();
        String emoji = type == null ? "" + Emojis.of(Emojis.SHIELD, "🛡️") + "" : type.emoji();
        StringBuilder body = new StringBuilder("Você recebeu **").append(label)
                .append("** em **").append(guild.getName()).append("** — Caso #").append(inf.caseNumber())
                .append(".\n" + Emojis.of(Emojis.NOTE, "📝") + " **Motivo** · ")
                .append(inf.reason() == null ? "*não informado*" : inf.reason());
        if (inf.durationMs() != null) {
            body.append("\n" + Emojis.of(Emojis.HOURGLASS, "⏳") + " **Duração** · `").append(Durations.format(inf.durationMs())).append('`');
        }
        Container dm = Panels.container(accent,
                Panels.text("## " + emoji + " " + label),
                Panels.divider(),
                Panels.text(body.toString()));
        target.openPrivateChannel()
                .flatMap(ch -> ch.sendMessageComponents(dm).useComponentsV2())
                .queue(ok -> {}, err -> {});
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
