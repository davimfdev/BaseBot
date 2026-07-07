# Painel `/economia` — o que fazer + cooldowns — design

**Data:** 2026-07-02
**Módulo:** Base (`modules/base/economy/`) — standalone, não depende de facs.
**Status:** Design aprovado no brainstorming (revisão do spec pendente).

## Objetivo

Um comando de leitura `/economia` que mostra ao membro, num painel só, **o que ele pode fazer agora** e **todos os cooldowns** — pra ninguém ficar perdido sobre o que existe no bot. Mostra também saldo e status criminal. Ações ainda indisponíveis aparecem **com a dica de como destravar**.

## Decisões (do brainstorming)

- **Comando:** `/economia` (efêmero). **Painel completo:** saldo + status criminal + ações com cooldown.
- **Ações bloqueadas aparecem com dica** (ex.: "equipe uma picareta no `/mercado`") — não são escondidas.
- **Sem migração, sem config nova.** Gated por `eco:enabled`.
- **Emojis:** **todos via `Emojis.of(name, fallbackUnicode)`** (regra do projeto — emojis custom do bot, nunca unicode cru). House style Components V2.

## Ações listadas + chaves de cooldown (já existentes)

| Ação | Comando | Chave cooldown | Duração | Slot exigido | Isenta na cadeia |
|---|---|---|---|---|---|
| Diário | `/daily` | `daily` | `EconomyDefaults.DAILY_COOLDOWN_S` | — | **sim** |
| Trabalhar | `/trabalhar` | `work` | `EconomyConfig.workCooldownSeconds(cfg)` | — | não |
| Minerar | `/minerar` | `minerar` | `MINE_COOLDOWN_S` | `MINING` | não |
| Cozinhar | `/cozinhar` | `cozinhar` | `COOK_COOLDOWN_S` | `COOKING` | não |
| Entregar | `/entregar` | `entregar` | `DELIVERY_COOLDOWN_S` | `DELIVERY` | não |
| Crime | `/crime` | `crime` | `CRIME_COOLDOWN_S` | `WEAPON` | não |
| Roubar | `/roubar` | `rob` | `ROB_COOLDOWN_S` | `WEAPON` | não |
| Crime organizado | `/crimeorganizado` | `orgcrime` | `ORG_DAILY_COOLDOWN_S` | `WEAPON` | não |

## Chaves de cooldown centralizadas (`EconomyCooldownKeys`) — anti-drift

Constantes num só lugar, **iguais às literais já usadas pelos serviços** (verificado no código: `EconomyService`=`daily`/`work`, `JobService`=`minerar`/`cozinhar`/`entregar`, `CrimeEconomyService`=`crime`/`rob`, `OrgCrimeService`=`orgcrime`):
```java
public static final String CD_DAILY="daily", CD_WORK="work", CD_MINE="minerar", CD_COOK="cozinhar",
        CD_DELIVERY="entregar", CD_CRIME="crime", CD_ROB="rob", CD_ORG="orgcrime";
```
O painel usa essas constantes. *(Nice-to-have futuro: refatorar os serviços pra usarem as constantes também — hoje as literais batem, então não é urgente.)*

## Resolver puro (`ActionStatus` — único ponto testado)

`enum Kind { READY, COOLDOWN, LOCKED, JAILED }`; `record ActionStatus(Kind kind, long readyAt)`.
`static ActionStatus resolve(long now, long lastTs, long cooldownS, boolean unlocked, boolean jailed, boolean exemptWhenJailed)` — **precedência**:
1. `jailed && !exemptWhenJailed` → `JAILED`.
2. `!unlocked` → `LOCKED`.
3. `now < lastTs + cooldownS*1000` → `COOLDOWN(readyAt = lastTs + cooldownS*1000)`.
4. senão → `READY`.

**`readyAt`** só é significativo em `COOLDOWN`; em `READY`/`LOCKED`/`JAILED` é **0** (o horário de saída da cadeia vem do `jailStatus`, não da ação). `CooldownRepository` guarda **ms** — `lastTs` já em ms, `cooldownS*1000` para bater.
*(Melhoria futura anotada: permitir dica extra no `LOCKED` mostrando também o cooldown — hoje "sem equipamento" vence e esconde o cooldown; v1 mantém simples.)*

## `EconomiaCommand` (efêmero)

Checa guild + membro + `eco:enabled` (senão efêmero "economia desativada"). **`long now = System.currentTimeMillis()` calculado uma vez** e reusado em todos os `resolve(...)`. Junta os dados:
- `wallet = wallets.get(g,u)`, `state = jail.resolve(g,u)` (kind + presoAte), `fichaSuja = jail.fichaSuja(g,u)`.
- Para cada slot (`MINING/COOKING/DELIVERY/WEAPON`): `Row eq = equipped(g,u,slot)` (4 leituras) → `unlocked = eq != null`; **`equippedLabel`** quando houver = `catalog.byKey(eq.itemKey()).name() + " " + eq.usosLeft() + "/" + catalog.byKey(...).maxUsos()` (ex.: `Picareta de Ferro 17/50`).
- Para cada ação: `lastTs = cooldowns.lastTs(g,u,chave)` (8 leituras) + `ActionStatus.resolve(now, lastTs, cooldownS, unlocked, jailed, exemptWhenJailed)`. Ações sem slot (`daily`/`trabalhar`) têm `unlocked=true` e `equippedLabel=null`.

Responde `EconomiaPanelView.panel(...)` efêmero.

## `EconomiaPanelView.panel(accent, member, wallet, jailStatus, fichaSuja, List<ActionLine> ações, cfg)`

`record ActionLine(String command, ActionStatus status, String unlockHint, String equippedLabel)` — `equippedLabel` = null quando a ação não usa equipamento ou nada está equipado. O comando monta a lista (uma por ação da tabela) e a view renderiza cada uma pelo `status.kind()`.

Container Components V2 (`Panels`), **emojis via `Emojis`**:
- **Cabeçalho:** `## {Emojis.MONEY 🪙} Economia de {nome}` + `Panels.divider()`.
- **Saldo:** `{Emojis.CASH 💵} Carteira · …` / `{Emojis.BANK 🏦} Banco · …` / `{Emojis.GEM 💠} Total · …` (via `EconomyFormat`).
- **Status criminal** (só se relevante): preso → `{Emojis.LOCK 🔒} Preso — sai <t:presoAte:R>. Pague /fianca pra sair já.`; senão ficha suja → `{Emojis.WARN ⚠️} Ficha suja — −15% em crimes. /limparficha limpa.`
- **Ações** (`Panels.divider()` + uma linha por ação). Se houver `equippedLabel`, ele vem no fim com `·`:
  - READY → `{Emojis.CHECK_YES ✅} /comando — disponível agora[ · {equippedLabel}]`
  - COOLDOWN → `{Emojis.HOURGLASS ⏳} /comando — <t:readyAt:R>[ · {equippedLabel}]`
  - LOCKED → `{Emojis.LOCK 🔒} /comando — {dica de desbloqueio}`
  - JAILED → `{Emojis.LOCK 🔒} /comando — preso`
- Dicas de desbloqueio: minerar→"equipe uma picareta no `/mercado`"; cozinhar→"equipe um utensílio no `/mercado`"; entregar→"equipe uma moto no `/mercado`"; crime/roubar/crime organizado→"equipe uma arma no `/mercado`".
- **Ações úteis** (`Panels.divider()`, só quando relevante): se preso → `{Emojis.KEY 🔓} /fianca — sair da cadeia por {EconomyFormat 15.000}`; se ficha suja (livre) → `{Emojis.BROOM 🧼} /limparficha — limpar a ficha por {EconomyFormat 30.000}`.
- Rodapé `-#`: "Roubo pode ter cooldown separado por alvo." sempre; + "Preso? Só `/daily` e eventos rendem." quando preso.

## Wiring

`EconomiaCommand` registrado em `BaseModule.register(...)` no bloco da economia (recebe `ctx` + o `jail` já existente). Sem handler (comando de leitura puro, sem botões).

## Observações de UX

- **`/economia` é "leitura" mas com um efeito colateral legítimo:** chamar `jail.resolve(g,u)` aplica o **release lazy** da cadeia — se a pena já terminou, marca `ficha_suja=true` e libera. Faz parte do design da cadeia; documentado aqui pra ninguém se assustar de ver uma "leitura" alterar `user_crime_state`.
- Quando há item equipado, a linha da ação mostra o **equipamento atual + usos** (`Picareta de Ferro 17/50`) — transforma o painel num status real.
- `/roubar` mostra só o cooldown **global** (`rob`); o por-alvo (`rob:<targetId>`) fica fora, com aviso discreto no rodapé (pra não parecer bug quando o global diz "disponível" mas um alvo específico recusa).
- `now = System.currentTimeMillis()` é calculado **uma vez** por execução e reusado em todos os `resolve(...)`.

## Testes

- `ActionStatusTest` — precedência: preso-não-isento vence tudo; sem equipamento vence cooldown; cooldown ativo vs expirado; isento na cadeia (daily) não fica JAILED; `readyAt` só preenchido em COOLDOWN. JUnit 5 puro.
- View/command via build (padrão do projeto).

## Fora de escopo

- Botões de atalho pra disparar as ações (é painel de leitura).
- Cooldown por-par do roubo (`rob:<alvo>`) — granular demais pro painel.
- Config nova / migração.
