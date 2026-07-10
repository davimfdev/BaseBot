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
    private dev.davimf.basebot.modules.base.leveling.LevelingService leveling;
    private dev.davimf.basebot.modules.base.events.ChatEventService chatEvents;
    private dev.davimf.basebot.modules.base.giveaway.GiveawayService giveaways;
    private dev.davimf.basebot.modules.base.utility.ReminderService reminders;
    private dev.davimf.basebot.modules.base.economy.ShopService shop;
    private dev.davimf.basebot.modules.base.economy.JailService jail;
    private dev.davimf.basebot.modules.base.economy.EquipmentService equipment;
    private dev.davimf.basebot.modules.base.snapshot.GuildSnapshotSync guildSnapshot;

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
        // Leveling — voz: ticker de XP a cada 60s + reconciliação das sessões no boot.
        if (leveling != null) {
            dev.davimf.basebot.modules.base.leveling.VoiceXpTicker ticker =
                    new dev.davimf.basebot.modules.base.leveling.VoiceXpTicker(ctx, leveling);
            ctx.scheduler().repeating(ticker::tick, 60, 60, TimeUnit.SECONDS);
            ctx.scheduler().once(() ->
                    dev.davimf.basebot.modules.base.leveling.VoiceReconciler.run(ctx), 5, TimeUnit.SECONDS);
        }
        // Tempo em call: sobe os baldes sujos pro Neon a cada 5 min; poda de 90 dias 1x/dia.
        // O sweeper roda DEPOIS do flusher (initialDelay maior) para que uma linha suja
        // recém-criada tenha chance de subir antes de ser considerada para poda.
        dev.davimf.basebot.modules.base.leveling.VoiceTimeFlusher voiceFlusher =
                new dev.davimf.basebot.modules.base.leveling.VoiceTimeFlusher(ctx);
        ctx.scheduler().repeating(voiceFlusher::flush, 90, 300, TimeUnit.SECONDS);
        dev.davimf.basebot.modules.base.leveling.VoiceRetentionSweeper voiceSweeper =
                new dev.davimf.basebot.modules.base.leveling.VoiceRetentionSweeper(ctx);
        ctx.scheduler().repeating(voiceSweeper::sweep, 600, TimeUnit.DAYS.toSeconds(1), TimeUnit.SECONDS);
        // Eventos de chat: verifica/dispara a cada 60s.
        if (chatEvents != null) {
            ctx.scheduler().repeating(chatEvents::tick, 60, 60, TimeUnit.SECONDS);
        }
        // Sorteios: encerra os vencidos a cada 30s (à prova de restart).
        if (giveaways != null) {
            ctx.scheduler().repeating(giveaways::sweep, 30, 30, TimeUnit.SECONDS);
        }
        // Lembretes: dispara os vencidos a cada 30s.
        if (reminders != null) {
            ctx.scheduler().repeating(reminders::sweep, 30, 30, TimeUnit.SECONDS);
        }
        // Snapshots do dashboard: registra a instância + full-sync no boot; sync periódico (<10min).
        if (guildSnapshot != null) {
            ctx.scheduler().once(() -> {
                guildSnapshot.bootstrapInstance();
                guildSnapshot.syncAll();
                dev.davimf.basebot.modules.base.setup.InitialGuildSetup.run(ctx);
            }, 8, TimeUnit.SECONDS);
            ctx.scheduler().repeating(guildSnapshot::syncAll, 300, 300, TimeUnit.SECONDS);
        }
        // Loja: remove cargos temporários expirados a cada 5 min (à prova de restart).
        if (shop != null) {
            ctx.scheduler().repeating(shop::sweep, 20, 300, TimeUnit.SECONDS);
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
        // Reconcile dos efeitos ativos: re-sincroniza o AutoMod nativo de cada guild a cada 5 min,
        // pra refletir mudanças feitas pelo dashboard (config lida via cache curto).
        ctx.scheduler().repeating(() -> {
            if (ctx.jda() == null) {
                return;
            }
            for (net.dv8tion.jda.api.entities.Guild g : ctx.jda().getGuilds()) {
                dev.davimf.basebot.modules.base.security.AutoModManager.sync(g,
                        ctx.database().guildConfig().findOrEmpty(g.getId()));
            }
        }, 300, 300, TimeUnit.SECONDS);
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
        // Snapshots do dashboard (canais/cargos por guild). O instance id vem do BOT_INSTANCE_ID
        // (override opcional) ou é gerado/persistido pelo próprio bot no 1º boot.
        this.guildSnapshot = new dev.davimf.basebot.modules.base.snapshot.GuildSnapshotSync(ctx);
        registry.listener(new dev.davimf.basebot.modules.base.snapshot.GuildSnapshotListener(ctx, guildSnapshot));
        // Segurança · Módulo 1: AutoMod nativo → warn (escalona via Infrações).
        registry.listener(new dev.davimf.basebot.modules.base.security.AutoModExecutionListener(ctx, moderation));
        // Segurança · Módulo 2: anti-raid (lockdown + alerta).
        registry.listener(new dev.davimf.basebot.modules.base.security.AntiRaidListener(ctx));
        // Segurança · Módulo 3: verificação (gate de entrada + painel Verificar).
        registry.listener(new dev.davimf.basebot.modules.base.security.VerificationListener(ctx));
        // Segurança · Módulo 4: anti-nuke (audit log + neutralização).
        registry.listener(new dev.davimf.basebot.modules.base.security.AntiNukeListener(ctx));
        // Segurança: reset da verificação quando o membro é expulso/banido.
        registry.listener(new dev.davimf.basebot.modules.base.security.VerificationResetListener(ctx));
        // Segurança: canal-armadilha anti-spam (kick + purga de mensagens recentes).
        registry.listener(new dev.davimf.basebot.modules.base.security.AntiSpamListener(ctx));
        registry.component(new dev.davimf.basebot.modules.base.security.SecurityComponentHandler(ctx));

        // Boas-vindas / despedida / autorole (Base) — requer GUILD_MEMBERS.
        registry.listener(new dev.davimf.basebot.modules.base.welcome.WelcomeListener(ctx));
        // Self-roles (Base) — painéis de auto-atribuição (runtime dos botões/menu).
        registry.component(new dev.davimf.basebot.modules.base.selfroles.SelfRoleComponentHandler());

        // Leveling (Base) — XP por mensagem, níveis, cargos por nível e ranking.
        this.leveling = new dev.davimf.basebot.modules.base.leveling.LevelingService(ctx);
        registry.listener(new dev.davimf.basebot.modules.base.leveling.MessageXpListener(ctx, leveling));
        registry.command(new dev.davimf.basebot.modules.base.commands.RankCommand(leveling));
        registry.command(new dev.davimf.basebot.modules.base.commands.TopCommand(leveling));
        registry.command(new dev.davimf.basebot.modules.base.commands.XpCommand(leveling));
        registry.component(new dev.davimf.basebot.modules.base.leveling.LevelingComponentHandler(leveling));
        // Leveling — voz (Plano 2): sessões persistidas para XP por tempo em call.
        registry.listener(new dev.davimf.basebot.modules.base.leveling.VoiceSessionListener(ctx, leveling));
        registry.listener(new dev.davimf.basebot.modules.base.leveling.VoiceStateListener(ctx, leveling));
        registry.command(new dev.davimf.basebot.modules.base.commands.TopCallCommand());
        registry.command(new dev.davimf.basebot.modules.base.commands.TempoCallCommand());
        registry.component(new dev.davimf.basebot.modules.base.leveling.VoiceTimeComponentHandler());

        // Economia por-usuário (Base) — separada do tesouro de facção.
        dev.davimf.basebot.modules.base.economy.EconomyService economy =
                new dev.davimf.basebot.modules.base.economy.EconomyService(ctx);
        // Cadeia/ficha (Base) — instanciado cedo pois /trabalhar já aplica o guard de preso.
        this.jail = new dev.davimf.basebot.modules.base.economy.JailService(ctx);
        registry.command(new dev.davimf.basebot.modules.base.commands.SaldoCommand(economy));
        registry.command(new dev.davimf.basebot.modules.base.commands.DailyCommand(economy));
        registry.command(new dev.davimf.basebot.modules.base.commands.TrabalharCommand(economy, jail));
        dev.davimf.basebot.modules.base.economy.CrimeEconomyService crimeService =
                new dev.davimf.basebot.modules.base.economy.CrimeEconomyService(ctx, jail);
        registry.command(new dev.davimf.basebot.modules.base.commands.CrimeCommand(crimeService, jail));
        registry.command(new dev.davimf.basebot.modules.base.commands.RoubarCommand(crimeService, jail));
        dev.davimf.basebot.modules.base.economy.OrgCrimeService orgCrime =
                new dev.davimf.basebot.modules.base.economy.OrgCrimeService(ctx, jail);
        registry.command(new dev.davimf.basebot.modules.base.commands.CrimeOrganizadoCommand(orgCrime));
        registry.component(new dev.davimf.basebot.modules.base.economy.OrgCrimeComponentHandler(orgCrime));
        registry.command(new dev.davimf.basebot.modules.base.commands.PagarCommand(economy));
        registry.command(new dev.davimf.basebot.modules.base.commands.DepositarCommand(economy));
        registry.command(new dev.davimf.basebot.modules.base.commands.SacarCommand(economy));
        registry.command(new dev.davimf.basebot.modules.base.commands.RicoCommand(economy));
        registry.command(new dev.davimf.basebot.modules.base.commands.EcoCommand(economy));
        registry.component(new dev.davimf.basebot.modules.base.economy.EconomyComponentHandler(economy));

        // Loja da economia (Base) — cargos perm/temp + itens custom.
        this.shop = new dev.davimf.basebot.modules.base.economy.ShopService(ctx);
        registry.command(new dev.davimf.basebot.modules.base.commands.LojaCommand(shop));
        registry.component(new dev.davimf.basebot.modules.base.economy.ShopComponentHandler(shop));

        // Equipamentos (Base) — fundação de empregos/crime (jail já instanciado acima).
        this.equipment = new dev.davimf.basebot.modules.base.economy.EquipmentService(ctx);
        registry.command(new dev.davimf.basebot.modules.base.commands.MercadoCommand(equipment));
        registry.command(new dev.davimf.basebot.modules.base.commands.InventarioCommand(equipment));
        registry.command(new dev.davimf.basebot.modules.base.commands.FiancaCommand(jail));
        registry.command(new dev.davimf.basebot.modules.base.commands.LimparFichaCommand(jail));
        registry.command(new dev.davimf.basebot.modules.base.commands.EconomiaCommand(jail));
        registry.component(new dev.davimf.basebot.modules.base.economy.EquipmentComponentHandler(equipment));

        // Empregos (Base) — exigem ferramenta equipada.
        dev.davimf.basebot.modules.base.economy.JobService jobs =
                new dev.davimf.basebot.modules.base.economy.JobService(ctx);
        registry.command(new dev.davimf.basebot.modules.base.commands.MinerarCommand(jobs, jail));
        registry.command(new dev.davimf.basebot.modules.base.commands.CozinharCommand(jobs, jail));
        registry.command(new dev.davimf.basebot.modules.base.commands.EntregarCommand(jobs, jail));

        // Eventos de chat (Base) — usa leveling + economia; canal principal + recompensa por nível.
        this.chatEvents = new dev.davimf.basebot.modules.base.events.ChatEventService(ctx, leveling, economy);
        registry.listener(new dev.davimf.basebot.modules.base.events.ChatEventListener(chatEvents));
        registry.component(new dev.davimf.basebot.modules.base.events.ChatEventComponentHandler(chatEvents));

        // Sorteios (Base) — usa voice_sessions + economia.
        this.giveaways = new dev.davimf.basebot.modules.base.giveaway.GiveawayService(ctx, economy);
        registry.command(new dev.davimf.basebot.modules.base.commands.SorteioCommand(giveaways));
        registry.component(new dev.davimf.basebot.modules.base.giveaway.GiveawayComponentHandler(giveaways));

        // Fun / Social (Base) — só diversão, sem XP/moedas.
        registry.command(new dev.davimf.basebot.modules.base.commands.DadoCommand());
        registry.command(new dev.davimf.basebot.modules.base.commands.CoinflipCommand());
        registry.command(new dev.davimf.basebot.modules.base.commands.ShipCommand());
        registry.command(new dev.davimf.basebot.modules.base.commands.RepCommand());
        registry.command(new dev.davimf.basebot.modules.base.commands.BiscoitoCommand());
        dev.davimf.basebot.modules.base.fun.JokenpoService jokenpo =
                new dev.davimf.basebot.modules.base.fun.JokenpoService(ctx);
        registry.command(new dev.davimf.basebot.modules.base.commands.JokenpoCommand(jokenpo));
        registry.component(new dev.davimf.basebot.modules.base.fun.JokenpoComponentHandler(jokenpo));
        dev.davimf.basebot.modules.base.fun.GifClient gifClient = new dev.davimf.basebot.modules.base.fun.GifClient();
        registry.command(new dev.davimf.basebot.modules.base.commands.TocaAquiCommand(gifClient));
        registry.command(new dev.davimf.basebot.modules.base.commands.AbracarCommand(gifClient));
        dev.davimf.basebot.modules.base.fun.MemeService memes =
                new dev.davimf.basebot.modules.base.fun.MemeService(ctx);
        registry.command(new dev.davimf.basebot.modules.base.commands.ProcuradoCommand(memes));
        registry.command(new dev.davimf.basebot.modules.base.commands.CartaReversoCommand(memes));

        // Fun — jogos (forca + quiz personalizado).
        dev.davimf.basebot.modules.base.fun.ForcaService forca =
                new dev.davimf.basebot.modules.base.fun.ForcaService(ctx);
        registry.command(new dev.davimf.basebot.modules.base.commands.ForcaCommand(forca));
        registry.listener(new dev.davimf.basebot.modules.base.fun.ForcaListener(forca));
        dev.davimf.basebot.modules.base.fun.QuizPlayService quiz =
                new dev.davimf.basebot.modules.base.fun.QuizPlayService(ctx);
        registry.command(new dev.davimf.basebot.modules.base.commands.QuizCommand(quiz));
        registry.component(new dev.davimf.basebot.modules.base.fun.QuizComponentHandler(quiz));

        // Utilidades (Base) — info, afk, enquete.
        registry.command(new dev.davimf.basebot.modules.base.commands.AvatarCommand());
        registry.command(new dev.davimf.basebot.modules.base.commands.BannerCommand());
        registry.command(new dev.davimf.basebot.modules.base.commands.UserInfoCommand());
        registry.command(new dev.davimf.basebot.modules.base.commands.ServerInfoCommand());
        dev.davimf.basebot.modules.base.utility.AfkRegistry afk =
                new dev.davimf.basebot.modules.base.utility.AfkRegistry();
        registry.command(new dev.davimf.basebot.modules.base.commands.AfkCommand(afk));
        registry.listener(new dev.davimf.basebot.modules.base.utility.AfkListener(ctx, afk));
        dev.davimf.basebot.modules.base.utility.EnqueteService enquete =
                new dev.davimf.basebot.modules.base.utility.EnqueteService(ctx);
        registry.command(new dev.davimf.basebot.modules.base.commands.EnqueteCommand(enquete));
        registry.component(new dev.davimf.basebot.modules.base.utility.EnqueteComponentHandler(enquete));

        // Lembretes (Base) — persistidos, DM à prova de restart.
        this.reminders = new dev.davimf.basebot.modules.base.utility.ReminderService(ctx);
        registry.command(new dev.davimf.basebot.modules.base.commands.LembreteCommand(reminders));
    }
}
