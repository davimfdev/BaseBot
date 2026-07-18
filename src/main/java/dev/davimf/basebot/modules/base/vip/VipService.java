package dev.davimf.basebot.modules.base.vip;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.database.postgres.VipGrantRepository;
import dev.davimf.basebot.database.postgres.VipPlanRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
}
