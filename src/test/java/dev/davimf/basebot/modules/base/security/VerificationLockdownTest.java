package dev.davimf.basebot.modules.base.security;

import dev.davimf.basebot.database.model.GuildConfig;
import net.dv8tion.jda.api.Permission;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VerificationLockdownTest {

    private static GuildConfig cfg(Map<String, String> channels, Map<String, String> roles) {
        return new GuildConfig("g1", null, null, channels, roles, Map.of(), List.of(), Map.of());
    }

    @Test
    void excludedIdsHaveLogsVerifyAntispamNoNullsNoDupes() {
        Map<String, String> channels = Map.of(
                "log-comandos", "100",
                "log-mensagens", "101",
                SecurityConfig.CHANNEL_VERIFY, "200",
                SecurityConfig.CHANNEL_ANTISPAM, "300");
        Set<String> ids = VerificationLockdown.excludedChannelIds(cfg(channels, Map.of()));
        assertTrue(ids.containsAll(Set.of("100", "101", "200", "300")));
        // canal comum (não log/verif/antispam) não entra
        assertFalse(ids.contains("999"));
    }

    @Test
    void excludedIdsIgnoreUnsetKeys() {
        // nenhum canal configurado -> conjunto vazio (sem NPE, sem nulls)
        Set<String> ids = VerificationLockdown.excludedChannelIds(cfg(Map.of(), Map.of()));
        assertTrue(ids.isEmpty());
    }

    @Test
    void openWhenViewNotDenied() {
        assertTrue(VerificationLockdown.isOpenForEveryone(null));
        assertTrue(VerificationLockdown.isOpenForEveryone(Set.of()));
        assertTrue(VerificationLockdown.isOpenForEveryone(Set.of(Permission.MESSAGE_SEND)));
        assertFalse(VerificationLockdown.isOpenForEveryone(Set.of(Permission.VIEW_CHANNEL)));
    }

    @Test
    void signatureNeedsBothDenyAndAllow() {
        assertTrue(VerificationLockdown.matchesLockdownSignature(
                Set.of(Permission.VIEW_CHANNEL), Set.of(Permission.VIEW_CHANNEL)));
        assertFalse(VerificationLockdown.matchesLockdownSignature(
                Set.of(Permission.VIEW_CHANNEL), Set.of()));               // membro não permite
        assertFalse(VerificationLockdown.matchesLockdownSignature(
                Set.of(), Set.of(Permission.VIEW_CHANNEL)));               // everyone não nega
        assertFalse(VerificationLockdown.matchesLockdownSignature(null, null));
    }
}
