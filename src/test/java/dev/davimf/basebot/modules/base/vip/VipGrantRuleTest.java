package dev.davimf.basebot.modules.base.vip;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class VipGrantRuleTest {
    @Test void rejects_active_duplicate() {
        var g = new VipGrant("i","g","p","u",null,null,true,Instant.EPOCH,null,true,
                VipProvisionStatus.ACTIVE,null,null,null,null,Instant.EPOCH);
        assertNotNull(VipService.rejectIfDuplicate(Optional.of(g)));
    }

    @Test void does_not_reject_provision_failed_grant_resumable() {
        var g = new VipGrant("i","g","p","u",null,null,true,Instant.EPOCH,null,true,
                VipProvisionStatus.PROVISION_FAILED,"boom",null,null,null,Instant.EPOCH);
        assertNull(VipService.rejectIfDuplicate(Optional.of(g)));
    }

    @Test void empty_is_not_a_duplicate() {
        assertNull(VipService.rejectIfDuplicate(Optional.empty()));
    }
}
