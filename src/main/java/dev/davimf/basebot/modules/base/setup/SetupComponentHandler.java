// [OUTLINE START]
// Package: dev.davimf.basebot.modules.base.setup
// 
// Class: SetupComponentHandler
// 
// Methods:
//   - `Method` : `public String namespace()`
//   - `Method` : `private Container hubScreen(BotContext ctx, String guildId)`
//   - `Method` : `private Container ticketsScreen(BotContext ctx, String guildId)`
//   - `Method` : `private Container actionsScreen(BotContext ctx, String guildId)`
//   - `Method` : `private GuildConfig config(BotContext ctx, String guildId)`
//   - `Method` : `private Container permAddRolePrompt(BotContext ctx, String guildId)`
//   - `Method` : `private String principalLabel(String principal, Guild guild)`
//   - `Method` : `private static String value(ModalInteractionEvent event, String key)`
//   - `Method` : `private static String newId()`
//   - `Method` : `private static int parseInt(String raw)`
//   - `Method` : `private static OptionalInt parsePositive(String raw)`
//   - `Method` : `private static String firstChannelId(EntitySelectInteractionEvent event)`
//   - `Method` : `private static String firstRoleId(EntitySelectInteractionEvent event)`
// [OUTLINE END]



package dev.davimf.basebot.modules.base.setup;

import dev.davimf.basebot.util.Emojis;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.component.ComponentHandler;
import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.database.model.TicketCategory;
import dev.davimf.basebot.database.postgres.ActionTypeRepository.ActionType;
import dev.davimf.basebot.modules.base.listeners.AttachmentVault;
import dev.davimf.basebot.modules.base.moderation.ModerationConfig;
import dev.davimf.basebot.modules.base.selfroles.SelfRolePanel;
import dev.davimf.basebot.modules.base.selfroles.SelfRolePanelRepository;
import dev.davimf.basebot.modules.base.vip.VipPlan;
import dev.davimf.basebot.modules.base.welcome.WelcomeConfig;
import dev.davimf.basebot.modules.facs.perms.ManagerPermissions;
import dev.davimf.basebot.modules.facs.perms.ManagerPermissions.Capability;
import dev.davimf.basebot.util.EmbedColor;
import dev.davimf.basebot.util.Replies;
import dev.davimf.basebot.util.TicketEmoji;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.callbacks.IMessageEditCallback;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * Drives the {@code /setup} wizard (Components V2). Navigation edits the single ephemeral
 * message; the Tickets section is a multi-category CRUD (list → detail → create/edit via
 * a modal that includes channel/role selects). Selects save on change.
 */
public final class SetupComponentHandler implements ComponentHandler {

    @Override
    public String namespace() {
        return SetupView.NS;
    }

    @Override
    public void onButton(ButtonInteractionEvent event, ComponentId id, BotContext ctx) {
        if (event.getGuild() == null) {
            return;
        }
        String guildId = event.getGuild().getId();
        switch (id.action()) {
            case "nav" -> edit(event, switch (id.arg(0)) {
                case "tickets" -> ticketsScreen(ctx, guildId);
                case "acoes" -> actionsScreen(ctx, guildId);
                case "permissoes" -> SetupView.permissionsHub(config(ctx, guildId), event.getGuild());
                case "moderacao" -> SetupView.moderation(config(ctx, guildId));
                case "farm" -> SetupView.farmScreen(config(ctx, guildId));
                case "seguranca" -> SetupView.securityScreen(config(ctx, guildId));
                case "boasvindas" -> SetupView.welcomeScreen(config(ctx, guildId));
                case "autocargos" -> selfRolesScreen(ctx, guildId);
                case "nivel" -> SetupView.levelingScreen(config(ctx, guildId), levelRewards(ctx).all(guildId));
                case "economia" -> SetupView.economyScreen(config(ctx, guildId));
                case "vip" -> SetupView.vipScreen(ctx, guildId);
                case "eventos" -> SetupView.eventsScreen(config(ctx, guildId));
                case "fun" -> SetupView.funScreen(config(ctx, guildId), quizRepo(ctx).list(guildId));
                default -> hubScreen(ctx, guildId);
            });
            case "logpage" -> edit(event, SetupView.logsPage(config(ctx, guildId), parseInt(id.arg(0))));
            case "logquicksetup" -> {
                event.deferEdit().queue();
                net.dv8tion.jda.api.entities.Guild guild = event.getGuild();
                ctx.scheduler().executor().execute(() -> {
                    QuickLogSetup.Summary s = QuickLogSetup.run(guild, ctx);
                    ctx.database().actionLogs().log(guildId, event.getUser().getId(), null,
                            "LOG_QUICK_SETUP", s.created() + " criados / " + s.skipped() + " já existiam");
                    GuildConfig refreshed = ctx.database().guildConfig().findOrEmpty(guildId);
                    event.getHook().editOriginalComponents(SetupView.logsPage(refreshed, 0))
                            .useComponentsV2().queue();
                });
            }
            case "rolepage" -> edit(event, SetupView.cargos(config(ctx, guildId), parseInt(id.arg(0))));
            case "rolequicksetup" -> {
                event.deferEdit().queue();
                net.dv8tion.jda.api.entities.Guild guild = event.getGuild();
                ctx.scheduler().executor().execute(() -> {
                    QuickRoleSetup.Summary s = QuickRoleSetup.run(guild, ctx);
                    ctx.database().actionLogs().log(guildId, event.getUser().getId(), null,
                            "ROLE_QUICK_SETUP", s.created() + " criados / " + s.skipped() + " já existiam");
                    GuildConfig refreshed = ctx.database().guildConfig().findOrEmpty(guildId);
                    event.getHook().editOriginalComponents(SetupView.cargos(refreshed, 0))
                            .useComponentsV2().queue();
                });
            }
            case "ticketnew" -> event.replyModal(SetupView.ticketModal("new", null)).queue();
            case "ticketedit" -> {
                Optional<TicketCategory> cat = ctx.database().ticketCategories().find(id.arg(0));
                if (cat.isPresent()) {
                    event.replyModal(SetupView.ticketModal(cat.get().id(), cat.get())).queue();
                } else {
                    Replies.ephemeral(event, ctx, "Categoria não encontrada.");
                }
            }
            case "ticketdel" -> {
                ctx.database().ticketCategories().delete(id.arg(0));
                ctx.database().actionLogs().log(guildId, event.getUser().getId(), id.arg(0),
                        "TICKET_CATEGORY_DELETE", null);
                edit(event, ticketsScreen(ctx, guildId));
            }
            case "actionnew" -> event.replyModal(SetupView.actionTypeModal("new", null)).queue();
            case "actionedit" -> {
                Optional<ActionType> type = ctx.database().actionTypes().find(id.arg(0));
                if (type.isPresent()) {
                    event.replyModal(SetupView.actionTypeModal(type.get().id(), type.get())).queue();
                } else {
                    Replies.ephemeral(event, ctx, "Ação não encontrada.");
                }
            }
            case "actiondel" -> {
                ctx.database().actionTypes().delete(id.arg(0));
                ctx.database().actionLogs().log(guildId, event.getUser().getId(), id.arg(0),
                        "ACTION_TYPE_DELETE", null);
                edit(event, actionsScreen(ctx, guildId));
            }
            case "botcolor" -> event.replyModal(
                    SetupView.colorModal(EmbedColor.hex(EmbedColor.resolve(config(ctx, guildId))))).queue();
            case "permaddrole" -> edit(event, permAddRolePrompt(ctx, guildId));
            case "farmadd" -> event.replyModal(SetupView.farmItemModal()).queue();
            case "secedit" -> event.replyModal(SetupView.securityModal(config(ctx, guildId))).queue();
            case "raidedit" -> event.replyModal(SetupView.antiraidModal(config(ctx, guildId))).queue();
            case "nukeedit" -> event.replyModal(SetupView.antinukeModal(config(ctx, guildId))).queue();
            case "verifypanel" -> publishVerify(event, ctx, guildId);
            case "secnav" -> {
                if ("verify".equals(id.arg(0))) {
                    edit(event, SetupView.verificationScreen(config(ctx, guildId),
                            ctx.database().verificationQuestions().listByGuild(guildId)));
                } else if ("antispam".equals(id.arg(0))) {
                    edit(event, SetupView.antispamScreen(config(ctx, guildId)));
                }
            }
            case "verifylock" -> runVerificationLockdown(event, ctx, guildId, true);
            case "verifyunlock" -> runVerificationLockdown(event, ctx, guildId, false);
            case "antispampanel" -> publishAntispam(event, ctx, guildId);
            case "verifyqadd" -> {
                if (ctx.database().verificationQuestions().count(guildId) >= 5) {
                    Replies.ephemeral(event, ctx, "Máximo de 5 perguntas.");
                } else {
                    event.replyModal(SetupView.verifyQuestionModal()).queue();
                }
            }
            case "verifyqdel" -> {
                ctx.database().verificationQuestions().delete(id.arg(0));
                edit(event, SetupView.verificationScreen(config(ctx, guildId),
                        ctx.database().verificationQuestions().listByGuild(guildId)));
            }
            case "sectoggle" -> {
                GuildConfig cfg = config(ctx, guildId);
                String arg = id.arg(0);
                String key = switch (arg) {
                    case "automod" -> dev.davimf.basebot.modules.base.security.SecurityConfig.KEY_AUTOMOD;
                    case "warn" -> dev.davimf.basebot.modules.base.security.SecurityConfig.KEY_AUTOMOD_WARN;
                    case "antiraid" -> dev.davimf.basebot.modules.base.security.SecurityConfig.KEY_ANTIRAID;
                    case "verify" -> dev.davimf.basebot.modules.base.security.SecurityConfig.KEY_VERIFY;
                    case "verifyuser" -> dev.davimf.basebot.modules.base.security.SecurityConfig.KEY_VERIFY_USERSELECT;
                    case "antispam" -> dev.davimf.basebot.modules.base.security.SecurityConfig.KEY_ANTISPAM;
                    case "antinuke" -> dev.davimf.basebot.modules.base.security.SecurityConfig.KEY_ANTINUKE;
                    default -> dev.davimf.basebot.modules.base.security.SecurityConfig.KEY_BLOCK_INVITES;
                };
                boolean def = "warn".equals(arg) || "invites".equals(arg); // warn/invites=true; automod/antiraid=false
                GuildConfig updated = GuildConfigEdits.withToggle(cfg, key, !cfg.toggle(key, def));
                ctx.database().guildConfig().save(updated);
                net.dv8tion.jda.api.entities.Guild guild = event.getGuild();
                ctx.scheduler().executor().execute(() ->
                        dev.davimf.basebot.modules.base.security.AutoModManager.sync(guild, updated));
                if ("verify".equals(arg) || "verifyuser".equals(arg)) {
                    if ("verify".equals(arg)) {
                        scheduleVerificationLockdown(ctx, event, updated);
                    }
                    edit(event, SetupView.verificationScreen(updated,
                            ctx.database().verificationQuestions().listByGuild(guildId)));
                } else if ("antispam".equals(arg)) {
                    edit(event, SetupView.antispamScreen(updated));
                } else {
                    edit(event, SetupView.securityScreen(updated));
                }
            }
            case "welcedit" -> event.replyModal(SetupView.welcomeModal(config(ctx, guildId))).queue();
            case "welctoggle" -> {
                GuildConfig cfg = config(ctx, guildId);
                String key = switch (id.arg(0)) {
                    case "dm" -> WelcomeConfig.KEY_DM;
                    case "farewell" -> WelcomeConfig.KEY_FAREWELL_ENABLED;
                    default -> WelcomeConfig.KEY_ENABLED;
                };
                GuildConfig updated = GuildConfigEdits.withToggle(cfg, key, !cfg.toggle(key, false));
                ctx.database().guildConfig().save(updated);
                edit(event, SetupView.welcomeScreen(updated));
            }
            case "niveltoggle" -> {
                GuildConfig cfg = config(ctx, guildId);
                GuildConfig updated = GuildConfigEdits.withToggle(cfg,
                        dev.davimf.basebot.modules.base.leveling.LevelingConfig.KEY_ENABLED,
                        !cfg.toggle(dev.davimf.basebot.modules.base.leveling.LevelingConfig.KEY_ENABLED, false));
                ctx.database().guildConfig().save(updated);
                edit(event, SetupView.levelingScreen(updated, levelRewards(ctx).all(guildId)));
            }
            case "nivelreward" -> event.replyModal(SetupView.levelRewardModal()).queue();
            case "vtscope" -> edit(event, SetupView.voiceTimeScreen(config(ctx, guildId)));
            case "ecotoggle" -> {
                GuildConfig cfg = config(ctx, guildId);
                GuildConfig updated = GuildConfigEdits.withToggle(cfg,
                        dev.davimf.basebot.modules.base.economy.EconomyConfig.KEY_ENABLED,
                        !cfg.toggle(dev.davimf.basebot.modules.base.economy.EconomyConfig.KEY_ENABLED, false));
                ctx.database().guildConfig().save(updated);
                edit(event, SetupView.economyScreen(updated));
            }
            case "ecocurrency" -> event.replyModal(SetupView.economyCurrencyModal(config(ctx, guildId))).queue();
            case "ecovalues" -> event.replyModal(SetupView.economyValuesModal(config(ctx, guildId))).queue();
            case "shop" -> edit(event, SetupView.shopScreen(EmbedColor.resolve(config(ctx, guildId)),
                    shopCatalog(ctx, guildId),
                    config(ctx, guildId)));
            case "shopadd" -> edit(event, SetupView.shopTypePrompt(EmbedColor.resolve(config(ctx, guildId))));
            case "shopform" -> event.replyModal(SetupView.shopItemModal(id.arg(0), id.arg(1))).queue();
            case "vipnew" -> event.replyModal(SetupView.vipModal("new", null)).queue();
            case "vipedit" -> {
                VipPlan p = ctx.database().vipPlans().findById(id.arg(0)).orElse(null);
                if (p == null) {
                    Replies.ephemeral(event, ctx, "Plano não encontrado.");
                } else {
                    event.replyModal(SetupView.vipModal(p.id(), p)).queue();
                }
            }
            case "vipdel" -> {
                ctx.database().vipPlans().delete(id.arg(0));
                ctx.database().actionLogs().log(guildId, event.getUser().getId(), id.arg(0), "VIP_PLAN_DELETE", null);
                edit(event, SetupView.vipScreen(ctx, guildId));
            }
            case "viptoggle" -> {
                VipPlan p = ctx.database().vipPlans().findById(id.arg(0)).orElse(null);
                if (p == null) {
                    Replies.ephemeral(event, ctx, "Plano não encontrado.");
                } else {
                    VipPlan updated = "role".equals(id.arg(1))
                            ? withUseControlRole(p, !p.useControlRole())
                            : withHasCall(p, !p.hasCall());
                    ctx.database().vipPlans().upsert(updated);
                    edit(event, SetupView.vipScreen(ctx, guildId));
                }
            }
            case "evttoggle" -> {
                GuildConfig cfg = config(ctx, guildId);
                GuildConfig updated = GuildConfigEdits.withToggle(cfg,
                        dev.davimf.basebot.modules.base.events.ChatEventConfig.KEY_ENABLED,
                        !cfg.toggle(dev.davimf.basebot.modules.base.events.ChatEventConfig.KEY_ENABLED, false));
                ctx.database().guildConfig().save(updated);
                edit(event, SetupView.eventsScreen(updated));
            }
            case "evtinterval" -> event.replyModal(SetupView.eventIntervalModal(config(ctx, guildId))).queue();
            case "quizadd" -> event.replyModal(SetupView.quizAddModal()).queue();
            case "modrules" -> event.replyModal(SetupView.moderationRulesModal(config(ctx, guildId))).queue();
            case "modtoggle" -> {
                GuildConfig cfg = config(ctx, guildId);
                String key = "dm".equals(id.arg(0)) ? ModerationConfig.KEY_DM
                        : ModerationConfig.KEY_REQUIRE_REASON;
                boolean def = "dm".equals(id.arg(0));
                GuildConfig updated = GuildConfigEdits.withToggle(cfg, key, !cfg.toggle(key, def));
                ctx.database().guildConfig().save(updated);
                edit(event, SetupView.moderation(updated));
            }
            case "permtoggle" -> {
                String principal = ManagerPermissions.principalFromToken(id.arg(0));
                Capability cap = Capability.fromKey(id.arg(1));
                if (cap != null) {
                    GuildConfig cfg = config(ctx, guildId);
                    String csv = ManagerPermissions.grants(cfg, cap, principal)
                            ? ManagerPermissions.revoke(cfg, cap, principal)
                            : ManagerPermissions.grant(cfg, cap, principal);
                    GuildConfig updated = GuildConfigEdits.withSetting(cfg, cap.settingKey(), csv);
                    ctx.database().guildConfig().save(updated);
                    ctx.database().actionLogs().log(guildId, event.getUser().getId(), null,
                            "PERM_TOGGLE", cap.key() + ":" + principal);
                    edit(event, SetupView.permissionsDetail(updated, principal,
                            principalLabel(principal, event.getGuild())));
                }
            }
            case "srnew" -> {
                String pid = selfRoles(ctx).createPanel(guildId, "Novo painel", null, SelfRolePanel.STYLE_BUTTONS, false);
                edit(event, selfRoleEditor(ctx, guildId, selfRoles(ctx).find(pid).orElseThrow()));
            }
            case "sredit" -> edit(event, selfRoleEditor(ctx, guildId, selfRoles(ctx).find(id.arg(0)).orElseThrow()));
            case "srdelete" -> { selfRoles(ctx).delete(id.arg(0)); edit(event, selfRolesScreen(ctx, guildId)); }
            case "srdetails" -> {
                SelfRolePanel p = selfRoles(ctx).find(id.arg(0)).orElseThrow();
                event.replyModal(SetupView.selfRoleDetailsModal(p.id(), p.title(), p.description())).queue();
            }
            case "srstyle" -> {
                SelfRolePanel p = selfRoles(ctx).find(id.arg(0)).orElseThrow();
                String next = SelfRolePanel.STYLE_MENU.equals(p.style()) ? SelfRolePanel.STYLE_BUTTONS : SelfRolePanel.STYLE_MENU;
                selfRoles(ctx).updatePanel(p.id(), p.title(), p.description(), next, p.unique());
                edit(event, selfRoleEditor(ctx, guildId, selfRoles(ctx).find(p.id()).orElseThrow()));
            }
            case "srunique" -> {
                SelfRolePanel p = selfRoles(ctx).find(id.arg(0)).orElseThrow();
                selfRoles(ctx).updatePanel(p.id(), p.title(), p.description(), p.style(), !p.unique());
                edit(event, selfRoleEditor(ctx, guildId, selfRoles(ctx).find(p.id()).orElseThrow()));
            }
            case "srpublish" -> publishSelfRole(event, ctx, guildId, id.arg(0));
            default -> { /* not ours */ }
        }
    }

    @Override
    public void onStringSelect(StringSelectInteractionEvent event, ComponentId id, BotContext ctx) {
        if (event.getGuild() == null) {
            return;
        }
        String guildId = event.getGuild().getId();
        switch (id.action()) {
            case "section" -> {
                GuildConfig cfg = config(ctx, guildId);
                Container screen = switch (event.getValues().get(0)) {
                    case "logs" -> SetupView.logsPage(cfg, 0);
                    case "roles" -> SetupView.cargos(cfg, 0);
                    case "tickets" -> ticketsScreen(ctx, guildId);
                    case "acoes" -> actionsScreen(ctx, guildId);
                    case "bot" -> SetupView.bot(EmbedColor.resolve(cfg),
                            event.getJDA().getSelfUser().getName(), event.getJDA().getSelfUser().getId());
                    case "permissoes" -> SetupView.permissionsHub(cfg, event.getGuild());
                    case "moderacao" -> SetupView.moderation(cfg);
                    case "farm" -> SetupView.farmScreen(cfg);
                    case "seguranca" -> SetupView.securityScreen(cfg);
                    case "boasvindas" -> SetupView.welcomeScreen(cfg);
                    case "autocargos" -> selfRolesScreen(ctx, guildId);
                    case "nivel" -> SetupView.levelingScreen(cfg, levelRewards(ctx).all(guildId));
                    case "economia" -> SetupView.economyScreen(cfg);
                    case "loja" -> SetupView.shopScreen(EmbedColor.resolve(cfg),
                            shopCatalog(ctx, guildId),
                            cfg);
                    case "vip" -> SetupView.vipScreen(ctx, guildId);
                    case "eventos" -> SetupView.eventsScreen(cfg);
                    case "fun" -> SetupView.funScreen(cfg, quizRepo(ctx).list(guildId));
                    default -> hubScreen(ctx, guildId);
                };
                edit(event, screen);
            }
            case "ticketcat" -> ctx.database().ticketCategories().find(event.getValues().get(0))
                    .ifPresent(cat -> edit(event,
                            SetupView.ticketDetail(EmbedColor.resolve(config(ctx, guildId)), cat)));
            case "actiontype" -> ctx.database().actionTypes().find(event.getValues().get(0))
                    .ifPresent(type -> edit(event,
                            SetupView.actionTypeDetail(EmbedColor.resolve(config(ctx, guildId)), type)));
            case "permcat" -> {
                String principal = ManagerPermissions.principalFromToken(event.getValues().get(0));
                edit(event, SetupView.permissionsDetail(config(ctx, guildId), principal,
                        principalLabel(principal, event.getGuild())));
            }
            case "farmremove" -> {
                GuildConfig cfg = config(ctx, guildId);
                GuildConfig updated = GuildConfigEdits.withSetting(cfg,
                        dev.davimf.basebot.modules.facs.economy.FarmItems.KEY,
                        dev.davimf.basebot.modules.facs.economy.FarmItems.withRemoved(cfg, event.getValues().get(0)));
                ctx.database().guildConfig().save(updated);
                edit(event, SetupView.farmScreen(updated));
            }
            case "nivelnotify" -> {
                GuildConfig cfg = config(ctx, guildId);
                GuildConfig updated = GuildConfigEdits.withSetting(cfg,
                        dev.davimf.basebot.modules.base.leveling.LevelingConfig.KEY_NOTIFY, event.getValues().get(0));
                ctx.database().guildConfig().save(updated);
                edit(event, SetupView.levelingScreen(updated, levelRewards(ctx).all(guildId)));
            }
            case "nivelrewarddel" -> {
                levelRewards(ctx).remove(guildId, parseInt(event.getValues().get(0)));
                edit(event, SetupView.levelingScreen(config(ctx, guildId), levelRewards(ctx).all(guildId)));
            }
            case "quizdel" -> {
                quizRepo(ctx).remove(event.getValues().get(0));
                edit(event, SetupView.funScreen(config(ctx, guildId), quizRepo(ctx).list(guildId)));
            }
            case "shoptype" -> {
                String type = event.getValues().get(0);
                if ("CUSTOM".equals(type)) {
                    event.replyModal(SetupView.shopItemModal("CUSTOM", "")).queue();
                } else {
                    edit(event, SetupView.shopRolePrompt(EmbedColor.resolve(config(ctx, guildId)), type));
                }
            }
            case "shopremove" -> {
                new dev.davimf.basebot.modules.base.economy.ShopItemRepository(ctx.database().postgres())
                        .delete(guildId, Long.parseLong(event.getValues().get(0)));
                edit(event, SetupView.shopScreen(EmbedColor.resolve(config(ctx, guildId)),
                        shopCatalog(ctx, guildId),
                        config(ctx, guildId)));
            }
            default -> { /* not ours */ }
        }
    }

    @Override
    public void onEntitySelect(EntitySelectInteractionEvent event, ComponentId id, BotContext ctx) {
        switch (id.action()) {
            case "setlogchannel" -> { saveChannel(event, ctx, id.arg(0), firstChannelId(event)); ack(event); }
            case "setrole" -> { saveRole(event, ctx, id.arg(0), firstRoleId(event)); ack(event); }
            case "shoprole" -> {
                String roleId = firstRoleId(event);
                if (roleId == null) {
                    ack(event);
                } else {
                    edit(event, SetupView.shopFormPrompt(EmbedColor.resolve(config(ctx, event.getGuild().getId())),
                            id.arg(0), roleId));
                }
            }
            case "verifychan" -> {
                saveChannel(event, ctx, id.arg(0), firstChannelId(event));
                edit(event, SetupView.verificationScreen(config(ctx, event.getGuild().getId()),
                        ctx.database().verificationQuestions().listByGuild(event.getGuild().getId())));
            }
            case "antispamchan" -> {
                saveChannel(event, ctx, id.arg(0), firstChannelId(event));
                edit(event, SetupView.antispamScreen(config(ctx, event.getGuild().getId())));
            }
            case "welcomechan" -> { saveChannel(event, ctx, id.arg(0), firstChannelId(event)); edit(event, SetupView.welcomeScreen(config(ctx, event.getGuild().getId()))); }
            case "welcomerole" -> { saveRole(event, ctx, id.arg(0), firstRoleId(event)); edit(event, SetupView.welcomeScreen(config(ctx, event.getGuild().getId()))); }
            case "eventchan" -> {
                saveChannel(event, ctx, id.arg(0), firstChannelId(event));
                edit(event, SetupView.eventsScreen(config(ctx, event.getGuild().getId())));
            }
            case "nivelnotifychan" -> {
                saveChannel(event, ctx, id.arg(0), firstChannelId(event));
                edit(event, SetupView.levelingScreen(config(ctx, event.getGuild().getId()),
                        levelRewards(ctx).all(event.getGuild().getId())));
            }
            case "nivelignored" -> {
                String guildId = event.getGuild().getId();
                String csv = event.getMentions().getChannels().stream()
                        .map(net.dv8tion.jda.api.entities.channel.middleman.GuildChannel::getId)
                        .collect(java.util.stream.Collectors.joining(","));
                GuildConfig updated = GuildConfigEdits.withSetting(config(ctx, guildId),
                        dev.davimf.basebot.modules.base.leveling.LevelingConfig.KEY_IGNORED, csv);
                ctx.database().guildConfig().save(updated);
                edit(event, SetupView.levelingScreen(updated, levelRewards(ctx).all(guildId)));
            }
            case "vtincch" -> saveVoiceScope(event, ctx,
                    dev.davimf.basebot.modules.base.leveling.VoiceTimeConfig.KEY_INCLUDE_CHANNELS);
            case "vtexcch" -> saveVoiceScope(event, ctx,
                    dev.davimf.basebot.modules.base.leveling.VoiceTimeConfig.KEY_EXCLUDE_CHANNELS);
            case "vtinccat" -> saveVoiceScope(event, ctx,
                    dev.davimf.basebot.modules.base.leveling.VoiceTimeConfig.KEY_INCLUDE_CATEGORIES);
            case "vtexccat" -> saveVoiceScope(event, ctx,
                    dev.davimf.basebot.modules.base.leveling.VoiceTimeConfig.KEY_EXCLUDE_CATEGORIES);
            case "permrole" -> {
                String principal = "role:" + firstRoleId(event);
                edit(event, SetupView.permissionsDetail(config(ctx, event.getGuild().getId()), principal,
                        principalLabel(principal, event.getGuild())));
            }
            case "vipcat" -> {
                String guildId = event.getGuild().getId();
                VipPlan p = ctx.database().vipPlans().findById(id.arg(0)).orElse(null);
                String catId = firstChannelId(event);
                if (p != null && catId != null) {
                    ctx.database().vipPlans().upsert(withDiscordCategoryId(p, catId));
                }
                edit(event, SetupView.vipScreen(ctx, guildId));
            }
            case "viprole" -> {
                String guildId = event.getGuild().getId();
                VipPlan p = ctx.database().vipPlans().findById(id.arg(0)).orElse(null);
                String roleId = firstRoleId(event);
                if (p != null && roleId != null) {
                    ctx.database().vipPlans().upsert(withVipRoleId(p, roleId));
                }
                edit(event, SetupView.vipScreen(ctx, guildId));
            }
            case "srroles" -> {
                List<net.dv8tion.jda.api.entities.Role> picked = event.getMentions().getRoles();
                List<SelfRolePanel.Option> opts = new java.util.ArrayList<>();
                for (int i = 0; i < picked.size(); i++) {
                    opts.add(new SelfRolePanel.Option(picked.get(i).getId(), picked.get(i).getName(), null, i));
                }
                selfRoles(ctx).setOptions(id.arg(0), opts);
                edit(event, selfRoleEditor(ctx, event.getGuild().getId(), selfRoles(ctx).find(id.arg(0)).orElseThrow()));
            }
            default -> { /* not ours */ }
        }
    }

    @Override
    public void onModal(ModalInteractionEvent event, ComponentId id, BotContext ctx) {
        if (event.getGuild() == null) {
            return;
        }
        if ("botcolorform".equals(id.action())) {
            saveColor(event, ctx);
            return;
        }
        if ("actionform".equals(id.action())) {
            saveActionType(event, ctx, id.arg(0));
            return;
        }
        if ("modrulesform".equals(id.action())) {
            saveModerationRules(event, ctx);
            return;
        }
        if ("farmaddform".equals(id.action())) {
            saveFarmItem(event, ctx);
            return;
        }
        if ("verifyqform".equals(id.action())) {
            saveVerifyQuestion(event, ctx);
            return;
        }
        if ("securityform".equals(id.action())) {
            saveSecurity(event, ctx);
            return;
        }
        if ("antiraidform".equals(id.action())) {
            saveAntiraid(event, ctx);
            return;
        }
        if ("antinukeform".equals(id.action())) {
            saveAntinuke(event, ctx);
            return;
        }
        if ("welcomeform".equals(id.action())) {
            saveWelcome(event, ctx);
            return;
        }
        if ("nivelrewardform".equals(id.action())) {
            saveLevelReward(event, ctx);
            return;
        }
        if ("economyform-currency".equals(id.action())) {
            saveEconomyCurrency(event, ctx);
            return;
        }
        if ("economyform-values".equals(id.action())) {
            saveEconomyValues(event, ctx);
            return;
        }
        if ("shopitemform".equals(id.action())) {
            saveShopItem(event, ctx, id);
            return;
        }
        if ("vipform".equals(id.action())) {
            saveVipPlan(event, ctx, id);
            return;
        }
        if ("quizaddform".equals(id.action())) {
            String guildId = event.getGuild().getId();
            String pergunta = value(event, "pergunta");
            String correta = value(event, "correta");
            String e1 = value(event, "errada1");
            String e2 = value(event, "errada2");
            String e3 = value(event, "errada3");
            if (pergunta == null || pergunta.isBlank() || correta == null || correta.isBlank()
                    || e1 == null || e1.isBlank() || e2 == null || e2.isBlank() || e3 == null || e3.isBlank()) {
                Replies.ephemeral(event, ctx, "Preencha todos os campos.");
                return;
            }
            quizRepo(ctx).add(guildId, pergunta.trim(), correta.trim(), e1.trim(), e2.trim(), e3.trim());
            edit(event, SetupView.funScreen(config(ctx, guildId), quizRepo(ctx).list(guildId)));
            return;
        }
        if ("eventform-interval".equals(id.action())) {
            String guildId = event.getGuild().getId();
            GuildConfig u = config(ctx, guildId);
            u = withLong(u, dev.davimf.basebot.modules.base.events.ChatEventConfig.KEY_MIN, value(event, "min"));
            u = withLong(u, dev.davimf.basebot.modules.base.events.ChatEventConfig.KEY_MAX, value(event, "max"));
            ctx.database().guildConfig().save(u);
            edit(event, SetupView.eventsScreen(u));
            return;
        }
        if ("srdetailsform".equals(id.action())) {
            SelfRolePanel p = selfRoles(ctx).find(id.arg(0)).orElseThrow();
            String title = value(event, "title");
            selfRoles(ctx).updatePanel(p.id(), title == null || title.isBlank() ? "Painel" : title.trim(),
                    value(event, "description"), p.style(), p.unique());
            edit(event, selfRoleEditor(ctx, event.getGuild().getId(), selfRoles(ctx).find(p.id()).orElseThrow()));
            return;
        }
        if (!"ticketform".equals(id.action())) {
            return;
        }
        String guildId = event.getGuild().getId();
        String nome = value(event, "nome");
        String emoji = TicketEmoji.channelSafe(value(event, "emoji"));
        String descricao = value(event, "descricao");

        ModalMapping catMap = event.getValue("categoria");
        ModalMapping cargosMap = event.getValue("cargos");
        List<GuildChannel> cats = catMap == null ? List.of() : catMap.getAsMentions().getChannels();
        List<Role> roles = cargosMap == null ? List.of() : cargosMap.getAsMentions().getRoles();

        if (nome == null || nome.isBlank() || cats.isEmpty() || roles.isEmpty()) {
            Replies.ephemeral(event, ctx,
                    "Preencha o nome, a categoria do Discord e ao menos um cargo que atende.");
            return;
        }

        String catId = "new".equals(id.arg(0)) ? newId() : id.arg(0);
        TicketCategory tc = new TicketCategory(catId, guildId, nome.trim(),
                emoji.isBlank() ? null : emoji,
                (descricao == null || descricao.isBlank()) ? null : descricao.trim(),
                cats.get(0).getId(), roles.stream().map(Role::getId).toList());
        ctx.database().ticketCategories().upsert(tc);
        ctx.database().actionLogs().log(guildId, event.getUser().getId(), catId, "TICKET_CATEGORY_SAVE", nome);

        edit(event, ticketsScreen(ctx, guildId));
    }

    private void saveActionType(ModalInteractionEvent event, BotContext ctx, String idArg) {
        String guildId = event.getGuild().getId();
        String nome = value(event, "nome");
        OptionalInt max = parsePositive(value(event, "maximo"));
        OptionalInt min = parsePositive(value(event, "minimo"));
        OptionalInt sujo = parsePositive(value(event, "sujo"));
        if (nome == null || nome.isBlank() || max.isEmpty() || min.isEmpty() || sujo.isEmpty()) {
            Replies.ephemeral(event, ctx,
                    "Preencha o nome e use números (>= 0) em máximo, mínimo e dinheiro sujo.");
            return;
        }
        String typeId = "new".equals(idArg) ? newId() : idArg;
        ctx.database().actionTypes().upsert(new ActionType(typeId, guildId, nome.trim(),
                max.getAsInt(), min.getAsInt(), sujo.getAsInt()));
        ctx.database().actionLogs().log(guildId, event.getUser().getId(), typeId, "ACTION_TYPE_SAVE", nome);
        edit(event, actionsScreen(ctx, guildId));
    }

    private void saveWelcome(ModalInteractionEvent event, BotContext ctx) {
        String guildId = event.getGuild().getId();
        GuildConfig u = config(ctx, guildId);
        u = GuildConfigEdits.withSetting(u, WelcomeConfig.KEY_MESSAGE, nullToEmpty(value(event, "message")));
        u = GuildConfigEdits.withSetting(u, WelcomeConfig.KEY_FAREWELL_MESSAGE, nullToEmpty(value(event, "farewell")));
        String imageUrl = value(event, "image");
        if (imageUrl == null || imageUrl.isBlank()) {
            // vazio = remover imagem
            u = GuildConfigEdits.withSetting(u, WelcomeConfig.KEY_IMAGE, "");
            ctx.database().guildConfig().save(u);
            edit(event, SetupView.welcomeScreen(u));
            return;
        }
        ctx.database().guildConfig().save(u);
        event.deferEdit().queue();
        final String url = imageUrl.trim();
        ctx.scheduler().executor().execute(() -> {
            dev.davimf.basebot.util.ImageMedia.Image img = null;
            try {
                img = dev.davimf.basebot.util.ImageMedia.fromUrl(url);
                final dev.davimf.basebot.util.ImageMedia.Image capturedImg = img;
                net.dv8tion.jda.api.utils.FileUpload file =
                        net.dv8tion.jda.api.utils.FileUpload.fromData(img.bytes(), img.fileName());
                AttachmentVault vault = new AttachmentVault(ctx, ctx.config().discord().vaultGuildId());
                vault.store(guildId, "welcome-banner " + guildId,
                        file,
                        (vChan, vMsg) -> {
                            GuildConfig saved = GuildConfigEdits.withSetting(config(ctx, guildId),
                                    WelcomeConfig.KEY_IMAGE, vChan + ":" + vMsg);
                            ctx.database().guildConfig().save(saved);
                            capturedImg.erase();
                            event.getHook().editOriginalComponents(SetupView.welcomeScreen(saved)).useComponentsV2().queue();
                        },
                        () -> {
                            capturedImg.erase();
                            event.getHook().editOriginalComponents(SetupView.welcomeScreen(config(ctx, guildId)))
                                    .useComponentsV2().queue();
                        });
            } catch (Exception e) {
                if (img != null) {
                    img.erase();
                }
                event.getHook().editOriginalComponents(SetupView.welcomeScreen(config(ctx, guildId)))
                        .useComponentsV2().queue();
            }
        });
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s.trim(); }

    private void saveAntiraid(ModalInteractionEvent event, BotContext ctx) {
        String guildId = event.getGuild().getId();
        GuildConfig u = config(ctx, guildId);
        String joins = value(event, "joins");
        String window = value(event, "window");
        String minage = value(event, "minage");
        String level = value(event, "locklevel");
        if (joins != null && joins.matches("\\d+")) {
            u = GuildConfigEdits.withSetting(u, dev.davimf.basebot.modules.base.security.SecurityConfig.KEY_RAID_JOINS, joins.trim());
        }
        if (window != null && window.matches("\\d+")) {
            u = GuildConfigEdits.withSetting(u, dev.davimf.basebot.modules.base.security.SecurityConfig.KEY_RAID_WINDOW_S, window.trim());
        }
        if (minage != null && minage.matches("\\d+")) {
            u = GuildConfigEdits.withSetting(u, dev.davimf.basebot.modules.base.security.SecurityConfig.KEY_RAID_MIN_AGE, minage.trim());
        }
        if (level != null) {
            String lv = level.trim().toUpperCase(java.util.Locale.ROOT);
            if (java.util.Set.of("NONE", "LOW", "MEDIUM", "HIGH", "VERY_HIGH").contains(lv)) {
                u = GuildConfigEdits.withSetting(u, dev.davimf.basebot.modules.base.security.SecurityConfig.KEY_RAID_LOCK_LEVEL, lv);
            }
        }
        ctx.database().guildConfig().save(u);
        edit(event, SetupView.securityScreen(u));
    }

    private void saveAntinuke(ModalInteractionEvent event, BotContext ctx) {
        String guildId = event.getGuild().getId();
        GuildConfig u = config(ctx, guildId);
        String max = value(event, "max");
        String window = value(event, "window");
        String whitelist = value(event, "whitelist");
        if (max != null && max.matches("\\d+")) {
            u = GuildConfigEdits.withSetting(u, dev.davimf.basebot.modules.base.security.SecurityConfig.KEY_ANTINUKE_MAX, max.trim());
        }
        if (window != null && window.matches("\\d+")) {
            u = GuildConfigEdits.withSetting(u, dev.davimf.basebot.modules.base.security.SecurityConfig.KEY_ANTINUKE_WINDOW_S, window.trim());
        }
        u = GuildConfigEdits.withSetting(u, dev.davimf.basebot.modules.base.security.SecurityConfig.KEY_ANTINUKE_WHITELIST,
                whitelist == null ? "" : whitelist.trim());
        ctx.database().guildConfig().save(u);
        edit(event, SetupView.securityScreen(u));
    }

    private void saveSecurity(ModalInteractionEvent event, BotContext ctx) {
        String guildId = event.getGuild().getId();
        GuildConfig u = config(ctx, guildId);
        String mention = value(event, "mention");
        String warnper = value(event, "warnper");
        String kw = value(event, "keywords");
        if (mention != null && mention.matches("\\d+")) {
            u = GuildConfigEdits.withSetting(u, dev.davimf.basebot.modules.base.security.SecurityConfig.KEY_MENTION_LIMIT, mention.trim());
        }
        if (warnper != null && warnper.matches("\\d+")) {
            u = GuildConfigEdits.withSetting(u, dev.davimf.basebot.modules.base.security.SecurityConfig.KEY_WARN_PER, warnper.trim());
        }
        u = GuildConfigEdits.withSetting(u, dev.davimf.basebot.modules.base.security.SecurityConfig.KEY_KEYWORDS,
                kw == null ? "" : kw.trim());
        ctx.database().guildConfig().save(u);
        net.dv8tion.jda.api.entities.Guild guild = event.getGuild();
        GuildConfig finalU = u;
        ctx.scheduler().executor().execute(() ->
                dev.davimf.basebot.modules.base.security.AutoModManager.sync(guild, finalU));
        edit(event, SetupView.securityScreen(u));
    }

    private void saveFarmItem(ModalInteractionEvent event, BotContext ctx) {
        String guildId = event.getGuild().getId();
        GuildConfig cfg = config(ctx, guildId);
        String nome = value(event, "nome");
        if (nome == null || nome.isBlank()) {
            Replies.ephemeral(event, ctx, "Informe o nome do item.");
            return;
        }
        GuildConfig updated = GuildConfigEdits.withSetting(cfg,
                dev.davimf.basebot.modules.facs.economy.FarmItems.KEY,
                dev.davimf.basebot.modules.facs.economy.FarmItems.withAdded(cfg, nome));
        ctx.database().guildConfig().save(updated);
        edit(event, SetupView.farmScreen(updated));
    }

    private void saveColor(ModalInteractionEvent event, BotContext ctx) {
        String guildId = event.getGuild().getId();
        OptionalInt parsed = EmbedColor.parse(value(event, "cor"));
        if (parsed.isEmpty()) {
            Replies.ephemeral(event, ctx, "Cor inválida. Use um hex como `#5865F2`.");
            return;
        }
        int color = parsed.getAsInt();
        GuildConfig cfg = GuildConfigEdits.withSetting(
                config(ctx, guildId), EmbedColor.SETTING_KEY, EmbedColor.hex(color));
        ctx.database().guildConfig().save(cfg);
        ctx.database().actionLogs().log(guildId, event.getUser().getId(), null,
                "SETUP_COLOR", EmbedColor.hex(color));
        edit(event, SetupView.bot(color, event.getJDA().getSelfUser().getName(),
                event.getJDA().getSelfUser().getId()));
    }

    // --- screens ---------------------------------------------------------------

    private Container hubScreen(BotContext ctx, String guildId) {
        return SetupView.hub(config(ctx, guildId), ctx.database().ticketCategories().count(guildId),
                ctx.database().actionTypes().count(guildId));
    }

    private Container ticketsScreen(BotContext ctx, String guildId) {
        return SetupView.ticketsList(EmbedColor.resolve(config(ctx, guildId)),
                ctx.database().ticketCategories().listByGuild(guildId));
    }

    private Container actionsScreen(BotContext ctx, String guildId) {
        return SetupView.actionTypesList(config(ctx, guildId),
                ctx.database().actionTypes().listByGuild(guildId));
    }

    private SelfRolePanelRepository selfRoles(BotContext ctx) {
        return new SelfRolePanelRepository(ctx.database().postgres());
    }

    private Container selfRolesScreen(BotContext ctx, String guildId) {
        int accent = EmbedColor.resolve(config(ctx, guildId));
        List<SelfRolePanel> panels = selfRoles(ctx).list(guildId);
        StringBuilder sb = new StringBuilder("## " + Emojis.of(Emojis.MEMBERS, "🏷️") + " Auto-cargos\n");
        List<ActionRow> rows = new java.util.ArrayList<>();
        if (panels.isEmpty()) {
            sb.append("-# Nenhum painel ainda. Crie um para os membros se auto-atribuírem cargos.");
        } else {
            for (SelfRolePanel p : panels) {
                sb.append("\n• **").append(p.title()).append("** · `").append(p.style())
                        .append(p.unique() ? "/exclusivo" : "").append("` · ").append(p.options().size()).append(" cargo(s)");
                rows.add(ActionRow.of(
                        Button.primary(ComponentId.of(SetupView.NS,"sredit", p.id()), "Editar: " + trim(p.title())).withEmoji(Emojis.button(Emojis.EDIT)),
                        Button.success(ComponentId.of(SetupView.NS,"srpublish", p.id()), "Publicar").withEmoji(Emojis.button(Emojis.SEND)),
                        Button.danger(ComponentId.of(SetupView.NS,"srdelete", p.id()), "Excluir")));
                if (rows.size() >= 4) {
                    break; // no máx. ~4 painéis editáveis por tela (limite de rows)
                }
            }
        }
        List<ContainerChildComponent> kids = new java.util.ArrayList<>();
        kids.add(Panels.text(sb.toString()));
        kids.add(Panels.divider());
        kids.addAll(rows);
        kids.add(ActionRow.of(
                Button.primary(ComponentId.of(SetupView.NS,"srnew"), "Novo painel").withEmoji(Emojis.button(Emojis.EDIT)),
                Button.secondary(ComponentId.of(SetupView.NS,"nav", "hub"), "◀ Voltar")));
        kids.add(SetupView.moduleNav("autocargos"));
        return Panels.container(accent, kids.toArray(new ContainerChildComponent[0]));
    }

    private static String trim(String s) { return s.length() > 20 ? s.substring(0, 20) : s; }

    private Container selfRoleEditor(BotContext ctx, String guildId, SelfRolePanel p) {
        int accent = EmbedColor.resolve(config(ctx, guildId));
        String body = "## " + Emojis.of(Emojis.MEMBERS, "🏷️") + " Editar painel\n---\n"
                + "**Título** · " + p.title() + "\n"
                + "**Estilo** · `" + p.style() + (p.unique() ? "/exclusivo" : "") + "`\n"
                + "**Cargos** · " + (p.options().isEmpty() ? "*nenhum*"
                    : p.roleIds().stream().map(r -> "<@&" + r + ">").collect(java.util.stream.Collectors.joining(" ")));
        EntitySelectMenu roles = EntitySelectMenu.create(ComponentId.of(SetupView.NS,"srroles", p.id()), EntitySelectMenu.SelectTarget.ROLE)
                .setPlaceholder("Cargos do painel (substitui a lista)…").setRequiredRange(1, 25).build();
        return Panels.container(accent,
                Panels.text(body),
                Panels.divider(),
                ActionRow.of(roles),
                ActionRow.of(
                        Button.secondary(ComponentId.of(SetupView.NS,"srdetails", p.id()), "Título/estilo").withEmoji(Emojis.button(Emojis.EDIT)),
                        Button.secondary(ComponentId.of(SetupView.NS,"srstyle", p.id()), "Alternar estilo"),
                        Button.secondary(ComponentId.of(SetupView.NS,"srunique", p.id()), "Exclusivo: " + (p.unique() ? "on" : "off"))),
                ActionRow.of(
                        Button.success(ComponentId.of(SetupView.NS,"srpublish", p.id()), "Publicar").withEmoji(Emojis.button(Emojis.SEND)),
                        Button.secondary(ComponentId.of(SetupView.NS,"nav", "autocargos"), "◀ Voltar")));
    }

    // --- persistence -----------------------------------------------------------

    private void saveChannel(EntitySelectInteractionEvent event, BotContext ctx, String key, String channelId) {
        if (channelId == null || event.getGuild() == null) {
            return;
        }
        String guildId = event.getGuild().getId();
        GuildConfig updated = GuildConfigEdits.withChannel(
                ctx.database().guildConfig().findOrEmpty(guildId), key, channelId);
        ctx.database().guildConfig().save(updated);
        ctx.database().actionLogs().log(guildId, event.getUser().getId(), channelId, "SETUP_CHANNEL", key);
    }

    private void saveRole(EntitySelectInteractionEvent event, BotContext ctx, String key, String roleId) {
        if (roleId == null || key == null || event.getGuild() == null) {
            return;
        }
        String guildId = event.getGuild().getId();
        GuildConfig updated = GuildConfigEdits.withRole(
                ctx.database().guildConfig().findOrEmpty(guildId), key, roleId);
        ctx.database().guildConfig().save(updated);
        ctx.database().actionLogs().log(guildId, event.getUser().getId(), roleId, "SETUP_ROLE", key);
    }

    private void saveVoiceScope(EntitySelectInteractionEvent event, BotContext ctx, String key) {
        if (event.getGuild() == null) {
            return;
        }
        String guildId = event.getGuild().getId();
        String csv = event.getMentions().getChannels().stream()
                .map(GuildChannel::getId)
                .collect(java.util.stream.Collectors.joining(","));
        GuildConfig updated = GuildConfigEdits.withSetting(config(ctx, guildId), key, csv);
        ctx.database().guildConfig().save(updated);
        edit(event, SetupView.voiceTimeScreen(updated));
    }

    private dev.davimf.basebot.modules.base.leveling.LevelRewardRepository levelRewards(BotContext ctx) {
        return new dev.davimf.basebot.modules.base.leveling.LevelRewardRepository(ctx.database().postgres());
    }

    /** Itens da loja com o contador {@code sold} preenchido a partir do shop_stock (SQLite). */
    private java.util.List<dev.davimf.basebot.modules.base.economy.ShopItem> shopCatalog(BotContext ctx, String guildId) {
        var repo = new dev.davimf.basebot.modules.base.economy.ShopItemRepository(ctx.database().postgres());
        var stockRepo = new dev.davimf.basebot.modules.base.economy.ShopStockRepository(ctx.database().sqlite());
        java.util.List<dev.davimf.basebot.modules.base.economy.ShopItem> out = new java.util.ArrayList<>();
        for (var it : repo.list(guildId)) {
            out.add(it.withSold(stockRepo.soldOf(it.id())));
        }
        return out;
    }

    private dev.davimf.basebot.modules.base.fun.QuizRepository quizRepo(BotContext ctx) {
        return new dev.davimf.basebot.modules.base.fun.QuizRepository(ctx.database().postgres());
    }

    private void saveEconomyCurrency(ModalInteractionEvent event, BotContext ctx) {
        String guildId = event.getGuild().getId();
        GuildConfig u = config(ctx, guildId);
        String nome = value(event, "nome");
        String emoji = value(event, "emoji");
        u = GuildConfigEdits.withSetting(u, dev.davimf.basebot.modules.base.economy.EconomyConfig.KEY_CURRENCY_NAME,
                nome == null ? "" : nome.trim());
        u = GuildConfigEdits.withSetting(u, dev.davimf.basebot.modules.base.economy.EconomyConfig.KEY_CURRENCY_EMOJI,
                emoji == null ? "" : emoji.trim());
        ctx.database().guildConfig().save(u);
        edit(event, SetupView.economyScreen(u));
    }

    private void saveEconomyValues(ModalInteractionEvent event, BotContext ctx) {
        String guildId = event.getGuild().getId();
        GuildConfig u = config(ctx, guildId);
        u = withLong(u, dev.davimf.basebot.modules.base.economy.EconomyConfig.KEY_DAILY, value(event, "daily"));
        u = withLong(u, dev.davimf.basebot.modules.base.economy.EconomyConfig.KEY_WORK_MIN, value(event, "work_min"));
        u = withLong(u, dev.davimf.basebot.modules.base.economy.EconomyConfig.KEY_WORK_MAX, value(event, "work_max"));
        u = withLong(u, dev.davimf.basebot.modules.base.economy.EconomyConfig.KEY_WORK_COOLDOWN, value(event, "work_cooldown"));
        ctx.database().guildConfig().save(u);
        edit(event, SetupView.economyScreen(u));
    }

    private void saveShopItem(ModalInteractionEvent event, BotContext ctx, ComponentId id) {
        String guildId = event.getGuild().getId();
        var repo = new dev.davimf.basebot.modules.base.economy.ShopItemRepository(ctx.database().postgres());
        if (repo.count(guildId) >= 25) {
            Replies.ephemeral(event, ctx, "Loja cheia (máx. 25 itens). Remova um item antes de adicionar.");
            return;
        }
        String type = id.arg(0);
        String roleId = id.arg(1) != null && !id.arg(1).isBlank() ? id.arg(1) : null;
        String name = value(event, "name");
        String description = value(event, "description");
        long price;
        try {
            price = Long.parseLong(value(event, "price").trim());
        } catch (Exception e) {
            Replies.ephemeral(event, ctx, "Preço inválido — use um número inteiro.");
            return;
        }
        if (price <= 0) {
            Replies.ephemeral(event, ctx, "Preço deve ser maior que zero.");
            return;
        }
        Long durationS = null;
        if ("ROLE_TEMP".equals(type)) {
            var parsed = dev.davimf.basebot.util.Durations.parse(value(event, "duration"));
            if (parsed.isEmpty()) {
                Replies.ephemeral(event, ctx, "Duração inválida — use ex.: 7d, 12h, 30m.");
                return;
            }
            durationS = parsed.getAsLong() / 1000L;
        }
        var limits = dev.davimf.basebot.modules.base.economy.ShopPurchaseRules.parseLimits(value(event, "limits"));
        if (!limits.valid()) {
            Replies.ephemeral(event, ctx, "Limites inválidos — use ex.: 10/1 (estoque/usuário).");
            return;
        }
        if (roleId != null) {
            Role role = event.getGuild().getRoleById(roleId);
            if (role == null) {
                Replies.ephemeral(event, ctx, "Cargo não encontrado.");
                return;
            }
            if (!event.getGuild().getSelfMember().canInteract(role)) {
                Replies.ephemeral(event, ctx, "Não consigo atribuir " + role.getAsMention()
                        + " (hierarquia) — mova o cargo do bot acima dele e tente de novo.");
                return;
            }
        }
        var item = new dev.davimf.basebot.modules.base.economy.ShopItem(0, guildId,
                dev.davimf.basebot.modules.base.economy.ShopItem.Type.valueOf(type), roleId,
                name == null ? "Item" : name.trim(),
                description == null ? null : description.trim(),
                price, durationS, limits.stock(), limits.perUser(), 0, System.currentTimeMillis());
        repo.insert(item);
        edit(event, SetupView.shopScreen(EmbedColor.resolve(config(ctx, guildId)), repo.list(guildId), config(ctx, guildId)));
    }

    private void saveVipPlan(ModalInteractionEvent event, BotContext ctx, ComponentId id) {
        String guildId = event.getGuild().getId();
        String nome = value(event, "nome");
        if (nome == null || nome.isBlank()) {
            Replies.ephemeral(event, ctx, "Preencha o nome do plano.");
            return;
        }
        int xpPct = parseInt(value(event, "xp_pct"));
        int ecoPct = parseInt(value(event, "eco_pct"));
        String duracaoRaw = value(event, "duracao_dias");
        Long durationMinutes = null;
        if (duracaoRaw != null && !duracaoRaw.isBlank()) {
            try {
                durationMinutes = Long.parseLong(duracaoRaw.trim()) * 1440L;
            } catch (NumberFormatException e) {
                Replies.ephemeral(event, ctx, "Duração inválida — use um número de dias, ou deixe vazio para permanente.");
                return;
            }
        }

        boolean isNew = "new".equals(id.arg(0));
        VipPlan existing = isNew ? null : ctx.database().vipPlans().findById(id.arg(0)).orElse(null);
        String planId = isNew ? UUID.randomUUID().toString() : id.arg(0);
        java.time.Instant now = java.time.Instant.now();
        VipPlan plan = new VipPlan(
                planId, guildId, nome.trim(),
                existing == null ? null : existing.discordCategoryId(),
                existing == null ? true : existing.hasCall(),
                existing == null ? null : existing.vipRoleId(),
                existing == null ? true : existing.useControlRole(),
                xpPct, ecoPct, durationMinutes,
                existing == null ? true : existing.revealDefault(),
                existing == null ? true : existing.enabled(),
                existing == null ? 0 : existing.position(),
                existing == null ? now : existing.createdAt(),
                now);
        ctx.database().vipPlans().upsert(plan);
        ctx.database().actionLogs().log(guildId, event.getUser().getId(), planId, "VIP_PLAN_SAVE", nome);
        edit(event, SetupView.vipScreen(ctx, guildId));
    }

    private static VipPlan withHasCall(VipPlan p, boolean hasCall) {
        return new VipPlan(p.id(), p.guildId(), p.name(), p.discordCategoryId(), hasCall, p.vipRoleId(),
                p.useControlRole(), p.xpBonusPct(), p.ecoBonusPct(), p.defaultDurationMinutes(),
                p.revealDefault(), p.enabled(), p.position(), p.createdAt(), java.time.Instant.now());
    }

    private static VipPlan withUseControlRole(VipPlan p, boolean useControlRole) {
        return new VipPlan(p.id(), p.guildId(), p.name(), p.discordCategoryId(), p.hasCall(), p.vipRoleId(),
                useControlRole, p.xpBonusPct(), p.ecoBonusPct(), p.defaultDurationMinutes(),
                p.revealDefault(), p.enabled(), p.position(), p.createdAt(), java.time.Instant.now());
    }

    private static VipPlan withDiscordCategoryId(VipPlan p, String categoryId) {
        return new VipPlan(p.id(), p.guildId(), p.name(), categoryId, p.hasCall(), p.vipRoleId(),
                p.useControlRole(), p.xpBonusPct(), p.ecoBonusPct(), p.defaultDurationMinutes(),
                p.revealDefault(), p.enabled(), p.position(), p.createdAt(), java.time.Instant.now());
    }

    private static VipPlan withVipRoleId(VipPlan p, String roleId) {
        return new VipPlan(p.id(), p.guildId(), p.name(), p.discordCategoryId(), p.hasCall(), roleId,
                p.useControlRole(), p.xpBonusPct(), p.ecoBonusPct(), p.defaultDurationMinutes(),
                p.revealDefault(), p.enabled(), p.position(), p.createdAt(), java.time.Instant.now());
    }

    /** Salva um inteiro válido (≥0); ignora entradas inválidas mantendo o valor anterior. */
    private static GuildConfig withLong(GuildConfig cfg, String key, String raw) {
        if (raw == null || raw.isBlank()) {
            return cfg;
        }
        try {
            long v = Long.parseLong(raw.trim());
            if (v < 0) {
                return cfg;
            }
            return GuildConfigEdits.withSetting(cfg, key, String.valueOf(v));
        } catch (NumberFormatException e) {
            return cfg;
        }
    }

    private void saveLevelReward(ModalInteractionEvent event, BotContext ctx) {
        String guildId = event.getGuild().getId();
        String nivelRaw = value(event, "nivel");
        String cargoRaw = value(event, "cargo");
        int level;
        try {
            level = Integer.parseInt(nivelRaw == null ? "" : nivelRaw.trim());
        } catch (NumberFormatException e) {
            Replies.ephemeral(event, ctx, "Nível inválido.");
            return;
        }
        String roleId = cargoRaw == null ? "" : cargoRaw.replaceAll("\\D", "");
        net.dv8tion.jda.api.entities.Role role = roleId.isBlank() ? null : event.getGuild().getRoleById(roleId);
        if (level < 1 || role == null) {
            Replies.ephemeral(event, ctx, "Informe um nível ≥ 1 e um cargo válido (id ou menção).");
            return;
        }
        levelRewards(ctx).put(guildId, level, roleId);
        edit(event, SetupView.levelingScreen(config(ctx, guildId), levelRewards(ctx).all(guildId)));
    }

    // --- helpers ---------------------------------------------------------------

    private void edit(IMessageEditCallback event, Container screen) {
        event.editComponents(screen).useComponentsV2().queue();
    }

    private void ack(EntitySelectInteractionEvent event) {
        event.deferEdit().queue();
    }

    private GuildConfig config(BotContext ctx, String guildId) {
        return ctx.database().guildConfig().findOrEmpty(guildId);
    }

    private void saveVerifyQuestion(ModalInteractionEvent event, BotContext ctx) {
        String guildId = event.getGuild().getId();
        String prompt = value(event, "prompt");
        if (prompt != null && !prompt.isBlank()
                && ctx.database().verificationQuestions().count(guildId) < 5) {
            int pos = ctx.database().verificationQuestions().count(guildId);
            ctx.database().verificationQuestions().add(
                    new dev.davimf.basebot.database.postgres.VerificationQuestionRepository.Question(
                            dev.davimf.basebot.database.postgres.VerificationQuestionRepository.newId(),
                            guildId, pos, prompt.trim(), true));
        }
        edit(event, SetupView.verificationScreen(config(ctx, guildId),
                ctx.database().verificationQuestions().listByGuild(guildId)));
    }

    private void publishAntispam(ButtonInteractionEvent event, BotContext ctx, String guildId) {
        if (!(event.getChannel() instanceof net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel ch)) {
            Replies.ephemeral(event, ctx, "Use num canal de texto.");
            return;
        }
        int accent = EmbedColor.resolve(config(ctx, guildId));
        ch.sendMessageComponents(dev.davimf.basebot.modules.base.security.AntiSpamView.panel(accent))
                .useComponentsV2()
                .queue(ok -> Replies.ephemeral(event, ctx, "Aviso do anti-spam publicado."),
                        err -> Replies.ephemeral(event, ctx, "Falha ao publicar: " + err.getMessage()));
    }

    private void publishVerify(ButtonInteractionEvent event, BotContext ctx, String guildId) {
        if (!(event.getChannel() instanceof net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel ch)) {
            Replies.ephemeral(event, ctx, "Use num canal de texto.");
            return;
        }
        int accent = EmbedColor.resolve(config(ctx, guildId));
        ch.sendMessageComponents(dev.davimf.basebot.modules.base.security.VerificationView.panel(accent))
                .useComponentsV2()
                .queue(ok -> Replies.ephemeral(event, ctx, "Painel de verificação publicado."),
                        err -> Replies.ephemeral(event, ctx, "Falha ao publicar: " + err.getMessage()));
    }

    /** Agenda o sweep de lockdown após um toggle verify (on→apply, off→revert). Se o cargo membro
     *  não estiver configurado ao ligar, não faz nada — o banner da sub-tela comunica o motivo. */
    private void scheduleVerificationLockdown(BotContext ctx, ButtonInteractionEvent event, GuildConfig updated) {
        boolean nowOn = dev.davimf.basebot.modules.base.security.SecurityConfig.verify(updated);
        boolean hasMembro = updated.role("membro") != null;
        if (nowOn && !hasMembro) {
            return;
        }
        net.dv8tion.jda.api.entities.Guild guild = event.getGuild();
        String userId = event.getUser().getId();
        ctx.scheduler().executor().execute(() -> {
            try {
                dev.davimf.basebot.modules.base.security.VerificationLockdown.Summary s = nowOn
                        ? dev.davimf.basebot.modules.base.security.VerificationLockdown.apply(ctx, guild)
                        : dev.davimf.basebot.modules.base.security.VerificationLockdown.revert(ctx, guild);
                ctx.database().actionLogs().log(guild.getId(), userId, null,
                        nowOn ? "VERIFY_LOCKDOWN" : "VERIFY_UNLOCK",
                        s.busy() ? "ocupado — operação já em andamento"
                                : s.memberRoleMissing() ? "cargo membro ausente — nada alterado"
                                : s.changed() + " alterados / " + s.skipped() + " pulados");
            } catch (RuntimeException e) {
                // best-effort; o toggle já foi respondido com a re-renderização síncrona da tela
            }
        });
    }

    /** Botão manual: esconde (hide=true) ou reabre (hide=false) e devolve um resumo efêmero. */
    private void runVerificationLockdown(ButtonInteractionEvent event, BotContext ctx, String guildId, boolean hide) {
        event.deferEdit().queue();
        net.dv8tion.jda.api.entities.Guild guild = event.getGuild();
        String userId = event.getUser().getId();
        ctx.scheduler().executor().execute(() -> {
            try {
                dev.davimf.basebot.modules.base.security.VerificationLockdown.Summary s = hide
                        ? dev.davimf.basebot.modules.base.security.VerificationLockdown.apply(ctx, guild)
                        : dev.davimf.basebot.modules.base.security.VerificationLockdown.revert(ctx, guild);
                GuildConfig refreshed = ctx.database().guildConfig().findOrEmpty(guildId);
                event.getHook().editOriginalComponents(SetupView.verificationScreen(refreshed,
                                ctx.database().verificationQuestions().listByGuild(guildId)))
                        .useComponentsV2().queue(ok -> {}, err -> {});
                String msg;
                String detail;
                if (s.busy()) {
                    msg = "Já há uma operação de lockdown em andamento neste servidor. Tente novamente em instantes.";
                    detail = "ocupado — operação já em andamento";
                } else if (s.memberRoleMissing()) {
                    msg = "Configure o cargo **membro** primeiro — nada foi alterado.";
                    detail = "cargo membro ausente — nada alterado";
                } else {
                    msg = (hide ? "Escondidos" : "Reabertos") + " `" + s.changed() + "` canais · `"
                            + s.skipped() + "` pulados.";
                    detail = s.changed() + " alterados / " + s.skipped() + " pulados";
                }
                ctx.database().actionLogs().log(guildId, userId, null,
                        hide ? "VERIFY_LOCKDOWN" : "VERIFY_UNLOCK", detail);
                event.getHook().sendMessageComponents(
                                Panels.container(EmbedColor.resolve(refreshed), Panels.text(msg)))
                        .useComponentsV2().setEphemeral(true).queue(ok -> {}, err -> {});
            } catch (RuntimeException e) {
                event.getHook().sendMessageComponents(
                                Panels.container(EmbedColor.resolve(GuildConfig.empty(guildId)),
                                        Panels.text("Falha ao " + (hide ? "esconder" : "reabrir")
                                                + " canais: " + e.getMessage())))
                        .useComponentsV2().setEphemeral(true).queue(ok -> {}, err -> {});
            }
        });
    }

    private void publishSelfRole(ButtonInteractionEvent event, BotContext ctx, String guildId, String panelId) {
        SelfRolePanel p = selfRoles(ctx).find(panelId).orElse(null);
        if (p == null || p.options().isEmpty()) {
            Replies.ephemeral(event, ctx, "Adicione ao menos um cargo antes de publicar.");
            return;
        }
        if (!(event.getChannel() instanceof net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel ch)) {
            Replies.ephemeral(event, ctx, "Use num canal de texto.");
            return;
        }
        int accent = EmbedColor.resolve(config(ctx, guildId));
        ch.sendMessageComponents(dev.davimf.basebot.modules.base.selfroles.SelfRoleView.panel(accent, p))
                .useComponentsV2()
                .queue(sent -> {
                    selfRoles(ctx).setPublished(panelId, ch.getId(), sent.getId());
                    Replies.ephemeral(event, ctx, "Painel publicado.");
                }, err -> Replies.ephemeral(event, ctx, "Falha ao publicar: " + err.getMessage()));
    }

    private void saveModerationRules(ModalInteractionEvent event, BotContext ctx) {
        String guildId = event.getGuild().getId();
        GuildConfig cfg = config(ctx, guildId);
        String escalation = ModerationConfig.serialize(ModerationConfig.parse(value(event, "escalation")));
        int ttlDays = 0;
        String rawTtl = value(event, "ttl");
        if (rawTtl != null && !rawTtl.isBlank()) {
            try {
                ttlDays = Math.max(0, Integer.parseInt(rawTtl.trim()));
            } catch (NumberFormatException ignored) {
                // invalid → keep 0 (never expires)
            }
        }
        GuildConfig updated = GuildConfigEdits.withSetting(
                GuildConfigEdits.withSetting(cfg, ModerationConfig.KEY_ESCALATION, escalation),
                ModerationConfig.KEY_WARN_TTL, String.valueOf(ttlDays));
        ctx.database().guildConfig().save(updated);
        ctx.database().actionLogs().log(guildId, event.getUser().getId(), null, "MOD_CONFIG", escalation);
        edit(event, SetupView.moderation(updated));
    }

    private Container permAddRolePrompt(BotContext ctx, String guildId) {
        EntitySelectMenu menu = EntitySelectMenu
                .create(ComponentId.of(SetupView.NS, "permrole"), EntitySelectMenu.SelectTarget.ROLE)
                .setPlaceholder("Escolha o cargo a configurar")
                .setRequiredRange(1, 1)
                .build();
        return Panels.container(EmbedColor.resolve(config(ctx, guildId)),
                Panels.text("## " + Emojis.of(Emojis.PERMS, "🔐") + " Adicionar cargo às permissões"),
                Panels.divider(),
                Panels.text("> Selecione um cargo do servidor para definir o que ele pode fazer."),
                ActionRow.of(menu));
    }

    private String principalLabel(String principal, Guild guild) {
        if (principal.startsWith("role:")) {
            String roleId = principal.substring("role:".length());
            Role r = guild == null ? null : guild.getRoleById(roleId);
            return r == null ? "Cargo " + roleId : r.getName();
        }
        return SetupRoleKeys.labelFor(principal);
    }

    private static String value(ModalInteractionEvent event, String key) {
        ModalMapping m = event.getValue(key);
        return m == null ? null : m.getAsString();
    }

    private static String newId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private static int parseInt(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Parses a non-negative integer from user text; empty if blank, non-numeric or negative. */
    private static OptionalInt parsePositive(String raw) {
        if (raw == null || raw.isBlank()) {
            return OptionalInt.empty();
        }
        try {
            int v = Integer.parseInt(raw.trim());
            return v < 0 ? OptionalInt.empty() : OptionalInt.of(v);
        } catch (NumberFormatException e) {
            return OptionalInt.empty();
        }
    }

    private static String firstChannelId(EntitySelectInteractionEvent event) {
        List<GuildChannel> channels = event.getMentions().getChannels();
        return channels.isEmpty() ? null : channels.get(0).getId();
    }

    private static String firstRoleId(EntitySelectInteractionEvent event) {
        List<Role> roles = event.getMentions().getRoles();
        return roles.isEmpty() ? null : roles.get(0).getId();
    }
}
