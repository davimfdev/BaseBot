# Sorteios (giveaways) com requisitos avançados (Base, fase entre 6–7)

**Data:** 2026-07-01
**Módulo:** Base (`modules/base/giveaway/`) — usa `voice_sessions` (leveling) + economy.
**Status:** Design aprovado (revisão do spec pendente)

## Objetivo

Sorteios com **painel de participação** e **requisitos configuráveis por sorteio** (cargo, dias no servidor, horas em call, call numa janela de horário). N ganhadores, prêmio em texto + moedas automáticas. Persistido (sobrevive a restart), com sorteio automático no fim, além de **encerrar agora** e **resortear** (admin).

## Decisões (do brainstorming)

- **4 requisitos, todos opcionais e por sorteio:** cargo; dias no servidor; horas em call (total); call em janela de horário.
- **N ganhadores** configurável (1 ou mais).
- **Prêmio:** descrição em texto + **moedas automáticas** opcionais ao(s) ganhador(es).
- **Encerrar antes + resortear** incluídos no v1 (admin).
- **Fuso fixo `America/Sao_Paulo`** para a janela de horário.
- Requisitos validados **na participação**; no sorteio, descarta só quem saiu do servidor.

## Dados (SQLite — migração `030_giveaways.sql`, anexar a `SqliteMigrator.MIGRATIONS`)

```sql
CREATE TABLE IF NOT EXISTS giveaways (
    id                  TEXT PRIMARY KEY,
    guild_id            TEXT NOT NULL,
    channel_id          TEXT NOT NULL,
    message_id          TEXT,
    prize               TEXT NOT NULL,
    coin_reward         INTEGER NOT NULL DEFAULT 0,
    winners             INTEGER NOT NULL DEFAULT 1,
    ends_at             INTEGER NOT NULL,
    ended               INTEGER NOT NULL DEFAULT 0,
    req_role_id         TEXT,
    req_min_days        INTEGER NOT NULL DEFAULT 0,
    req_min_voice_hours INTEGER NOT NULL DEFAULT 0,
    req_window_start    INTEGER NOT NULL DEFAULT -1,
    req_window_end      INTEGER NOT NULL DEFAULT -1
);
CREATE TABLE IF NOT EXISTS giveaway_entries (
    giveaway_id TEXT NOT NULL,
    user_id     TEXT NOT NULL,
    PRIMARY KEY (giveaway_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_giveaways_active ON giveaways (ended, ends_at);
```

- `Giveaway` (record espelhando as colunas). `GiveawayRepository`: `create(Giveaway)`, `find(id)`, `setMessageId(id,msg)`, `setEnded(id)`, `boolean addEntry(id,user)` (`INSERT OR IGNORE` → novo?), `List<String> entries(id)`, `int entryCount(id)`, `List<Giveaway> dueActive(now)` (`ended=0 AND ends_at<=?`).
- `id`: 8 chars de um `UUID` (mostrado no painel; usado em encerrar/resortear). **`create` à prova de colisão:** a chance é ínfima (~2.8 tri de combinações), mas o `create` gera o id e **retenta** com um novo id se o `INSERT` violar a PK (loop curto, ex.: 5 tentativas) — nunca quebra por id repetido.

## `voice_sessions` — adições (`VoiceSessionRepository`)

- `long totalVoiceMs(guildId, userId)`: soma `leave_time - join_time` das sessões fechadas + `agora - join_time` das abertas.
- `List<long[]> sessionsOf(guildId, userId)`: pares `[join, leave]` (leave=agora se aberta) para o requisito de janela.

## Requisitos (puros/testáveis)

- **`VoiceWindow.overlapsDailyWindow(joinMs, leaveMs, startHour, endHour, ZoneId)`** → boolean: a sessão intersecta a faixa `[startHour:00, endHour:00)` em alguma data que ela cobre (fuso fixo `America/Sao_Paulo`). Puro.
- **`GiveawayWindow.parse("20-23")`** → `int[]{20,23}` ou `null` (inválido). Puro.
- **`GiveawayRequirements.firstUnmet(Giveaway g, boolean hasRole, long joinedEpochMs, long totalVoiceMs, boolean windowOk, long now)`** → `String motivo` (ou `null` se ok): checa, na ordem, cada requisito **ativo** e devolve a 1ª falha (ex.: "Você precisa do cargo X", "Precisa de N dias no servidor", "Precisa de N h em call", "Precisa ter ficado em call entre X–Yh"). Puro. O serviço resolve `hasRole`/`joined`/`totalVoiceMs`/`windowOk` a partir do JDA + voice antes de chamar.

## Sorteio (`GiveawayDraw`, puro)

`pick(List<String> entrants, int n, Random r)` → embaralha uma cópia e devolve os primeiros `min(n, size)` (distintos). Puro/testável.

## Fluxo / serviço (`GiveawayService`)

- **`create(...)`**: monta o `Giveaway` (id gerado, `ends_at = agora + duração`), persiste, posta o painel (`GiveawayView.panel`), grava `message_id`.
- **`enter(ButtonInteractionEvent)`**: carrega o sorteio ativo (pelo id no botão); resolve os dados do membro + voz; `GiveawayRequirements.firstUnmet(...)`; se ok → `addEntry` (aviso efêmero "inscrito!" / "você já está participando"), atualiza a contagem no painel; se não → recusa efêmera com o motivo.
- **`draw(giveaway)`** (sweep + encerrar): entries → filtra quem ainda está na guild → `GiveawayDraw.pick(n)` → anuncia ganhador(es) no canal (marcando), credita `coin_reward` (se >0 e `eco:enabled`), edita o painel p/ "encerrado", `setEnded`.
- **`reroll(id)`**: sorteio já encerrado → sorteia de novo entre as entradas (fresh), anuncia + credita moedas aos novos ganhadores. **Anúncio explícito** ("🔁 Novo ganhador sorteado! Prêmio entregue.") para dar visibilidade/responsabilidade à staff sobre o double-pay (admin-only; risco baixo aceito no v1).
- **Sweep** (`onReady`, a cada 30s): `dueActive(agora)` → `draw(...)`. À prova de restart.

## Comandos + UI

- **`/sorteio criar`** (admin `MANAGE_SERVER`): `premio` (texto, obrig.), `duracao` (ex.: `2h`/`1d`, via `Durations`), `ganhadores` (int, default 1), `moedas` (int, opc.), `cargo` (ROLE, opc.), `dias_servidor` (int, opc.), `horas_call` (int, opc.), `janela` (texto "20-23", opc.).
- **`/sorteio encerrar id:<id>`** (admin): encerra agora (chama `draw`).
- **`/sorteio resortear id:<id>`** (admin): re-sorteia um encerrado.
- **Painel** (`GiveawayView`): prêmio, requisitos ativos listados, moedas (se houver), contagem de participantes, "encerra <t:…:R>", id no rodapé, botão **Participar** (`gwy:enter:<id>`). Ao encerrar: painel de ganhadores.
- Namespace de componente: `gwy`.

## Config

Sem config em `guild_config` (cada sorteio carrega seus próprios parâmetros). Sem tela de setup — criação é por comando. Requer intents `GUILD_MEMBERS` (dias/cargo) e `GUILD_VOICE_STATES` (já habilitados).

## Tratamento de erros / bordas

- `duracao`/`janela` inválidas → erro efêmero na criação; nada é criado.
- Sorteio inexistente/encerrado em encerrar/resortear → aviso efêmero.
- Sem participantes válidos no sorteio → anuncia "sem ganhadores".
- Ganhador que saiu do servidor → descartado no sorteio; se sobrar menos que N, sorteia os que der.
- Botão de sorteio já encerrado → aviso efêmero.
- `ganhadores`/números negativos → normalizados (mín. 1 ganhador; requisitos ≥0).

## Testes

- `GiveawayDrawTest` (pega N distintos; N>tamanho → todos; embaralha com Random semeado).
- `VoiceWindowTest` (sessão dentro/fora da faixa; que cruza meia-noite; borda inclusiva/exclusiva).
- `GiveawayWindowParseTest` ("20-23" ok; "abc"/"25-30" inválidos).
- `GiveawayRequirementsTest` (cada requisito ativo falhando isoladamente; todos ok → null; requisito inativo ignorado).
- `GiveawayRepositoryTest` (create/find/addEntry idempotente/entries/dueActive/setEnded; migração 030).
- `VoiceSessionRepositoryTest` (adição): `totalVoiceMs` soma fechadas + aberta.

## Fora de escopo v1

- Editar sorteio depois de criado; múltiplos prêmios; requisitos combináveis por "OU" (são todos "E").
- Fuso configurável (fixo BR); re-validação de requisitos no momento do sorteio (valida na entrada).
- Blacklist de participantes; entradas extras (bônus de peso).
