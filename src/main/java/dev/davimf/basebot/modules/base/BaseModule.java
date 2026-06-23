package dev.davimf.basebot.modules.base;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.modules.BotModule;
import dev.davimf.basebot.modules.ModuleRegistry;
import dev.davimf.basebot.modules.base.commands.AddCargoCommand;
import dev.davimf.basebot.modules.base.commands.BanCommand;
import dev.davimf.basebot.modules.base.commands.ClearCommand;
import dev.davimf.basebot.modules.base.commands.KickCommand;
import dev.davimf.basebot.modules.base.commands.RemoveCargoCommand;
import dev.davimf.basebot.modules.base.commands.PingCommand;
import dev.davimf.basebot.modules.base.commands.SetupCommand;
import dev.davimf.basebot.modules.base.commands.UnbanCommand;
import dev.davimf.basebot.modules.base.listeners.GeneralLoggingListener;
import dev.davimf.basebot.modules.base.setup.SetupComponentHandler;

/**
 * Module 1 — Base &amp; Utility (BOTSPECS §Module 1).
 *
 * <p>Owns configuration ({@code /setup}), moderation ({@code /kick}, {@code /ban},
 * {@code /mute}…), role management ({@code /addcargo}…), embeds, and the general
 * event-logging listeners. Only {@code /ping} is implemented here as the reference
 * command that exercises the framework end-to-end; the rest are scaffolded TODOs.
 */
public final class BaseModule implements BotModule {

    @Override
    public String name() {
        return "Base";
    }

    @Override
    public void register(ModuleRegistry registry, BotContext ctx) {
        // Reference implementation proving the command pipeline compiles & routes.
        registry.command(new PingCommand());

        // Moderation (BOTSPECS Module 1) — hierarchy-validated.
        registry.command(new KickCommand());
        registry.command(new BanCommand());
        registry.command(new UnbanCommand());

        // Message purging (BOTSPECS Module 1) — /cl is an alias of /clear.
        registry.command(new ClearCommand("clear"));
        registry.command(new ClearCommand("cl"));

        // Role management (BOTSPECS Module 1) — hierarchy-validated.
        registry.command(new AddCargoCommand());
        registry.command(new RemoveCargoCommand());

        // Configuration hub (BOTSPECS Module 1).
        registry.command(new SetupCommand());
        registry.component(new SetupComponentHandler());

        // General logging: command executions, message deletes/edits, joins/leaves,
        // voice traffic, bans, kicks (BOTSPECS §General Logging).
        registry.listener(new GeneralLoggingListener(ctx));

        // TODO(Module 1): /setup, /cl, /clear, /bot-name, /bot-icon, /bot-nick,
        // /disconnect, /voice-move, /mute, /unmute, /mutecall, /unmutecall, /kick,
        // /ban, /unban, /addcargo, /removecargo, /listacargo, /lock, /unlock,
        // /embed, /editembed, /addemoji, /formulario.
    }
}
