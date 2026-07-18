package dev.davimf.basebot.database.postgres;

import dev.davimf.basebot.modules.base.vip.VipPlan;
import java.util.List;
import java.util.Optional;

public interface VipPlanRepository {
    void upsert(VipPlan p);
    Optional<VipPlan> findById(String id);
    List<VipPlan> listByGuild(String guildId);
    List<VipPlan> listEnabledByGuild(String guildId);
    void delete(String id);
}
