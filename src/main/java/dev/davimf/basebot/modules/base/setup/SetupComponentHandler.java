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
import dev.davimf.basebot.database.sqlite.ActionTypeRepository.ActionType;
import dev.davimf.basebot.modules.base.listeners.AttachmentVault;
import dev.davimf.basebot.modules.base.moderation.ModerationConfig;
import dev.davimf.basebot.modules.base.selfroles.SelfRolePanel;
import dev.davimf.basebot.modules.base.selfroles.SelfRolePanelRepository;
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
            case "sectoggle" -> {
                GuildConfig cfg = config(ctx, guildId);
                String arg = id.arg(0);
                String key = switch (arg) {
                    case "automod" -> dev.davimf.basebot.modules.base.security.SecurityConfig.KEY_AUTOMOD;
                    case "warn" -> dev.davimf.basebot.modules.base.security.SecurityConfig.KEY_AUTOMOD_WARN;
                    case "antiraid" -> dev.davimf.basebot.modules.base.security.SecurityConfig.KEY_ANTIRAID;
                    case "verify" -> dev.davimf.basebot.modules.base.security.SecurityConfig.KEY_VERIFY;
                    case "antinuke" -> dev.davimf.basebot.modules.base.security.SecurityConfig.KEY_ANTINUKE;
                    default -> dev.davimf.basebot.modules.base.security.SecurityConfig.KEY_BLOCK_INVITES;
                };
                boolean def = "warn".equals(arg) || "invites".equals(arg); // warn/invites=true; automod/antiraid=false
                GuildConfig updated = GuildConfigEdits.withToggle(cfg, key, !cfg.toggle(key, def));
                ctx.database().guildConfig().save(updated);
                net.dv8tion.jda.api.entities.Guild guild = event.getGuild();
                ctx.scheduler().executor().execute(() ->
                        dev.davimf.basebot.modules.base.security.AutoModManager.sync(guild, updated));
                edit(event, SetupView.securityScreen(updated));
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
            default -> { /* not ours */ }
        }
    }

    @Override
    public void onEntitySelect(EntitySelectInteractionEvent event, ComponentId id, BotContext ctx) {
        switch (id.action()) {
            case "setlogchannel" -> { saveChannel(event, ctx, id.arg(0), firstChannelId(event)); ack(event); }
            case "setrole" -> { saveRole(event, ctx, id.arg(0), firstRoleId(event)); ack(event); }
            case "welcomechan" -> { saveChannel(event, ctx, id.arg(0), firstChannelId(event)); edit(event, SetupView.welcomeScreen(config(ctx, event.getGuild().getId()))); }
            case "welcomerole" -> { saveRole(event, ctx, id.arg(0), firstRoleId(event)); edit(event, SetupView.welcomeScreen(config(ctx, event.getGuild().getId()))); }
            case "permrole" -> {
                String principal = "role:" + firstRoleId(event);
                edit(event, SetupView.permissionsDetail(config(ctx, event.getGuild().getId()), principal,
                        principalLabel(principal, event.getGuild())));
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
        return new SelfRolePanelRepository(ctx.database().sqlite());
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
