# Inventário de configuração no Neon (para o dashboard) — 2026-07-02

Levantamento de **tudo que é configuração e vive no Postgres/Neon** (a "source of truth" que o dashboard edita). O bot lê/escreve via `JdbcGuildConfigRepository`; o modelo é `database/model/GuildConfig`.

> ⚠️ Nota do schema (`001_guild_config.sql`): o dashboard **hoje persiste em Supabase** (`guilds`); quando migrar pro Neon, reconciliar as colunas dos dois lados pra lerem/escreverem as mesmas linhas.

---

## 1. Tabelas no Neon (só 2)

### `guild_config` — 1 linha por servidor
| coluna | tipo | o que é |
|---|---|---|
| `guild_id` | TEXT PK | id do servidor |
| `log_channel_id` | TEXT | canal de log "comum" (legado) |
| `ticket_log_channel_id` | TEXT | canal de log de tickets |
| `channels` | JSONB | **mapa** nome-lógico → channel id (todos os logs + canais de feature) |
| `roles` | JSONB | **mapa** nome-lógico → role id (cargos lógicos) |
| `toggles` | JSONB | **mapa** flag → bool (liga/desliga de features) |
| `staff_role_ids` | JSONB array | cargos de staff dos tickets (nível guild) |
| `settings` | JSONB | **mapa** chave → texto livre (valores, mensagens, CSVs) |
| `updated_at` | TIMESTAMPTZ | |

### `ticket_categories` — N linhas por servidor
`id`, `guild_id`, `name`, `emoji`, `description`, `discord_category_id`, `staff_role_ids` (JSONB), `position`, `created_at`.

Toda a "config longa" vive nos 4 mapas JSONB → **adicionar uma feature nova não precisa de migração** (só uma chave nova no mapa).

---

## 2. Chaves dentro dos mapas JSONB (por módulo)

### 2.1 Logs — `channels` (24 tipos, `SetupLogTypes`)
`log-comandos`, `log-mensagens`, `log-entradas`, `log-saidas`, `log-membros`, `log-voz`, `log-canais`, `log-cargos`, `log-servidor`, `log-bans`, `log-kicks`, `log-moderacao`, `log-formularios`, `log-loja` *(Base)*; `log-tickets` *(Tickets)*; `log-orcamentos`, `log-vendas` *(Vendas)*; `log-farm`, `log-punicoes`, `log-hierarquia`, `log-financeiro`, `log-acoes`, `log-pds`, `log-sets` *(Facs)*.

### 2.2 Cargos lógicos — `roles` (`SetupRoleKeys`)
Base: `moderador`, `staff`, `mutado`, `nao-verificado`, `vendedor`.
Hierarquia FiveM (facs): `lider`, `sub-lider`, `gerente-geral`, `gerente-vendas`, `gerente-elite`, `gerente-elite-feminina`, `gerente-recrutamento`, `gerente-farm`, `recrutador`, `elite`, `elite-feminina`, `membro`, `sem-set`.
Extra: `welcome:autorole` (auto-cargo de boas-vindas).

### 2.3 Moderação — `settings`/`toggles` (`mod:*`)
`mod:warn-ttl-days`, `mod:escalation` (ex.: `3=timeout:1h,5=kick,7=ban`), `mod:dm-on-action` *(toggle)*, `mod:require-reason` *(toggle)*.

### 2.4 Segurança — `settings`/`toggles` (`sec:*`)
AutoMod: `sec:automod` *(t)*, `sec:automod-warn` *(t)*, `sec:automod-warn-per`, `sec:automod-window-s`, `sec:automod-mention-limit`, `sec:automod-block-invites` *(t)*, `sec:automod-keywords`, `sec:exempt-roles`, `sec:exempt-channels`.
Verificação: `sec:verify` *(t)*.
Anti-raid: `sec:antiraid` *(t)*, `sec:antiraid-joins`, `sec:antiraid-window-s`, `sec:antiraid-min-age-days`, `sec:antiraid-lock-level`, `sec:antiraid-prev-level` *(estado de runtime, não editável)*.
Anti-nuke: `sec:antinuke` *(t)*, `sec:antinuke-max`, `sec:antinuke-window-s`, `sec:antinuke-whitelist`.

### 2.5 Boas-vindas / despedida — `settings`/`toggles`/`channels`/`roles` (`welcome:*`)
`welcome:enabled` *(t)*, `welcome:channel`, `welcome:dm` *(t)*, `welcome:message`, `welcome:image`, `welcome:autorole` *(role)*, `welcome:farewell-enabled` *(t)*, `welcome:farewell-channel`, `welcome:farewell-message`.

### 2.6 Leveling — `settings`/`channels` (`level:*`)
`level:enabled` *(t)*, `level:notify` (modo: atual/canal/dm/off), `level:ignored-channels`, `level-notify` *(channel)*.
> Cargos por nível ficam em **SQLite** (`level_rewards`), **não** no Neon.

### 2.7 Economia — `settings`/`toggles` (`eco:*`)
`eco:enabled` *(t)*, `eco:currency-name`, `eco:currency-emoji`, `eco:daily`, `eco:work-min`, `eco:work-max`, `eco:work-cooldown`.
> Loja, equipamentos, carteiras, etc. ficam em **SQLite**.

### 2.8 Eventos de chat — `settings`/`channels` (`event:*`)
`event:enabled` *(t)*, `event-channel` *(channel)*, `event:min-interval`, `event:max-interval`.

### 2.9 Tickets — tabela `ticket_categories` + `staff_role_ids` + `ticket_log_channel_id`
Descrição/emoji por categoria ficam na própria linha da categoria.

### 2.10 Facs (FiveM) — `settings`/`channels`
Canais de ação: `acoes-escalacoes`, `acoes-alinhamentos` *(channels)*.
Hierarquia: `hierarchy-channel-id`, `hierarchy-message-id` *(settings — painel publicado)*.
Farm: `farm-items` (CSV de itens), `farm-payout-cents`.
Permissões de gerência (`ManagerPermissions`, 5 capacidades): `perm:acoes`, `perm:financeiro`, `perm:farm`, `perm:recrutamento`, `perm:punicoes` — cada uma = CSV de principals (chave de cargo lógico ou `role:<id>`).
Recrutamento usa o cargo `membro` + canal `log-sets`.

---

## 3. ⚠️ Config que NÃO está no Neon (está em SQLite) — relevante pro dashboard

Estes são "configuração" (o admin define), mas hoje vivem em **SQLite** local do bot, não no Neon. Pro dashboard editar, teriam que **migrar pro Neon** ou o bot expor uma API:
- **Painéis de auto-cargos** (`self_roles`, `self_role_options`) — migração 025.
- **Cargos por nível** (`level_rewards`) — 027.
- **Perguntas de quiz personalizado** (`quiz`) — 032.
- **Itens da Loja** (`shop_items`) — 034 (cargos perm/temp + custom).
- **Tipos de ação salvos (facs)** (`action_types`) — 019.
- **Chaves Pix por vendedor** (`pix_keys`) — 026.
- Catálogo/receitas de farm (`catalog`, `recipes`) — 005/010 (parte é hardcoded, parte SQLite).

> **Dado operacional / gameplay (NÃO é config, fica em SQLite e não vai pro dashboard de config):** carteiras, cooldowns, inventário/equipamento, estado de cadeia/ficha, infrações/casos, XP e sessões de voz, sorteios, lembretes, eventos, social, arquivo de mensagens + cofre de anexos, orçamentos, punições, PDs, finanças da facção.

---

## 4. Implicações pro dashboard (para o brainstorming)

- **A maior parte da config já é "chave→valor" em JSONB** → um dashboard genérico consegue editar quase tudo por schema (grupos: Logs, Cargos, Moderação, Segurança, Boas-vindas, Nível, Economia, Eventos, Tickets, Facs).
- **3 decisões grandes** a resolver no design: (a) **Supabase → Neon** (unificar a fonte); (b) como o dashboard escreve → o bot **lê direto do Neon** (fácil) mas mutações mais ricas (Loja, self-roles, quiz, ticket categories) podem precisar de **validação/efeitos** que hoje moram no bot; (c) **quem valida** os dados (ex.: role id existe? canal existe? escalação bem-formada?) — hoje é o `/setup`.
- Itens da §3 (SQLite) são o **gap** principal: ou migram pro Neon, ou o bot expõe endpoints.
