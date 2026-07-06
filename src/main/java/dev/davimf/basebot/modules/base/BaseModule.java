// [OUTLINE START]
// Package: dev.davimf.basebot.modules.base
// 
// Class: BaseModule
// 
// Methods:
//   - `Method` : `public String name()`
// 
// Fields:
//   - `Field` : `private MuteService muteService`
// [OUTLINE END]



package dev.davimf.basebot.modules.base;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.modules.BotModule;
import dev.davimf.basebot.modules.ModuleRegistry;
import dev.davimf.basebot.modules.base.commands.AddCargoCommand;
import dev.davimf.basebot.modules.base.commands.AddEmojiCommand;
import dev.davimf.basebot.modules.base.commands.AvisarCommand;
import dev.davimf.basebot.modules.base.commands.BanCommand;
import dev.davimf.basebot.modules.base.commands.CasoCommand;
import dev.davimf.basebot.modules.base.commands.InfracoesCommand;
import dev.davimf.basebot.modules.base.commands.NotaCommand;
import dev.davimf.basebot.modules.base.commands.NukeCommand;
import dev.davimf.basebot.modules.base.commands.PurgeCommand;
import dev.davimf.basebot.modules.base.commands.RevogarCommand;
import dev.davimf.basebot.modules.base.commands.SlowmodeCommand;
import dev.davimf.basebot.modules.base.commands.SoftbanCommand;
import dev.davimf.basebot.modules.base.commands.TempbanCommand;
import dev.davimf.basebot.modules.base.commands.TimeoutCommand;
import dev.davimf.basebot.modules.base.commands.UntimeoutCommand;
import dev.davimf.basebot.modules.base.moderation.InfractionComponentHandler;
import dev.davimf.basebot.modules.base.moderation.ModerationService;
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
import dev.davimf.basebot.modules.base.listeners.AttachmentVault;
import dev.davimf.basebot.modules.base.listeners.BanLoggingListener;
import dev.davimf.basebot.modules.base.listeners.ChannelLoggingListener;
import dev.davimf.basebot.modules.base.listeners.CommandLoggingListener;
import dev.davimf.basebot.modules.base.listeners.GuildLoggingListener;
import dev.davimf.basebot.modules.base.listeners.InviteTracker;
import dev.davimf.basebot.modules.base.listeners.MembershipLoggingListener;
import dev.davimf.basebot.modules.base.listeners.MessageLoggingListener;
import dev.davimf.basebot.modules.base.listeners.RoleLoggingListener;
import dev.davimf.basebot.modules.base.listeners.VoiceLoggingListener;
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
    private ModerationService moderation;

    @Override
    public String name() {
        return "Base";
    }

    @Override
    public void onReady(BotContext ctx) {
        // Sync custom application emojis: uploads any missing bundled PNGs once (idempotent).
        dev.davimf.basebot.util.EmojiRegistry.sync(ctx.jda());
        // Lift expired text/voice mutes shortly after they end (sweep also catches any
        // mutes whose timer elapsed while the bot was offline).
        if (muteService != null) {
            ctx.scheduler().repeating(muteService::sweepExpired, 5, 30, TimeUnit.SECONDS);
        }
        // Decay expired warns and lift expired tempbans.
        if (moderation != null) {
            ctx.scheduler().repeating(moderation::sweepExpired, 10, 60, TimeUnit.SECONDS);
        }
        // Sincroniza as regras de AutoMod nativo de cada guilda com a config (off-thread).
        ctx.scheduler().executor().execute(() -> {
            if (ctx.jda() == null) {
                return;
            }
            for (net.dv8tion.jda.api.entities.Guild g : ctx.jda().getGuilds()) {
                dev.davimf.basebot.modules.base.security.AutoModManager.sync(g,
                        ctx.database().guildConfig().findOrEmpty(g.getId()));
            }
        });
    }

    @Override
    public void register(ModuleRegistry registry, BotContext ctx) {
        // Reference implementation proving the command pipeline compiles & routes.
        registry.command(new PingCommand());

        // Moderation + infractions (Base) — every action records a numbered case via the
        // unified ModerationService (DM + modlog + auto-escalation).
        this.moderation = new ModerationService(ctx);
        registry.component(new InfractionComponentHandler(moderation));
        registry.command(new KickCommand(moderation));
        registry.command(new BanCommand(moderation));
        registry.command(new UnbanCommand(moderation));
        registry.command(new TempbanCommand(moderation));
        registry.command(new SoftbanCommand(moderation));
        registry.command(new TimeoutCommand(moderation));
        registry.command(new UntimeoutCommand(moderation));
        registry.command(new AvisarCommand(moderation));
        registry.command(new NotaCommand(moderation));
        registry.command(new InfracoesCommand(moderation));
        registry.command(new CasoCommand(moderation));
        registry.command(new RevogarCommand(moderation));
        registry.command(new PurgeCommand());
        registry.command(new SlowmodeCommand());
        registry.command(new NukeCommand());

        // Message purging (BOTSPECS Module 1): /clear deletes anyone's messages,
        // /cl deletes only the executor's own messages.
        registry.command(new ClearCommand("clear", false));
        registry.command(new ClearCommand("cl", true));

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
        registry.command(new MuteCallCommand(moderation));
        registry.command(new UnmuteCallCommand(moderation));
        // Re-apply persistent call mutes when a flagged member joins voice / is un-muted.
        registry.listener(new VoiceMutePersistenceListener(ctx));
        // Sweeper that lifts text/voice mutes once their timer expires (scheduled onReady).
        this.muteService = new MuteService(ctx);

        // Text mute via the configured "mutado" role (set in /setup → Cargos).
        registry.command(new MuteCommand(moderation));
        registry.command(new UnmuteCommand(moderation));

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

        // General logging (BOTSPECS §General Logging): one focused listener per category,
        // each posting Components V2 entries to its per-type log channel.
        registry.listener(new CommandLoggingListener(ctx));
        InviteTracker inviteTracker = new InviteTracker();
        registry.listener(inviteTracker);
        registry.listener(new MembershipLoggingListener(ctx, inviteTracker));
        registry.listener(new BanLoggingListener(ctx));
        AttachmentVault attachmentVault = new AttachmentVault(ctx, ctx.config().discord().vaultGuildId());
        registry.listener(new MessageLoggingListener(ctx, attachmentVault));
        registry.listener(new VoiceLoggingListener(ctx));
        registry.listener(new ChannelLoggingListener(ctx));
        registry.listener(new RoleLoggingListener(ctx));
        registry.listener(new GuildLoggingListener(ctx));
        // Segurança · Módulo 1: AutoMod nativo → warn (escalona via Infrações).
        registry.listener(new dev.davimf.basebot.modules.base.security.AutoModExecutionListener(ctx, moderation));
        // Segurança · Módulo 2: anti-raid (lockdown + alerta).
        registry.listener(new dev.davimf.basebot.modules.base.security.AntiRaidListener(ctx));
        // Segurança · Módulo 3: verificação (gate de entrada + painel Verificar).
        registry.listener(new dev.davimf.basebot.modules.base.security.VerificationListener(ctx));
        // Segurança · Módulo 4: anti-nuke (audit log + neutralização).
        registry.listener(new dev.davimf.basebot.modules.base.security.AntiNukeListener(ctx));
        registry.component(new dev.davimf.basebot.modules.base.security.SecurityComponentHandler(ctx));
    }
}
