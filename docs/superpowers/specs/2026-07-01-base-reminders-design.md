# Utilidades — `/lembrete` (Plano 2) — Design

**Data:** 2026-07-01
**Módulo:** Base · Utilidades (`modules/base/utility/`)
**Escopo:** `/lembrete criar | listar | cancelar` — lembretes pessoais persistentes, entregues por DM (fallback no canal), à prova de restart.

## Objetivo

Fechar o Plano 2 do subsistema de Utilidades (Plano 1 = info + afk + enquete, já concluído). Um usuário agenda um lembrete com um tempo relativo (`10m`, `1h30m`, `2d`); quando vence, o bot avisa na DM e, se a DM estiver fechada, no canal onde foi criado mencionando o usuário. Sobrevive a reinícios do bot.

## Decisões de design

- **Persistência + sweep** (não em memória): tabela SQLite + sweep de 30s. Requisito "à prova de restart" (PROGRESS §21). Mesmo padrão de `/sorteio` (`GiveawayRepository` + `GiveawayService.sweep`).
- **Entrega:** DM primeiro; fallback no `channel_id` mencionando o usuário quando a DM está fechada (caso comum). Por isso o canal é guardado.
- **Reuso de tempo:** `util/Durations.parse` (`10m`/`1h30m`/`2d`, teto de 28 dias, já testado e usado por `/mute`). Nenhum parser novo.
- **Sem `/setup`:** é utilitário por-usuário, sem config de guild (igual AFK/enquete). YAGNI.
- **Sem recorrência:** lembretes são disparo único. YAGNI para a v1.
- **Teto de 25 lembretes pendentes por usuário** (anti-abuso / anti-spam de DM).
- **`cancelar` com autocomplete** dos lembretes pendentes do próprio usuário (infra `AutocompleteCommand` já existe) — melhor UX que digitar id.

## Componentes

### 1. Migração `033_reminders.sql`

Registrar em `SqliteMigrator.MIGRATIONS` (senão nunca aplica — gotcha conhecido).

```sql
CREATE TABLE IF NOT EXISTS reminders (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    guild_id   TEXT NOT NULL,
    user_id    TEXT NOT NULL,
    channel_id TEXT NOT NULL,   -- fallback quando a DM está fechada
    message    TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    remind_at  INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_reminders_due ON reminders(remind_at);
```

### 2. `ReminderRepository` (SQLite)

Molde do `GiveawayRepository`. Registrar no `DatabaseManager`.

- `record Reminder(long id, String guildId, String userId, String channelId, String message, long createdAt, long remindAt)`
- `long create(String guildId, String userId, String channelId, String message, long createdAt, long remindAt)` — retorna o id gerado.
- `List<Reminder> listByUser(String guildId, String userId)` — ordenado por `remind_at` ASC.
- `int countByUser(String guildId, String userId)` — para o teto.
- `List<Reminder> due(long nowMillis)` — `remind_at <= ?`, ordenado por `remind_at`.
- `boolean cancel(long id, String userId)` — `DELETE ... WHERE id=? AND user_id=?` (guarda de dono); retorna `rows > 0`.
- `void delete(long id)` — remoção pós-disparo (claim no sweep).

### 3. `ReminderService`

- `create(SlashCommandInteractionEvent event, long millis, String message)`:
  - Bloqueia se `countByUser >= 25` → efêmero "Você já tem 25 lembretes pendentes."
  - `remindAt = now + millis`; insere; efêmero de confirmação com `<t:remindAt:F> • <t:remindAt:R>`.
- `list(SlashCommandInteractionEvent event)` — container efêmero com os pendentes (`#id · <t:…:F> • <t:…:R>` + `> mensagem`); vazio → "Você não tem lembretes."
- `cancel(SlashCommandInteractionEvent event, long id)` — `repo.cancel(id, userId)`; efêmero (sucesso / "não encontrado ou não é seu").
- `sweep()` (scheduler, 30s):
  - `repo.due(now)` → para cada lembrete:
    1. **Claim:** `repo.delete(id)` primeiro (evita disparo duplo em sweeps sobrepostos / callbacks assíncronos lentos).
    2. Resolve o usuário e envia por DM: `jda.retrieveUserById(userId).flatMap(User::openPrivateChannel)` → `sendMessageComponents(ReminderView.delivered(...)).useComponentsV2()`.
    3. `err` (DM fechada / usuário inacessível) → fallback: `guild.getChannelById(...)` / `jda.getChannelById(MessageChannel.class, channelId)` → posta o container mencionando `<@userId>` (`setAllowedMentions(USER)`).
  - Janela de perda: só se o bot cair entre o `delete` e o envio — desprezível e aceitável.

### 4. `ReminderView`

Containers V2 (house style + `Emojis`, `Panels.container/text/divider`):
- `confirm(accent, remindAt, message)` — confirmação da criação.
- `list(accent, List<Reminder>)` — listagem.
- `delivered(accent, message, createdAt)` — a mensagem entregue ("⏰ Lembrete! · você pediu <t:createdAt:R>" + `> mensagem`).

### 5. `/lembrete` (`ReminderCommand implements SlashCommand, AutocompleteCommand`)

Subcomandos (`SubcommandData`):
- `criar` — `tempo` (STRING, obrigatório), `mensagem` (STRING, obrigatório). `Durations.parse(tempo)` vazio → erro orientando `10m`, `1h30m`, `2d`.
- `listar` — sem opções.
- `cancelar` — `id` (INTEGER, obrigatório, `setAutoComplete(true)`).

Autocomplete: quando o foco é `cancelar → id`, sugere os pendentes do usuário (`repo.listByUser`), rótulo = `Durations.format(remindAt-now)` + trecho da mensagem, valor = id (máx 25 choices).

### 6. Registro (`BaseModule`)

- `register(...)`: `ReminderRepository` (via `ctx.database()`), `ReminderService`, `registry.command(new ReminderCommand(service))`. Autocomplete não precisa de registro separado: `CommandManager` roteia interações de autocomplete para qualquer `SlashCommand` que também implemente `AutocompleteCommand` (referência: `ProduzirCommand`).
- `onReady(...)`: `ctx.scheduler().repeating(reminders::sweep, 30, 30, TimeUnit.SECONDS)`.

## Testes

JUnit 5 puro, SQLite in-memory, **sem Mockito** (convenção do projeto):
- `ReminderRepositoryTest` — aplica a migração 033 num DB temporário; cobre `create` (retorna id), `listByUser` (ordem + escopo por guild/user), `countByUser`, `due` (só vencidos), `cancel` (guarda de dono: outro usuário não apaga), `delete`.
- `Durations` já tem teste; sem parser novo.

## Fora de escopo (YAGNI)

- Lembretes recorrentes / cron.
- Editar um lembrete existente (cancela + cria de novo).
- Lembretes para outros usuários ou para um canal explícito na criação.
- Config em `/setup`.

## Riscos / gotchas

- **Migração:** anexar `033` a `SqliteMigrator.MIGRATIONS` (senão silenciosamente não aplica).
- **Sweep duplo:** claim (`delete` antes do envio) evita disparo duplicado em callbacks assíncronos.
- **Fallback de canal:** o canal pode ter sido deletado / bot sem permissão — o envio falha silenciosamente (best-effort), lembrete já removido.
- **`retrieveUserById`** precisa de rede; roda no thread do scheduler via RestAction assíncrona (não bloqueia).
