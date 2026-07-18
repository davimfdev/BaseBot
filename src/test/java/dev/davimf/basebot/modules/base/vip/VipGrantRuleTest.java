package dev.davimf.basebot.modules.base.vip;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class VipGrantRuleTest {
    @Test void rejects_duplicate() {
        var g = new VipGrant("i","g","p","u",null,null,true,Instant.EPOCH,null,true,
                VipProvisionStatus.ACTIVE,null,null,null,null,Instant.EPOCH);
        assertNotNull(VipService.rejectIfDuplicate(Optional.of(g)));
        assertNull(VipService.rejectIfDuplicate(Optional.empty()));
    }
}
