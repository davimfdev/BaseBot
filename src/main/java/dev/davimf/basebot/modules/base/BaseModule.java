package dev.davimf.basebot.modules.base;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.modules.BotModule;
import dev.davimf.basebot.modules.ModuleRegistry;
import dev.davimf.basebot.modules.base.commands.AddCargoCommand;
import dev.davimf.basebot.modules.base.commands.AddEmojiCommand;
import dev.davimf.basebot.modules.base.commands.BanCommand;
import dev.davimf.basebot.modules.base.commands.BotIconCommand;
import dev.davimf.basebot.modules.base.commands.BotNameCommand;
import dev.davimf.basebot.modules.base.commands.BotNickCommand;
import dev.davimf.basebot.modules.base.commands.ClearCommand;
import dev.davimf.basebot.modules.base.commands.DisconnectCommand;
import dev.davimf.basebot.modules.base.commands.KickCommand;
import dev.davimf.basebot.modules.base.commands.ListaCargoCommand;
import dev.davimf.basebot.modules.base.commands.LockCommand;
import dev.davimf.basebot.modules.base.commands.MuteCallCommand;
import dev.davimf.basebot.modules.base.commands.MuteCommand;
import dev.davimf.basebot.modules.base.commands.RemoveCargoCommand;
import dev.davimf.basebot.modules.base.commands.UnlockCommand;
import dev.davimf.basebot.modules.base.commands.UnmuteCallCommand;
import dev.davimf.basebot.modules.base.commands.UnmuteCommand;
import dev.davimf.basebot.modules.base.commands.PingCommand;
import dev.davimf.basebot.modules.base.commands.SetupCommand;
import dev.davimf.basebot.modules.base.commands.UnbanCommand;
import dev.davimf.basebot.modules.base.commands.VoiceMoveCommand;
import dev.davimf.basebot.modules.base.commands.FormularioCommand;
import dev.davimf.basebot.modules.base.commands.MensagemCommand;
import dev.davimf.basebot.modules.base.forms.FormComponentHandler;
import dev.davimf.basebot.modules.base.forms.FormRepository;
import dev.davimf.basebot.modules.base.message.MessageBuilderComponentHandler;
import dev.davimf.basebot.modules.base.message.MessageBuilderService;
import dev.davimf.basebot.modules.base.message.MessageDraftRepository;
import dev.davimf.basebot.modules.base.listacargo.ListaCargoComponentHandler;
import dev.davimf.basebot.modules.base.listeners.GeneralLoggingListener;
import dev.davimf.basebot.modules.base.setup.SetupComponentHandler;
import dev.davimf.basebot.modules.base.voice.MuteService;
import dev.davimf.basebot.modules.base.voice.VoiceMutePersistenceListener;

import java.util.concurrent.TimeUnit;

/**
 * Module 1 — Base &amp; Utility (BOTSPECS §Module 1).
 *
 * <p>Owns configuration ({@code /setup}), moderation ({@code /kick}, {@code /ban},
 * {@code /mute}…), role management ({@code /addcargo}…), embeds, and the general
 * event-logging listeners. Only {@code /ping} is implemented here as the reference
 * command that exercises the framework end-to-end; the rest are scaffolded TODOs.
 */
public final class BaseModule implements BotModule {

    private MuteService muteService;

    @Override
    public String name() {
        return "Base";
    }

    @Override
    public void onReady(BotContext ctx) {
        // Lift expired text/voice mutes shortly after they end (sweep also catches any
        // mutes whose timer elapsed while the bot was offline).
        if (muteService != null) {
            ctx.scheduler().repeating(muteService::sweepExpired, 5, 30, TimeUnit.SECONDS);
        }
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

        // Channel utilities (BOTSPECS Module 1).
        registry.command(new LockCommand());
        registry.command(new UnlockCommand());
        registry.command(new AddEmojiCommand());
        registry.command(new ListaCargoCommand());
        registry.component(new ListaCargoComponentHandler());

        // Voice moderation (BOTSPECS Module 1).
        registry.command(new DisconnectCommand());
        registry.command(new VoiceMoveCommand());
        registry.command(new MuteCallCommand());
        registry.command(new UnmuteCallCommand());
        // Re-apply persistent call mutes when a flagged member joins voice / is un-muted.
        registry.listener(new VoiceMutePersistenceListener(ctx));
        // Sweeper that lifts text/voice mutes once their timer expires (scheduled onReady).
        this.muteService = new MuteService(ctx);

        // Text mute via the configured "mutado" role (set in /setup → Cargos).
        registry.command(new MuteCommand());
        registry.command(new UnmuteCommand());

        // Bot profile (BOTSPECS Module 1) — /bot-name + /bot-icon are GLOBAL (2x/hour cap).
        registry.command(new BotNameCommand());
        registry.command(new BotIconCommand());
        registry.command(new BotNickCommand());

        // Configuration hub (BOTSPECS Module 1).
        registry.command(new SetupCommand());
        registry.component(new SetupComponentHandler());

        // Message builder (BOTSPECS Module 1) — /mensagem: interactive embed/Container V2
        // builder with block management and optional webhook impersonation on send.
        MessageBuilderService messageBuilder = new MessageBuilderService(
                ctx, new MessageDraftRepository(ctx.database().sqlite()));
        registry.command(new MensagemCommand(messageBuilder));
        registry.component(new MessageBuilderComponentHandler(messageBuilder));

        // Configurable forms (BOTSPECS Module 1) — /formulario dispatches a modal whose
        // answers are posted to #log-formularios.
        FormRepository forms = new FormRepository(ctx.database().sqlite());
        registry.command(new FormularioCommand(forms));
        registry.component(new FormComponentHandler(forms));

        // General logging: command executions, message deletes/edits, joins/leaves,
        // voice traffic, bans, kicks (BOTSPECS §General Logging).
        registry.listener(new GeneralLoggingListener(ctx));
    }
}
