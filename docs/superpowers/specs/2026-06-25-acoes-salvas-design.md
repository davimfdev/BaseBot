# Ações salvas + novo modal de `/painel-acoes`

**Data:** 2026-06-25 · **Módulo:** 4 (Facs) · **Status:** aprovado

## Objetivo

Hoje `/painel-acoes` abre um modal com dois campos livres (**Quando** texto + **Vagas**).
Trocar por um modal com **Hora**, **Data** e um **select de "ações salvas"**, onde cada ação
salva carrega `nome`, `contingente máximo`, `contingente mínimo` e `dinheiro sujo`. As ações
salvas são gerenciadas (CRUD) numa nova seção **Ações** do `/setup`, espelhando o padrão de
**Tickets** (lista → detalhe → modal de criar/editar).

## Decisões (fechadas no brainstorming)

- Gestão dos tipos: **100% no `/setup`** (sem comando `/acoes-salvas`), cadastro via **modal**.
- `máx` = capacidade da ação (`capacity`); `mín` = mínimo para alinhar (mostrado no painel,
  avisado no **Alinhamento** se não atingido).
- **Dinheiro sujo:** creditado **só na Vitória**, como item de estoque `dinheiro_sujo`
  (`EconomyRepository.addStock`). Derrota não credita.
- **Reserva Elite:** quando um Elite empurra um membro sem prioridade para a reserva, o bot
  **avisa o removido no privado** (falha silenciosa se DM fechado) — comportamento novo.
- Persistência só em **SQLite** (o domínio facs não usa Postgres).

## Dados

### Nova migração `019_action_types.sql`
```sql
CREATE TABLE IF NOT EXISTS fac_action_types (
    id             TEXT PRIMARY KEY,
    guild_id       TEXT NOT NULL,
    name           TEXT NOT NULL,
    max_contingent INTEGER NOT NULL DEFAULT 0,
    min_contingent INTEGER NOT NULL DEFAULT 0,
    dirty_money    INTEGER NOT NULL DEFAULT 0,
    created_at     TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_action_types_guild_name
    ON fac_action_types (guild_id, name);

-- Denormaliza o tipo escolhido em cada ação (histórico sobrevive a editar/remover o tipo).
ALTER TABLE fac_actions ADD COLUMN action_name    TEXT;
ALTER TABLE fac_actions ADD COLUMN min_contingent INTEGER NOT NULL DEFAULT 0;
ALTER TABLE fac_actions ADD COLUMN dirty_money    INTEGER NOT NULL DEFAULT 0;
```
Registrar o arquivo em `SqliteMigrator.MIGRATIONS` (append; nunca reordenar).

### `ActionTypeRepository` (pacote `database.sqlite`)
Espelha `ActionLogRepository`/`EconomyRepository` (SQLite). Record aninhado
`ActionType(id, guildId, name, maxContingent, minContingent, dirtyMoney)`. Métodos:
`listByGuild`, `find(id)`, `count(guildId)`, `upsert(ActionType)`, `delete(id)`.
Exposto em `DatabaseManager.actionTypes()`.

### `fac_actions` / `ActionRepository.Action`
Record ganha `actionName`, `minContingent`, `dirtyMoney`. `create(...)` passa a receber e
gravar esses campos; `capacity` recebe o **máximo**. `find()` mapeia as novas colunas.

## Telas do `/setup` (seção Ações)

- **Hub:** nova opção `addOption("Ações", "acoes", "Tipos de ação: nome, contingente e dinheiro sujo")`
  e contagem no corpo. Roteada pelo `SetupComponentHandler`.
- `SetupView.actionTypesList(accent, List<ActionType>)` → `StringSelectMenu` dos tipos
  (descrição `máx X · mín Y · sujo Z`) + **➕ Nova ação** + **◀ Voltar**.
- `SetupView.actionTypeDetail(accent, ActionType)` → campos + **✏️ Editar / 🗑️ Remover / ◀ Voltar**.
- `SetupView.actionTypeModal(id, existing)` → só TextInputs: **Nome**, **Máximo**, **Mínimo**,
  **Dinheiro sujo** (números validados no submit; reusa valores no editar).

Custom-ids (ns `setup`): `actionnew` (botão), `actiontype` (string select), `actionedit:<id>`,
`actiondel:<id>` (botões), `actionform:<id|new>` (modal), `nav:acoes`. Espelha exatamente o
fluxo de tickets em `SetupComponentHandler`. Removendo um tipo → log `ACTION_TYPE_DELETE`,
salvando → `ACTION_TYPE_SAVE`.

## Modal de `/painel-acoes`

`ActionView.createModal(List<ActionType> types)`:
- **Hora** — TextInput, placeholder `HH:mm`, obrigatório.
- **Data** — TextInput, placeholder `dd/mm/yy · dd/mm · dd/mm/yyyy`, obrigatório.
- **Ação** — `StringSelectMenu` (`acao`) dentro de `Label`, 1 opção por tipo (label = nome,
  desc = `máx X · mín Y · sujo Z`, value = id). Lido no submit com `getValue("acao").getAsStringList()`.

`PainelAcoesCommand.execute`: carrega `ctx.database().actionTypes().listByGuild(guildId)`; se
vazio, responde efêmero pedindo para cadastrar em `/setup → Ações` (modal não pode ter select
vazio); senão abre o modal.

## Fluxo de criação (`ActionService.create`)

1. Lê `hora`, `data`, tipo selecionado.
2. Parse: `hora` `HH:mm` (00–23 / 00–59); `data` em 3 formatos (`yy`→`20yy`, sem ano → ano atual).
   Inválido → erro efêmero, não cria.
3. Resolve o tipo (`actionTypes().find(id)`); ausente → erro efêmero.
4. `when_text = "dd/MM/yyyy HH:mm"`; grava `capacity = max`, `action_name`, `min_contingent`,
   `dirty_money`.

## Painel, Alinhamento e DM

- **Painel:** título = nome da ação; linha `Confirmados (n/máx)`; mostra o **mínimo** com aviso
  visual se `confirmados < mín`.
- **Alinhamento:** se `confirmados < mín`, prefixa aviso (`⚠️ Abaixo do mínimo (n/mín)`) antes do ping.
- **Bump Elite (novo):** ao empurrar um membro sem prioridade para a reserva, DM ao removido
  (`retrieveUserById → openPrivateChannel → sendMessage`, falha silenciosa).

## Dinheiro sujo

`ActionService.result`, no caso **VICTORY** e `dirtyMoney > 0`:
`economy.addStock(guildId, "dinheiro_sujo", dirtyMoney)` + log. Injetar `EconomyRepository` no
`ActionService` (reusar a instância já criada no `FacsModule`).

## Arquivos tocados

- **Novo:** `019_action_types.sql`, `database/sqlite/ActionTypeRepository.java`.
- **Edit:** `SqliteMigrator`, `DatabaseManager`, `SetupView`, `SetupComponentHandler`,
  `ActionView`, `ActionRepository`, `ActionService`, `PainelAcoesCommand`, `FacsModule`.

## Atualização (alinhamento à BOTSPECS detalhada)

Após a versão detalhada da BOTSPECS, o fluxo de ações foi expandido (migração `020_actions_detail.sql`):

- **`/painel-acoes`** agora posta um **painel de gestão** (botões **Registrar ação** / **Editar ações**), não um modal direto.
- **Registrar ação:** escolhe a ação salva → escolhe **"já aconteceu"** ou **"vai acontecer"**.
  - Futura → modal de Hora/Data; embed postada no **canal de escalações** (`acoes-escalacoes`).
  - Passada → modal com **User Select** dos participantes; embed só com Vitória/Derrota.
- **Canais** (`/setup → Ações`): **escalações** e **alinhamentos** (`acoes-escalacoes`, `acoes-alinhamentos`), salvos em `guild_config.channels`.
- **Painel:** mostra o **mínimo primeiro** e passa a mostrar o **máximo** após atingir o mínimo.
- **Configurar:** adicionar/remover membros, **liberar/bloquear entradas** (`entries_open`), **mudar horário** (`due_at`), e Vitória/Derrota/Encerrar.
- **Alinhamento** é postado no canal de alinhamentos (ping real).
- **Scheduler** (1 min): revela Vitória/Derrota quando o horário da ação agendada chega (`due_notified`).

## Fora de escopo

Coluna `dirty_balance_cents` no painel financeiro, edição de horário pós-criação, lavagem do
dinheiro sujo (item de estoque fica pronto para integrar depois).
