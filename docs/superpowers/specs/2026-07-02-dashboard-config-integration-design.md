# Integração dashboard ↔ bot — Parte A (lado do bot): config no Neon + snapshots + segurança — design

**Data:** 2026-07-02
**Escopo (esta Parte A):** o trabalho **dentro do repo BaseBot** — migrar a **configuração** que hoje vive no SQLite local do bot para o **Neon (Postgres)**, sincronizar **snapshots** das guilds pro dashboard validar, e o consumo/segurança do lado do bot. Sem criar servidor HTTP no bot.
**Parte B (lado do site):** `davimf.dev/docs/2026-07-02-dashboard-config-integration-site-design.md`.
**Contrato de schema (fonte-verdade compartilhada):** `docs/superpowers/dashboard-config-schema-contract.md`.
**Inventário de config:** `docs/superpowers/dashboard-config-inventory.md`.
**Status:** Design aprovado no brainstorming; revisado 2026-07-02 (modelo 1-bot-por-cliente + snapshots).

> **Estado real do site (2026-07-02):** o `davimf.dev` é uma **Vite/React SPA** com backend via **Netlify Functions** (não Next.js). As functions fazem OAuth, autorização, validação e escrita server-side no Neon. O Neon já está plugado no site (`neon(process.env.DATABASE_URL)`) — mas esse `DATABASE_URL` é o **banco do próprio site**; o Neon de **config do bot** é um projeto **separado**, ligado na Parte B por uma env var nova (`BOT_CONFIG_DATABASE_URL`, role de menos-privilégio). Hoje as functions de config do bot ainda batem no **Supabase** (schema desconectado do bot) → a Parte B repontua pro Neon.

## Objetivo

Deixar o dashboard editar **toda a configuração** do bot com segurança, lendo/escrevendo o **mesmo Neon que o bot lê**. Esta Parte A entrega o lado do bot: migra a config presa no SQLite pro Neon, publica **snapshots** de canais/cargos/permissões pro dashboard validar sem token de bot, e consome a config com cache + reconcile.

## Modelo do produto: 1 bot por cliente

Cada cliente roda **sua própria instância** do bot, com **seu próprio token**, localmente. **Não existe** um `DISCORD_BOT_TOKEN` global que o dashboard possa usar — o dashboard **não conhece nem armazena** tokens dos bots dos clientes. Consequência de design: a validação de canais/cargos/permissões do dashboard é feita contra **snapshots** que cada bot sincroniza no Neon (ver seção *Snapshot Discord*), não chamando a API do Discord com token de bot.

## Decisões (do brainstorming)

- **Sem servidor HTTP no bot.** O site (Netlify Functions, OAuth Discord, acesso ao Neon) escreve config direto no Neon; o bot **lê**.
- **Neon = só config.** Todo gameplay/operacional continua no **SQLite local do bot** → o site, mesmo com acesso ao Neon, **não alcança** dados de jogo (isolamento físico — propriedade de segurança).
- **Escritores:** site, bot e `/setup` são clientes do mesmo Neon. Toda escrita é **merge por chave** (nunca sobrescreve mapa inteiro) e **preserva** o que não gerencia.
- **Autorização owner-first:** v1 só o **dono**; v2 delega via `dashboard_access`. Gateado **no site** (Parte B); o bot não gateia por `dashboard_access`, só o preserva.
- **Efeitos ativos** (publicar/atualizar painel, sync AutoMod nativo) são **eventualmente consistentes** (reconcile ~2–5 min). Config passiva lida sob demanda.
- **`pix_keys` fica no SQLite** (config por-usuário, não de admin).

## Arquitetura-alvo

```
Navegador ──OAuth──▶ Netlify Functions (site) ── authz owner-first + validação via snapshot ──▶ Neon (SÓ CONFIG)   ◀── Parte B
                                                                                                   ▲   ▲
                                                       lê config (cache curto) + escreve snapshots │   │ reconcile p/ efeitos ativos
                                                                                                   │   │
                                                                            Bot (Java) ── SQLite local: gameplay/operacional   ◀── Parte A
```

## 1. Split de dados: Neon vs SQLite

**Neon (config, editável pelo dashboard):** `guild_config` (+ `dashboard_access`), `ticket_categories`, e as **5 tabelas migradas** — `self_roles`(+`self_role_options`), `level_rewards`, `quiz`, `shop_items` (só config), `action_types`. Além disso o bot escreve os **snapshots** (`bot_instances`, `bot_guilds`, `guild_channels_snapshot`, `guild_roles_snapshot`). Schema exato: ver **contrato**.

**SQLite (gameplay/operacional, fica no bot):** carteiras, cooldowns, inventário/equipamento, cadeia/ficha, infrações, XP+voz, sorteios, lembretes, eventos, social, arquivo de mensagens, orçamentos, punições, PDs, finanças de facção, **`pix_keys`**, e:
- **`shop_stock(item_id, sold)`** — o contador `sold` da loja é estado **operacional**; fica no SQLite pra o Neon não receber escrita operacional. A reserva atômica (`reserveStock`) roda no SQLite lendo o **limite** (`stock`/`per_user`) do Neon.
- **Cross-store (sem FK de banco, lookup no código):** `shop_purchases`/`user_levels`/`actions` (SQLite) referenciam `shop_items`/`level_rewards`/`action_types` (Neon) por id.

## 2. Migração das 5 tabelas → Postgres (HÁ dados em produção)

Cada tabela ganha schema Postgres (em `resources/db/postgres/NNN_*.sql`, via `ApplyPostgresSchema`) + um **repo JDBC** (padrão `JdbcGuildConfigRepository`) que substitui o repo SQLite, devolvendo os **mesmos records** (serviços/handlers não mudam assinatura). `DatabaseManager` passa a expor esses repos no `PostgresPool`. Colunas: ver **contrato** (destaques: `self_roles.is_unique` — nunca `unique`; `shop_items`/`self_roles` com `enabled boolean` pra soft-delete).

**Script de migração de dados (idempotente):**
- Copia as linhas do SQLite pro Neon **preservando ids/PKs** — as tabelas operacionais SQLite referenciam esses ids; se mudarem, os vínculos quebram.
- Ajusta as sequences do Postgres pro maior id migrado (`setval`) pra novas linhas não colidirem.
- `shop_stock` (SQLite) recebe o `sold` atual de cada item; o resto de `shop_items` vai pro Neon.
- Rodar com o bot parado (ou em janela sem escrita); **verificar contagem por tabela** antes/depois; rodar duas vezes **não duplica** (upsert por PK).

## 3. Consumo pelo bot + efeitos ativos

- **Cache curto por-guild** (TTL ~30–60s) sobre as leituras de config. O bot **não é o único escritor** → usa **TTL** (eventualmente consistente), não invalidação. Relógio injetável (testável).
- **Leitura defensiva:** id ausente/inválido → degrada (null); linha malformada → pulada; número com fallback. Config inválida gravada pelo site **não derruba** o bot.
- **Reconcile loop** (`ctx.scheduler().repeating`, ~2–5 min) para efeitos ativos:
  - **AutoMod nativo:** re-`AutoModManager.sync(guild, cfg)`.
  - **Painéis publicados** (self-roles, ticket, hierarquia, recrutamento): se `updated_at` > última publicação e há `message_id`, **edita**; se marcado publicar e sem `message_id`, **posta** e grava o `message_id`. Idempotente, à prova de restart.
  - Config passiva não precisa de reconcile.

## 4. Escrita segura do bot (merge + preservação)

**Regra:** toda escrita do bot em `guild_config` — inclusive `/setup` — usa **merge por chave** e **preserva** campos/chaves que o bot não gerencia. O bot **não pode apagar `dashboard_access`** nem chaves criadas pelo dashboard. Nunca sobrescrever `channels`/`roles`/`toggles`/`settings`/`dashboard_access` inteiros.

**Testes (Parte A):**
- `/setup` atualiza canais/cargos **sem apagar** `dashboard_access`.
- update de `channels` não apaga `toggles` (nem vice-versa).
- update de uma chave de `toggles` não apaga outras chaves do mesmo mapa.
- campos/chaves desconhecidos são **preservados** num round-trip read→write.

## 5. Snapshot Discord para validação do dashboard

Como o dashboard **não tem token de bot** (1 bot por cliente), cada bot sincroniza no Neon o mínimo pro dashboard validar canais/cargos/permissões. Tabelas: `bot_instances`, `bot_guilds`, `guild_channels_snapshot`, `guild_roles_snapshot` (DDL no **contrato**).

**Conteúdo:** guilds onde o bot está; canais visíveis (com `bot_can_view`/`bot_can_send`); cargos (com `managed`/`bot_can_assign`); `bot_present`; `last_seen_at`/`updated_at`.

**Quando o bot atualiza:**
- no **boot** (todas as guilds);
- ao **entrar/sair** de guild (`GuildJoin`/`GuildLeave` → `bot_present`);
- **periodicamente** (loop, junto do reconcile);
- quando possível, em **eventos** de criação/edição/remoção de canal e cargo (channel/role create/update/delete) — atualização incremental.

**Como o dashboard usa (Parte B):** valida cada campo de canal/cargo contra o snapshot — canal existe em `guild_channels_snapshot`; tipo compatível (texto ≠ voz); `bot_can_view=true` (e `bot_can_send=true` se o bot for enviar ali); cargo existe em `guild_roles_snapshot`; `bot_can_assign=true` quando aplicável. Snapshot velho → aviso; alteração crítica com snapshot velho demais → bloqueia.

**Implementação no bot:** um `GuildSnapshotSync` (serviço) que mapeia `Guild.getChannels()`/`getRoles()` + `PermissionUtil`/`getSelfMember().hasPermission(channel, VIEW_CHANNEL/MESSAGE_SEND)` e `canInteract(role)` → upsert no Neon; escrita em lote por guild.

**Provisionamento da instância.** O bot recebe no `.env`:
```env
BOT_INSTANCE_ID=<uuid>          # identidade estável da instância (por cliente)
BOT_CLIENT_NAME=<nome opcional>
BOT_CONFIG_DATABASE_URL=<url do Neon de config, sslmode=require>
```
No **boot**, o bot faz **upsert** em `bot_instances` por esse `BOT_INSTANCE_ID` (PK), preenchendo `bot_user_id` e `application_id` via JDA. Usar o id do `.env` (em vez de gerar) evita criar instância nova a cada deploy/restart. Todo `bot_guilds`/snapshot escrito carrega esse `bot_instance_id`.

**Isolamento multi-tenant (Neon central).** Como todos os bots escrevem no mesmo Neon, uma credencial comprometida não pode tocar linhas de outro cliente:
- o bot usa **sempre** o `BOT_INSTANCE_ID` do `.env` — nunca um id vindo de payload;
- todo `UPDATE`/upsert de config/snapshot filtra por `bot_instance_id` no `WHERE` (ex.: `... WHERE guild_id=$1 AND bot_instance_id=$2`), então id que não é dono da linha **não afeta nada**;
- v1 = validação no `WHERE`; endurecer depois com **RLS** (role/credential por instância) ou **stored procedures `SECURITY DEFINER`** com *instance secret*. Ver contrato, *Segurança multi-tenant*.

**Frescor (limiares padrão, ajustáveis):** snapshot fresco se `updated_at`/`last_seen_at` < **10 min**. O dashboard (Parte B) usa: aviso se > 10 min; **bloqueio** de write crítico de canal/cargo se > **15 min**; toggles/settings sem dependência de canal/cargo gravam normalmente. O sync periódico do bot deve rodar em intervalo **< 10 min** pra manter os snapshots frescos.

## 6. Segurança — propriedades garantidas pelo bot

A **autorização/validação/auth** é responsabilidade do **site (Parte B)**. Aqui, o que o bot sustenta:
- **Isolamento físico:** Neon só-config; gameplay é SQLite no host do bot → vazamento do acesso do site **não alcança** dado de jogo.
- **Isolamento multi-tenant:** no Neon central, o bot só escreve linhas do seu `bot_instance_id` (guard no `WHERE`; endurecível com RLS/`SECURITY DEFINER`). Instância comprometida não altera outro cliente.
- **Snapshots só-leitura pro dashboard:** o bot **escreve** os snapshots; o dashboard só **lê**. Nenhum token de bot no site.
- **Segredos:** creds do Neon do bot e `BOT_INSTANCE_ID` só no `.env` do bot; `sslmode=require`.
- **Leitura defensiva** (2ª linha de validação).

## 7. Decomposição

**Esta Parte A → 1 plano (repo BaseBot):** DDL Postgres das 5 tabelas + snapshots; repos JDBC (trocando SQLite no `DatabaseManager`); `shop_stock` no SQLite; migração de dados preservando ids; cache TTL; reconcile loop; `GuildSnapshotSync` + provisionamento `bot_instances` (`BOT_INSTANCE_ID`/`BOT_CLIENT_NAME` no `.env`) + guard multi-tenant no `WHERE`; `/setup`+escritas do bot com merge seguro (preserva `dashboard_access`); `dashboard_access` no modelo `GuildConfig` (só-leitura). **Sem mudar comportamento visível** — troca backend do dado + snapshots + reconcile.

**Parte B (repo `davimf.dev`) — spec separado:** repontuar functions Supabase→Neon, `requireGuildAccess(event, guildId)`, cookie httpOnly + CSRF/Origin, owner-first + `bot_guilds.bot_present`, validação via snapshot, CRUDs, frontend, migração Supabase→Neon.

## 8. Testes

- Repos JDBC contra Postgres (mesmos casos dos SQLite atuais).
- Merge/preservação do `guild_config` (§4).
- Migração: ids preservados; `setval` nas sequences; `sold`→`shop_stock`; rodar 2× não duplica; contagem bate.
- `GuildSnapshotSync`: mapeamento de permissões (parte pura) testável; efeitos JDA via build/smoke.
- **Multi-tenant:** upsert de snapshot/config de uma guild de **outro** `bot_instance_id` não altera linhas (o `WHERE` filtra); `bot_instances` upsert por `BOT_INSTANCE_ID` do `.env` não cria duplicata em restart.
- Cache TTL (relógio injetável); reconcile (detecção "mudou desde a última publicação").

## 9. Fora de escopo / riscos

- **Implementação do dashboard** (functions, OAuth, telas, authz, CSRF) — **Parte B**.
- **Migração Supabase→Neon** dos dados do dashboard antigo — **Parte B**.
- `pix_keys` e gameplay ficam no SQLite.
- Latência do Neon no caminho quente → mitigada pelo cache; medir depois.
- Eventual-consistência dos efeitos ativos (poucos min) — aceito.
- Escrita concorrente (site e `/setup`) → last-write-wins **por chave** (merge, não sobrescrita); `updated_at`/`updated_by` auditam.
- **Frescor do snapshot:** entre syncs, canal/cargo criado/removido pode não estar refletido → o dashboard trata com avisos/bloqueio (Parte B); reduzir latência com sync por-evento.
