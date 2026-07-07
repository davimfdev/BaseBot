# Empregos, Equipamentos & Crime (Base, economia — fase 6, expansão) — design

**Data:** 2026-07-02
**Módulo:** Base (`modules/base/economy/`) — standalone, **não depende de facs**.
**Status:** Design aprovado no brainstorming (revisão do spec pendente)
**Specs-mãe:** `2026-07-01-base-economy-design.md` (núcleo) + `2026-07-02-base-shop-design.md` (Loja de cargos). Esta é a **expansão de gameplay** da economia por-usuário.

## Objetivo

Dar profundidade e **ralos de dinheiro** à economia via um sistema de **equipamentos com tiers e durabilidade**: empregos especializados (`/minerar`, `/cozinhar`, `/entregar`) que exigem ferramentas; crime armado (`/crime`, `/roubar`) que exige armas; **crime organizado** em grupo; e um ciclo punitivo de **cadeia + fiança + ficha criminal**. Catálogo fixo no código (mesmo em todo servidor), compra num `/mercado` dedicado, tudo gated por `eco:enabled`.

## Decisões (do brainstorming)

- **Catálogo fixo no código** (não configurável por guild); stats em constantes tunáveis.
- **Compra no `/mercado`** (dedicado, separado da `/loja` de cargos).
- **Equipar explícito** — um item equipado por slot (`MINING`/`COOKING`/`DELIVERY`/`WEAPON`); os comandos usam o item equipado do slot.
- **Combustível auto-debitado** da carteira por `/entregar` (escala com a moto).
- **Durabilidade:** cada uso decrementa `usos_left`; ao chegar a 0 o item **quebra** (removido) e desequipa. Arma **destruída na hora** se pego em `/crime`/org.
- **Arma obrigatória** para `/crime`, `/roubar` e `/crimeorganizado`.
- **Cadeia por tempo + fiança + ficha** (detalhes na §5).
- **Balanço:** números fixos em código; nenhuma nova config por-guild no v1.

## Arquitetura / decomposição

**Um spec-guarda-chuva, 4 planos** (cada um = spec-mãe → seu próprio plano de implementação; ordem obrigatória):

1. **Fundação** — `EquipmentCatalog`, migração (`user_inventory` + `user_crime_state`), `InventoryRepository`, `CrimeStateRepository` + `JailService`, `/mercado`, `/inventario`, `/fianca`, `/limparficha`, e o **guard de cadeia** aplicado ao `/trabalhar` existente.
2. **Empregos** — `/minerar`, `/cozinhar`, `/entregar`.
3. **Crime armado** — modifica `/crime` e `/roubar` (arma obrigatória, chance/retorno por arma + ficha, perda de arma). **Sem cadeia** — só o org prende.
4. **Crime organizado** — `/crimeorganizado` (lobby) + cadeia pesada + ficha.

---

## 1. Catálogo de equipamentos (`EquipmentCatalog` — puro, fixo)

`Slot { MINING, COOKING, DELIVERY, WEAPON }`. Registro imutável de itens; `record Equip(String key, String name, Slot slot, int tier, long price, int maxUsos, long payoutMin, long payoutMax, long fuel, int chanceBonus, double mult, long robCap)` (campos não usados pelo slot ficam 0). Helpers: `Equip byKey(String)`, `List<Equip> ofSlot(Slot)`, `int tierOf(String key)`.

**Picareta (`MINING`)** — lucro por uso:

| key | Nome | Preço | Usos | Lucro |
|---|---|---|---|---|
| `pickaxe_wood` | Picareta de Madeira | 500 | 15 | 60–120 |
| `pickaxe_stone` | Picareta de Pedra | 1.500 | 30 | 120–220 |
| `pickaxe_iron` | Picareta de Ferro | 4.000 | 50 | 250–400 |
| `pickaxe_gold` | Picareta de Ouro | 10.000 | 25 | 600–900 |
| `pickaxe_diamond` | Picareta de Diamante | 25.000 | 100 | 800–1.400 |

**Utensílio (`COOKING`)** — lucro base:

| key | Nome | Preço | Usos | Lucro |
|---|---|---|---|---|
| `cook_spoon` | Colher de Pau | 300 | 20 | 50–100 |
| `cook_whisk` | Fouet de Silicone | 1.200 | 30 | 110–200 |
| `cook_knife` | Faca do Chef (Aço Inox) | 3.500 | 40 | 220–380 |
| `cook_torch` | Maçarico Culinário | 8.000 | 35 | 400–700 |
| `cook_case` | Maleta Masterchef | 20.000 | 80 | 750–1.200 |

**Moto (`DELIVERY`)** — combustível debitado por entrega + lucro bruto:

| key | Nome | Preço | Usos | Fuel | Lucro |
|---|---|---|---|---|---|
| `moto_pop` | Honda Pop 100 | 2.500 | 40 | 30 | 150–250 |
| `moto_titan` | CG Titan 160 | 8.000 | 60 | 60 | 300–500 |
| `moto_xre` | Honda XRE 300 | 18.000 | 80 | 100 | 550–850 |
| `moto_xt` | Yamaha XT 660 (Meiota) | 40.000 | 100 | 180 | 1.000–1.500 |
| `moto_bmw` | BMW R1250 GS (Foguete) | 90.000 | 120 | 300 | 1.800–2.800 |

**Arma (`WEAPON`)** — bônus de chance + multiplicador de retorno + **cap absoluto de roubo** (teto do que um `/roubar` tira, pra o fuzil não drenar carteiras milionárias num comando):

| key | Nome | Preço | Usos | Bônus | Mult | Cap roubo |
|---|---|---|---|---|---|---|
| `weapon_knife` | Canivete Borboleta | 2.000 | 15 | +5% | 1.0x | 5.000 |
| `weapon_machete` | Facão de Selva | 6.000 | 25 | +12% | 1.3x | 10.000 |
| `weapon_pistol` | Pistola 9mm | 20.000 | 40 | +25% | 1.8x | 25.000 |
| `weapon_rifle` | Fuzil AR-15 | 50.000 | 60 | +40% | 2.5x | 50.000 |

O `Equip` de arma carrega o campo extra `robCap`.

## 2. Dados (SQLite — migração nova, anexar a `SqliteMigrator.MIGRATIONS`)

```sql
CREATE TABLE IF NOT EXISTS user_inventory (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    guild_id   TEXT NOT NULL,
    user_id    TEXT NOT NULL,
    item_key   TEXT NOT NULL,
    slot       TEXT NOT NULL,               -- redundante com o catálogo, mas simplifica "um equipado por slot"
    usos_left  INTEGER NOT NULL,
    equipped   INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_inv_user_slot ON user_inventory(guild_id, user_id, slot);
-- Impede, no banco, dois itens equipados no mesmo slot (SQLite suporta índice único parcial):
CREATE UNIQUE INDEX IF NOT EXISTS uniq_equipped_slot
    ON user_inventory(guild_id, user_id, slot) WHERE equipped = 1;

CREATE TABLE IF NOT EXISTS user_crime_state (
    guild_id  TEXT NOT NULL,
    user_id   TEXT NOT NULL,
    preso_ate INTEGER NOT NULL DEFAULT 0,
    ficha_suja INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (guild_id, user_id)
);
```

- Cada compra = uma **instância** (`usos_left = maxUsos`, `equipped=0`, `created_at = agora`). Pode ter reservas do mesmo item; o `created_at` permite ordenar as mais antigas primeiro no `/inventario`.
- **`InventoryRepository`:** `long buy(g,u,itemKey,slot,usos)`, `List<Row> list(g,u)` (ordem `slot, created_at`), `Row equipped(g,u,slot)` (null se nenhum), `boolean equip(g,u,rowId)` (**numa transação:** valida dono+existência, desequipa os outros do mesmo slot, equipa este — respeita o unique parcial), **`UseResult useOnce(g,u,rowId)`**, **`DestroyResult destroy(g,u,rowId)`**. `record Row(long id, String itemKey, Slot slot, int usosLeft, boolean equipped)`.
  - **`enum UseResultType { USED, USED_AND_BROKE, PERMANENT, NOT_FOUND, NOT_OWNER }`** + `record UseResult(UseResultType type, int usosLeft)`. `useOnce` numa transação: `NOT_FOUND` se a linha sumiu, `NOT_OWNER` se não é do usuário, `PERMANENT` se `usos_left < 0` (no-op, reservado a itens futuros — v1 não usa), senão decrementa e devolve `USED_AND_BROKE` (removeu a linha ao chegar a 0) ou `USED`.
  - **`enum DestroyResultType { DESTROYED, NOT_FOUND, NOT_OWNER }`** + `record DestroyResult(DestroyResultType type)` — mesma granularidade do `useOnce` (não um `boolean` cru), pra log/teste/manutenção distinguirem "sumiu por corrida" de "não é do dono" (componente velho/tentativa maliciosa).
  - **⚠️ Contrato pro handler:** o comando **só paga/aplica efeito depois de confirmar** o resultado. `NOT_FOUND`/`NOT_OWNER` → aborta sem pagar nem aplicar cooldown (corrida: item removido/desequipado por outro evento entre resolver o equipado e consumir). Fecha o exploit de "pagar sem consumir".
  - **Slot nunca é confiado sozinho:** no `buy`, o `slot` inserido vem **obrigatoriamente** de `EquipmentCatalog.byKey(itemKey).slot()`. No `equip`/`list`/`useOnce`, se `item_key` não existe no catálogo **ou** `catálogo.slot != row.slot`, a linha é tratada como **inválida** (não equipa/usa) — blinda contra dados antigos/migrados inconsistentes.
- **`CrimeStateRepository`:** `State get(g,u)` (`record State(long presoAte, boolean fichaSuja)`), `void jail(g,u,ate)`, `void setFicha(g,u,boolean)`, `void release(g,u)` (preso_ate=0). Upsert.

## 3. Cadeia + ficha (`JailService` — Fundação)

Estado puro/lazy, sem sweep obrigatório (a penalidade só importa quando a pessoa age):
- **`Status resolve(g,u)`** — lê `State`. Se `preso_ate > 0 && agora >= preso_ate` → **cumpriu a pena**: marca `ficha_suja=true` + `release` (preso_ate=0), devolve `LIVRE`. Se `agora < preso_ate` → `PRESO(preso_ate)`. Senão `LIVRE`. (Marcar a ficha no release-por-tempo é o que diferencia "cumprir pena" de "pagar fiança".)
- **`boolean blockedIfJailed(event, ctx)`** — helper que os comandos de ação chamam no topo: se `resolve` == PRESO, responde efêmero "Você está preso — sai <t:presoAte:R>. Pague `/fianca` pra sair agora." e devolve true. **Isento:** `/daily`, `/saldo`, `/rico`, `/inventario`, `/fianca`, e ganhos de **eventos** (ChatEventService não checa).
- **`/fianca`** — só se PRESO: debita **15.000** (carteira), `release` **sem** marcar a ficha (a prisão não vai pra ficha). Saldo insuficiente → aviso.
- **`/limparficha`** — só se `ficha_suja` e não preso: debita **30.000** (dobro da fiança) → `setFicha(false)`.
- **Jail on arrest** (chamado **só pelo crime organizado**): `jail(g,u, agora + duração)`. O crime solo **não prende**.
- **Penalidade de ficha:** a ficha suja é **adquirida só no org** (cumprir a pena sem fiança), mas penaliza **todos os crimes futuros**: **−15%** de chance em `/crime` e `/roubar` solo; no org, **−5% por membro com ficha suja**.

## 3.5. Atomicidade, ordem de mutação e cooldown (regra transversal)

**Serviços de caso de uso** — a lógica cruza vários repos (inventário, carteira, cooldown, estado criminal), então **não** fica espalhada nos comandos slash. Cada domínio tem um serviço que devolve um `record` de resultado; o handler só chama e formata:
- `EquipmentEconomyService.runMining/runCooking/runDelivery(g,u) → JobResult`
- `CrimeEconomyService.runCrime(g,u) → CrimeResult`, `runRobbery(g,attacker,target) → RobResult`
- `OrgCrimeService` (lobby + `resolve(snapshot) → OrgResult`)

**Ordem de checagem (todo comando), antes de qualquer mutação:** (1) `eco:enabled` — economia off recusa e **nada** muda; (2) `blockedIfJailed`; (3) pré-condições (ferramenta/arma equipada, saldo pro combustível/fiança, alvo válido, lobby válido).

**Cooldown só é gravado após uma tentativa VÁLIDA que rodou:**
- **Não grava** em: economia off, preso, sem item equipado, sem dinheiro pro combustível, alvo inválido/self/bot, lobby inválido, `useOnce` = `NOT_FOUND`/`NOT_OWNER`.
- **Grava** em: `/minerar`/`/cozinhar`/`/entregar` bem-sucedidos e **`/crime`/`/roubar` mesmo na falha** (o crime foi tentado). No org, o cooldown diário de 24h por pessoa é gravado **quando o crime INICIA**, nunca ao entrar no lobby (senão quem entra num lobby que expira perde o dia à toa).

**Transações (mutações críticas juntas):**
- **compra:** debitar carteira + inserir no inventário;
- **equipar:** desequipar o slot + equipar o novo (respeita o unique parcial);
- **emprego:** `useOnce` (confirma `USED`/`USED_AND_BROKE`) → pagar recompensa → gravar cooldown;
- **entrega:** conferir moto + `cash >= fuel` → debitar fuel + `useOnce` + pagar lucro + cooldown (o uso é consumido durante a ação; se a moto chegar a 0 a ação **conclui normalmente** e o item é removido ao final);
- **`/crime`:** revalida arma → sucesso: `useOnce` (confirma) → paga; falha: `destroy` (confirma) → multa; grava cooldown nos dois casos;
- **`/roubar` sucesso:** revalida arma + alvo → calcula roubável → **`useOnce` (confirma `USED`/`USED_AND_BROKE`) → só então transfer alvo→ladrão** (transfer é atômico) → cooldowns. **Falha:** `destroy` (confirma) → multa ao alvo → cooldowns. Consumir/destruir a arma **antes** de mover dinheiro (coerente com "nunca pagar/transferir antes de confirmar");
- **crime organizado:** ao **iniciar**, snapshot dos válidos → resolve **em lote** (regra de consumo/cancelamento na §6); ninguém sai depois de iniciado.

> **Atomicidade cross-tabela:** quando carteira, inventário, cooldown e estado criminal estiverem no **mesmo banco/conexão**, o serviço deve usar uma **transação única** sempre que possível. Quando não der pra cruzar os repositórios na mesma transação, a segurança **mínima obrigatória** é: **confirmar consumo/destruição (`useOnce`/`destroy`) antes de pagar/transferir**, e **abortar** em qualquer `NOT_FOUND`/`NOT_OWNER`. O núcleo de dinheiro (`transfer`/`tryDebitCash`) já é atômico e anti-double-spend.

### Ordem segura de pagamento/transferência

Nenhum comando pode creditar recompensa, transferir valor ou aplicar benefício **antes** de confirmar o consumo/destruição do item exigido:
- **Empregos / crime sucesso:** `useOnce` confirmado → recompensa.
- **Roubo sucesso:** `useOnce` confirmado → transfer alvo→ladrão.
- **Crime/roubo falha:** `destroy` confirmado → multa/cooldown.
- **Org sucesso:** consumo das armas do snapshot confirmado → pagamento das fatias.
- **Org falha:** destruição das armas do snapshot confirmada → cadeia/cooldown.

Se `useOnce`/`destroy` devolver `NOT_FOUND`/`NOT_OWNER`, o handler **aborta sem recompensa, sem transferência e sem cooldown** — exceto na resolução de org já iniciada, que segue a regra de cancelamento do snapshot (§6).

### Multas sem saldo negativo

Multas de `/crime` e `/roubar` são limitadas ao dinheiro atual do infrator: `multaEfetiva = min(multaRolada, cashAtual)` (nunca negativa). Faltar saldo **não** anula a punição principal — **perda/destruição da arma e cooldown continuam** valendo.

## 4. Empregos (Plano 2) — `JobOutcome` puro + comandos

Cada comando (ordem/atomicidade na §3.5): `eco:enabled` → `blockedIfJailed` → cooldown ativo? (recusa, sem gravar) → ferramenta **equipada** no slot? (senão "equipe uma X no `/inventario`") → `useOnce` (confirma `USED`/`USED_AND_BROKE`; `NOT_FOUND`/`NOT_OWNER` aborta) → paga o lucro do tier → **grava o cooldown**.
- **`/minerar`** (`MINING`, cooldown 30min) → lucro `payoutMin..payoutMax` do item equipado → carteira.
- **`/cozinhar`** (`COOKING`, cooldown 30min) → idem.
- **`/entregar`** (`DELIVERY`, cooldown **15min**) → **auto-debita `fuel`** da carteira antes; se `cash < fuel` → "sem dinheiro pro combustível"; senão paga o lucro bruto (net = lucro − fuel). Cooldown menor equilibrado pelo ralo do combustível.
- Ferramenta quebra (usos→0) no meio → entrega o lucro daquela ação e avisa "sua X quebrou". `JobOutcome.reward(min,max,roll)` puro.

## 5. Crime armado (Plano 3) — modifica `/crime` e `/roubar`

Ambos: `blockedIfJailed` → exigem **arma equipada** (`WEAPON`), senão "equipe uma arma". No sucesso consomem 1 uso da arma. **Regra geral:** **toda falha criminal ativa (crime solo, roubo, org) destrói a arma equipada na hora** — sem isso, um fuzil (+40%, 2.5x) tornaria o roubo um farm de risco quase nulo (a multa é irrisória). Chance e retorno escalam com a arma; ficha suja penaliza.
- **`/crime`** (cooldown 1h): sucesso se `roll < 50 + arma.bonus − (fichaSuja?15:0)` (teto sensato ~95%). **Sucesso:** `(100..500) × arma.mult` → carteira. **Falha:** arma **destruída** + **multa 50–250**. **Sem cadeia** (crime solo não prende).
- **`/roubar @alvo`** (cooldown 2h **+ cooldown por par atacante→alvo de 12h**, anti-perseguição — guardado em `eco_cooldowns` com ação `rob:<targetId>`): sucesso se `roll < 40 + arma.bonus − (fichaSuja?15:0)`. **Carteira protegida:** o alvo nunca cai abaixo de **500** — `stealable = max(0, targetCash − 500)`; se `stealable <= 0` recusa **sem** aplicar cooldown. **Sucesso:** rouba `min((10–30% da carteira) × arma.mult, arma.robCap, stealable)` via transfer atômico. **Falha:** arma **destruída** + **multa paga ao alvo (50–200)** — **sem cadeia** (roubo solo não prende, mas perde a arma). Alvo bot/self bloqueado.
- `CrimeOutcome`/`RobOutcome` viram puros e testáveis (roll injetável): recebem `roll`, `base`, `bonus`, `fichaPenalty`, `mult` — e, no roubo, também `targetCash`, `robCap`, `protectedFloor=500` → `RobOutcome` computa o roubável como `min(rolled×mult, robCap, targetCash − floor)`. A destruição/consumo da arma é **efeito do serviço** (via `InventoryRepository.destroy`/`useOnce`), fora do resolver puro.

## 6. Crime organizado (Plano 4) — `/crimeorganizado` + `OrgCrime` puro

- **Lobby in-memory** (1 ativo por guild, padrão do giveaway/jokenpo): `/crimeorganizado` posta painel público com botão **Entrar**. Pra entrar (checagens de UX, sem gravar cooldown): `eco:enabled`, **não preso**, **arma equipada**, e ainda não participou hoje (cooldown `orgcrime` 24h). Máx **10**; o lobby expira sozinho após alguns minutos se não iniciar.
- **Revalidação no início (obrigatória — anti-exploit):** ao clicar **Iniciar**, revalida **cada** participante (livre/não preso, cooldown 24h ainda ok, **arma ainda equipada** — pode ter desequipado/quebrado/perdido depois de entrar) e **remove os inválidos**; se sobrar **<5**, recusa o início. Os válidos viram um **snapshot fixo**: dali em diante ninguém entra nem sai, e o snapshot é resolvido **em lote**. O **cooldown diário de 24h só é persistido depois** que o snapshot é válido **e** a resolução de fato acontece (após o consumo das armas, abaixo) — se o início for recusado/cancelado antes da rolagem, **ninguém** pega cooldown.
- **Chance** = `30 + média(bonus das armas) − 5×(nº de membros com ficha suja)` (teto ~90%). `OrgCrime.chance(...)` puro.
- **Pote** = `~4.500 × nº de participantes` (5→22.5k … 10→45k, dentro do envelope 20–50k; constante tunável). **Divisão por peso**: fatia de cada um ∝ `arma.mult` (fuzil 2.5x leva 2.5× a fatia de um canivete 1.0x). `OrgCrime.split(pote, weights)` puro (soma exata, sem perder moedas por arredondamento — resto pro maior peso).
- **Resolução (em lote, por-participante — segura sem transação cross-repo):** o serviço consome (sucesso → `useOnce`) / destrói (falha → `destroy`) a arma de cada participante e **age apenas sobre quem teve a arma efetivamente mutada** (`USED`/`USED_AND_BROKE` no consumo; `DESTROYED` na destruição). Quem, por corrida (rodou `/crime` e perdeu a arma no meio-tempo), devolver `NOT_FOUND`/`NOT_OWNER`/`PERMANENT` é **simplesmente excluído** desta resolução — não recebe nem é preso (não perdeu nada a mais). Se **ninguém** teve a arma mutada, cancela sem efeito. **Nunca** se diz "ninguém foi afetado" depois de já ter consumido a arma de alguém. *(A garantia ideal — transação única cruzando inventário/carteira/cooldown/cadeia com rollback — é follow-up; o v1 evita estado parcial agindo por-participante sobre o resultado real da mutação, não com "cancela tudo".)*
- **Sucesso:** o pote (`~4.500 × nº de quem participou de fato`) é dividido **por peso** entre **os que tiveram a arma consumida**; cada um recebe sua fatia + cooldown 24h.
- **Falha:** **os que tiveram a arma destruída** vão pra **cadeia 6–12h** (aleatório por pessoa) + ficha suja **se cumprirem a pena** (quem pagar `/fianca` escapa da marca) + cooldown 24h.
- **Validação de entrada única:** o **líder** (que abre o lobby) passa pelas **mesmas** checagens de entrada de quem clica Entrar — economia ligada, não preso, arma equipada, cooldown 24h livre — via um resultado explícito do `open(...)`, não um `null` mudo.
- **Lobby expira em 5 min** (obrigatório): ao expirar, sai de `lobbies` e o painel vira "lobby expirado"/botões desativados, pra não travar a guild.

## 7. Config

Gated por `eco:enabled` (checado **antes** de cooldown, inventário, cadeia e qualquer mutação). **Nenhuma config nova por-guild no v1** — catálogo e balanço em `EquipmentCatalog` + constantes em `EconomyDefaults`: cooldowns dos empregos (mineração/cozinha 30min, entrega 15min), `BAIL_BASE=15.000`, `EXPUNGE=30.000`, penalidade de ficha −15%, base do org 30%, `ORG_POT_PER_PLAYER≈4.500`, cadeia do org 6–12h, `ROB_PROTECTED_FLOOR=500`, `ROB_PAIR_COOLDOWN=12h`, `ORG_DAILY_COOLDOWN=24h` (caps de roubo por arma vêm do catálogo via `robCap`). Balance pass anotado como follow-up (a picareta de ouro subiu pra **25 usos** — era 15 — pra o retorno total não ficar abaixo da de ferro; pode ser revisitado).

## 8. Balanço (ralos vs fontes)

- **Fontes:** empregos por tier (mais tier = mais lucro), crime armado (alto risco/retorno), org (jackpot social), + `/daily`/`/trabalhar`/eventos existentes.
- **Ralos (novos, contra inflação):** compra + **rebuy** de equipamento que quebra, **combustível** por entrega, **multas**, **fiança 15k**, **limparficha 30k**, e a **perda de armas caras** (até 50k) quando pego. Alinha com o alerta anti-inflação do FUTURE-IDEAS.

## 9. UI

- **`/mercado`** — efêmero; `StringSelect` de slot → lista os itens do slot (nome, preço, usos, stat) → botão comprar. No clique, **revalida o item no catálogo E o preço atual** (nunca confia no preço renderizado na UI — protege quando o preço mudar num update futuro) + saldo. V2 container, house style.
- **`/inventario`** — efêmero; itens agrupados por slot, ordenados por `created_at`, cada linha com **`rowId` + nome + usos** e marca de equipado (ex.: `Equipar #42 — Picareta de Ferro — 17/50`). Todo controle opera **por `rowId`**, nunca por `item_key` (há múltiplas instâncias): custom id tipo `equip:<rowId>`, revalidando no clique **guild + dono + item existe + slot correto**. Componentes de inventário **só aceitam interação do usuário dono** (o efêmero já garante; regra explícita caso algum painel deixe de ser efêmero no futuro). Namespace próprio.
- **Lobby do org** — painel público (Componentes V2) com contagem, lista de participantes, botões Entrar/Iniciar.
- Comandos de ação (`/minerar` etc.) usam `Replies.reply` (temporário) pro resultado; leituras efêmeras. Emojis custom via `Emojis`.

## 10. Testes

- **Puros:** `EquipmentCatalog` (byKey/ofSlot/tier), `JobOutcome.reward`, `CrimeOutcome` (bonus/ficha/mult), `RobOutcome` (**cap + floor 500 + mult**: `min(rolled×mult, robCap, targetCash−500)`; recusa se `stealable<=0`), `OrgCrime.chance` (base+média−5%/ficha, teto) + `OrgCrime.split` (pesos, soma exata) + `OrgCrime.validParticipants(snapshot)` (filtra os inválidos), `JailService.resolve` (release-por-tempo marca ficha; fiança não marca; expunge limpa), afford de combustível.
- **Puros (cont.):** multa-piso `min(rolled, cash)` (nunca negativa); `OrgCrimeService`/serviço: cancelamento quando um consumo do snapshot falha (nenhum pagamento/cadeia/cooldown).
- **Repos (SQLite temp):** `InventoryRepository` — buy (grava `slot` do catálogo); list ordenada; **equip um-por-slot** (o unique parcial impede 2 equipados no mesmo slot); **`useOnce`** → `USED`/`USED_AND_BROKE`/`NOT_FOUND`/`NOT_OWNER`/`PERMANENT` (quebra em 0); **`destroy`** → `DESTROYED`/`NOT_FOUND`/`NOT_OWNER`; linha com `item_key` fora do catálogo ou `slot` inconsistente = inválida (não equipa/usa). `CrimeStateRepository` (jail/ficha/release upsert).
- **Views/handlers/serviços:** via build (padrão do projeto). JUnit 5 puro, sem Mockito.

## 11. Bordas / erros

- Economia off → recusa **antes de qualquer mutação/cooldown** (efêmero).
- Preso → comandos de ação recusam (menos os isentos); `/fianca` disponível; **sem cooldown**.
- Sem ferramenta/arma equipada → instrução pra equipar; **sem cooldown**.
- Cooldown ativo → informa com `<t:…:R>`.
- Combustível impagável → recusa a entrega **sem cooldown**.
- `/roubar`: alvo com carteira ≤ 500 → recusa **sem cooldown**; par atacante→alvo em cooldown de 12h → recusa; alvo bot/self → recusa.
- Org: ao iniciar, participantes inválidos (presos/sem arma/em cooldown) são **removidos**; se sobrar <5 → recusa; já participou hoje → não entra; lobby duplicado por guild → recusa. Cooldown diário gravado **só no início**.
- Ferramenta/arma quebra exatamente na ação → conclui a ação atual e avisa.
- `useOnce`/`destroy` em linha inexistente ou de outro dono (corrida com equip/venda) → resultado explícito `NOT_FOUND`/`NOT_OWNER`; o handler **aborta sem pagar nem aplicar cooldown** (fecha o exploit de pagar sem consumir).

## 12. Fora de escopo (v1)

- Config por-guild de preços/stats (catálogo é fixo).
- **Cozinha permanente** (sub-slot `COOKING_POT` de panelas que não gastam) — v1 é só utensílios consumíveis; a base já suporta via sentinela `usos_left < 0`.
- **Escalar fiança/cadeia pelo tier da arma usada** — v1 usa fiança fixa (15k) e cadeia 6–12h; ciente de que a fiança fixa pode isolar novatos que entram no lobby de um veterano (decisão de v1: "crime não compensa, cumpra a pena").
- Reembolso/venda de equipamento pelo usuário.
- Consumível de combustível estocável (é auto-debitado).
- Ficha com níveis acumulativos por usuário (é booleana; org conta nº de membros sujos).
- Câmbio com a tesouraria de facção (`fac_finance`) — permanecem separadas.
- Integração com a `/loja` de cargos (sistemas distintos; a Loja segue admin-configurável).
