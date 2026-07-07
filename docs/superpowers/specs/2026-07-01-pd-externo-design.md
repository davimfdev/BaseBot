# PD externo — registrar PD de quem já saiu do Discord

**Data:** 2026-07-01
**Módulo:** Facs (`modules/facs/commands/PdCommand`)
**Status:** Design aprovado

## Problema

`/pd` hoje exige um `usuario` (membro atual do Discord): dá kick + registra nos logs. Quando alguém toma PD no jogo e **sai sozinho do Discord** antes, não há como registrar o PD — não existe membro para selecionar. O responsável quer registrar mesmo assim, informando o **ID de jogo** + **nome no RP**.

## Decisões (do brainstorming)

- **Um só `/pd`** com campos opcionais (não subcomandos).
- Campos do PD externo: **id de jogo + nome no RP + motivo**.
- PD externo **não dá kick** (a pessoa já saiu) — só registra nos logs.
- PDs continuam **sem tabela/histórico próprio** (só `action_logs` + canais de log, como hoje).

## Comando

`/pd` — `motivo` continua obrigatório (por isso vem primeiro, já que o Discord exige obrigatórios antes de opcionais):

| Opção | Tipo | Obrigatória | Uso |
|---|---|---|---|
| `motivo` | STRING | sim | motivo do PD (ambos os fluxos) |
| `usuario` | USER | não | membro do Discord (fluxo atual) |
| `id_jogo` | STRING | não | ID de jogo (fluxo externo) |
| `nome_rp` | STRING | não | nome no RP (fluxo externo) |

## Lógica

Permissão **Punições** (`ManagerPermissions.Capability.PUNICOES`) — inalterada.

Resolução de modo (puro/testável — `PdCommand.mode(hasUsuario, idJogo, nomeRp)` → `Mode.MEMBER | EXTERNAL | INVALID`):
- `usuario` informado → **MEMBER**.
- senão, `id_jogo` e `nome_rp` ambos não-vazios → **EXTERNAL**.
- senão → **INVALID**.

Fluxos:
- **MEMBER:** comportamento atual — resolve `getAsMember`; se nulo (saiu), erro orientando usar `id_jogo` + `nome_rp`; senão checa hierarquia (`Moderation.canModerate`), dá kick com `ModReason.of(...)`, registra.
- **EXTERNAL:** sem kick, sem hierarquia. Registra direto.
- **INVALID:** erro efêmero: "Informe o **usuário**, ou o **id_jogo** + **nome_rp** (para quem já saiu)."

## Registro

Ambos os fluxos escrevem o mesmo conjunto: `action_logs.log(guildId, actorId, targetId, "PD", motivo)` + `FacsLog.post(..., "log-pds", entry)` + `FacsLog.post(..., "log-punicoes", entry)`.

- **MEMBER:** `targetId` = id do Discord; linha do membro = `{tag} · \`{id}\`` (como hoje).
- **EXTERNAL:** `targetId` = `id_jogo`; linha do membro = `{nome_rp} · id de jogo \`{id_jogo}\` · (fora do Discord)`.

Entrada externa:
```
## 💀 PD aplicado
---
👤 Membro · {nome_rp} · id de jogo `{id_jogo}` · (fora do Discord)
---
🛡️ Responsável · <@actorId>
📝 Motivo · {motivo}
```

## Tratamento de erros

- Sem permissão Punições → erro efêmero (inalterado).
- Modo INVALID → erro efêmero orientando os campos.
- `usuario` informado mas não é mais membro → erro efêmero sugerindo o fluxo externo.
- Falha no kick (MEMBER) → erro efêmero (inalterado).

## Testes

- **`PdModeTest`** (novo, puro): `mode(...)` retorna MEMBER quando há usuário; EXTERNAL quando id+nome preenchidos; INVALID quando falta (nenhum, só id, só nome, brancos).

## Fora de escopo

- Sistema de consulta/histórico de PDs (continuam sendo só entradas de log).
- Validação do formato do id de jogo (texto livre).
