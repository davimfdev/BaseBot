package dev.davimf.basebot.modules.facs.perms;

import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.modules.facs.perms.ManagerPermissions.Capability;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManagerPermissionsTest {

    private static GuildConfig cfg(Map<String, String> roles, Map<String, String> settings) {
        return new GuildConfig("g1", null, null, Map.of(), roles, Map.of(), List.of(), settings);
    }

    @Test
    void defaultsApplyWhenUnset() {
        List<String> p = ManagerPermissions.principals(cfg(Map.of(), Map.of()), Capability.FINANCEIRO);
        assertTrue(p.containsAll(List.of("gerente-vendas", "lider", "sub-lider", "gerente-geral")));
        assertEquals(List.of("lider", "sub-lider", "gerente-geral"),
                ManagerPermissions.principals(cfg(Map.of(), Map.of()), Capability.PUNICOES));
    }

    @Test
    void allowedRoleIdsResolvesHierarchyKeysAndSkipsUnconfigured() {
        GuildConfig c = cfg(Map.of("gerente-vendas", "100", "lider", "200"), Map.of());
        assertEquals(Set.of("100", "200"), ManagerPermissions.allowedRoleIds(c, Capability.FINANCEIRO));
    }

    @Test
    void allowedRoleIdsResolvesRawRolePrincipal() {
        GuildConfig c = cfg(Map.of(), Map.of("perm:farm", "role:999"));
        assertEquals(Set.of("999"), ManagerPermissions.allowedRoleIds(c, Capability.FARM));
    }

    @Test
    void grantMaterializesDefaultsThenAddsPrincipal() {
        GuildConfig c = cfg(Map.of(), Map.of());
        String csv = ManagerPermissions.grant(c, Capability.PUNICOES, "role:5");
        GuildConfig c2 = cfg(Map.of(), Map.of("perm:punicoes", csv));
        assertTrue(ManagerPermissions.grants(c2, Capability.PUNICOES, "role:5"));
        assertTrue(ManagerPermissions.grants(c2, Capability.PUNICOES, "lider"));
    }

    @Test
    void revokeToEmptyMeansAdministratorOnly() {
        GuildConfig c = cfg(Map.of(), Map.of("perm:acoes", "gerente-elite"));
        String csv = ManagerPermissions.revoke(c, Capability.ACOES, "gerente-elite");
        assertEquals("", csv);
        assertTrue(ManagerPermissions.principals(cfg(Map.of(), Map.of("perm:acoes", csv)),
                Capability.ACOES).isEmpty());
    }

    @Test
    void isAllowedAdministratorBypassesAndMembershipMatches() {
        assertTrue(ManagerPermissions.isAllowed(Set.of(), true, Set.of()));
        assertTrue(ManagerPermissions.isAllowed(Set.of("100"), false, Set.of("100")));
        assertFalse(ManagerPermissions.isAllowed(Set.of("x"), false, Set.of("100")));
    }

    @Test
    void customIdTokenRoundTrips() {
        assertEquals("role-7", ManagerPermissions.customIdToken("role:7"));
        assertEquals("role:7", ManagerPermissions.principalFromToken("role-7"));
        assertEquals("gerente-farm", ManagerPermissions.customIdToken("gerente-farm"));
        assertEquals("gerente-farm", ManagerPermissions.principalFromToken("gerente-farm"));
    }

    @Test
    void freeRolePrincipalsAreCollected() {
        GuildConfig c = cfg(Map.of(), Map.of("perm:farm", "gerente-farm,role:42", "perm:acoes", "role:42"));
        assertEquals(List.of("role:42"), ManagerPermissions.freeRolePrincipals(c));
    }
}
