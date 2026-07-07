# BaseBot — Progress & Session Handoff

**Last updated:** 2026-07-02
**Branch:** `master` — **everything below is UNCOMMITTED in the working tree** (standing rule: commit only when explicitly asked).
**Build/tests:** `./gradlew build` → **BUILD SUCCESSFUL, 314 tests passing.** JDK 22, JDA 6.4.2 (Components V2).
**Roadmap:** fases 1–9 essencialmente completas. A última peça foi a **Loja da Economia (fase 6 — Plano 2)** — ver §22. Pendências agora são só **smoke manual** (precisa de servidor de teste + intents privilegiados) e uma **revisão final fresh-eyes** da Loja.

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

## 13. ⭐ Fase 4 — Boas-vindas / Despedida / Autorole + Self-roles — COMPLETA
- Spec: `docs/superpowers/specs/2026-06-30-base-welcome-selfroles-design.md`; plano: `docs/superpowers/plans/2026-06-30-base-welcome-selfroles.md`.
- **Boas-vindas (`modules/base/welcome/`):** `WelcomeConfig` (leitor puro, prefixo `welcome:`), `WelcomeText` (placeholders `{user}/{mention}/{server}/{count}`, puro), `WelcomeListener` (join → mensagem no canal e/ou DM + autorole; leave → despedida opcional). Imagem/banner persistida no **vault** via novo `AttachmentVault.store(...)`. Autorole faz fallback para a chave-string `sem-set` quando facs está em uso (base ≠ facs). DM fechada engolida.
- **Self-roles (`modules/base/selfroles/`):** migração `025_self_roles.sql` (2 tabelas) + `SelfRolePanel` (record + `Option`) + `SelfRolePanelRepository` + `SelfRoleView` (botões OU menu) + `SelfRoleComponentHandler` (namespace `selfrole`, toggle/select, modo **exclusivo** remove os outros). Máx 25 opções.
- **`/setup`:** duas novas seções — **Boas-vindas** (tela + modal de mensagens + selects canal/autorole/despedida + toggles) e **Auto-cargos** (lista, editor com entity-select de cargos, alternar estilo/exclusivo, **Publicar**). Ambas no `moduleNav`. Untracked extra fora do plano: `QuickRoleSetup` (setup rápido de cargos, análogo ao `QuickLogSetup`), já referenciado no `SetupComponentHandler`.
- **⚠️ Onde a sessão anterior foi cortada:** faltava só a **Task 10** — registrar `WelcomeListener` (listener) + `SelfRoleComponentHandler` (component) no `BaseModule.register(...)`. **Feito agora** (após o bloco de segurança). Sem isso o listener nunca disparava e os cliques de self-role não roteavam.
- **Tests novos:** `WelcomeConfigTest`, `WelcomeTextTest`, `SelfRolePanelRepositoryTest`. `./gradlew build` ✅ (154 testes).
- **Smoke manual pendente** (servidor de teste, `GUILD_MEMBERS` ligado): entrar/sair com um alt (mensagem + autorole + despedida); criar/publicar painel de auto-cargos e alternar cargos como membro comum.

---

## 14. ⭐ Reforma do Pix — painel único, normalização de chaves e correção do BR Code inválido
- Spec: `docs/superpowers/specs/2026-07-01-pix-panel-normalization-design.md`; plano: `docs/superpowers/plans/2026-07-01-pix-panel-normalization.md`.
- **Bug do "Pix inválido" corrigido:** `PixPayload.emv()` declarava o comprimento TLV em **caracteres** mas o app pagador/CRC contam **bytes** — acentos (`â` em `Goiânia`, `é` em `José`) = 1 char / 2 bytes → parse desalinhava. Fix: **dobra ASCII** (`Normalizer` NFD + remove `\p{M}` + descarta não-ASCII) no nome e cidade dentro de `PixPayload.ascii(...)`. ⚠️ **Verificação final pendente:** só um pagamento real num app de banco confirma 100% — o fix ataca o defeito de encoding confirmado.
- **Cidade removida** do registro; campo EMV 60 fixo `BRASIL` (default do builder). `PixKey` (model) perdeu `merchantCity` e ganhou `long id`.
- **`PixKeyNormalizer`** (novo, puro + `PixKeyNormalizerTest`): CPF/CNPJ com validação de dígito verificador, PHONE→E.164 `+55…`, EMAIL (trim+lower), RANDOM/UUID. Retorna `Result(ok, value, error)`.
- **`/pix` virou comando único** com **painel efêmero** (`PixPanelView` + `PixComponentHandler` reescrito, namespace `pix`): **Enviar** (select das chaves → modal de valor opcional → cobrança **pública** no canal) e **Gerenciar** (cadastrar via select-de-tipo→modal / editar valor+nome / remover). **Múltiplas chaves por vendedor** agora.
- **Modelo de dados:** migração `026_pix_keys_multi.sql` (recria `pix_keys` com `id` autoincrement, copia linhas, dropa `merchant_city`; registrada em `SqliteMigrator.MIGRATIONS`). `PixKeyRepository` reescrito: `insert/list/find/update/delete/findDefault` (some `upsert/findByUser`).
- **Orçamentos:** `BudgetService` usa `findDefault` (primeira chave do vendedor). Escolher chave por orçamento = follow-up.
- Gate do cargo **vendedor** mantido. Tests: `PixKeyNormalizerTest`, `PixPayloadTest` (dobra ASCII), `PixKeyRepositoryTest` (multi-chave reescrito). `./gradlew build` ✅ 168 testes.
- **Cliente opcional:** a tela "Enviar cobrança" tem um `EntitySelectMenu` de usuário (`pix:sendclient`, 0–1) além do select de chave. O cliente escolhido é encodado no id do select de chave (`sendkey:<clientId>`) → viaja pelo modal (`sendform:<keyId>:<clientId>`) → aparece na cobrança, na confirmação e no log de venda, e vai no id do botão confirmar (`confirmar:<owner>:<cents>:<clientId>`). Orçamentos passam `b.clientId()` automaticamente.
- **Confirmar pagamento:** ao clicar (restrito ao vendedor/dono), a mensagem de cobrança é **editada** (`setReplace(true)` remove chave, botão e QR) para um painel "✅ Pagamento Confirmado" (confirmado pelo vendedor `<@id>` + valor). O valor viaja no id do botão (`pix:confirmar:<ownerId>:<centavos>`). Gera **log de venda** em `ChannelLog.post(..., "log-vendas", ...)` (log type `log-vendas` já existia). BR Code validado por eye no teste real (nome ASCII "Davi Monteiro Fonseca", cidade BRASIL).
- **Smoke manual pendente (usuário):** `/pix` → cadastrar (Telefone `62986089609`→`+5562986089609`; CPF formatado→só dígitos; inválido→erro) → Enviar → **pagar num app de banco real** → **Confirmar** → mensagem vira "Pagamento Confirmado" + entra em `#log-vendas` (configurar o canal em /setup → Logs).

---

## 15. PD externo (facs) — registrar PD de quem já saiu do Discord
- Spec: `docs/superpowers/specs/2026-07-01-pd-externo-design.md`; plano: `docs/superpowers/plans/2026-07-01-pd-externo.md`.
- `/pd` agora tem `motivo` (obrigatório, **primeiro** por causa da regra do Discord) + opcionais `usuario`, `id_jogo`, `nome_rp`. Resolvedor puro `PdCommand.mode(hasUsuario, idJogo, nomeRp)` → `MEMBER | EXTERNAL | INVALID` (testado em `PdModeTest`).
- **MEMBER:** fluxo atual (kick + hierarquia + logs). **EXTERNAL** (`id_jogo`+`nome_rp`, sem `usuario`): sem kick, registra em `action_logs` (targetId = id_jogo) + `log-pds` + `log-punicoes` marcado "(fora do Discord)". **INVALID:** erro orientando os campos. Entrada de log via `pdEntry(...)` compartilhado. Só `PdCommand.java` mudou. `./gradlew build` ✅ 171 testes.

## 16. ⭐ Leveling (fase 5) — COMPLETO (Plano 1 Núcleo + Plano 2 Voz)
- Spec: `docs/superpowers/specs/2026-07-01-base-leveling-design.md`; planos: `.../plans/2026-07-01-base-leveling-core.md` (Plano 1 ✅) + **Plano 2 (voz) pendente**.
- Pacote `modules/base/leveling/`: `LevelFormula` (curva MEE6 `5n²+50n+100`, puro), `LevelingConfig` (prefixo `level:`), `UserLevelRepository`+`LevelRewardRepository` (migração `027`), `LevelRewards` (coleta de cargos cruzados, puro), `RankData`, `LevelingService` (award → level-up → **cargos numa única `modifyMemberRoles`** → notificação roteada current/channel/dm/off), `MessageXpListener` (15–25 XP/60s, ignora bots/canais ignorados), `RankView`/`TopView`, `LevelingComponentHandler` (paginação /top).
- Comandos: `/rank [usuario]`, `/top` (**efêmeros** — não poluem o canal; paginação do /top via editComponents na msg efêmera), `/xp add|remove|set|reset` (admin `MANAGE_SERVER`). `/setup → Nível`: toggle, modo de notificação, canal fixo, canais ignorados, editor de cargos-por-nível.
- **Cascata de level-up** (ex.: `/xp add 100000`) coleta cargos de todos os níveis num set → **uma** chamada de API + **uma** notificação (evita rate-limit). Notif de voz no modo "atual" cai na DM (Plano 2).
- Tests: `LevelFormulaTest`, `LevelingConfigTest`, `UserLevelRepositoryTest`, `LevelRewardRepositoryTest`, `LevelRewardsTest`. `./gradlew build` ✅ **185 testes**.
- **Plano 2 (voz) ✅ COMPLETO** (`.../plans/2026-07-01-base-leveling-voice.md`): migração `028_voice_sessions` (índice **parcial** `WHERE leave_time IS NULL`) + `VoiceSessionRepository`; `VoiceEligibility` (puro, conta humanos filtrando `isBot()`); `VoiceXpBatch` (**uma transação SQL por ciclo** — avança `xp_credited_until` + soma XP; devolve old/new p/ level-up após commit); `VoiceSessionListener` (`GuildVoiceUpdateEvent` unificado join/leave/move); `VoiceReconciler` (boot, delay 5s, sem creditar offline); `VoiceXpTicker` (60s, ~10 XP/min elegível, `getChannelById(AudioChannel.class,...)` p/ suportar Stage); `LevelingService.applyVoiceLevelUp` (efeitos fora da transação, notif de voz cai na DM no modo "atual"). Registrado no `BaseModule` (listener em register + ticker/reconciler em onReady). Tests: `VoiceSessionRepositoryTest`, `VoiceEligibilityTest`, `VoiceXpBatchTest`. `./gradlew build` ✅ **192 testes**.
- **Smoke pendente (usuário):** (a) msg → `/rank`/`/top`, cargo por nível + `/xp add` cascata, trocar modo de notificação; (b) voz: 2+ humanos ganham ~10 XP/min, sozinho/ensurdecido/AFK/bot-música não contam, trocar de canal não duplica, **restart com gente em call** reabre as sessões sem creditar offline, level-up em call no modo "atual" chega na DM.

## 17. ⭐ Economia por-usuário (fase 6) — Plano 1 (Núcleo) COMPLETO
- Spec: `docs/superpowers/specs/2026-07-01-base-economy-design.md`; planos: `.../plans/2026-07-01-base-economy-core.md` (Plano 1 ✅) + **Plano 2 (Loja) pendente**. Pacote `modules/base/economy/`, **separado** do tesouro de facção (`fac_finance`).
- **Puro/testável:** `EconomyConfig` (prefixo `eco:`), `EconomyFormat` (emoji + agrupamento BR), `EconomyDefaults`, `CrimeOutcome`/`RobOutcome` (resolvers com roll injetável).
- **Dados (migração 029):** `user_wallets(cash,bank)` + `eco_cooldowns`. `WalletRepository` com **deduções atômicas via `WHERE … >= ?`** (anti double-spend) e `transfer` transacional (debita pagador → credita recebedor com upsert; cria linha do novo). `CooldownRepository`.
- **`EconomyService`:** `/daily` (500/24h), `/trabalhar` (50–250/1h), `/crime` (50%, ganha 100–500 / multa 50–250), `/roubar` (40%, 10–30% da carteira do alvo, banco seguro), `/pagar`, `/depositar`, `/sacar`. Cooldown com timestamp nativo `<t:…:R>`. Bot/self bloqueados no back-end.
- **Comandos:** `/saldo` + `/rico` (**efêmeros**, /rico paginado), `/daily /trabalhar /crime /roubar /pagar /depositar /sacar` (mensagem temporária `Replies.reply`), `/eco add|remove|set|reset` (admin `MANAGE_SERVER`, destino carteira/banco). `/setup → Economia` (toggle, moeda nome+emoji, valores daily/trabalhar) — no hub e no moduleNav.
- **Ranking `/rico`** por total (carteira+banco). Config v1: moeda + daily + trabalhar; crime/roubo fixos em `EconomyDefaults`.
- Tests: `EconomyConfigTest`, `EconomyFormatTest`, `CrimeOutcomeTest`, `RobOutcomeTest`, `WalletRepositoryTest` (atomicidade + recebedor novo), `CooldownRepositoryTest`. `./gradlew build` ✅ **209 testes**. Infra confirmada: `SqliteManager` usa **HikariCP** (conexões isoladas) → transações seguras.
- **Plano 2 (Loja) — pendente:** `shop_items` (cargos permanentes + temporários) + `/loja` (ver/comprar) + sweep de expiração de cargos temporários (ralo contra inflação) + gestão no setup (migração 030).
- **Smoke pendente (usuário):** ligar em `/setup → Economia`; `/daily`/`/trabalhar` (cooldown), `/crime`, `/saldo`, `/depositar tudo` → `/roubar` (só carteira), `/pagar` (recebedor novo), `/rico`, `/eco add`.

## 18. ⭐ Eventos aleatórios de chat + integração nível→dinheiro — COMPLETO
- Spec: `docs/superpowers/specs/2026-07-01-base-chat-events-design.md`; plano: `.../plans/2026-07-01-base-chat-events.md`. Pacote `modules/base/events/` (usa leveling + economy).
- **`LevelBonus.scale(base, nível)`** (puro, em leveling): +2%/nível, **teto no nível 50** (máx +100%). Aplicado nas **moedas dos eventos** e no **`/daily`** (`EconomyService.daily` agora lê o nível via `UserLevelRepository`).
- **4 eventos** sorteados: `QUIZ` (botões), `TYPING` (digitar palavra), `MATH` (gerado), `GRAB` (botão "Pegar!"). Bancos estáticos (`QuizBank`, `WordBank`), `MathEvent` gerador, `ChatEventAnswer` (checagem trim/caixa) — puros/testados.
- **`ChatEventService`** (in-memory, 1 evento ativo por guild): `tick()` (scheduler 60s: dispara se passou o intervalo aleatório **e** houve atividade recente no canal — evita canal morto), `onGuildMessage` (marca atividade + resolve TYPING/MATH — **apaga a msg vencedora** pra manter o chat limpo), `resolveButton` (QUIZ/GRAB). **Race-safe:** um único vencedor via `active.remove(guildId, ev)` (remoção condicional atômica). Expira em ~60s (edita p/ "expirado").
- **Recompensa:** `LevelBonus.scale(100, nível)` moedas (se `eco:enabled`) + 50 XP (se `level:enabled`, pode dar level-up). `ChatEventListener` + `ChatEventComponentHandler` (namespace `chatevt`).
- **Config `event:`** + `/setup → Eventos` (canal principal, toggle, intervalo min/max) — no hub e no moduleNav. Sem canal → inativo.
- Tests: `LevelBonusTest`, `ChatEventConfigTest`, `MathEventTest`, `ChatEventAnswerTest`. `./gradlew build` ✅ **219 testes**.
- **Smoke pendente (usuário):** `/setup → Eventos` (ligar, canal, intervalo curto), mandar msg pra marcar atividade → evento dispara; testar quiz/coleta (botões) e digitação/matemática (chat, msg vencedora some); conferir `/saldo` e `/rank`; `/daily` com nível alto rende mais.

## 19. ⭐ Sorteios (giveaways) com requisitos avançados — COMPLETO
- Spec: `docs/superpowers/specs/2026-07-01-base-giveaways-design.md`; plano: `.../plans/2026-07-01-base-giveaways.md`. Pacote `modules/base/giveaway/` (usa `voice_sessions` + economy).
- **`voice_sessions` ganhou** `totalVoiceMs` + `sessionsOf` (reuso do sistema de voz do leveling — sem banco de métricas duplicado).
- **Puros/testados:** `VoiceWindow.overlapsDailyWindow` (fuso fixo **America/Sao_Paulo**), `GiveawayWindow.parse`, `GiveawayDraw.pick`, `GiveawayRequirements.firstUnmet`.
- **Dados (migração 030):** `giveaways` + `giveaway_entries`. `GiveawayRepository` com `create` **à prova de colisão** de id (8 chars UUID, retenta).
- **Requisitos por sorteio (opcionais, "E"):** cargo, dias no servidor, horas em call (total), call em janela de horário. Validados **na entrada**; no sorteio descarta só quem saiu.
- **`GiveawayService`:** criar (painel + botão Participar), enter (valida + inscreve + atualiza contagem), sweep (30s, encerra vencidos, à prova de restart), endNow, **reroll** (re-credita moedas com **anúncio explícito** "🔁 Novo ganhador sorteado! Prêmio entregue.").
- **`/sorteio criar|encerrar|resortear`** (admin `MANAGE_SERVER`); `GiveawayComponentHandler` (namespace `gwy`). Prêmio = texto + **moedas automáticas** ao(s) N ganhador(es).
- Tests: `VoiceSessionTotalsTest`, `VoiceWindowTest`, `GiveawayWindowParseTest`, `GiveawayDrawTest`, `GiveawayRepositoryTest`, `GiveawayRequirementsTest`. `./gradlew build` ✅ **235 testes**.
- **Smoke pendente (usuário):** `/sorteio criar premio:… duracao:2m` → painel/Participar; testar cada requisito (recusa com motivo); esperar o sweep sortear + creditar; `/sorteio resortear`/`encerrar`.

## 20. ⭐ Fun / Social (fase 7) — COMPLETO (Plano 1 básico + Plano 2 jogos)
- Spec: `docs/superpowers/specs/2026-07-01-base-fun-design.md`; planos: `.../plans/2026-07-01-base-fun-basic.md` (Plano 1 ✅) + **Plano 2 (forca + quiz personalizado) pendente**. Pacote `modules/base/fun/`. **Fun = sem XP/moedas.**
- **Simples:** `/dado [lados]`, `/coinflip`, `/ship @a @b` (`ShipCalc.percent` estável por par, puro/testado).
- **Social (migração 031):** `/rep [usuario]` + `/biscoito [usuario]` — 1/dia (cooldown 24h) com **gate atômico** (`SocialRepository`: INSERT OR IGNORE → UPDATE condicional, anti-spam) + ranking efêmero. `SocialGive` compartilhado + `SocialView`.
- **Jokenpo:** `/jokenpo @alvo` (in-memory, namespace `jkp`) — voto `synchronized` no `Match` + revela **uma vez só** via `matches.remove(id, match)`. `JokenpoResult.decide` puro/testado.
- **GIF (nekos.best, sem chave):** `/toca_aqui` (`pat`) + `/abracar` (`hug`) — `GifClient` async (`HttpClient.sendAsync` + Jackson), `extractUrl` puro/testado; `deferReply` → `editOriginalComponents` com `MediaGallery`; falha de rede → fallback texto.
- Saídas lúdicas públicas; rankings efêmeros. Tests: `ShipCalcTest`, `JokenpoResultTest`, `GifClientTest`, `SocialRepositoryTest`. `./gradlew build` ✅ **245 testes**.
- **Plano 2 ✅ COMPLETO** (`.../plans/2026-07-01-base-fun-games.md`): **`/forca`** — `HangmanState` puro (normaliza acento/caixa na letra E na palavra inteira via `Normalizer` NFD+`\p{M}`), `HangmanBank` embutido, `ForcaService` (in-memory por canal, chute `synchronized` no wrapper Game), `ForcaListener` (chat). **`/quiz` personalizado** — migração `032` + `QuizRepository`, `QuizPlayService` (embaralha alternativas no play, fallback pro `events.QuizBank`, revela uma vez via `remove(chId,a)`), gestão em `/setup → Fun` (add via modal de 5 campos, listar, remover). Tests: `HangmanStateTest`, `QuizRepositoryTest`. `./gradlew build` ✅ **253 testes**.
- **Smoke pendente (usuário):** básico (`/dado`/`/coinflip`/`/ship`, `/rep`/`/biscoito`, `/jokenpo`, `/toca_aqui`/`/abracar`) + `/forca` (acentos) + `/setup → Fun` add pergunta → `/quiz` (alternativas embaralhadas).

## 21. ⭐ Utilidades (fase 8) — Plano 1 (info + afk + enquete) COMPLETO
- Spec: `docs/superpowers/specs/2026-07-01-base-utilities-design.md`; planos: `.../plans/2026-07-01-base-utilities.md` (Plano 1 ✅) + **Plano 2 (lembrete) pendente**. Pacote `modules/base/utility/`.
- **Info:** `/avatar` (imagem + botão-link **dentro** do container), `/banner` (async `retrieveProfile`, fallback "sem banner"), `/userinfo`, `/serverinfo` (`InfoView`, timestamps nativos).
- **AFK (in-memory):** `AfkRegistry` (por guild:user) + `/afk [motivo]` + `AfkListener` (remove ao falar + avisa ao ser mencionado; mensagens auto-deletam).
- **Enquete (in-memory):** `PollTally` (puro), `/enquete pergunta opcoes:"a | b | c"` (2–6), voto por botão 1/pessoa (troca permitida) via **`editComponents` só** (sem efêmero — barra sobe na mensagem pública), botão **Encerrar** do criador. Namespace `enq`.
- Tests: `AfkRegistryTest`, `PollTallyTest`. `./gradlew build` ✅ **258 testes**.
- **Plano 2 ✅ COMPLETO** (`.../plans/2026-07-01-base-utilities-reminders.md`; spec `.../specs/2026-07-01-base-reminders-design.md`): `/lembrete criar|listar|cancelar` — persistente, DM à prova de restart. Migração `033_reminders.sql` (id TEXT 8-char, registrada em `SqliteMigrator.MIGRATIONS`) + `Reminder`/`ReminderRepository` (create/due/delete/listByUser/cancel-com-guarda-de-dono) + `ReminderService.sweep()` (30s: `due(now)` → **claim por delete** antes de entregar, evita disparo duplo → DM via `openPrivateChannelById`, fallback no `channel_id` mencionando o usuário quando a DM está fechada). `LembreteCommand` (subcomandos; tempo via `Durations.parse`, msg capada em 500; **teto de 25 lembretes ativos/usuário**; **`cancelar` com autocomplete** dos próprios lembretes ativos via `AutocompleteCommand` — rótulo = tempo restante + mensagem, valor = id). Registrado no `BaseModule` (comando em register + sweep no onReady). Tests: `ReminderRepositoryTest`. `./gradlew build` ✅ **260 testes**.
- **Smoke pendente (usuário):** `/avatar`/`/banner`/`/userinfo`/`/serverinfo`; `/afk` (menção + voltar); `/enquete` (votar/trocar/encerrar); **`/lembrete criar tempo:1m mensagem:"x"`** → chega na DM em ~1min (ou no canal se DM bloqueada); `/lembrete listar`/`cancelar id:<id>` (cancelar id de outro recusa); **restart com lembrete futuro** ainda dispara na hora.

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
1. **Moderation + Infractions** ✅ done (§7)
2. **Logging completion** ✅ done (§8, §10, §11). ⚠️ Needs `GUILD_MEMBERS` + `MESSAGE_CONTENT` no Dev Portal + smoke.
3. **AutoMod + Security** ✅ done (§12). Smoke pendente (precisa `MESSAGE_CONTENT` + `GUILD_MEMBERS`).
4. **Welcome / self-roles** ✅ done (§13). Precisa `GUILD_MEMBERS`; smoke pendente.
5. **Leveling** ✅ done (§16, núcleo + voz).
6. **Economy (per-user)** ✅ done — Plano 1 núcleo (§17) + **Plano 2 Loja (§22)**.
7. **Sorteios / Eventos** ✅ done (§18 eventos de chat, §19 giveaways).
8. **Fun / Social** ✅ done (§20, básico + jogos).
9. **Utilidades** ✅ done (§21, info/afk/enquete + lembretes).

**Roadmap fases 1–9 completas.** Não há próximo subsistema pendente no roadmap numerado. Trabalho restante:
- **Smoke manual** de cada fase num servidor de teste (listados por seção acima; a maioria precisa de `GUILD_MEMBERS`/`MESSAGE_CONTENT` ligados no Dev Portal).
- **Revisão final fresh-eyes da Loja** (o code-review de §22 rodou inline por causa do limite de sessão; rodar `/code-review` ou subagente quando quiser um olhar independente).
- **Backlog opcional** (não no roadmap numerado): geradores de imagem/memes (`/carta_reverso`, `/procurado`) — ver `FUTURE-IDEAS.md`.
- **Decisão pendente:** nada está commitado; quando for commitar, ver o agrupamento sugerido em Gotchas.

Cada subsistema = seu próprio spec → plan → implementação em `docs/superpowers/{specs,plans}/`.

---

## Gotchas / reminders
- **Migrations:** append every new `NNN_*.sql` to `SqliteMigrator.MIGRATIONS` or it silently never applies.
- **Privileged intents** are OFF in `BotApplication` — enable before relying on automod/welcome/member-logs/message-XP.
- **Components V2:** custom emojis only render in container/embed **text**, not button labels (use `.withEmoji`). Keep the house style (see `embed-design-system` memory).
- **Base ≠ facs:** don't add facs deps to base; base gates on Discord perms, facs management gates on `ManagerPermissions`.
- **Nothing is committed.** When ready, the natural grouping is: (a) V2 migration + design reform, (b) Hikari/deprecation fixes, (c) manager-permissions, (d) feminina roles, (e) moderation+infractions, (f) complete logging system (§8: 8 listeners + message archive + ModReason/AuditLookup + intents + QuickLogSetup).

## 22. ⭐ Loja da Economia (fase 6 — Plano 2) — COMPLETO
- Spec: `docs/superpowers/specs/2026-07-02-base-shop-design.md`; plano: `.../plans/2026-07-02-base-shop.md`. Pacote `modules/base/economy/` (separado de facs). **Executado via subagent-driven; o subagente da Task 1 bateu no limite de sessão, então as 7 tasks foram feitas inline** (código verbatim do plano já revisado).
- **Migração `034_shop.sql`** (registrada em `SqliteMigrator.MIGRATIONS`): `shop_items` (tipo `ROLE_PERM|ROLE_TEMP|CUSTOM`, cargo, nome/descrição, preço, `duration_s`, `stock`, `per_user`, contador `sold`) + `shop_purchases` (rastreio de expiração/limite/histórico) + índice parcial `WHERE expires_at IS NOT NULL`.
- **Puros/testados:** `ShopItem` (record+enum, `soldOut/remaining/limited`), `ShopPurchaseRules` (`perUserReached/newExpiry/extendedExpiry/parseLimits` — campo "estoque/usuário"). Repos: `ShopItemRepository` (insert/list/count/find/delete + `reserveStock`/`releaseStock` atômicos via UPDATE condicional), `ShopPurchaseRepository` (insert/`countActiveByUserItem`/`activeTemp`/`extend`/`due`/`claim` claim-by-delete).
- **`ShopService`** — saga *reservar→debitar (`tryDebitCash`, só carteira)→entregar→compensar*: renovação de temp ativo **estende** (sem estoque/per_user), `ROLE_PERM` já possuído bloqueia, `canInteract` checado antes de debitar, falha assíncrona de grant faz **estorno** (release+addCash+claim+log). `CUSTOM` → posta em `log-loja` p/ entrega manual. `sweep()` (boot+5min) remove cargos temporários vencidos (claim-by-delete, à prova de restart).
- **`/loja`** vitrine efêmera + `StringSelect` → confirmação → Comprar (resultado edita a msg efêmera, privado). `ShopComponentHandler` (namespace `shop`, dedicado). Registrados + sweep agendado em `BaseModule`.
- **`/setup → Economia → Loja`:** listar, adicionar (StringSelect de tipo → CUSTOM abre modal direto; cargo → EntitySelect → botão "Preencher detalhes" → modal, respeitando "modal só tem text inputs"), remover (select). Teto de **25 itens/guild**. Modal com campo único "Limites" (`estoque/usuário`) p/ caber em ≤5 inputs.
- **Novo log type `log-loja`** ("Loja", Base) em `SetupLogTypes` (Base 13→14; `SetupLogTypesTest` atualizado).
- Tests novos: `ShopPurchaseRulesTest` (6), `ShopItemRepositoryTest` (5), `ShopPurchaseRepositoryTest` (4). `./gradlew build` ✅ **275 testes** (era 258).
- **Revisão final fresh-eyes ainda pendente** (limite de sessão) — rodar `/code-review` ou subagente opus quando resetar. **Smoke manual pendente:** ligar economia, criar item perm/temp(estoque 2/1)/custom em `/setup → Economia → Loja`, apontar `log-loja`, `/daily`→`/loja` comprar cada tipo (perm recompra bloqueia; temp recompra estende; custom cai no log), esperar sweep expirar o temp, reiniciar com temp ativo (ainda expira), saldo insuficiente recusa sem debitar.

_Pós-revisão:_ aplicadas as 2 correções do code-review (aviso de log-loja em item custom; bloqueio de criar item de cargo sem hierarquia) + Loja virou seção de primeira classe no /setup (hub inicial + navegação interna moduleNav + roteamento "loja"). Build ✅ 275 testes.

## 23. ⭐ Empregos, Equipamentos & Crime (expansão da economia) — COMPLETO (4 planos)
- Spec: `docs/superpowers/specs/2026-07-02-base-jobs-tools-crime-design.md`; planos `.../plans/2026-07-02-base-jobs-tools-crime-{1-foundation,2-jobs,3-armed-crime,4-org-crime}.md`. Pacote `modules/base/economy/`. Executado via **subagent-driven** (1 subagente/task, no-commit). O usuário revisou os planos em várias rodadas (pegou o bug grave de atomicidade do org, o paradoxo do fuzil no roubo, ordem de mutação, unidade de cooldown, etc. — todos corrigidos nos planos antes de executar).
- **Plano 1 — Fundação:** `EquipmentCatalog` (catálogo fixo: picareta/utensílio/moto/arma com tiers, preços, usos, stats), migração **035** (`user_inventory` com slot+created_at+unique-parcial de equipado; `user_crime_state`), `InventoryRepository` (durabilidade com **`UseResult`/`DestroyResult` explícitos**, equip um-por-slot transacional, slot validado contra o catálogo), `CrimeStateRepository` + `JailService` (cadeia lazy: cumprir pena marca ficha, fiança não; `/fianca` 15k, `/limparficha` 30k), `/mercado` + `/inventario` (por `rowId`), guard de cadeia no `/trabalhar`.
- **Plano 2 — Empregos:** `JobOutcome` (puro) + `JobService`; `/minerar` `/cozinhar` `/entregar` (ferramenta equipada, cooldown próprio, entrega auto-debita combustível). Emojis custom PICKAXE/COOK/MOTO ainda não existem → placeholders GEM/GEAR/COMPASS com fallback unicode ⛏️/🍳/🏍️.
- **Plano 3 — Crime armado:** `CrimeOutcome`/`RobOutcome` reescritos (puros: bônus de arma + penalidade de ficha −15% + multiplicador + cap/floor do roubo); `CrimeEconomyService`; `/crime` e `/roubar` exigem arma, **consomem/destroem a arma ANTES de pagar/transferir**, toda falha destrói a arma; roubo com cap por tier + carteira protegida em 500 + cooldown por par 12h; multa `min(rolled, cash)`. Removidos os `EconomyService.crime/rob` antigos.
- **Plano 4 — Crime organizado:** `OrgCrime` (puro: chance combinada + split por peso, com guards) + `OrgCrimeService` (lobby in-memory, 1/guild); `/crimeorganizado` (painel público Entrar/Iniciar, 5–10 armados, 1x/dia). **Resolução por-participante segura** (age só sobre quem teve a arma efetivamente mutada — sem cancelamento falso após consumir); líder valida igual ao join (erro explícito); `start` checa eco; `participants()` imutável; expiração de 5 min no callback do reply; `WeaponSnapshot` (item_key validado). Falha: cadeia 6–12h + ficha (se cumprir a pena).
- **Novos comandos:** `/mercado /inventario /minerar /cozinhar /entregar /crimeorganizado /fianca /limparficha` (+ `/crime` `/roubar` modificados). **Testes novos:** `EquipmentCatalogTest`, `InventoryRepositoryTest`, `JailServiceTest`, `JobOutcomeTest`, `CrimeOutcomeTest`/`RobOutcomeTest` (reescritos), `OrgCrimeTest`. `./gradlew build` ✅ **302 testes**.
- **Follow-ups documentados (não-bloqueantes v1):** transação única cross-repo no org (rollback), resultado em `record` em vez de String, cozinha permanente (sentinela `usos_left<0` já suportada), escalar fiança/cadeia por tier, emojis custom de equipamento, **balance pass** (picareta de ouro subiu p/ 25 usos; revisar curvas).
- **⚠️ Smoke manual pendente (usuário, precisa `GUILD_MEMBERS` + economia ligada + bot com cargo acima dos vendidos... não — equipamentos não dão cargo; só precisa economia ligada):** `/setup → Economia` ligar; `/mercado` comprar picareta/moto/arma; `/inventario` equipar; `/minerar`/`/cozinhar`/`/entregar` (combustível debita, ferramenta quebra ao zerar); `/crime` e `/roubar` (sucesso paga×mult, falha perde a arma; roubo respeita cap/floor/par-cooldown); montar `/crimeorganizado` com 5+ armados e resolver (sucesso divide pote por peso; falha prende 6–12h → `/fianca` sai sem marcar, cumprir pena suja a ficha → `/limparficha`); abrir lobby em cooldown recusa; lobby abandonado expira em 5 min.

## 24. Painel `/economia` — o que fazer + cooldowns
- Spec: `docs/superpowers/specs/2026-07-02-base-economy-panel-design.md`; plano: `.../plans/2026-07-02-base-economy-panel.md`. Comando efêmero que lista saldo + status de cadeia/ficha + as 8 ações (daily/trabalhar/minerar/cozinhar/entregar/crime/roubar/crimeorganizado) com estado (pronta/cooldown/bloqueada-por-equipamento/preso) + o equipamento equipado e usos.
- `ActionStatus` (resolver puro, precedência preso→bloqueado→cooldown→pronto; `readyAt` só em COOLDOWN) + `EconomyCooldownKeys` (chaves centralizadas, verificadas iguais às dos serviços) + `EconomiaPanelView` + `EconomiaCommand` (lê equipamento 1×/slot num EnumMap, `now`/`preso` uma vez). Emojis via `Emojis`. Ações bloqueadas mostram a dica de desbloqueio; seção "Ações úteis" com `/fianca`/`/limparficha` quando relevante.
- Tests: `ActionStatusTest` (5). Executado inline (subagente bateu no limite de sessão). **Peguei um bug de teste do próprio plano** (`readyWhenNeverUsed` usava `now` irreal → COOLDOWN; corrigido pra `now` epoch).

## 25. Geradores de imagem / memes (`/procurado`, `/carta_reverso`)
- Spec: `docs/superpowers/specs/2026-07-02-base-image-memes-design.md`; plano: `.../plans/2026-07-02-base-image-memes.md`. Pacote `modules/base/fun/`. Primeiro item do backlog FUTURE-IDEAS (fase 7/8) entregue.
- **Templates PNG só-arte** (fornecidos pelo usuário via IA, sem texto/marca) em `resources/memes/procurado.png` + `carta_reverso.png`. O bot desenha todo o texto (fonte nativa) e compõe o avatar. `/carta_reverso` é genérica (sem Uno/"+4").
- `MemeTemplate` (coords **fracionárias** 0–1: caixa do avatar + `TextAnchor` com `maxWidth`), `MemeRender` (puro: **copia** o template, avatar cover-crop no RECT / máscara `Ellipse2D` no CIRCLE, texto com **fit** que reduz a fonte/corta com `…`; hints de antialias/bicubic), `MemeService` (async: `deferReply(false)` → `scheduler().executor()` → baixa avatar via `ImageMedia` → compõe → `MediaGallery` V2 + menção → `erase()` no finally; cache de template imutável; cap 8 MB; avatar animado→1º frame). Comandos `/procurado`/`/carta_reverso` (opção `usuario`, default autor). Java puro (BufferedImage/Graphics2D/ImageIO), **sem dependência nova**.
- Tests: `MemeRenderTest` (5: PNG válido/dims, círculo, texto gigante, template não-mutado, textos ausentes). `./gradlew build` ✅ **312 testes**.
- **Rework "furo transparente" (v2, muito melhor):** os templates foram processados com **ImageMagick** (flood-fill do centro → placeholder vira **alpha transparente**: poster = abertura da moldura; carta = **toda a elipse branca**). `MemeRender` reescrito: **avatar por baixo (cover-crop) → template por cima (o furo revela e mascara no formato exato) → texto**. Removido o clip circular/Shape; `AvatarBox` agora só cobre o furo (poster `0.66×0.37`, carta `0.90×0.92`). Calibrado por render local (poster preenche a moldura; carta preenche o espaço branco com as setas por cima) — ambos ótimos.
- **Fix do trava:** `MemeService` agora tem **handler de falha + log** no `editOriginalComponents(...).queue()` (antes, sem callback de erro, qualquer falha do REST deixava o `deferReply` "pensando" pra sempre; agora vira erro visível + log). Mantido `MediaGalleryItem.fromFile` (o anexo funciona). (Teste-descartável de calibração já removido.)

## 26. /forca com tema/dica
- Spec: docs/superpowers/specs/2026-07-02-base-forca-temas-design.md. `HangmanBank` virou temático (record Entry(word,theme); ~28 palavras em 5 temas: Animais/Natureza/Comida/Objetos/Lugares); `ForcaService.Game` carrega o tema; `ForcaView.panel(accent, state, theme)` mostra `💡 Tema · X` desde o início. `HangmanState` intacto. Test: `HangmanBankTest`. Build ✅ 314 testes.
