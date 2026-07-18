package dev.davimf.basebot.modules.base.vip;

public record VipBonusValue(int xpPct, int ecoPct) {
    public static final VipBonusValue NONE = new VipBonusValue(0, 0);
    public boolean isNone() { return xpPct == 0 && ecoPct == 0; }
}
