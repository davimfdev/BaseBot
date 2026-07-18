package dev.davimf.basebot.database.postgres;

import dev.davimf.basebot.modules.base.vip.VipGrant;
import dev.davimf.basebot.modules.base.vip.VipProvisionStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface VipGrantRepository {
    void upsertActive(VipGrant g);
    Optional<VipGrant> findActiveByUser(String guildId, String userId);
    Optional<VipGrant> findById(String id);
    List<VipGrant> activeGrants();
    List<VipGrant> listActiveByGuild(String guildId);
    List<VipGrant> dueForExpiry(Instant now);
    void updateProvision(String id, VipProvisionStatus status, String error, String callChannelId, String controlRoleId);
    void updateReveal(String id, boolean reveal);
    void deactivate(String id, VipProvisionStatus status, String revokedBy, Instant revokedAt);
}
