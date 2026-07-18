package dev.davimf.basebot.modules.base.vip;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.database.postgres.VipGrantRepository;
import dev.davimf.basebot.database.postgres.VipPlanRepository;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.requests.restaction.ChannelAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Orquestra planos/grants VIP: cache de bônus (VipBonusSource), provisionamento e expiração. */
public final class VipService implements VipBonusSource {
    private static final Logger log = LoggerFactory.getLogger(VipService.class);

    private final BotContext ctx;
    private final VipPlanRepository plans;
    private final VipGrantRepository grants;
    // guildId -> (userId -> bônus efetivo). Volátil-por-referência: troca atômica no reload.
    private volatile Map<String, Map<String, VipBonusValue>> cache = Map.of();

    public VipService(BotContext ctx) {
        this.ctx = ctx;
        this.plans = ctx.database().vipPlans();
        this.grants = ctx.database().vipGrants();
    }

    @Override public VipBonusValue bonusFor(String guildId, String userId) {
        return cache.getOrDefault(guildId, Map.of()).getOrDefault(userId, VipBonusValue.NONE);
    }

    /** Recarrega o cache do banco. Se o Neon falhar, mantém o cache anterior e loga. */
    public void reload() {
        try {
            List<VipGrant> active = grants.activeGrants();
            Map<String, VipPlan> plansById = new HashMap<>();
            for (VipGrant g : active) {
                if (!plansById.containsKey(g.planId())) {
                    plans.findById(g.planId()).ifPresent(p -> plansById.put(p.id(), p));
                }
            }
            int max = VipConfig.DEFAULT_MAX_BONUS_PCT; // teto global; per-guild aplicado no compute abaixo
            this.cache = computeCache(active, plansById, Instant.now(), max);
        } catch (RuntimeException e) {
            log.error("VIP: reload do cache falhou; mantendo cache anterior", e);
        }
    }

    /** Puro: monta o mapa de bônus, ignorando grants vencidos e planos ausentes. */
    public static Map<String, Map<String, VipBonusValue>> computeCache(
            List<VipGrant> grants, Map<String, VipPlan> plansById, Instant now, int maxPct) {
        Map<String, Map<String, VipBonusValue>> out = new ConcurrentHashMap<>();
        for (VipGrant g : grants) {
            if (!VipExpiry.effective(g, now)) continue;
            VipPlan p = plansById.get(g.planId());
            if (p == null) continue;
            int xp = VipBonus.cap(p.xpBonusPct(), maxPct);
            int eco = VipBonus.cap(p.ecoBonusPct(), maxPct);
            out.computeIfAbsent(g.guildId(), k -> new ConcurrentHashMap<>())
               .put(g.userId(), new VipBonusValue(xp, eco));
        }
        return out;
    }

    /** Invalida o bônus de um usuário no cache (após conceder/revogar/expirar). */
    void invalidate(String guildId, String userId) {
        Map<String, VipBonusValue> g = cache.get(guildId);
        if (g != null) g.remove(userId);
    }
    void put(String guildId, String userId, VipBonusValue v) {
        cache.computeIfAbsent(guildId, k -> new ConcurrentHashMap<>()).put(userId, v);
    }

    VipPlanRepository plans() { return plans; }
    VipGrantRepository grants() { return grants; }
    BotContext ctx() { return ctx; }

    /** Puro: mensagem de rejeição da regra "1 VIP ativo por usuário", ou {@code null} se ok. */
    static String rejectIfDuplicate(Optional<VipGrant> existing) {
        return existing.isPresent() ? "Este membro já tem um VIP ativo. Revogue antes de conceder outro." : null;
    }

    /** Grant ativo (não vencido, active=true) do usuário no guild, se houver. */
    public Optional<VipGrant> activeGrant(String guildId, String userId) {
        return grants().findActiveByUser(guildId, userId);
    }

    public record GrantResult(boolean ok, String message, VipGrant grant) {}

    /** Concede o VIP: cria/reusa recursos Discord (cargo-controle, call, cargo-VIP) e grava o
     *  grant. BLOQUEANTE — usa {@code .complete()} em toda a chamada; o chamador deve rodar isto
     *  em {@code ctx.scheduler().executor()}, nunca na thread de eventos do JDA. Idempotente: em
     *  retry após falha parcial, os ids já salvos no grant são reusados (não recriados). */
    public GrantResult grant(Guild guild, Member target, VipPlan plan, Long durationMinutes, String grantedBy) {
        String dup = rejectIfDuplicate(activeGrant(guild.getId(), target.getId()));
        if (dup != null) {
            return new GrantResult(false, dup, null);
        }

        Long minutes = durationMinutes != null ? durationMinutes : plan.defaultDurationMinutes();
        Instant expiresAt = minutes == null ? null : Instant.now().plus(java.time.Duration.ofMinutes(minutes));

        String id = UUID.randomUUID().toString();
        Instant now = Instant.now();
        VipGrant grant = new VipGrant(id, guild.getId(), plan.id(), target.getId(),
                null, null, plan.revealDefault(), now, expiresAt, true,
                VipProvisionStatus.PENDING, null, grantedBy, null, null, now);
        grants().upsertActive(grant);

        String callId = null;
        String controlRoleId = null;
        try {
            if (VipProvision.forResource(plan.useControlRole(), null, false) == VipProvision.ResourceAction.CREATE) {
                Role controlRole = guild.createRole()
                        .setName("VIP • " + target.getUser().getName())
                        .complete();
                controlRoleId = controlRole.getId();
                guild.addRoleToMember(target, controlRole).complete();
            }

            if (plan.hasCall()) {
                Category category = plan.discordCategoryId() == null
                        ? null : guild.getCategoryById(plan.discordCategoryId());
                ChannelAction<VoiceChannel> action = guild
                        .createVoiceChannel(target.getUser().getName() + " VIP", category)
                        .addPermissionOverride(guild.getPublicRole(), null,
                                EnumSet.of(Permission.VIEW_CHANNEL, Permission.VOICE_CONNECT))
                        .addMemberPermissionOverride(target.getIdLong(),
                                EnumSet.of(Permission.VIEW_CHANNEL, Permission.VOICE_CONNECT), null);
                if (controlRoleId != null) {
                    action = action.addRolePermissionOverride(Long.parseLong(controlRoleId),
                            EnumSet.of(Permission.VIEW_CHANNEL, Permission.VOICE_CONNECT), null);
                }
                VoiceChannel call = action.reason("VIP de " + target.getUser().getName()).complete();
                callId = call.getId();
            }

            if (plan.vipRoleId() != null) {
                Role vipRole = guild.getRoleById(plan.vipRoleId());
                if (vipRole != null && guild.getSelfMember().canInteract(vipRole)) {
                    guild.addRoleToMember(target, vipRole).complete();
                }
            }

            grants().updateProvision(id, VipProvisionStatus.ACTIVE, null, callId, controlRoleId);
            int max = VipConfig.DEFAULT_MAX_BONUS_PCT;
            put(guild.getId(), target.getId(),
                    new VipBonusValue(VipBonus.cap(plan.xpBonusPct(), max), VipBonus.cap(plan.ecoBonusPct(), max)));
            VipGrant activeGrant = new VipGrant(id, guild.getId(), plan.id(), target.getId(),
                    callId, controlRoleId, plan.revealDefault(), now, expiresAt, true,
                    VipProvisionStatus.ACTIVE, null, grantedBy, null, null, Instant.now());
            return new GrantResult(true, null, activeGrant);
        } catch (RuntimeException e) {
            log.error("VIP: falha ao provisionar grant {} no guild {}", id, guild.getId(), e);
            grants().updateProvision(id, VipProvisionStatus.PROVISION_FAILED, e.getMessage(), callId, controlRoleId);
            VipGrant failedGrant = new VipGrant(id, guild.getId(), plan.id(), target.getId(),
                    callId, controlRoleId, plan.revealDefault(), now, expiresAt, true,
                    VipProvisionStatus.PROVISION_FAILED, e.getMessage(), grantedBy, null, null, Instant.now());
            return new GrantResult(false, "falha ao provisionar: " + e.getMessage(), failedGrant);
        }
    }
}
