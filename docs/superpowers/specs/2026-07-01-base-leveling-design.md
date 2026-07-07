# Leveling (Base, fase 5) — XP por mensagem e voz, níveis, cargos e ranking

**Data:** 2026-07-01
**Módulo:** Base (`modules/base/leveling/`) — standalone, **não depende de facs**.
**Status:** Design aprovado (revisão do spec pendente)

## Objetivo

Sistema de níveis estilo MEE6: membros ganham XP por atividade (mensagens e tempo em call), sobem de nível por uma curva, ganham **cargos por nível**, e podem ver o próprio `/rank` e o `/top` do servidor. Configurável em `/setup → Nível`.

## Decisões (do brainstorming)

- **Fontes de XP:** mensagens **e** voz, ambas nesta fase.
- **Voz:** construir o sistema completo de **`voice_sessions` persistidas + reconciliação no boot** agora (fonte de verdade do tempo em call, reusável nos sorteios); o **XP de voz sai do tempo acumulado** em call.
- **Cargos por nível:** sim.
- **Rank:** painel **Components V2** agora, mas os dados vêm de um `RankData` para um renderer de imagem futuro consumir o mesmo contrato.
- **Notificação de level-up:** configurável (canal fixo / canal atual / DM / desligado), default = canal atual.
- **Taxas padrão (fixas no v1):** curva `5n²+50n+100`; mensagem 15–25 XP com cooldown de 60s; voz ~10 XP/min elegível.

## Escopo / decomposição

Um spec, **dois planos**:
- **Plano 1 — Núcleo:** fórmula, config, `user_levels`, `level_rewards`, XP por mensagem, level-up + cargos + notificação, `/rank` `/top` `/xp`, `/setup → Nível`. Entrega leveling completo por mensagem.
- **Plano 2 — Voz:** `voice_sessions` + reconciliação no boot + ticker de crédito de XP por tempo em call. Depende do Plano 1.

## Curva e fórmula (puro — `LevelFormula`)

- XP para subir **do** nível `n` para `n+1`: `5·n² + 50·n + 100`.
- `levelForXp(totalXp)` → nível atual; `xpForLevel(n)` → custo do nível `n`; `totalXpForLevel(n)` → XP acumulado necessário para atingir o nível `n`; `progress(totalXp)` → record `(int level, long into, long needed)` (XP dentro do nível atual e custo do próximo). Puro/testável, sem I/O.

## Dados (SQLite — migração `027_leveling.sql`, anexar a `SqliteMigrator.MIGRATIONS`)

```sql
CREATE TABLE IF NOT EXISTS user_levels (
    guild_id        TEXT NOT NULL,
    user_id         TEXT NOT NULL,
    xp              INTEGER NOT NULL DEFAULT 0,
    last_message_ts INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (guild_id, user_id)
);
CREATE TABLE IF NOT EXISTS level_rewards (
    guild_id TEXT NOT NULL,
    level    INTEGER NOT NULL,
    role_id  TEXT NOT NULL,
    PRIMARY KEY (guild_id, level)
);
CREATE TABLE IF NOT EXISTS voice_sessions (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    guild_id          TEXT NOT NULL,
    user_id           TEXT NOT NULL,
    channel_id        TEXT NOT NULL,
    join_time         INTEGER NOT NULL,
    leave_time        INTEGER,
    xp_credited_until INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_voice_sessions_open ON voice_sessions (guild_id, user_id) WHERE leave_time IS NULL;
```

> **Índice parcial** (`WHERE leave_time IS NULL`): a reconciliação e o ticker só consultam sessões **abertas**; o índice parcial cobre exatamente essas buscas e não incha em disco conforme o histórico de sessões fechadas cresce. SQLite suporta índice parcial nativamente.

- Nível **não** é armazenado — derivado de `xp` via `LevelFormula`.
- Repos: `UserLevelRepository` (`get/addXp(→retorna novo total)/setXp/topPage/rank(position)/reset`), `LevelRewardRepository` (`list/put(level,role)/remove(level)/rolesForLevel(level)`), `VoiceSessionRepository` (`open/closeOpen/openSessions/creditUntil/...`).

## XP por mensagem (`MessageXpListener`)

- Intent **`GUILD_MESSAGES`** (já habilitado — os logs de mensagem usam). Ignora: bots, webhooks, DMs, sistema, e canais em `level:ignored-channels`. Só quando `level:enabled`.
- Cooldown **60s por usuário** (compara `now - last_message_ts`); ao conceder, grava `last_message_ts = now` e soma **15–25 XP** aleatório via `LevelingService.award(...)`.

## XP por voz (`voice_sessions` + `VoiceXpTicker`)

**Sessões (fonte de verdade do tempo):** `VoiceSessionListener` (intent **`GUILD_VOICE_STATES`**, já habilitado) escuta o evento **unificado** `GuildVoiceUpdateEvent` (JDA 5+, não os antigos join/leave separados) e mapeia:
- **Entrou:** `getChannelJoined() != null && getChannelLeft() == null` → `open` (`join_time = now`, `leave_time = NULL`, `xp_credited_until = now`).
- **Saiu:** `getChannelLeft() != null && getChannelJoined() == null` → `closeOpen` (`leave_time = now`).
- **Mudou de canal:** `getChannelLeft() != null && getChannelJoined() != null` → **fecha** a sessão do `channelLeft` + **abre** uma nova para o `channelJoined`.

**Reconciliação no boot** (`onReady`, com delay de ~5s pro cache de voz popular): fecha sessões órfãs (`leave_time IS NULL`) de quem não está mais em call (`leave_time = now`, sem creditar o período offline); abre sessões para quem está em call agora sem sessão aberta. (Apêndice do roadmap `all-in-one-roadmap`.)

**Crédito de XP (`VoiceXpTicker`, scheduler a cada 60s):** para cada sessão **aberta**, se o membro está **elegível agora**, credita XP proporcional ao delta `now - xp_credited_until` (~10 XP/min) via `LevelingService.award(...)`; **sempre** avança `xp_credited_until = now` (tempo inelegível não gera XP mas não acumula dívida). Ao fechar a sessão, um crédito final do delta elegível.

**Elegibilidade (`VoiceEligibility.isEligible(...)`, puro):** não-bot, **≥2 humanos** no canal, **não ensurdecido** (self/server deaf) e **fora do canal AFK** da guilda.
> **Gotcha — contar humanos, não membros:** um bot de música (ou o próprio bot) entrando numa call sobe `getMembers().size()` para 2, mas humanos continua 1. A contagem **deve filtrar `member.getUser().isBot()`** explicitamente: `channel.getMembers().stream().filter(m -> !m.getUser().isBot()).count() >= 2`. Nunca usar o tamanho bruto da lista de membros (evita falso-positivo e leituras defasadas do cache de voz).

> Ligação XP↔tempo: o `xp_credited_until` na linha da sessão amarra o XP ao tempo em call e o torna à prova de restart (o gap offline não é creditado).

> **⚠️ Plano 2 — transação única por ciclo do ticker (evita `database is locked`):** o SQLite bloqueia o arquivo inteiro em cada escrita. Com 100–200 membros ativos em voz, o ticker de 60s rodaria 100+ `award(...)` em loop, cada um uma gravação separada → I/O de disco em rajada e risco de `SQLITE_BUSY/database is locked`. **Todas as atualizações de XP de um mesmo ciclo do ticker devem rodar dentro de UMA transação** (`BEGIN … COMMIT`): 100 updates custam ~o tempo de uma gravação. O `LevelingService`/repo precisa expor um caminho em lote (ex.: `award` que aceite uma `Connection`/transação compartilhada, ou um `creditBatch(...)` que abra uma transação, aplique todos os deltas e faça os `modifyMemberRoles`/notificações **depois** do commit). A detecção de level-up e as chamadas de API do Discord ficam fora da transação (só o I/O do SQLite é transacionado).

## Serviço central (`LevelingService`)

`award(guildId, member, amount, notifyContext)`:
1. `long novo = users.addXp(guildId, userId, amount)`.
2. `int antes = LevelFormula.levelForXp(novo - amount)`, `int agora = LevelFormula.levelForXp(novo)`.
3. Se `agora > antes`: **acumula** os cargos de recompensa de **todos** os níveis de `antes+1..agora` num único conjunto e faz **uma só** chamada de API (`guild.modifyMemberRoles(member, rolesToAdd, List.of())`), mantendo os cargos já ganhos (**stacking**, nunca remove); dispara **uma** notificação (para o nível final `agora`).
> **Gotcha — level-up em cascata:** `/xp add` pode conceder 10.000 XP de uma vez e cruzar dezenas de níveis. Coletar os `role_id` de todos os níveis num set e chamar `modifyMemberRoles` **uma única vez** — nunca `addRoleToMember` em loop por nível (rate-limit severo). Idem: **uma** notificação para o nível final, não uma por nível.
- **Notificação** conforme `level:notify`: `channel` (canal `level-notify`), `current` (canal da mensagem que causou o level-up), `dm`, `off`. **Level-up vindo da voz** não tem "canal atual" — no modo `current` cai na **DM**; nos modos `channel`/`dm`/`off` segue igual. Texto V2: `🎉 <@user> subiu para o nível N!` (+ cargo ganho, se houver).
> **DM à prova de bloqueio:** o modo `dm` (e o fallback de voz) usa `user.openPrivateChannel().queue(pc -> ..., err -> {})` com callback de erro que **engole** `CANNOT_SEND_TO_USER` (DMs bloqueadas para o servidor) — só log silencioso no terminal, nunca propaga a exceção.

## Comandos + UI

- **`/rank [usuario]`** — `RankView.panel(accent, RankData)` (V2): nível, XP total, barra de progresso textual, XP até o próximo, posição no ranking. `RankData` (record: level, xpTotal, into, needed, rank) calculado por `LevelingService.rank(...)` → contrato reusável pelo renderer de imagem futuro.
- **`/top`** — leaderboard paginado (10/página) por XP desc; botões de paginação no `LevelingComponentHandler`.
- **`/xp`** (admin, `MANAGE_SERVER`) — subcomandos `add/remove/set/reset` (usuário + valor); reajusta cargos/nível conforme o novo XP.
- **`/setup → Nível`** — toggle ligar/desligar; modo de notificação (select) + canal de notificação; canais ignorados (multi-select); editor de cargos-por-nível (adicionar `nível`+`cargo`, listar, remover). Gate `MANAGE_SERVER` (o setup já é gated).

## Config (`guild_config`, prefixo `level:`)

- `level:enabled` (toggle), `level:notify` (setting: `current|channel|dm|off`, default `current`), canal `level-notify`, `level:ignored-channels` (setting CSV de channel ids). Cargos por nível na tabela `level_rewards`.

## Tratamento de erros / bordas

- XP desligado (`!level:enabled`) → listeners e ticker não creditam.
- Cargo de recompensa acima do bot / inexistente → ignora silenciosamente (log best-effort).
- Notificação em canal inexistente / DM fechada → engole.
- `/rank` de quem não tem XP → nível 0, XP 0, sem posição.
- Reconciliação: nunca credita período offline; delay no boot pra cache popular.

## Testes

- `LevelFormulaTest` (curva, levelForXp/xpForLevel/progress em limites).
- `LevelingConfigTest` (defaults + leitura de notify/ignored).
- `UserLevelRepositoryTest`, `LevelRewardRepositoryTest`, `VoiceSessionRepositoryTest` (SQLite in-memory/temp; migração 027).
- `LevelingServiceTest` (award: sem level-up, com level-up, múltiplos níveis de uma vez, atribuição de cargos — JDA mockado via Proxy conforme o padrão do projeto).
- Elegibilidade de voz: extrair `VoiceEligibility.isEligible(...)` puro e testar (≥2 humanos, deaf, AFK).

## Fora de escopo

- Renderer de imagem do rank (só o contrato `RankData` fica pronto).
- Sorteios (usarão `voice_sessions` depois).
- XP por reações/quiz (fase Fun).
- Taxas configuráveis por servidor (fixas no v1).
