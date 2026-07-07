# Contrato de schema — config do dashboard ↔ bot (fonte da verdade)

**Data:** 2026-07-02 · **Status:** contrato compartilhado entre a Parte A (bot, repo `BaseBot`) e a Parte B (site, repo `davimf.dev`).

Este arquivo é a **única fonte da verdade** para o schema de configuração no Neon, as chaves JSONB permitidas, os enums/limites, a semântica de merge/delete e o *ownership* de cada campo. Bot e dashboard devem concordar com o que está aqui. Canônico vive no `BaseBot`; a Parte B referencia por este caminho.

## Invariantes (não-negociáveis)

1. **Neon** é a única fonte da verdade para **config administrativa**.
2. **SQLite** (local do bot) é a única fonte da verdade para **gameplay/estado operacional**. Não está no Neon; o dashboard não o alcança.
3. **Todo ID do Discord é `text`/string** — nunca número JS (snowflakes ultrapassam a precisão segura de `Number`).
4. **Nenhuma escrita JSONB substitui o mapa inteiro** — sempre merge/patch/delete por chave.
5. Toda escrita do **dashboard** registra `updated_by` e `updated_at`.
6. Toda rota de config exige **dono** (v1) ou `dashboard_access` (v2).
7. O **bot nunca confia cegamente** na config (leitura defensiva).
8. Toda escrita do **bot** (inclusive `/setup`) **preserva** chaves/campos que ele não gerencia (não apaga `dashboard_access` nem chaves do dashboard).
9. `pix_keys` **não** entra no dashboard.
10. **Tokens dos bots dos clientes não ficam no dashboard.** Modelo é **1 bot por cliente**; o dashboard valida canais/cargos via **snapshots** no Neon, não chamando a API do Discord com token de bot.
11. **Isolamento multi-tenant no Neon central:** um bot **só** escreve config/snapshot de guilds vinculadas ao seu próprio `bot_instance_id`. Uma instância comprometida **não** pode alterar linhas de outra (ver *Segurança multi-tenant*).

## Segurança multi-tenant no Neon central

No modelo Neon central (v1), todos os bots escrevem no mesmo banco → uma credencial de bot comprometida poderia tentar escrever config/snapshot de **outro** cliente. Proteção obrigatória:

Toda escrita de snapshot/config feita pelo bot deve garantir:
- o bot só atualiza guilds vinculadas ao **seu** `bot_instance_id`;
- `bot_instance_id` **não** é aceito cegamente do payload;
- o vínculo `guild_id → bot_instance_id` é validado **no banco**;
- uma instância comprometida não consegue alterar linhas de outra.

Implementações (escolher no plano):
- **Simples (v1):** cada bot tem `BOT_INSTANCE_ID` no `.env`; o código sempre usa esse id; o banco valida `guild_id + bot_instance_id` no `WHERE` de todo `UPDATE`/upsert (ex.: `... WHERE guild_id=$1 AND bot_instance_id=$2`), então um id que não é o dono da linha não afeta nada.
- **Mais segura:** **RLS** no Postgres; uma role/credential por `bot_instance`; policies limitando ao próprio `bot_instance_id`.
- **Intermediária:** o dashboard tem role ampla (só tabelas de config); os bots escrevem snapshots via **stored procedures `SECURITY DEFINER`** que validam um *instance secret*.

## Provisionamento da instância (`bot_instances`)

Cada bot recebe no `.env`:
```env
BOT_INSTANCE_ID=<uuid>          # identidade estável da instância
BOT_CLIENT_NAME=<nome opcional>
BOT_CONFIG_DATABASE_URL=<url do Neon de config, sslmode=require>
```
No **boot**, o bot faz **upsert** em `bot_instances` por esse `BOT_INSTANCE_ID` (PK), preenchendo `bot_user_id` e `application_id` obtidos via JDA. Usar o `BOT_INSTANCE_ID` do `.env` evita criar uma instância nova a cada deploy/restart. `bot_guilds.bot_instance_id` referencia essa linha.

## Frescor de snapshot (limiares padrão)

`updated_at`/`last_seen_at` define o frescor. Padrão inicial (ajustável no plano):
- **Fresco:** < **10 min**.
- **Selects no frontend:** mostrar **aviso** se > 10 min.
- **Writes críticos de canal/cargo:** **bloquear** se > **15 min**.
- **Toggles/settings** que não dependem de canal/cargo: continuam gravando normalmente.

## Ownership de cada campo

| Fonte | Escreve | Lê |
|---|---|---|
| `guild_config.*` (mapas + colunas typed) | bot (`/setup`) **e** dashboard (merge por chave) | bot |
| `guild_config.dashboard_access` | **só dashboard** | dashboard (bot só preserva; não gateia) |
| `ticket_categories`, `self_roles`(+options), `level_rewards`, `quiz`, `shop_items`, `action_types` | bot **e** dashboard | bot |
| `shop_stock.sold` (SQLite) | **só bot** (operacional) | bot |
| `bot_instances`, `bot_guilds`, `guild_channels_snapshot`, `guild_roles_snapshot` | **só bot** (sincroniza) | dashboard (validação/UI) |
| `shop_purchases`, `user_levels`, `actions`, carteiras, etc. (SQLite) | **só bot** | bot |

## Tabelas no Neon

### `guild_config` — 1 linha/guild
`guild_id text PK`, `log_channel_id text`, `ticket_log_channel_id text`, `channels jsonb`, `roles jsonb`, `toggles jsonb`, `staff_role_ids jsonb`, `settings jsonb`, `dashboard_access jsonb` (`{"users":[...],"roles":[...]}`), `updated_by text`, `updated_at timestamptz`.

Config longa vive nos mapas JSONB → feature nova = chave nova no mapa (sem migração de schema).

### `ticket_categories` — N/guild
`id`, `guild_id text`, `name`, `emoji`, `description`, `discord_category_id text`, `staff_role_ids jsonb`, `position int`, `created_at`.

### 5 tabelas de config migradas do SQLite (a Parte A cria no Neon)
- `self_roles(id, guild_id text, title, description, style, is_unique boolean not null default false, channel_id text, message_id text, enabled boolean not null default true, created_at)` — **`is_unique`, nunca `unique`** (palavra reservada).
- `self_role_options(panel_id, role_id text, label, emoji, position int)`.
- `level_rewards(guild_id text, level int, role_id text)`.
- `quiz(id, guild_id text, question, correct, wrong1, wrong2, wrong3, created_at)`.
- `shop_items(id, guild_id text, type, role_id text, name, description, price, duration_s, stock, per_user, enabled boolean not null default true, created_at)` — **sem `sold`** (fica no SQLite). Remoção pelo dashboard = `enabled=false` (soft-delete), não `DELETE` — preserva `shop_purchases` histórico.
- `action_types(...)` — mesmas colunas do SQLite atual.

### Snapshots Discord (só o bot escreve; o dashboard valida com eles)
```sql
bot_instances (
  id uuid primary key,
  client_name text,
  bot_user_id text not null,
  application_id text,
  active boolean not null default true,
  created_at timestamptz not null default now()
);
bot_guilds (
  guild_id text primary key,
  bot_instance_id uuid not null references bot_instances(id),
  guild_name text,
  owner_id text,
  bot_present boolean not null default true,
  last_seen_at timestamptz not null default now()
);
guild_channels_snapshot (
  guild_id text not null,
  channel_id text not null,
  name text,
  type text not null,
  parent_id text,
  position int,
  bot_can_view boolean not null default false,
  bot_can_send boolean not null default false,
  updated_at timestamptz not null default now(),
  primary key (guild_id, channel_id)
);
guild_roles_snapshot (
  guild_id text not null,
  role_id text not null,
  name text,
  position int,
  managed boolean not null default false,
  bot_can_assign boolean not null default false,
  updated_at timestamptz not null default now(),
  primary key (guild_id, role_id)
);
```

## IDs como `text`
`guild_id`, `channel_id`, `role_id`, `message_id`, `user_id`, `bot_user_id`, `application_id` — todos `text`. No frontend/functions, tratar como string (nunca `Number`/`parseInt` de snowflake).

## Semântica de merge/delete JSONB
Mapas afetados: `channels`, `roles`, `toggles`, `settings`, `dashboard_access`. Nunca sobrescrever o mapa inteiro.
```sql
-- setar/atualizar 1 chave
channels = channels || jsonb_build_object($key, $value)
-- patch de várias chaves
channels = channels || $patch::jsonb
-- remover chave
channels = channels - $key
```
`null` **não** é remoção (a não ser que o contrato diga que `null` é valor válido daquela chave). Validar **chaves permitidas** antes de gravar — o dashboard não grava chave arbitrária.

## Chaves JSONB permitidas (allowlist)
Fonte detalhada: `dashboard-config-inventory.md` §2. Resumo:
- **`channels`**: 24 tipos de log (`log-comandos`…`log-sets`) + canais de feature (`event-channel`, `level-notify`, `welcome:channel`, `welcome:farewell-channel`, `acoes-escalacoes`, `acoes-alinhamentos`).
- **`roles`**: `moderador`, `staff`, `mutado`, `nao-verificado`, `vendedor`, `welcome:autorole` + hierarquia FiveM (`lider`…`sem-set`).
- **`toggles`**: `mod:dm-on-action`, `mod:require-reason`; `sec:automod*`, `sec:verify`, `sec:antiraid`, `sec:antinuke`; `welcome:enabled`, `welcome:dm`, `welcome:farewell-enabled`; `level:enabled`; `eco:enabled`; `event:enabled`.
- **`settings`**: `mod:warn-ttl-days`, `mod:escalation`; `sec:*` (limites/janelas/keywords/whitelists); `welcome:message`/`image`/`farewell-message`; `level:notify` (enum `atual|canal|dm|off`), `level:ignored-channels`; `eco:currency-name`/`emoji`/`daily`/`work-min`/`work-max`/`work-cooldown`; `event:min-interval`/`max-interval`; facs (`farm-items`, `farm-payout-cents`, `hierarchy-channel-id`, `hierarchy-message-id`, `perm:acoes|financeiro|farm|recrutamento|punicoes`).
- **`dashboard_access`**: só `users` (array de user id) e `roles` (array de role id).

## Enums / limites (validação do dashboard)
- `level:notify` ∈ `{atual, canal, dm, off}`.
- `mod:escalation`: formato `N=acao[,N=acao...]`, ação ∈ `{timeout:<dur>, kick, ban}`.
- `self_role_options` / `shop_items` por guild/painel: ≤ 25 (limite de componentes do Discord).
- Números (`*-cents`, janelas em s, limites) ≥ 0 e em faixa sensata.
- Canais/cargos referenciados devem existir no snapshot (ver Parte A §Snapshot).
