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
import dev.davimf.basebot.modules.base.moderation.PurgeLogSuppressor;
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
import dev.davimf.basebot.modules.base.vip.VipConcederCommand;
import dev.davimf.basebot.modules.base.vip.VipListaCommand;
import dev.davimf.basebot.modules.base.vip.VipPainelCommand;
import dev.davimf.basebot.modules.base.vip.VipPanelComponentHandler;
import dev.davimf.basebot.modules.base.vip.VipRevogarCommand;
import dev.davimf.basebot.modules.base.vip.VipService;
import dev.davimf.basebot.modules.base.vip.VipVoiceListener;

import static dev.davimf.basebot.core.command.GroupCommand.Sub;

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
    private dev.davimf.basebot.modules.base.economy.JobNotifyService jobNotify;
    private dev.davimf.basebot.modules.base.economy.ShopService shop;
    private dev.davimf.basebot.modules.base.economy.JailService jail;
    private dev.davimf.basebot.modules.base.economy.EquipmentService equipment;
    private dev.davimf.basebot.modules.base.snapshot.GuildSnapshotSync guildSnapshot;
    private VipService vip;
    // Trava de reconciliação de voz: criada aqui (não em register/onReady) para existir nos dois.
    private final dev.davimf.basebot.modules.base.leveling.VoiceGate voiceGate =
            new dev.davimf.basebot.modules.base.leveling.VoiceGate();

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
                    new dev.davimf.basebot.modules.base.leveling.VoiceXpTicker(ctx, leveling, voiceGate);
            ctx.scheduler().repeating(ticker::tick, 60, 60, TimeUnit.SECONDS);
            // Delay de 5s: dá tempo do cache de guild/member da JDA assentar após o READY. A
            // trava (voiceGate), não este timing, é o que garante que nada credita o período
            // offline — os listeners de voz já estão vivos desde o register(), antes disto rodar.
            ctx.scheduler().once(() ->
                    dev.davimf.basebot.modules.base.leveling.VoiceReconciler.run(ctx, voiceGate), 5, TimeUnit.SECONDS);
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
        // Avisos de trabalho: varre prefs e manda DM quando um job fica disponível (à prova de restart).
        if (jobNotify != null) {
            ctx.scheduler().repeating(jobNotify::sweep, 60, 60, java.util.concurrent.TimeUnit.SECONDS);
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
        // VIPs: recarrega o cache de bônus no boot + a cada 5 min; expira grants vencidos a cada 60s.
        if (vip != null) {
            ctx.scheduler().once(vip::reload, 15, TimeUnit.SECONDS);
            ctx.scheduler().repeating(vip::reload, 300, 300, TimeUnit.SECONDS);
            ctx.scheduler().repeating(vip::sweepExpired, 60, 60, TimeUnit.SECONDS);
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
        // Deleções feitas por /purge, /clear e /cl não vão pro log de mensagens (evita flood).
        PurgeLogSuppressor purgeSuppressor = new PurgeLogSuppressor();
        registry.command(new PurgeCommand(purgeSuppressor));
        registry.command(new NukeCommand());

        // Message purging (BOTSPECS Module 1): /clear deletes anyone's messages,
        // /cl deletes only the executor's own messages.
        registry.command(new ClearCommand("clear", false, purgeSuppressor));
        registry.command(new ClearCommand("cl", true, purgeSuppressor));

        // Role management (BOTSPECS Module 1) — hierarchy-validated.
        registry.command(new AddCargoCommand());
        registry.command(new RemoveCargoCommand());

        // Controle de canal agrupado em /canal (trancar, destrancar, lento).
        registry.command(new dev.davimf.basebot.core.command.GroupCommand("canal",
                "Controle do canal: trancar, destrancar e modo lento.",
                net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions.enabledFor(
                        net.dv8tion.jda.api.Permission.MANAGE_CHANNEL),
                java.util.List.of(
                        dev.davimf.basebot.core.command.GroupCommand.Sub.gated("trancar",
                                "Tranca o canal atual (impede @everyone de enviar).",
                                new LockCommand(),
                                net.dv8tion.jda.api.Permission.MANAGE_CHANNEL),
                        dev.davimf.basebot.core.command.GroupCommand.Sub.gated("destrancar",
                                "Destranca o canal atual.",
                                new UnlockCommand(),
                                net.dv8tion.jda.api.Permission.MANAGE_CHANNEL),
                        dev.davimf.basebot.core.command.GroupCommand.Sub.gated("lento",
                                "Define o modo lento do canal (0 desativa).",
                                new SlowmodeCommand(),
                                net.dv8tion.jda.api.Permission.MANAGE_CHANNEL))));
        registry.command(new AddEmojiCommand());
        registry.command(new ListaCargoCommand());
        registry.component(new ListaCargoComponentHandler());

        // Moderação de voz agrupada em /voz (mutar, desmutar, mover, desconectar).
        // O Discord só gateia permissão no comando de topo; cada subcomando checa a sua no execute.
        registry.command(new dev.davimf.basebot.core.command.GroupCommand("voz",
                "Moderação de voz: mutar, mover e desconectar membros.",
                net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions.enabledFor(
                        net.dv8tion.jda.api.Permission.VOICE_MOVE_OTHERS),
                java.util.List.of(
                        dev.davimf.basebot.core.command.GroupCommand.Sub.gated("mutar",
                                "Silencia um membro na call por um tempo.",
                                new MuteCallCommand(moderation),
                                net.dv8tion.jda.api.Permission.VOICE_MUTE_OTHERS),
                        dev.davimf.basebot.core.command.GroupCommand.Sub.gated("desmutar",
                                "Remove o silêncio de call de um membro.",
                                new UnmuteCallCommand(moderation),
                                net.dv8tion.jda.api.Permission.VOICE_MUTE_OTHERS),
                        dev.davimf.basebot.core.command.GroupCommand.Sub.gated("mover",
                                "Move um membro para outro canal de voz.",
                                new VoiceMoveCommand(),
                                net.dv8tion.jda.api.Permission.VOICE_MOVE_OTHERS),
                        dev.davimf.basebot.core.command.GroupCommand.Sub.gated("desconectar",
                                "Desconecta um membro do canal de voz.",
                                new DisconnectCommand(),
                                net.dv8tion.jda.api.Permission.VOICE_MOVE_OTHERS))));
        // Re-apply persistent call mutes when a flagged member joins voice / is un-muted.
        registry.listener(new VoiceMutePersistenceListener(ctx));
        // Sweeper that lifts text/voice mutes once their timer expires (scheduled onReady).
        this.muteService = new MuteService(ctx);

        // Text mute via the configured "mutado" role (set in /setup → Cargos).
        registry.command(new MuteCommand(moderation));
        registry.command(new UnmuteCommand(moderation));

        // Perfil do bot agrupado em /bot (icone, nome, apelido). Ícone/nome são globais (2x/hora).
        registry.command(new dev.davimf.basebot.core.command.GroupCommand("bot",
                "Perfil do bot: ícone, nome e apelido.",
                net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions.enabledFor(
                        net.dv8tion.jda.api.Permission.MANAGE_SERVER),
                java.util.List.of(
                        dev.davimf.basebot.core.command.GroupCommand.Sub.gated("icone",
                                "Altera o avatar global do bot (limite: 2x por hora).",
                                new BotIconCommand(),
                                net.dv8tion.jda.api.Permission.MANAGE_SERVER),
                        dev.davimf.basebot.core.command.GroupCommand.Sub.gated("nome",
                                "Altera o nome global do bot (limite: 2x por hora).",
                                new BotNameCommand(),
                                net.dv8tion.jda.api.Permission.MANAGE_SERVER),
                        dev.davimf.basebot.core.command.GroupCommand.Sub.gated("apelido",
                                "Altera o apelido do bot apenas neste servidor.",
                                new BotNickCommand(),
                                net.dv8tion.jda.api.Permission.NICKNAME_MANAGE))));

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
        registry.listener(new MessageLoggingListener(ctx, attachmentVault, purgeSuppressor));
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
        registry.command(new dev.davimf.basebot.modules.base.commands.XpCommand(leveling));
        registry.component(new dev.davimf.basebot.modules.base.leveling.LevelingComponentHandler(leveling));
        // Leveling — voz (Plano 2): sessões persistidas para XP por tempo em call.
        registry.listener(new dev.davimf.basebot.modules.base.leveling.VoiceSessionListener(ctx, leveling, voiceGate));
        registry.listener(new dev.davimf.basebot.modules.base.leveling.VoiceStateListener(ctx, leveling, voiceGate));
        registry.command(new dev.davimf.basebot.modules.base.commands.TempoCallCommand(voiceGate));
        registry.component(new dev.davimf.basebot.modules.base.leveling.VoiceTimeComponentHandler(voiceGate));

        // Sistema de VIPs (Base) — instanciado cedo pois a economia injeta o bônus nos payouts.
        this.vip = new VipService(ctx);

        // Economia por-usuário (Base) — separada do tesouro de facção.
        dev.davimf.basebot.modules.base.economy.EconomyService economy =
                new dev.davimf.basebot.modules.base.economy.EconomyService(ctx, vip);
        // Cadeia/ficha (Base) — instanciado cedo pois /trabalhar já aplica o guard de preso.
        this.jail = new dev.davimf.basebot.modules.base.economy.JailService(ctx);
        dev.davimf.basebot.modules.base.economy.CrimeEconomyService crimeService =
                new dev.davimf.basebot.modules.base.economy.CrimeEconomyService(ctx, jail, vip);
        dev.davimf.basebot.modules.base.economy.OrgCrimeService orgCrime =
                new dev.davimf.basebot.modules.base.economy.OrgCrimeService(ctx, jail);
        registry.component(new dev.davimf.basebot.modules.base.economy.OrgCrimeComponentHandler(orgCrime));
        // Loja da economia (Base) — cargos perm/temp + itens custom.
        this.shop = new dev.davimf.basebot.modules.base.economy.ShopService(ctx);
        // Equipamentos (Base) — fundação de empregos/crime (jail já instanciado acima).
        this.equipment = new dev.davimf.basebot.modules.base.economy.EquipmentService(ctx);
        // Empregos (Base) — exigem ferramenta equipada.
        dev.davimf.basebot.modules.base.economy.JobService jobs =
                new dev.davimf.basebot.modules.base.economy.JobService(ctx, vip);
        // Avisos de trabalho (Base) — prefs + watermark; sweep agendado no onReady.
        dev.davimf.basebot.modules.base.economy.JobNotifyRepository jobNotifyRepo =
                new dev.davimf.basebot.modules.base.economy.JobNotifyRepository(ctx.database().sqlite());
        this.jobNotify = new dev.davimf.basebot.modules.base.economy.JobNotifyService(ctx, jail);

        // Toda a categoria de economia num único /economia (24 subcomandos = 1 slot); acesso livre.
        registry.command(new dev.davimf.basebot.core.command.GroupCommand("economia",
                "Ganhos, trabalhos, loja, banco e cadeia — tudo da economia.",
                net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions.ENABLED,
                java.util.List.of(
                        Sub.open("painel", "Seu painel de economia.", new dev.davimf.basebot.modules.base.commands.EconomiaCommand(jail, jobNotifyRepo)),
                        Sub.open("saldo", "Mostra seu saldo.", new dev.davimf.basebot.modules.base.commands.SaldoCommand(economy)),
                        Sub.open("daily", "Recompensa diária de moedas.", new dev.davimf.basebot.modules.base.commands.DailyCommand(economy)),
                        Sub.open("trabalhar", "Trabalhe para ganhar moedas.", new dev.davimf.basebot.modules.base.commands.TrabalharCommand(economy, jail)),
                        Sub.open("pagar", "Paga moedas a outro membro.", new dev.davimf.basebot.modules.base.commands.PagarCommand(economy)),
                        Sub.open("depositar", "Deposita moedas no banco.", new dev.davimf.basebot.modules.base.commands.DepositarCommand(economy)),
                        Sub.open("sacar", "Saca moedas do banco.", new dev.davimf.basebot.modules.base.commands.SacarCommand(economy)),
                        Sub.open("minerar", "Minera com a picareta equipada.", new dev.davimf.basebot.modules.base.commands.MinerarCommand(jobs, jail)),
                        Sub.open("cozinhar", "Cozinha com o utensílio equipado.", new dev.davimf.basebot.modules.base.commands.CozinharCommand(jobs, jail)),
                        Sub.open("entregar", "Faz entregas com a moto equipada.", new dev.davimf.basebot.modules.base.commands.EntregarCommand(jobs, jail)),
                        Sub.open("programar", "Freela de programação com o teclado equipado.", new dev.davimf.basebot.modules.base.commands.ProgramarCommand(jobs, jail)),
                        Sub.open("plantar", "Colhe com o equipamento de fazenda equipado.", new dev.davimf.basebot.modules.base.commands.PlantarCommand(jobs, jail)),
                        Sub.open("pescar", "Pesca com a vara equipada.", new dev.davimf.basebot.modules.base.commands.PescarCommand(jobs, jail)),
                        Sub.open("explorar", "Explora com o equipamento equipado.", new dev.davimf.basebot.modules.base.commands.ExplorarCommand(jobs, jail)),
                        Sub.open("faturar", "Fatura os lucros do seu negócio.", new dev.davimf.basebot.modules.base.commands.FaturarCommand(jobs, jail)),
                        Sub.open("reparar", "Repara um item quase quebrado.", new dev.davimf.basebot.modules.base.commands.RepararCommand(equipment)),
                        Sub.open("crime", "Comete um crime (exige arma).", new dev.davimf.basebot.modules.base.commands.CrimeCommand(crimeService, jail)),
                        Sub.open("roubar", "Tenta roubar outro membro (exige arma).", new dev.davimf.basebot.modules.base.commands.RoubarCommand(crimeService, jail)),
                        Sub.open("crimeorganizado", "Inicia/entra num crime organizado.", new dev.davimf.basebot.modules.base.commands.CrimeOrganizadoCommand(orgCrime)),
                        Sub.open("mercado", "Compra equipamentos.", new dev.davimf.basebot.modules.base.commands.MercadoCommand(equipment)),
                        Sub.open("inventario", "Vê e equipa seus itens.", new dev.davimf.basebot.modules.base.commands.InventarioCommand(equipment)),
                        Sub.open("loja", "Loja de cargos e itens do servidor.", new dev.davimf.basebot.modules.base.commands.LojaCommand(shop)),
                        Sub.open("fianca", "Paga fiança para sair da cadeia.", new dev.davimf.basebot.modules.base.commands.FiancaCommand(jail)),
                        Sub.open("limparficha", "Limpa sua ficha criminal.", new dev.davimf.basebot.modules.base.commands.LimparFichaCommand(jail)))));
        registry.component(new dev.davimf.basebot.modules.base.economy.RepararComponentHandler(equipment));
        registry.component(new dev.davimf.basebot.modules.base.economy.EconomiaPanelComponentHandler(jail, jobNotifyRepo));

        // Rankings agrupados em /top (rico, xp, call) — 3 comandos num slot só; acesso livre.
        registry.command(new dev.davimf.basebot.core.command.GroupCommand("top",
                "Rankings do servidor: riqueza, nível e tempo em call.",
                net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions.ENABLED,
                java.util.List.of(
                        dev.davimf.basebot.core.command.GroupCommand.Sub.open("rico",
                                "Ranking dos mais ricos do servidor.",
                                new dev.davimf.basebot.modules.base.commands.RicoCommand(economy)),
                        dev.davimf.basebot.core.command.GroupCommand.Sub.open("xp",
                                "Ranking de nível do servidor.",
                                new dev.davimf.basebot.modules.base.commands.TopCommand(leveling)),
                        dev.davimf.basebot.core.command.GroupCommand.Sub.open("call",
                                "Ranking de tempo em call desta semana.",
                                new dev.davimf.basebot.modules.base.commands.TopCallCommand(voiceGate)))));
        registry.command(new dev.davimf.basebot.modules.base.commands.EcoCommand(economy));
        registry.component(new dev.davimf.basebot.modules.base.economy.EconomyComponentHandler(economy));
        registry.component(new dev.davimf.basebot.modules.base.economy.ShopComponentHandler(shop));
        registry.component(new dev.davimf.basebot.modules.base.economy.EquipmentComponentHandler(equipment));

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
        registry.command(new dev.davimf.basebot.modules.base.commands.InteragirCommand(gifClient));
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

        // Sistema de VIPs (Base) — planos, grants, painel do membro e reveal-on-occupancy de call
        // (instância criada mais acima, antes da economia, para poder injetar o bônus nos payouts).
        registry.command(new dev.davimf.basebot.core.command.GroupCommand("vip",
                "Sistema de VIPs — painel do membro e gestão.",
                net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions.ENABLED,
                java.util.List.of(
                        Sub.open("painel", "Seu painel de VIP.", new VipPainelCommand(vip)),
                        Sub.gated("conceder", "Concede um VIP a um membro.", new VipConcederCommand(vip),
                                net.dv8tion.jda.api.Permission.ADMINISTRATOR),
                        Sub.gated("revogar", "Revoga o VIP de um membro.", new VipRevogarCommand(vip),
                                net.dv8tion.jda.api.Permission.ADMINISTRATOR),
                        Sub.gated("lista", "Lista os VIPs ativos.", new VipListaCommand(vip),
                                net.dv8tion.jda.api.Permission.ADMINISTRATOR))));
        registry.component(new VipPanelComponentHandler(vip));
        registry.listener(new VipVoiceListener(ctx, vip));
    }
}
