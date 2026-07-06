# BaseBot — Progress & Session Handoff

**Last updated:** 2026-06-30
**Branch:** `master` — **everything below is UNCOMMITTED in the working tree** (standing rule: commit only when explicitly asked).
**Build/tests:** `./gradlew test` → **BUILD SUCCESSFUL, 121 tests passing.** JDK 22, JDA 6.4.2 (Components V2).

> ⚠️ **Git note:** `git status` shows ~230 modified files, but most of that is **IDE/linter noise** — an `// [OUTLINE START] … // [OUTLINE END]` header injected at the top of nearly every `.java` file. The *real* changes are the features below + the ~25 untracked new files. Don't be alarmed by the file count.

---

## Big-picture direction (see memory)
- The bot is becoming an **all-in-one** that replaces other bots (moderation, automod/security, fun, leveling, economy, utilities) — except music.
- **Base module is a standalone product**, designed independently of **facs** (FiveM faction module), which may be split out later. Base must NOT depend on facs.
- **Privileged intents to enable** (`MESSAGE_CONTENT` + `GUILD_MEMBERS`) — currently commented out in `BotApplication`. Turn on in dev portal (no verification under ~100 guilds).
- Memory files: `all-in-one-roadmap.md`, `moderation-infractions-built.md`, `embed-design-system.md`, `v2-container-everywhere-rule.md`, `manager-permissions` (in code/spec).

---

## Work completed this session (in order)

### 1. Actions panel (escalações) — lifecycle buttons + custom emojis
- `ActionView.panel`: finished/occurred actions hide Entrar/Sair/Alinhamento (only Configurar + Vitória/Derrota); once a result is declared, only Configurar remains.
- Replaced unicode emojis with the server's custom emoji palette in embed **text**; buttons use `.withEmoji(Emoji.fromFormatted(...))` (custom emojis don't render in button label text).
- Files: `modules/facs/actions/ActionView.java`, `ActionService.java`. New: `util/Emojis.java` (custom-emoji constants, incl. `branco_cadeado_abrindo`, `branco_arma`).

### 2. V2-container-everywhere migration (project-wide)
- **Every bot-sent message is now a Components V2 container** (no plain text). New `util/Replies.java` helper: `ephemeral/reply/hook/hookEphemeral(event, ctx, msg)` (guild-coloured). Channel posts/DMs use `sendMessageComponents(Panels.container(...))`.
- Swept ~53 files. Mentions suppressed except where a ping is intended (alignment, budget notices, ghost-pings → `MentionType.USER`).
- **Exempt:** the `/mensagem` builder's *authored output* stays flexible (embed or V2).

### 3. Embed design-system reform (all panels) + log polish
- House style: `## title` + `Panels.divider()` + sectioned body, `code` for ids/counts/money, `>` blockquotes, `-#` subtext, one-per-line numbered lists, Discord timestamps `<t:…:F> • <t:…:R>`.
- Reformed every panel (Action, Ticket, Setup, Finance, Farm, SetRequest, Recruit, Hierarchy, Punish-history, Budget, Tabela, Pix, Form, ListaCargo) **and** all log entries (`ChannelLog.post` auto-splits a leading `## heading` into title + divider).

### 4. Small fixes
- `ListaCargoView`: deprecated `role.getColorRaw()` → `role.getColors()...` (also fixed a latent white-tint bug for uncoloured roles).
- `PostgresPool` (Neon/Hikari): `minimumIdle(0)` + `idleTimeout(60s)` + `maxLifetime(240s)` to stop the "connection has been closed" warnings (serverless idle disconnects).

### 5. Manager Permissions (facs gerência capabilities)
- New `modules/facs/perms/ManagerPermissions.java`: 5 capabilities (`acoes/financeiro/farm/recrutamento/punicoes`) granted to principals (hierarchy role keys or `role:<id>`), stored as CSV in `guild_config.settings` (`perm:<cap>`). Administrator always bypasses; per-domain defaults.
- Replaced raw Discord-perm gates in all facs management with `ManagerPermissions.can(...)`. New `/setup → Permissões` screen (category-oriented, free-role support). Sets keeps the outrank-the-role guard.
- Spec: `docs/superpowers/specs/2026-06-28-manager-permissions-design.md`; plan: `docs/superpowers/plans/2026-06-28-manager-permissions.md`. Tests: `ManagerPermissionsTest`, `SetupPermissionsViewTest`.
- **Out of scope:** `/hierarquia` still uses `MANAGE_ROLES` (not in the agreed 5 capabilities).

### 6. Two new facs roles
- Added `gerente-elite-feminina` (Gerente de Elite Feminina) and `elite-feminina` (Elite Feminina) to `FacHierarchy.LEVELS`. Same privileges as their elite counterparts: Elite Feminina gets actions queue priority (`ActionService.isPriority`); Gerente de Elite Feminina is a default owner of the `acoes` capability (`Capability` now takes multiple domain owners).

### 7. ⭐ Moderation + Infractions (the big one — first all-in-one subsystem)
- **Standalone base** unified case system in `modules/base/moderation/`. Every mod action records a numbered per-guild **case**.
- Files (new): `Infraction`, `InfractionType`, `InfractionRepository`, `ModerationConfig` (escalation `3=timeout:1h,5=kick,7=ban` + warn TTL + toggles in `guild_config`), `ModerationService` (record → DM → modlog → auto-escalation; sweep decays warns + lifts tempbans), `InfractionView` + `InfractionComponentHandler`. Migration `022_infractions.sql`.
- **New commands:** `/avisar /infrações /caso /revogar /timeout /untimeout /tempban /softban /nota /purge /slowmode /nuke`.
- **Refactored to record cases:** `/ban /kick /mute /mutecall` (+ `/unban /unmute /unmutecall` deactivate). `/timeout` (native) = default mute; `/mute` (role) = secondary.
- **`/setup` nav:** persistent module-picker select (`SetupView.moduleNav`) added to **every** screen (kept existing Voltar/pagination). New `/setup → Moderação` config screen.
- Wired in `BaseModule` (service + sweep on `onReady`). New `log-moderacao` log type. Spec: `docs/superpowers/specs/2026-06-29-base-moderation-infractions-design.md`.
- Tests: `ModerationConfigTest`, `InfractionViewTest`, `InfractionRepositoryTest` (applies migration 022 to a temp DB).
- **Bug fixed (would crash on boot):** new migrations must be appended to `SqliteMigrator.MIGRATIONS` (hardcoded list) — `022` is now registered.

### 8. ⭐ Sistema de Logging Completo (next session's "logging completion")
- Replaced the single `GeneralLoggingListener` with **8 focused listeners** in `modules/base/listeners/`: `Command`, `Membership`, `Ban`, `Message`, `Voice`, `Channel`, `Role`, `Guild` — all post Components V2 via `ChannelLog.post`.
- **Log keys** (`SetupLogTypes`): consolidated `log-msgdel`/`log-msgedit` → `log-mensagens`; added `log-membros`/`log-canais`/`log-cargos`/`log-servidor`. Base now 13 types (was 10).
- **Message archive:** migration `023_message_archive.sql` + `MessageArchiveRepository` (registered in `DatabaseManager` + `SqliteMigrator.MIGRATIONS`). 90-day retention; purge scheduled in `BaseModule.onReady` (boot + every 12h).
- **Utils:** `ModReason` (embeds the human moderator in the Discord audit reason — bot is the actor) and `AuditLookup` (moderator+reason from audit log; nullable target for pins, `onAbsent` overload for voice force-move). `ModReason.of(...)` applied across mod commands (`/ban /kick /mute /timeout /untimeout /tempban /softban /mutecall /unmutecall /lock /unlock /slowmode`, nuke, `/pd`, `/cargo add|remove`, `/unmute`, bot nick).
- **Intents:** enabled `GUILD_MEMBERS`, `MESSAGE_CONTENT`, `GUILD_INVITES`, `SCHEDULED_EVENTS` in `BotApplication`.
- **Quick setup:** `BotContext.activeModules()` (populated in `BotApplication`) + `QuickLogSetup` (idempotent: creates `logs {modulo}` categories + `📂・{log}` channels hidden from @everyone, only for active modules) wired to a new **⚡ Setup rápido** button on `/setup → Logs`.
- Tests added: `MessageArchiveRepositoryTest`, `ModReasonTest`, `QuickLogSetupTest`; `SetupLogTypesTest` updated for 13 Base types. **`./gradlew build` BUILD SUCCESSFUL, all tests passing.**
- **Deviations from the plan (intentional):** `VoiceMoveCommand` got no `.reason()` — `moveVoiceMember` returns `RestAction`, not `AuditableRestAction`. `ModerationService` reasons left as-is — those are *automatic* escalation/sweep actions (actor "system"), no human moderator to embed. `GuildVoiceVideoEvent` uses `isSendingVideo()` (not `isVideo()`). Override target mention via `PermissionOverride.getMember()/getRole()` (`IPermissionHolder` is not `IMentionable`).
- **Known limitations:** unpin has no reliable JDA event (only pin is logged, via system message). User name/global-name changes are logged across mutual guilds.
- **⚠️ Still TODO (user):** enable `GUILD_MEMBERS` + `MESSAGE_CONTENT` in the Dev Portal and run the plan's Task 19 manual smoke test.

---

## 9. Custom application-emoji system + project-wide sweep
- **App emojis (not guild):** `util/EmojiRegistry` syncs `resources/emojis/*.png` (107 PNGs + `manifest.txt`) to the bot's *application* emojis on `onReady` (idempotent, resolves by name). `util/Emojis` exposes name constants + `of(name, unicodeFallback)` (text) and `button(name)` (`withEmoji`). Wired in `BaseModule.onReady`.
- **107 custom emojis** copied into `src/main/resources/emojis/` (verified by eye via contact sheets); `emoji_040` police-car dropped (PD now uses `skull`). 7 legacy concepts without art remapped: VOICE→volume, WEAPON→swords, LOADING→hourglass, DOT→online, COMMAND/COMMENT/SEND→message.
- **Project-wide sweep:** all raw Unicode emojis in bot messages converted to `Emojis.of(...)`/`Emojis.button(...)` across **65 files** (419 text + 31 button usages). Typographic arrows `→ ← ↑ ↓` left as text. Fallback = original glyph, so output is unchanged until the PNGs upload to the app, then auto-switches to custom. House rule saved to memory ([[custom-emojis-in-bot-messages]]).
- **⚠️ User action:** boot once so `EmojiRegistry` uploads the 107 (needs the app; no privileged intent required for this). `./gradlew build` ✅.

---

## 10. Rich join/leave logs + invite tracking + footer on all logs
- **`util/ChannelLog`** reworked: every log entry now ends with a **footer timestamp** (`-# <t:…:f>`). New rich overload `post(ctx, guild, key, md, thumbnailUrl, imageUrl)` renders an avatar **thumbnail** (V2 `Section`, attached to the first block / header) and a full-width **banner image** (V2 `MediaGallery`). `FacsLog` delegates here, so all logs (base + facs) get the footer. **Layout convention:** a line that is exactly `---` in the markdown becomes a real divider line, splitting the body into "field"-like groups (Components V2 has no embed fields). Chosen log style = "separado por linhas" (groups divided by lines), applied across **all event-log listeners** (command, membership join/leave/kick/nick/roles/timeout, ban, message del/edit/bulk/pin, voice, channel, guild). Role logs are single-field (already clean). Facs/moderation/sales module logs not yet grouped (offered).
- **`InviteTracker`** (new listener): caches per-guild invite uses (primed on `GuildReady`/`GuildJoin`, kept fresh on invite create/delete); `resolveUsedInvite()` diffs on join to attribute the invite + its creator. Needs **Manage Server** perm + `GUILD_INVITES` (enabled); degrades to "desconhecido" (vanity URL / missing perm).
- **`MembershipLoggingListener`** join/leave rewritten: mention + username, ID, account-created (abs + relative), **invite used + who created it** (join), **member count** (join & leave), joined-at + roles (leave, if cached), avatar thumbnail, banner image. Wired with `InviteTracker` in `BaseModule`.
- **`CommandLoggingListener`**: now logs the **parameters** passed (`getOptions()`), not just the command name; `action_logs` stores `getCommandString()`.
- `./gradlew build` ✅. **User action:** give the bot **Manage Server** in the guild (for invite attribution); restart so the invite cache primes.

---

## 11. Attachment vault (preserve deleted/edited attachments) + log layout "separado por linhas"
- **Problem:** storing only the CDN URL means deleted-message attachments 404 (Discord also expiring/signing URLs). **Fix:** re-host every received attachment to a central **vault guild**.
- **`AttachmentVault`** (new): on each received message with attachments, re-uploads them (lazy `downloadAsFileUpload`, oversized skipped at the vault guild's limit) to a per-origin-guild channel (named with the origin guild id, auto-created) with the original message id as content. Stores `vault_channel_id` + `vault_message_id` in `message_archive` (migration `024`). On delete/edit, re-fetches the vault message for fresh URLs and shows them as a `MediaGallery`.
- **Config:** `BotConfig.Discord.vaultGuildId` (`VAULT_GUILD_ID` env / `discord.vaultGuildId` yaml). Vault guild = **`1521388944963534869`**. Disabled when unset. **No purge** — vault + archive kept forever (removed the 90d `message_archive` purge).
- **`ChannelLog`** gained a multi-image overload (List<String> → MediaGallery).
- **Log layout:** "separado por linhas" (`---` group dividers) applied across **all event-log listeners AND module logs** (facs: actions/punish/farm/produzir/pd/recruit/sets; moderation modlog + escalation; forms). Ticket closure stays a bespoke panel; sales has no ChannelLog-based log. `MessageArchiveRepository` got `vaultChannelId`/`vaultMessageId` + `setVault(...)`.
- **User action:** set `VAULT_GUILD_ID=1521388944963534869` in `.env`; ensure the bot is in that server with **Manage Channels** + **Send Messages/Attach Files**. `./gradlew build` ✅.

## 12. ⭐ Fase 3 — Segurança (AutoMod + Anti-raid + Verificação + Anti-nuke) — COMPLETA
- Spec umbrella: `docs/superpowers/specs/2026-06-30-base-automod-security-design.md`. Plano por módulo em `docs/superpowers/plans/2026-06-30-base-{automod,antiraid,verification,antinuke}.md`. Tudo em `modules/base/security/`. Config em `guild_config` (prefixo `sec:`), pura/testável em `SecurityConfig`. UI em `/setup → Segurança`.
- **Módulo 1 — AutoMod (nativo):** `AutoModManager` sincroniza regras nativas do Discord (prefixo `BaseBot · `, delete+recreate idempotente por nome) a partir da config (anti-spam/menção/convites/keywords, capadas a 30). `AutoModExecutionListener` → isenção → `ViolationWindow` → `ModerationService.warn(...)` (escalona via Infrações) + modlog. Intents `AUTO_MODERATION_*`.
- **Módulo 2 — Anti-raid:** `AntiRaidListener` (`JoinWindow` + idade da conta) → `AntiRaidService.lockdown` sobe o nível de verificação da guilda (guarda o anterior em `sec:antiraid-prev-level`), posta alerta com botão **Desativar lockdown** (`SecurityComponentHandler` "raidunlock", exige Gerenciar Servidor).
- **Módulo 3 — Verificação:** `VerificationListener` atribui o cargo `nao-verificado` no join (novo slot em `SetupRoleKeys`); `VerificationView.panel` (botão **Verificar**, publicado por `/setup → Segurança → Publicar painel`); `SecurityComponentHandler` "verify" concede `membro` + remove `nao-verificado`.
- **Módulo 4 — Anti-nuke:** `AntiNukeListener` escuta delete de canal/cargo, ban e kick; resolve o ator no audit log com **retry curto** (`NukeAuditLookup`, 3× ~500ms via `scheduler().once`); conta por ator (`ActorWindow`) e ao passar de `sec:antinuke-max`/`window-s` chama `AntiNukeService.neutralize` (remove os cargos abaixo do bot + **pinga o dono** no modlog; alerta de "impotente" quando o ator está acima do bot). Dono + whitelist (`user:`/`role:`) isentos. Precisa de **Ver Registro de Auditoria** + **Gerenciar Cargos** + cargo do bot no topo.
- **Tests novos:** `SecurityConfigTest`, `ViolationWindowTest`, `JoinWindowTest`, `ActorWindowTest`. `./gradlew build` ✅.
- **Sem migração nova** (config vive em `guild_config`). **Smoke manual pendente** num servidor de teste (cada módulo) — depende de `MESSAGE_CONTENT` (AutoMod keywords) e `GUILD_MEMBERS` (verificação/raid).

---

## New files created this session (untracked)
```
util/Emojis.java, util/Replies.java
modules/facs/perms/ManagerPermissions.java (+ test, SetupPermissionsViewTest)
modules/base/moderation/{Infraction,InfractionType,InfractionRepository,ModerationConfig,
                         ModerationService,InfractionView,InfractionComponentHandler}.java
modules/base/commands/{Avisar,Caso,Infracoes,Nota,Nuke,Purge,Revogar,Slowmode,Softban,
                       Tempban,Timeout,Untimeout}Command.java
resources/db/sqlite/022_infractions.sql
test: ModerationConfigTest, InfractionViewTest, InfractionRepositoryTest
docs/superpowers/specs/{2026-06-28-manager-permissions, 2026-06-29-base-moderation-infractions}-design.md
docs/superpowers/plans/2026-06-28-manager-permissions.md
--- logging system (§8) ---
util/{ModReason,AuditLookup}.java
modules/base/listeners/{Command,Membership,Ban,Message,Voice,Channel,Role,Guild}LoggingListener.java
  (deletes modules/base/listeners/GeneralLoggingListener.java)
database/sqlite/MessageArchiveRepository.java
resources/db/sqlite/023_message_archive.sql
modules/base/setup/QuickLogSetup.java
test: MessageArchiveRepositoryTest, ModReasonTest, QuickLogSetupTest
docs/superpowers/specs/2026-06-29-complete-logging-system-design.md
docs/superpowers/plans/2026-06-29-complete-logging-system.md
--- Fase 3 segurança (§12) ---
modules/base/security/{SecurityConfig,AutoModManager,AutoModExecutionListener,ViolationWindow,
                       AntiRaidService,AntiRaidListener,JoinWindow,SecurityComponentHandler,
                       VerificationListener,VerificationView,
                       ActorWindow,NukeAuditLookup,AntiNukeService,AntiNukeListener}.java
test: SecurityConfigTest, ViolationWindowTest, JoinWindowTest, ActorWindowTest
docs/superpowers/specs/2026-06-30-base-automod-security-design.md
docs/superpowers/plans/2026-06-30-base-{automod,antiraid,verification,antinuke}.md
```
(`019/020/021_*.sql`, `ActionTypeRepository.java`, `facs/actions` tests, `ActionView` were already untracked from a prior session.)

---

## Next steps (build order for the all-in-one)
1. **Moderation + Infractions** ✅ done
2. **Logging completion** ✅ done (this session — see §8 below). ⚠️ Needs `GUILD_MEMBERS` + `MESSAGE_CONTENT` enabled in the Discord Dev Portal, then the manual smoke test (plan Task 19).
3. **AutoMod + Security** ✅ done (this session — see §12). All 4 modules built; manual smoke pending (needs `MESSAGE_CONTENT` + `GUILD_MEMBERS`).
4. **Welcome / self-roles** (needs `GUILD_MEMBERS`).
5. **Leveling** → 6. **Economy (general, per-user)** → 7. **Fun** → 8. **Misc utilities**.

Each subsystem = its own spec → plan → implementation. Idea menu lives in this session's history; re-derive from `all-in-one-roadmap.md` if needed.

---

## Gotchas / reminders
- **Migrations:** append every new `NNN_*.sql` to `SqliteMigrator.MIGRATIONS` or it silently never applies.
- **Privileged intents** are OFF in `BotApplication` — enable before relying on automod/welcome/member-logs/message-XP.
- **Components V2:** custom emojis only render in container/embed **text**, not button labels (use `.withEmoji`). Keep the house style (see `embed-design-system` memory).
- **Base ≠ facs:** don't add facs deps to base; base gates on Discord perms, facs management gates on `ManagerPermissions`.
- **Nothing is committed.** When ready, the natural grouping is: (a) V2 migration + design reform, (b) Hikari/deprecation fixes, (c) manager-permissions, (d) feminina roles, (e) moderation+infractions, (f) complete logging system (§8: 8 listeners + message archive + ModReason/AuditLookup + intents + QuickLogSetup).
