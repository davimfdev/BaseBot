# Economia por-usuário (Base, fase 6) — carteira/banco, ganhos, roubo, ranking e loja

**Data:** 2026-07-01
**Módulo:** Base (`modules/base/economy/`) — standalone, **não depende de facs**.
**Status:** Design aprovado (revisão do spec pendente)

## Objetivo

Economia geral por usuário (estilo UnbelievaBoat), **separada** da tesouraria de facção (`fac_finance/…`). Membros ganham moedas (`/daily`, `/trabalhar`, `/crime`), roubam (`/roubar`), transferem (`/pagar`), guardam no banco (`/depositar`/`/sacar`), veem saldo/ranking e compram cargos (loja). Configurável em `/setup → Economia`.

## Decisões (do brainstorming)

- **Moeda:** fixa por padrão (🪙 "moedas"), **nome+emoji configuráveis** no setup. Inteiro (sem centavos).
- **Carteira + Banco:** o roubo só atinge a **carteira**; o banco é seguro (incentivo a depositar).
- **Fontes de renda:** `/daily`, `/trabalhar`, `/crime` (risco), `/roubar` (entre usuários).
- **Loja de cargos:** permanentes **e temporários que expiram** (ralo contra inflação).
- **Ranking `/rico`:** por **total** (carteira + banco).
- **Configurável no v1:** moeda (nome/emoji), `daily`, `trabalhar` (faixa + cooldown). **Crime/roubo:** defaults fixos.
- **Anti-poluição:** comandos de **leitura** (`/saldo`, `/rico`) são **efêmeros**; resultados de **ação** (`/daily /trabalhar /crime /roubar /pagar /depositar /sacar`) usam `Replies.reply` (mensagem temporária que auto-deleta em ~8s).

## Escopo / decomposição

Um spec, **dois planos**:
- **Plano 1 — Núcleo:** saldos, ganhos, roubo, transferência, banco, `/saldo`, `/rico`, `/eco` (admin), `/setup → Economia`.
- **Plano 2 — Loja:** `shop_items` (perm + temporários), `/loja` (ver + comprar), sweep de expiração de cargos temporários, gestão no setup.

## Moeda e formatação (puro — `EconomyFormat`)

`format(long amount, GuildConfig cfg)` → `{emoji} {valor agrupado}` (ex.: `🪙 1.234`). Agrupamento BR (`.`), inteiro. Emoji/nome de `EconomyConfig`. Nome aparece em textos longos (ex.: "1.234 moedas"); `format(...)` usa emoji + número; helper `formatNamed(...)` acrescenta o nome.

## Dados (SQLite — migração `029_economy.sql`, anexar a `SqliteMigrator.MIGRATIONS`)

```sql
CREATE TABLE IF NOT EXISTS user_wallets (
    guild_id TEXT NOT NULL,
    user_id  TEXT NOT NULL,
    cash     INTEGER NOT NULL DEFAULT 0,
    bank     INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (guild_id, user_id)
);
CREATE TABLE IF NOT EXISTS eco_cooldowns (
    guild_id TEXT NOT NULL,
    user_id  TEXT NOT NULL,
    action   TEXT NOT NULL,
    last_ts  INTEGER NOT NULL,
    PRIMARY KEY (guild_id, user_id, action)
);
```
- `WalletRepository`: `Wallet get(g,u)` (`record Wallet(long cash, long bank)`), `long addCash(g,u,delta)` (upsert; só p/ injetar dinheiro — daily/work/crime-win/admin), `boolean deposit(g,u,amount)`/`withdraw(...)` (atômicos), `boolean transfer(g,fromU,toU,amount)` (atômico c/ upsert do recebedor), `boolean tryDebitCash(g,u,amount)` (multa), `List<Entry> topPage(g,limit,offset)` (order by `cash+bank` desc), `int rank(g,u)`, `int count(g)`. `record Entry(String userId, long total)`.
- **⚠️ Dedução atômica absoluta (anti double-spend):** nunca `SELECT` do saldo seguido de `UPDATE`. O lock vai **na própria instrução** via `WHERE`:
  ```sql
  UPDATE user_wallets SET cash = cash - ?, bank = bank + ? WHERE guild_id=? AND user_id=? AND cash >= ?;
  ```
  Se `executeUpdate() == 0` → saldo insuficiente (ou conta inexistente) → aborta/`false`. Blinda contra race condition por spam de comandos. (`withdraw` faz o simétrico com `bank >= ?`.)
- **⚠️ `transfer` (`/pagar`) — duas etapas numa transação** (`BEGIN…COMMIT`), pois o recebedor pode não existir ainda:
  1. **Debita o pagador** com a regra atômica acima (`... SET cash = cash - ? WHERE … AND cash >= ?`). Se `executeUpdate()==0` → `rollback` + `false`.
  2. **Credita o recebedor** com `INSERT INTO user_wallets (guild_id,user_id,cash) VALUES (?,?,?) ON CONFLICT (guild_id,user_id) DO UPDATE SET cash = cash + excluded.cash` (cria a linha se for novo). `commit`. (Roubo bem-sucedido usa o mesmo caminho de transferência.)
- `CooldownRepository`: `long lastTs(g,u,action)`, `void stamp(g,u,action,ts)`.
- Leaderboard ordena por `(cash + bank)`.

## Mecânicas + defaults (`EconomyDefaults` — constantes v1)

Resolvers **puros** para o que tem aleatoriedade (roll injetável → testável):
- **`/daily`** — cooldown 24h; valor fixo (default `500`, `eco:daily`) → carteira.
- **`/trabalhar`** — cooldown 1h (`eco:work-cooldown`); aleatório `eco:work-min`..`eco:work-max` (default 50–250) → carteira; frase de trabalho aleatória.
- **`/crime`** — cooldown 1h; `CrimeOutcome.resolve(rollPct, winAmount, fineAmount)`: sucesso (default 50%) → +100..500; falha → −(50..250) da carteira (piso 0). Ralo de dinheiro.
- **`/roubar @alvo`** — cooldown 2h; `RobOutcome.resolve(rollPct, targetCash, ...)`: sucesso (default 40%) → rouba 10–30% da **carteira** do alvo (mín. carteira do alvo `100`, senão bloqueia); falha → paga multa 50–200 ao alvo. Sem bots/si mesmo. Transferência atômica.
- **Banco:** `/depositar [qtd|tudo]` (carteira→banco), `/sacar [qtd|tudo]` (banco→carteira). Sem teto no v1.
- **`/pagar @alvo qtd`:** transfere da carteira do autor p/ a carteira do alvo (atômico; sem bots/si mesmo; só se houver saldo).

## Serviço (`EconomyService`)

Orquestra as operações lendo `EconomyConfig` + repos. Toda operação que mexe em 2 linhas (pagar/roubar) ou lê-e-escreve condicional (depositar/sacar/multa) é **atômica** (transação no repo, deduz só se houver — padrão do `deductStock`). Cooldowns via `CooldownRepository`; devolve o tempo restante quando ainda em cooldown.

## Comandos (Plano 1)

- **`/saldo [usuario]`** — carteira + banco + total + posição (efêmero).
- **`/daily`, `/trabalhar`, `/crime`, `/roubar @alvo`, `/pagar @alvo qtd`, `/depositar [qtd|tudo]`, `/sacar [qtd|tudo]`** — resultado em `Replies.reply` (temporário).
- **`/rico`** — ranking por total, paginado (efêmero; paginação via `editComponents`).
- **`/eco add|remove|set|reset`** (admin `MANAGE_SERVER`) — ajusta carteira/banco de um usuário; `reset` zera ambos.

## `/setup → Economia`

Toggle liga/desliga; **moeda** (modal: nome + emoji); **valores** (modal: daily, trabalhar-min, trabalhar-max, cooldown-trabalhar). (Plano 2: editor de itens da loja.) Gate `MANAGE_SERVER`.

## Config (`guild_config`, prefixo `eco:`)

`eco:enabled` (toggle), `eco:currency-name` (default "moedas"), `eco:currency-emoji` (default = emoji custom `MONEY`), `eco:daily` (500), `eco:work-min` (50), `eco:work-max` (250), `eco:work-cooldown` (segundos, 3600). Crime/roubo: constantes em `EconomyDefaults`.

## Tratamento de erros / bordas

- Economia desligada (`!eco:enabled`) → comandos respondem "economia desativada" (efêmero).
- Saldo insuficiente (pagar/depositar/sacar/multa) → aviso; nada é debitado (deduções atômicas).
- Cooldown ativo → informa quando libera com **timestamp nativo do Discord** `<t:{epochSegundos}:R>` (renderiza "em 45 minutos", localizado). O `EconomyService` calcula `readyAt = lastTs + cooldown` e passa o epoch em segundos.
- `/roubar`/`/pagar` em bot ou em si mesmo → bloqueia; alvo com carteira abaixo do mínimo → bloqueia.
- Admin `set` negativo → clampa em 0.

## Testes

- `EconomyFormatTest` (agrupamento, emoji/nome default e configurados).
- `EconomyConfigTest` (defaults + leitura).
- `WalletRepositoryTest` (deposit/withdraw/transfer atômicos: **falha retorna false sem debitar** quando saldo insuficiente; **transfer cria a linha do recebedor** se ele for novo; `tryDebitCash` piso; topPage/rank por total; SQLite temp, migração 029).
- `CrimeOutcomeTest`/`RobOutcomeTest` (sucesso/falha por roll injetado; multa/roubo calculados; piso 0; mínimo do alvo).
- `CooldownRepositoryTest` (stamp/lastTs).

## Fora de escopo

- Loja (Plano 2) — cargos temporários + sweep de expiração + migração 030.
- Integração XP↔economia (boosts) — fase Fun.
- Juros do banco, itens consumíveis não-cargo, apostas (jogos) — futuro.
- Câmbio entre a economia por-usuário e a tesouraria de facção (permanecem separadas).
