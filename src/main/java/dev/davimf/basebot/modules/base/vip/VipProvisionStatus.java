package dev.davimf.basebot.modules.base.vip;

/** Estado do provisionamento dos recursos Discord de um grant (separado de vip_grants.active). */
public enum VipProvisionStatus {
    PENDING, ACTIVE, PROVISION_FAILED, REVOKED, EXPIRED;

    public String db() { return name().toLowerCase(); }

    public static VipProvisionStatus fromDb(String s) {
        if (s == null || s.isBlank()) return ACTIVE;
        return VipProvisionStatus.valueOf(s.trim().toUpperCase());
    }
}
