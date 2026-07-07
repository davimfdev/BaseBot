# Utilidades (Base, fase 8) — info, lembrete, afk, enquete

**Data:** 2026-07-01
**Módulo:** Base (`modules/base/utility/`).
**Status:** Design aprovado (revisão do spec pendente)

## Objetivo

Comandos utilitários: `/avatar` `/banner` `/userinfo` `/serverinfo` (exibição); `/lembrete` (DM agendada, persistente, com listar/cancelar); `/afk` (aviso de ausência); `/enquete` (votação com botões).

## Decisões (do brainstorming)

- **4 grupos:** info, lembrete, afk, enquete.
- **Lembrete persiste** (tabela + sweep à prova de restart) com subcomandos **criar / listar / cancelar**.
- **AFK e enquete in-memory** (transitórios; somem no restart).
- **Enquete:** opções separadas por `|` (2–6); voto por botão (1/pessoa, troca permitida); botão **Encerrar** só do criador.

## Escopo / decomposição

Um spec, **dois planos**:
- **Plano 1 — sem migração:** info (avatar/banner/userinfo/serverinfo) + afk + enquete.
- **Plano 2 — lembrete:** migração 033 + repositório + comando (criar/listar/cancelar) + sweep.

## Plano 1 — Info + AFK + Enquete

### Info (exibição; sem estado)

- **`/avatar [usuario]`** — avatar grande via V2 `MediaGallery(getEffectiveAvatarUrl(1024))` + botão-link "Baixar".
- **`/banner [usuario]`** — `user.retrieveProfile().queue(p -> p.getBannerUrl(...))` (async; `deferReply` → `editOriginalComponents`); banner nulo → aviso "sem banner".
- **`/userinfo [usuario]`** — menção, id, conta criada (`<t:…:F> • <t:…:R>`), entrou no servidor, nº de cargos, principais cargos. Usa `Member`.
- **`/serverinfo`** — nome, id, dono, criação, membros, canais (texto/voz), cargos, nível de boost. Usa `Guild`.
- Pouca lógica pura (glue de JDA) → sem teste unitário (padrão do projeto para comandos).

### AFK (in-memory)

- **`AfkRegistry`**: `ConcurrentHashMap<String, Afk>` keyed por `guildId + ":" + userId`; `record Afk(String reason, long since)`; `set/get/remove`. Testável.
- **`/afk [motivo]`** (default "Ausente") → marca AFK; confirma "Você está AFK".
- **`AfkListener`** (`onMessageReceived`): (a) se o autor está AFK → `remove` + "bem-vindo de volta, removi seu AFK" (temporária); (b) para cada usuário **mencionado** que está AFK → responde "fulano está AFK: motivo (desde <t:…:R>)". Ignora bots.

### Enquete (in-memory)

- **`PollTally`** (puro): `tally(Map<String,Integer> votes, int options)` → `int[] counts`; `bar(count, total)` → barrinha. Testado.
- **`/enquete pergunta:… opcoes:"a | b | c"`** (2–6 opções por `|`; menos de 2 → erro). Posta painel com um botão por opção. `EnqueteService` guarda `Poll(question, options, creatorId, Map<userId,Integer> votes)` em `ConcurrentHashMap<messageId, Poll>`.
- **Votar** (`enq:vote:<idx>`): registra/atualiza o voto do usuário (1/pessoa, pode trocar), confirma efêmero, edita o painel com as contagens/barras.
- **Encerrar** (`enq:end`): só o `creatorId` → edita para o resultado final (sem botões) e remove do mapa.

## Plano 2 — Lembrete (persistente)

- Migração `033_reminders.sql`:
```sql
CREATE TABLE IF NOT EXISTS reminders (
    id         TEXT PRIMARY KEY, guild_id TEXT NOT NULL, user_id TEXT NOT NULL,
    channel_id TEXT NOT NULL, message TEXT NOT NULL, remind_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_reminders_due ON reminders (remind_at);
```
- `Reminder` (record) + `ReminderRepository`: `create(...)` (id 8-char), `List<Reminder> due(now)`, `void delete(id)`, `List<Reminder> listByUser(guild,user)`, `boolean cancel(id, userId)` (apaga só se for do dono). Testado.
- **`/lembrete criar tempo:2h mensagem:…`** — `Durations.parse` (inválido → erro); `remind_at = now + dur`; confirma com `<t:…:R>`. Limite de mensagem (500) e teto de duração (o `Durations` já limita).
- **`/lembrete listar`** — seus lembretes ativos (id + quando + trecho), efêmero.
- **`/lembrete cancelar id:<id>`** — cancela o próprio (checa dono); aviso se não achar.
- **Sweep** (`onReady`, 30s): `due(now)` → **DM** "⏰ Lembrete: {msg}" ao usuário; se a DM falhar (`CANNOT_SEND_TO_USER`), posta no `channel_id` de origem mencionando; então `delete`. À prova de restart.

## Componentes

`utility/{AfkRegistry, AfkListener, PollTally, EnqueteView, EnqueteService, EnqueteComponentHandler, InfoView, Reminder, ReminderRepository}` + comandos `{AvatarCommand, BannerCommand, UserInfoCommand, ServerInfoCommand, AfkCommand, EnqueteCommand, LembreteCommand}`.

## Tratamento de erros / bordas

- `/banner` sem banner → "usuário sem banner". `retrieveProfile` falha → aviso.
- `/enquete` <2 ou >6 opções → erro; votar em enquete encerrada → "já encerrou"; Encerrar por não-criador → aviso.
- `/afk` em bot/DM → ignora; menção de bot AFK impossível (bots não usam /afk).
- `/lembrete` tempo inválido → erro; cancelar id de outro → recusa; DM bloqueada → fallback no canal.

## Testes

- `AfkRegistryTest` (set/get/remove; chave por guild+user).
- `PollTallyTest` (conta votos por opção; troca de voto; total; sem votos).
- `ReminderRepositoryTest` (create/due/delete/listByUser/cancel com dono; migração 033).

## Fora de escopo v1

- Geradores de imagem (`/procurado`, `/carta_reverso`) — dependem do pipeline de imagem (follow-up).
- Enquete persistida / multi-voto / anônima; lembretes recorrentes; AFK persistente.
