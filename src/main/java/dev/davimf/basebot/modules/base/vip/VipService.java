package dev.davimf.basebot.modules.base.vip;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.database.postgres.VipGrantRepository;
import dev.davimf.basebot.database.postgres.VipPlanRepository;
import dev.davimf.basebot.util.EmbedColor;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.UserSnowflake;
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
    // guildId -> (callChannelId -> grant). Índice O(1) das calls VIP ativas, para o
    // VipVoiceListener resolver "este canal de voz é uma call VIP?" sem I/O. Mantido em conjunto
    // com `cache` (mesmas trocas atômicas em reload/grant/revoke), mas é um mapa separado.
    private volatile Map<String, Map<String, VipGrant>> callIndex = Map.of();

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
            Instant now = Instant.now();
            this.cache = computeCache(active, plansById, now, max);
            this.callIndex = computeCallIndex(active, now);
        } catch (RuntimeException e) {
            log.error("VIP: reload do cache falhou; mantendo cache anterior", e);
        }
    }

    /** Puro: monta o índice guildId -> (callChannelId -> grant), ignorando grants vencidos e
     *  grants sem call provisionada. */
    public static Map<String, Map<String, VipGrant>> computeCallIndex(List<VipGrant> grants, Instant now) {
        Map<String, Map<String, VipGrant>> out = new ConcurrentHashMap<>();
        for (VipGrant g : grants) {
            if (!VipExpiry.effective(g, now)) continue;
            if (g.callChannelId() == null) continue;
            out.computeIfAbsent(g.guildId(), k -> new ConcurrentHashMap<>()).put(g.callChannelId(), g);
        }
        return out;
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

    /** Adiciona/atualiza uma call VIP no índice (chamado por {@link #grant}). Sem-op se o grant
     *  ainda não tem call provisionada. */
    private void indexCall(String guildId, String callChannelId, VipGrant grant) {
        if (callChannelId == null) return;
        callIndex.computeIfAbsent(guildId, k -> new ConcurrentHashMap<>()).put(callChannelId, grant);
    }
    /** Remove uma call VIP do índice (chamado por {@link #revoke}). Sem-op se o grant não tinha call. */
    private void deindexCall(String guildId, String callChannelId) {
        if (callChannelId == null) return;
        Map<String, VipGrant> g = callIndex.get(guildId);
        if (g != null) g.remove(callChannelId);
    }

    /** Lookup O(1) sem I/O: o grant cuja call VIP é {@code channelId}, ou {@code null} se esse
     *  canal não for (ou não for mais) uma call VIP ativa. Usado pelo {@code VipVoiceListener}. */
    public VipGrant grantByCallId(String guildId, String channelId) {
        return callIndex.getOrDefault(guildId, Map.of()).get(channelId);
    }

    VipPlanRepository plans() { return plans; }
    VipGrantRepository grants() { return grants; }
    BotContext ctx() { return ctx; }

    /** Puro: mensagem de rejeição da regra "1 VIP ativo por usuário", ou {@code null} se ok/retomável.
     *  Um grant existente com {@code provisionStatus == PROVISION_FAILED} não é duplicata: é um
     *  provisionamento parcial que {@link #grant} deve retomar em vez de rejeitar. */
    static String rejectIfDuplicate(Optional<VipGrant> existing) {
        if (existing.isEmpty()) return null;
        if (existing.get().provisionStatus() == VipProvisionStatus.PROVISION_FAILED) return null;
        return "Este membro já tem um VIP ativo. Revogue antes de conceder outro.";
    }

    /** Grant ativo (não vencido, active=true) do usuário no guild, se houver. */
    public Optional<VipGrant> activeGrant(String guildId, String userId) {
        return grants().findActiveByUser(guildId, userId);
    }

    public record GrantResult(boolean ok, String message, VipGrant grant) {}

    /** Concede o VIP: cria/reusa recursos Discord (cargo-controle, call, cargo-VIP) e grava o
     *  grant. BLOQUEANTE — usa {@code .complete()} em toda a chamada; o chamador deve rodar isto
     *  em {@code ctx.scheduler().executor()}, nunca na thread de eventos do JDA. Idempotente: se já
     *  existe um grant ativo do usuário com {@code provisionStatus == PROVISION_FAILED} (falha
     *  parcial anterior), este método RETOMA esse mesmo grant — reusa seu id de linha e os ids de
     *  recurso (cargo-controle/call) já salvos, sem inserir uma linha nova — em vez de rejeitar como
     *  duplicata. Qualquer outro grant ativo existente (PENDING/ACTIVE) é rejeitado. */
    public GrantResult grant(Guild guild, Member target, VipPlan plan, Long durationMinutes, String grantedBy) {
        Optional<VipGrant> existingOpt = activeGrant(guild.getId(), target.getId());
        String dup = rejectIfDuplicate(existingOpt);
        if (dup != null) {
            return new GrantResult(false, dup, null);
        }

        Long minutes = durationMinutes != null ? durationMinutes : plan.defaultDurationMinutes();

        boolean resuming = existingOpt.isPresent()
                && existingOpt.get().provisionStatus() == VipProvisionStatus.PROVISION_FAILED;

        String id;
        Instant now;
        String savedCallId;
        String savedControlRoleId;
        Instant expiresAt;
        if (resuming) {
            VipGrant existing = existingOpt.get();
            id = existing.id();
            now = existing.grantedAt();
            savedCallId = existing.callChannelId();
            savedControlRoleId = existing.controlRoleId();
            expiresAt = existing.expiresAt();
            // não insere linha nova: a PROVISION_FAILED existente já está persistida (active=true).
        } else {
            id = UUID.randomUUID().toString();
            now = Instant.now();
            savedCallId = null;
            savedControlRoleId = null;
            expiresAt = minutes == null ? null : Instant.now().plus(java.time.Duration.ofMinutes(minutes));
            VipGrant grant = new VipGrant(id, guild.getId(), plan.id(), target.getId(),
                    null, null, plan.revealDefault(), now, expiresAt, true,
                    VipProvisionStatus.PENDING, null, grantedBy, null, null, now);
            grants().upsertActive(grant);
        }

        String callId = null;
        String controlRoleId = null;
        try {
            VipProvision.ResourceAction roleAction = VipProvision.forResource(plan.useControlRole(),
                    savedControlRoleId, savedControlRoleId != null && guild.getRoleById(savedControlRoleId) != null);
            if (roleAction == VipProvision.ResourceAction.CREATE) {
                Role controlRole = guild.createRole()
                        .setName("VIP • " + target.getUser().getName())
                        .setPermissions(0L)
                        .complete();
                controlRoleId = controlRole.getId();
                guild.addRoleToMember(target, controlRole).complete();
            } else if (roleAction == VipProvision.ResourceAction.REUSE) {
                controlRoleId = savedControlRoleId;
                Role controlRole = guild.getRoleById(controlRoleId);
                if (controlRole != null) {
                    guild.addRoleToMember(target, controlRole).complete();
                }
            }

            VipProvision.ResourceAction callAction = VipProvision.forResource(plan.hasCall(),
                    savedCallId, savedCallId != null && guild.getVoiceChannelById(savedCallId) != null);
            if (callAction == VipProvision.ResourceAction.CREATE) {
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
            } else if (callAction == VipProvision.ResourceAction.REUSE) {
                callId = savedCallId;
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
            indexCall(guild.getId(), callId, activeGrant);
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

    /** Revoga o VIP: remove o cargo-VIP do dono, remove o cargo-controle de TODOS os membros que o
     *  têm (não só o dono — pode ter sido concedido a convidados), oculta a call (nega
     *  {@code VIEW_CHANNEL}/{@code VOICE_CONNECT} de {@code @everyone}), desativa o grant
     *  ({@link VipProvisionStatus#EXPIRED} ou {@link VipProvisionStatus#REVOKED}) e invalida o
     *  cache de bônus. <b>Não deleta</b> a call nem o cargo-controle — ambos são reutilizados no
     *  próximo grant do mesmo usuário. Sem-op seguro se não houver grant ativo. BLOQUEANTE — usa
     *  {@code .complete()} nas operações do Discord; o chamador deve rodar isto em
     *  {@code ctx.scheduler().executor()}, nunca na thread de eventos do JDA. */
    public void revoke(Guild guild, String userId, String revokedBy, boolean expired) {
        Optional<VipGrant> existingOpt = activeGrant(guild.getId(), userId);
        if (existingOpt.isEmpty()) {
            return; // nada ativo para revogar.
        }
        VipGrant grant = existingOpt.get();
        VipPlan plan = plans().findById(grant.planId()).orElse(null);

        if (plan != null && plan.vipRoleId() != null) {
            Role vipRole = guild.getRoleById(plan.vipRoleId());
            if (vipRole != null) {
                try {
                    guild.removeRoleFromMember(UserSnowflake.fromId(userId), vipRole).complete();
                } catch (RuntimeException e) {
                    log.warn("VIP: falha ao remover cargo-VIP de {} no guild {}", userId, guild.getId(), e);
                }
            }
        }

        if (grant.controlRoleId() != null) {
            Role controlRole = guild.getRoleById(grant.controlRoleId());
            if (controlRole != null) {
                for (Member m : guild.getMembersWithRoles(controlRole)) {
                    try {
                        guild.removeRoleFromMember(m, controlRole).complete();
                    } catch (RuntimeException e) {
                        log.warn("VIP: falha ao remover cargo-controle de {} no guild {}", m.getId(), guild.getId(), e);
                    }
                }
            }
        }

        if (grant.callChannelId() != null) {
            VoiceChannel call = guild.getVoiceChannelById(grant.callChannelId());
            if (call != null) {
                try {
                    call.getManager().putPermissionOverride(guild.getPublicRole(), null,
                            java.util.EnumSet.of(Permission.VIEW_CHANNEL, Permission.VOICE_CONNECT)).complete();
                } catch (RuntimeException e) {
                    log.warn("VIP: falha ao ocultar a call {} no guild {}", grant.callChannelId(), guild.getId(), e);
                }
            }
        }

        VipProvisionStatus status = expired ? VipProvisionStatus.EXPIRED : VipProvisionStatus.REVOKED;
        grants().deactivate(grant.id(), status, revokedBy, Instant.now());
        invalidate(guild.getId(), userId);
        deindexCall(guild.getId(), grant.callChannelId());

        String planName = plan != null ? plan.name() : "VIP";
        String reasonText = expired ? "expirou" : "foi revogado";
        int accent = EmbedColor.resolve(ctx.database().guildConfig().findOrEmpty(guild.getId()));
        guild.getJDA().retrieveUserById(userId)
                .flatMap(User::openPrivateChannel)
                .flatMap(pc -> pc.sendMessageComponents(Panels.container(accent,
                                Panels.text("Seu VIP **" + planName + "** em **" + guild.getName() + "** " + reasonText + ".")))
                        .useComponentsV2())
                .queue(ok -> { }, err -> { });
    }

    /** Nível de boost mínimo do servidor para permitir emoji custom em cargo (mesmo limite do painel). */
    private static final int EMOJI_MIN_BOOST_TIER = 2;

    /** Renomeia a call do grant. BLOQUEANTE (usa {@code .complete()}) — o chamador deve rodar isto
     *  em {@code ctx.scheduler().executor()}, nunca na thread de eventos do JDA. Opera só sobre o
     *  recurso desse grant ({@code grant.callChannelId()}); sem-op se o guild ou a call já não
     *  existirem mais (recurso pode ter sido excluído fora do bot). */
    public void renameCall(VipGrant grant, String newName) {
        Guild guild = ctx.jda().getGuildById(grant.guildId());
        if (guild == null || grant.callChannelId() == null) {
            return;
        }
        VoiceChannel call = guild.getVoiceChannelById(grant.callChannelId());
        if (call == null) {
            return;
        }
        call.getManager().setName(newName).complete();
    }

    /** Renomeia o cargo-controle do grant. BLOQUEANTE — mesmas regras de {@link #renameCall}. */
    public void renameControlRole(VipGrant grant, String newName) {
        Guild guild = ctx.jda().getGuildById(grant.guildId());
        if (guild == null || grant.controlRoleId() == null) {
            return;
        }
        Role role = guild.getRoleById(grant.controlRoleId());
        if (role == null) {
            return;
        }
        role.getManager().setName(newName).complete();
    }

    /** Alterna reveal-on-occupancy e persiste. Puramente uma escrita no Postgres (sem chamada ao
     *  Discord), retorna o novo valor para o chamador re-renderizar. */
    public boolean toggleReveal(VipGrant grant) {
        boolean next = !grant.revealOnOccupancy();
        grants().updateReveal(grant.id(), next);
        return next;
    }

    /** Define o emoji (unicode) do cargo-controle do grant. BLOQUEANTE. Re-checa o tier de boost
     *  mínimo do servidor mesmo que o botão do painel já venha desabilitado abaixo do tier 2 —
     *  defesa em profundidade contra customIds manipulados. */
    public void setRoleIcon(VipGrant grant, String emoji) {
        Guild guild = ctx.jda().getGuildById(grant.guildId());
        if (guild == null || grant.controlRoleId() == null) {
            return;
        }
        if (guild.getBoostTier().getKey() < EMOJI_MIN_BOOST_TIER) {
            return;
        }
        Role role = guild.getRoleById(grant.controlRoleId());
        if (role == null) {
            return;
        }
        role.getManager().setIcon(emoji == null || emoji.isBlank() ? null : emoji.trim()).complete();
    }

    /** Concede acesso ao membro: adiciona o cargo-controle se o plano usa um (grant.controlRoleId()
     *  presente), senão libera um override pessoal ({@code VIEW_CHANNEL}+{@code VOICE_CONNECT}) na
     *  call. BLOQUEANTE. Sem-op se o membro não pertence ao guild do grant, ou se o recurso alvo já
     *  não existe. */
    public void grantAccess(VipGrant grant, Member member) {
        Guild guild = member.getGuild();
        if (!guild.getId().equals(grant.guildId())) {
            return;
        }
        if (grant.controlRoleId() != null) {
            Role role = guild.getRoleById(grant.controlRoleId());
            if (role != null) {
                guild.addRoleToMember(member, role).complete();
            }
            return;
        }
        if (grant.callChannelId() != null) {
            VoiceChannel call = guild.getVoiceChannelById(grant.callChannelId());
            if (call != null) {
                call.getManager().putMemberPermissionOverride(member.getIdLong(),
                        EnumSet.of(Permission.VIEW_CHANNEL, Permission.VOICE_CONNECT), null).complete();
            }
        }
    }

    /** Revoga acesso do membro: remove o cargo-controle se o plano usa um, senão remove o override
     *  pessoal na call. BLOQUEANTE. Mesmas guardas de {@link #grantAccess}. */
    public void revokeAccess(VipGrant grant, Member member) {
        Guild guild = member.getGuild();
        if (!guild.getId().equals(grant.guildId())) {
            return;
        }
        if (grant.controlRoleId() != null) {
            Role role = guild.getRoleById(grant.controlRoleId());
            if (role != null) {
                guild.removeRoleFromMember(member, role).complete();
            }
            return;
        }
        if (grant.callChannelId() != null) {
            VoiceChannel call = guild.getVoiceChannelById(grant.callChannelId());
            if (call != null) {
                call.getManager().removePermissionOverride(member).complete();
            }
        }
    }

    /** O membro tem acesso permitido a esta call VIP? Dono do grant, ou tem o cargo-controle, ou
     *  tem um override pessoal na call liberando {@code VIEW_CHANNEL}+{@code VOICE_CONNECT}. Só lê
     *  estado já em cache local do JDA (não chama a API) — usado pelo {@code VipVoiceListener} para
     *  contar humanos permitidos na call. Bots nunca contam: o chamador deve filtrá-los antes. */
    boolean isAllowedInCall(Member member, VipGrant grant, VoiceChannel call) {
        if (member.getId().equals(grant.userId())) {
            return true;
        }
        if (grant.controlRoleId() != null) {
            Role controlRole = member.getGuild().getRoleById(grant.controlRoleId());
            if (controlRole != null && member.getRoles().contains(controlRole)) {
                return true;
            }
        }
        PermissionOverride override = call.getPermissionOverride(member);
        return override != null
                && override.getAllowed().contains(Permission.VIEW_CHANNEL)
                && override.getAllowed().contains(Permission.VOICE_CONNECT);
    }

    /** A call está atualmente revelada para {@code @everyone} (VIEW_CHANNEL não negado)? Sem
     *  override explícito de {@code @everyone} conta como revelada (nada nega a visão). Só lê
     *  estado já em cache local do JDA. */
    boolean isRevealedToEveryone(VoiceChannel call) {
        PermissionOverride everyoneOverride = call.getPermissionOverride(call.getGuild().getPublicRole());
        return everyoneOverride == null || !everyoneOverride.getDenied().contains(Permission.VIEW_CHANNEL);
    }

    /** Revela ou oculta a call para {@code @everyone}. Revelar libera {@code VIEW_CHANNEL} mas
     *  mantém {@code VOICE_CONNECT} negado (só quem tem acesso via cargo-controle/override entra);
     *  ocultar nega os dois, igual ao estado inicial pós-{@link #grant}. BLOQUEANTE — usa
     *  {@code .complete()}; o chamador deve rodar isto em {@code ctx.scheduler().executor()}, nunca
     *  na thread de eventos do JDA. */
    void setEveryoneView(VoiceChannel call, boolean reveal) {
        Role everyone = call.getGuild().getPublicRole();
        if (reveal) {
            call.getManager().putPermissionOverride(everyone,
                    EnumSet.of(Permission.VIEW_CHANNEL), EnumSet.of(Permission.VOICE_CONNECT)).complete();
        } else {
            call.getManager().putPermissionOverride(everyone,
                    null, EnumSet.of(Permission.VIEW_CHANNEL, Permission.VOICE_CONNECT)).complete();
        }
    }
}
