package dev.davimf.basebot.modules.base.vip;

/** Fonte de bônus VIP consultada nos sites de payout. VipService implementa via cache. */
public interface VipBonusSource {
    VipBonusValue bonusFor(String guildId, String userId);

    VipBonusSource NONE = (g, u) -> VipBonusValue.NONE;
}
