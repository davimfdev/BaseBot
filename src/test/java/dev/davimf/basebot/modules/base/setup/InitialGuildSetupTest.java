package dev.davimf.basebot.modules.base.setup;

import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.database.postgres.GuildConfigRepository;
import net.dv8tion.jda.api.entities.Guild;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InitialGuildSetupTest {

    @Test
    void setupIsNeededOnlyWhenGuildHasNoConfigRow() {
        assertTrue(InitialGuildSetup.shouldSetup(Optional.empty()));
        assertFalse(InitialGuildSetup.shouldSetup(Optional.of(GuildConfig.empty("guild"))));
    }

    @Test
    void vaultGuildIsExcludedFromAutomaticSetup() {
        assertTrue(InitialGuildSetup.isVaultGuild("vault", "vault"));
        assertFalse(InitialGuildSetup.isVaultGuild("normal", "vault"));
    }

    @Test
    void joinedGuildWithoutConfigRunsInitialSetupAfterSnapshot() {
        AtomicBoolean ran = new AtomicBoolean();
        boolean created = InitialGuildSetup.runGuild(guild("new-guild"), emptyRepository(),
                ignored -> ran.set(true));

        assertTrue(created);
        assertTrue(ran.get());
    }

    private static Guild guild(String id) {
        return (Guild) Proxy.newProxyInstance(Guild.class.getClassLoader(), new Class<?>[]{Guild.class},
                (proxy, method, args) -> method.getName().equals("getId") ? id : null);
    }

    private static GuildConfigRepository emptyRepository() {
        return new GuildConfigRepository() {
            @Override public Optional<GuildConfig> find(String guildId) { return Optional.empty(); }
            @Override public void save(GuildConfig config) {}
            @Override public void setToggle(String guildId, String key, boolean value) {}
        };
    }
}
