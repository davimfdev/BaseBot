# Loja da Economia (Base, fase 6 — Plano 2) — itens de cargo/custom, compra, estoque e expiração

**Data:** 2026-07-02
**Módulo:** Base (`modules/base/economy/`) — standalone, **não depende de facs**.
**Status:** Design aprovado (revisão do spec pendente)
**Spec-mãe:** `2026-07-01-base-economy-design.md` (este é o **Plano 2 — Loja**; o Plano 1/Núcleo já está implementado).

## Objetivo

Dar ao membro **onde gastar** as moedas (ralo contra inflação): uma loja por servidor com itens que são **cargos permanentes**, **cargos temporários que expiram**, ou **itens custom** (sem cargo — entregues manualmente pela staff via um canal de log). Vitrine em `/loja`, gestão em `/setup → Economia`.

## Decisões (do brainstorming)

- **Pagamento:** o preço sai **só da carteira** (`cash`), igual a `/pagar` e às multas. Consistente e mantém o banco como cofre seguro; se faltar cash, o usuário saca antes.
- **Três tipos de item:** `ROLE_PERM`, `ROLE_TEMP`, `CUSTOM`.
- **Metadados por item:** nome custom + descrição opcional (vitrine), preço, e — conforme o tipo — cargo, duração (temp), **estoque global** e **limite por usuário** (ambos opcionais; vazio = ilimitado).
- **Recompra de cargo temporário ativo:** **estende** a duração (soma ao tempo restante), sem duplicar o cargo.
- **UI de compra:** vitrine **efêmera** + `StringSelectMenu` (até 25 itens) → confirmação → botão Comprar. Não polui o canal.
- **Log de compra:** novo tipo `log-loja` (módulo Base) em `/setup → Logs`; usado principalmente para **entregar itens custom** (comprador + item + preço).
- **Sweep de expiração** dos cargos temporários agendado no boot + periódico, à prova de restart.

## Dados (SQLite — migração `034_shop.sql`, anexar a `SqliteMigrator.MIGRATIONS`)

```sql
CREATE TABLE IF NOT EXISTS shop_items (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    guild_id    TEXT NOT NULL,
    type        TEXT NOT NULL,              -- 'ROLE_PERM' | 'ROLE_TEMP' | 'CUSTOM'
    role_id     TEXT,                       -- NULL para CUSTOM
    name        TEXT NOT NULL,              -- nome custom (vitrine)
    description TEXT,                        -- opcional
    price       INTEGER NOT NULL,
    duration_s  INTEGER,                    -- NULL exceto ROLE_TEMP
    stock       INTEGER,                    -- NULL = ilimitado (estoque global)
    per_user    INTEGER,                    -- NULL = ilimitado por usuário
    sold        INTEGER NOT NULL DEFAULT 0, -- unidades vendidas (p/ estoque global)
    created_at  INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS shop_purchases (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    guild_id    TEXT NOT NULL,
    item_id     INTEGER NOT NULL,
    user_id     TEXT NOT NULL,
    role_id     TEXT,                       -- p/ o sweep de temp (NULL se custom)
    expires_at  INTEGER,                    -- NULL = permanente/custom; sweep remove quando vence
    price_paid  INTEGER NOT NULL,
    created_at  INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_shop_purch_expiry
    ON shop_purchases(expires_at) WHERE expires_at IS NOT NULL;
```

`shop_purchases` serve a três propósitos: (a) **sweep** de expiração dos cargos temporários (índice parcial, padrão de `voice_sessions`), (b) contar `per_user` por item, (c) histórico de compras. O contador `sold` em `shop_items` cobre o **estoque global** de forma barata (sem `COUNT` na tabela de compras).

## Repositórios

- **`ShopItemRepository`** (`ShopItem` = `record(long id, String guildId, ItemType type, String roleId, String name, String description, long price, Long durationS, Integer stock, Integer perUser, int sold, long createdAt)`):
  - `long insert(ShopItem)`, `List<ShopItem> list(guildId)` (todos, ordem por preço/id — usado pela gestão do setup **e** pela vitrine), `int count(guildId)` (para o teto de 25), `ShopItem find(guildId, id)`, `boolean delete(guildId, id)`. **`list` não leva `LIMIT`** (a gestão precisa ver todos); o teto de 25 é imposto na **criação** (setup), garantindo que o único `StringSelect` da vitrine nunca estoure.
  - `boolean reserveStock(id)` — `UPDATE shop_items SET sold = sold + 1 WHERE id=? AND (stock IS NULL OR sold < stock)`; `true` se `executeUpdate()==1` (reserva atômica; `false` = esgotado).
  - `void releaseStock(id)` — `UPDATE ... SET sold = sold - 1 WHERE id=? AND sold > 0` (estorno se o débito da carteira falhar depois de reservar).
- **`ShopPurchaseRepository`** (`Purchase = record(long id, String guildId, long itemId, String userId, String roleId, Long expiresAt, long pricePaid, long createdAt)`):
  - `long insert(Purchase)`.
  - `int countActiveByUserItem(guildId, userId, itemId, nowMs)` — para o limite `per_user`: conta compras **ativas** desse item (`expires_at IS NULL` [perm/custom] **ou** `expires_at > now` [temp não expirado]).
  - `Purchase activeTemp(guildId, userId, itemId, nowMs)` — compra temp ativa (`expires_at > now`) desse item, para **estender**.
  - `boolean extend(purchaseId, newExpiresAt)`.
  - `List<Purchase> due(nowMs)` — compras com `expires_at <= now` (sweep).
  - `boolean claim(purchaseId)` — `DELETE ... WHERE id=? RETURNING`-style (na prática `DELETE` e checar `executeUpdate()==1`); **claim-by-delete** evita remoção dupla no sweep.

## Regras puras (`ShopPurchaseRules` — testável)

Sem JDA/DB. Concentra a aritmética de decisão:
- `boolean alreadyOwnsPerm(boolean memberHasRole)` — bloqueia recompra de `ROLE_PERM` já possuído.
- `boolean perUserReached(Integer perUser, int alreadyBought)` — `perUser != null && alreadyBought >= perUser`.
- `long extendedExpiry(long currentExpiresAt, long durationS, long nowMs)` — `max(currentExpiresAt, now) + durationS*1000` (estende a partir do que resta; se já venceu por corrida, a partir de agora).
- `long newExpiry(long durationS, long nowMs)` — `now + durationS*1000`.

## Serviço (`ShopService`)

Orquestra config + repos + JDA. Métodos principais:

- **`List<ShopItem> catalog(Guild)`** — itens ativos; a view marca esgotado/limite.
- **`String buy(Guild, Member, long itemId)`** — fluxo de compra (devolve mensagem já formatada, padrão do `EconomyService`):
  1. Economia desligada (`!eco:enabled`) → "economia desativada".
  2. `item = find(...)`; inexistente → "item indisponível".
  3. **Ramo de renovação (`ROLE_TEMP` com `activeTemp` presente):** é a mesma unidade sendo estendida — **não** consome estoque nem conta pro `per_user`. Debita carteira (`tryDebitCash`; `false` → "saldo insuficiente"); garante o cargo presente (re-`addRoleToMember` se por acaso não tiver); `extend(extendedExpiry)`. Confirmação de renovação. **Fim.**
  4. **Compra nova** — validações prévias (sem debitar): `ROLE_PERM` já possuído → erro; `per_user` atingido (`countActiveByUserItem`) → erro; para tipos de cargo, cargo sumiu (role_id não resolve) → "item mal configurado".
  5. **Reservar estoque** (`reserveStock`); `false` → "esgotado".
  6. **Debitar carteira** (`wallets.tryDebitCash(price)`); `false` → `releaseStock` + "saldo insuficiente na carteira".
  7. **Entregar conforme o tipo:**
     - `ROLE_PERM` → `guild.addRoleToMember` (compra automática, actor = sistema; reason simples) + `insert(Purchase expires=null)`.
     - `ROLE_TEMP` (primeira compra) → `addRoleToMember` + `insert(Purchase expires=newExpiry, role_id)`.
     - `CUSTOM` → `insert(Purchase expires=null)` + **posta no `log-loja`** (comprador, item, preço, id da compra) pra entrega manual.
  8. Confirmação formatada (preço debitado + o que recebeu / "registrado, a staff vai entregar").
  - **Falha ao conceder cargo** (ex.: hierarquia — cargo do bot abaixo): estorna (`releaseStock` + `wallets.addCash` de volta) e informa "não consegui te dar o cargo (hierarquia)". *Cargo temp que falha ao conceder também estorna.* **Loga o estorno no `log-loja`** ("↩️ estorno automático — comprador, item, valor devolvido, motivo") para a staff auditar: `addCash` (INSERT-or-UPDATE) sempre devolve o dinheiro, então sem o log fica invisível por que alguém recuperou moedas sem o cargo.
- **`void sweep()`** — `due(now)` → para cada compra: `claim(id)` (se `false`, outro tick já pegou → pula) → `guild.removeRoleFromMember(role_id)` (ignora se membro/cargo sumiu). À prova de restart e de tick duplo.

**Nota de atomicidade:** `SqliteManager` usa HikariCP (conexões isoladas), então `reserveStock` (update condicional) e `tryDebitCash` (`WHERE cash >= ?`) são individualmente atômicos. A compra não é uma transação única entre as duas tabelas, mas a ordem **reservar → debitar → (estornar em falha)** com estornos compensatórios evita tanto vender além do estoque quanto debitar sem entregar. (Corrida rara de crash entre reservar e debitar deixa no máximo 1 unidade "presa" no `sold` — aceitável; sem venda fantasma nem cobrança fantasma.)

## Comando `/loja`

- **`/loja`** — vitrine **efêmera**. Container V2: título + lista de itens (nome, `EconomyFormat` do preço, tipo/duração legível via `Durations`, estoque restante = `stock - sold` quando limitado, "esgotado"/"limite atingido" quando aplicável). `StringSelectMenu` (`shop:pick`) com os itens compráveis → tela de **confirmação** (item + preço + saldo atual do autor) com botão **Comprar** (`shop:buy:<itemId>`) e **Cancelar** → resultado em `Replies.reply` (temporário). Lista vazia → "a loja está vazia". Como a criação é capada em 25 itens/guild, o único select sempre cabe; ainda assim a view **fatia defensivamente em 25** opções.
- Revalida tudo no clique de compra (estoque/limite/saldo podem ter mudado).

## Componentes (`ShopComponentHandler` — namespace `shop`)

Handler **dedicado** (não incha o `EconomyComponentHandler`): `shop:pick` (select → confirmação, editando a mensagem efêmera), `shop:buy:<itemId>` (compra), `shop:cancel`. Setup usa o namespace de setup existente (ver abaixo).

## `/setup → Economia` — seção Loja

A tela de Economia ganha um bloco **Loja** (gate `MANAGE_SERVER`, padrão base):
- **Listar** os itens com preço/tipo/estoque (`list()` completo — vê todos, mesmo além de 25 se um dia existirem por migração externa).
- **Adicionar** — **teto de 25 itens/guild** (checa `count()`; 26º → aviso "loja cheia, remova um item"). O fluxo respeita a restrição do Discord de que **um modal só contém text inputs** (nunca um select) e de que **não se abre modal a partir de um modal-submit**:
  - Botão **Adicionar** → `StringSelectMenu` de **tipo** (`ROLE_PERM` / `ROLE_TEMP` / `CUSTOM`).
  - **`CUSTOM`:** o select de tipo **abre o modal** direto (nome, descrição, preço) → submit insere. (Select→modal já é usado no projeto, ex.: Pix.)
  - **`ROLE_PERM` / `ROLE_TEMP`:** o select de tipo responde com um `EntitySelectMenu` de **cargo**; ao escolher, esse select **abre o modal** com o `roleId` (e o tipo) carregados no `customId` do modal → o submit lê `roleId`/tipo do próprio `customId` e insere. **Sem rascunho em memória** — o cargo viaja pelo `customId`, os textos vêm do modal. O modal coleta nome, descrição, preço, e (por tipo) **duração** (só `ROLE_TEMP`), **estoque** e **limite por usuário** (vazios = ilimitado).
- **Remover** item (select → confirma → `delete`). Remover item **não** revoga cargos já vendidos (donos mantêm; temp expira normalmente).
- Wireado no namespace de componentes de setup e no `moduleNav` (a seção já existe).

## Log — novo tipo `log-loja`

Adicionar `new LogType("log-loja", "Loja", "Base")` em `SetupLogTypes.ALL`. Configurável em `/setup → Logs` e criado pelo **Setup rápido** (`QuickLogSetup`, que já itera os tipos ativos). `SetupLogTypesTest` atualizado para a nova contagem de tipos Base. Compras logam via `ChannelLog.post(ctx, guild, "log-loja", md)`; itens **CUSTOM** são o caso que exige a entrega manual pela staff.

## Sweep (agendamento)

`ShopService.sweep()` registrado em `BaseModule.onReady`: roda no boot e a cada **5 min** (padrão dos outros sweeps do módulo), via `ctx.scheduler()`. Idempotente (claim-by-delete). Sem heartbeat — expira pelo `expires_at` absoluto persistido, então reinício não perde nem duplica remoções.

## Tratamento de erros / bordas

- Economia desligada → `/loja` e compra respondem "economia desativada" (efêmero).
- Item inexistente/removido entre abrir a vitrine e comprar → "item indisponível".
- Esgotado (estoque global) ou limite por usuário atingido → erro; nada debitado.
- Saldo insuficiente na carteira → erro; nada debitado (débito atômico) + estoque estornado.
- Cargo mal configurado (role sumiu) ou falha de hierarquia ao conceder → estorna dinheiro + estoque e informa.
- Recompra de `ROLE_PERM` já possuído → bloqueia.
- Recompra de `ROLE_TEMP` ativo → estende a duração (não cobra duplo cargo, cobra o preço normalmente — é renovação).
- Preço/estoque/duração inválidos no setup (≤0, não numérico) → validação no modal com aviso.

## Testes

- `ShopItemRepositoryTest` — CRUD; `count`; `reserveStock` esgota via update condicional (não passa de `stock`); `releaseStock` decrementa com piso; migração 034 em DB temp.
- `ShopPurchaseRepositoryTest` — `insert`/`countByUserItem`/`activeTemp` (ignora expirados)/`extend`/`due`/`claim` (claim-by-delete: segunda chamada retorna `false`).
- `ShopPurchaseRulesTest` — `alreadyOwnsPerm`, `perUserReached`, `extendedExpiry` (estende do que resta; do agora se já venceu), `newExpiry`.
- Padrão do projeto: JUnit 5 puro, SQLite in-memory, sem Mockito. `SetupLogTypesTest` atualizado.

## Fora de escopo

- Reembolso / revenda pelo usuário.
- Reposição automática de estoque; estoque com janela de tempo.
- Itens custom com **efeito automático** (boosts de XP, consumíveis com lógica) — só entrega manual via log no v1.
- Câmbio com a tesouraria de facção (`fac_finance`) — permanecem separadas.
- Pagamento pelo banco (decidido: só carteira).
