# Verificação (aprovação + memória) e Canal Anti-spam — Design

Data: 2026-07-06
Módulo: Base · Segurança (`modules/base/security/`)
Status: aprovado, pronto para planejamento

## Contexto

Dois recursos para servidores privados/comprometidos:

1. **Verificação** — hoje existe só um botão "Verificar" que concede o cargo de
   membro na hora (`VerificationView`/`VerificationListener`, toggle `sec:verify`).
   Vamos expandir para: perguntas configuráveis (texto e/ou seleção de usuário),
   fila de aprovação em um canal, memória por servidor (verifica 1x) e reset quando
   a pessoa é expulsa por um moderador.
2. **Canal Anti-spam** — recurso novo. Um canal-armadilha: qualquer mensagem de
   membro ali resulta em kick automático + remoção das 10 mensagens mais recentes
   do autor em todo o servidor. O canal mantém um aviso permanente do bot.

Ambos estendem a Fase 3 (Segurança), que segue o padrão: config pura em
`SecurityConfig` (prefixo `sec:` no `guild_config`), UI em `/setup → Segurança`,
listeners em `modules/base/security/`.

## Decisões (do brainstorming)

- Memória de verificação é **por servidor** (não global).
- Formato das perguntas é **configurável por servidor**: só texto, só seleção de
  usuário, ou ambos.
- Ações do moderador na fila: **Aprovar** e **Recusar** (sem botão de expulsar).
- Punição do anti-spam: **kick** (fixo). Quantidade de mensagens apagadas: **10** (fixo).
- **Tabela de pendências** obrigatória: uma pessoa com pedido em aberto não pode
  enviar outro (anti-spam da própria fila).
- Config → **Neon (Postgres)**; DDL nova é aplicada **manualmente** pelo usuário.
  Estado operacional (verificados/pendências) → **SQLite**, migração automática.
- As perguntas ficam em uma **tabela dedicada no Neon** (`verification_questions`),
  no padrão de `quiz_questions`/`self_role_panels`. Toggles e canais reusam as
  colunas JSONB existentes de `guild_config` (sem DDL).

---

## Parte 1 — Verificação

### Configuração (`SecurityConfig`, prefixo `sec:`)

| Chave | Tipo | Default | Onde vive |
|---|---|---|---|
| `sec:verify` | toggle | `false` | `guild_config.toggles` |
| `sec:verify-userselect` | toggle | `false` | `guild_config.toggles` |
| canal de aprovação → slot `verificacao` | channel | — | `guild_config.channels` |
| cargo gate → slot `nao-verificado` (já existe) | role | — | `guild_config.roles` |
| cargo verificado → slot `membro` (já existe) | role | — | `guild_config.roles` |
| perguntas de texto | tabela | — | Neon `verification_questions` |

Regras derivadas:
- Passo de **texto** existe quando há ≥1 linha em `verification_questions` (até 5).
- Passo de **seleção de usuário** existe quando `sec:verify-userselect = true`.
- Se ambos vazios/off e `sec:verify = true`, o fluxo é o clique simples de hoje
  (Verificar → entra na fila sem respostas), mantendo compatibilidade.

### Persistência

**Neon (Postgres) — DDL manual (o usuário roda):**

```sql
-- Perguntas de verificação (Base · Segurança · Verificação). Uma linha por pergunta,
-- até 5 por guild, ordenadas por position. Editadas pelo /setup e futuramente pelo
-- dashboard; o bot lê para montar o modal de verificação. Idempotente.
CREATE TABLE IF NOT EXISTS verification_questions (
    id          TEXT PRIMARY KEY,
    guild_id    TEXT NOT NULL,
    position    INTEGER NOT NULL DEFAULT 0,
    prompt      TEXT NOT NULL,
    required    BOOLEAN NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_verification_questions_guild
    ON verification_questions (guild_id);
```

`prompt` é o label do campo (≤45 chars por limite de modal do Discord). `required`
marca se o campo do modal é obrigatório. Um repo Postgres novo
(`VerificationQuestionRepository`) faz o CRUD, seguindo `ActionTypeRepository`.

**SQLite — migração automática `038_verification.sql`:**

```sql
-- Verificação (Base security). verified_members: quem já passou (drive do auto-cargo
-- na re-entrada). verification_requests: fila de pendências, UMA por pessoa.
CREATE TABLE IF NOT EXISTS verified_members (
    guild_id    TEXT NOT NULL,
    user_id     TEXT NOT NULL,
    verified_at INTEGER NOT NULL,
    PRIMARY KEY (guild_id, user_id)
);

CREATE TABLE IF NOT EXISTS verification_requests (
    guild_id   TEXT NOT NULL,
    user_id    TEXT NOT NULL,
    message_id TEXT,
    answers    TEXT,
    created_at INTEGER NOT NULL,
    PRIMARY KEY (guild_id, user_id)
);
```

`answers` guarda o snapshot das respostas (JSON: perguntas→texto + lista de usuários
marcados) para a mensagem de aprovação sobreviver a restart. `message_id` é a
mensagem postada no canal de aprovação (para editar/apagar ao resolver).

Repo novo `VerificationRepository` (SQLite), registrado no `DatabaseManager`:
- `boolean isVerified(guild, user)` / `void markVerified(guild, user)` / `void forget(guild, user)`
- `boolean hasPending(guild, user)` / `void openRequest(guild, user, messageId, answersJson)` /
  `Optional<Request> findPending(guild, user)` / `void closeRequest(guild, user)`

### Fluxo do usuário — botão "Verificar"

O botão no painel público (`VerificationView.panel`) mantém o `ComponentId`
`sec:verify`. No handler:

1. Se `verification_requests` já tem pendência do usuário → resposta efêmera
   *"Você já tem um pedido em análise, aguarde um moderador."* Encerra (sem spam).
2. Se há perguntas de texto → abre **modal** com os campos (até 5). Sem perguntas,
   pula para o passo 3/4.
3. Ao submeter o modal (ou direto, se sem texto): se `sec:verify-userselect = true`
   → responde efêmero com um **menu de seleção de usuário** ("Marque quem você
   conhece aqui"). Ao confirmar, segue para 4. Se userselect off, segue direto.
4. **Grava a pendência** (`openRequest`) e **posta no canal `verificacao`** um
   container V2 com: candidato (menção + id), respostas de texto, usuários marcados,
   e botões **Aprovar** / **Recusar** com o `userId` no `ComponentId`
   (`sec:vapprove:<userId>` / `sec:vreject:<userId>`). Confirma ao usuário por efêmero.

Notas de implementação (limites do Discord):
- Modal e user-select **não coexistem** numa resposta; por isso o user-select é um
  segundo passo (mensagem efêmera com o menu), não parte do modal.
- Se `verificacao` não estiver configurado, o fluxo avisa o usuário e loga um alerta
  ao staff (sem canal, não há como aprovar).

### Aprovação (moderador)

Handler no `SecurityComponentHandler` (namespace `sec`):

- **Aprovar** (`vapprove:<userId>`): valida pendência; concede cargo `membro`,
  remove `nao-verificado`, `markVerified`, `closeRequest`, edita a mensagem para
  "✅ Aprovado por @mod", tenta DM/aviso ao usuário. Requer que o bot possa
  interagir com o cargo `membro`.
- **Recusar** (`vreject:<userId>`): `closeRequest` (apaga a pendência → a pessoa
  pode tentar de novo), edita a mensagem para "❌ Recusado por @mod".
- Guarda de permissão: só quem tem permissão de moderação (mesmo critério usado
  nos outros painéis de segurança/moderação) aciona os botões.

### Memória / re-entrada (o "só 1x")

`VerificationListener.onGuildMemberJoin`:
1. Se `sec:verify` off → não faz nada (como hoje).
2. Se `isVerified(guild, user)` → concede `membro` direto (se o bot pode interagir),
   **não** aplica `nao-verificado`, **não** entra na fila. Loga "auto-verificado
   (re-entrada)".
3. Caso contrário → aplica `nao-verificado` (gate), como hoje.

### Reset ao ser expulso

Novo `VerificationResetListener`:
- `GuildMemberRemoveEvent`: consulta o audit log (padrão do `NukeAuditLookup`, com
  retry curto via `scheduler().once`) por uma ação **KICK** recente contra o usuário.
  Se foi kick por um moderador → `forget(guild, user)`. Saída voluntária → mantém.
- `GuildBanEvent`: sempre `forget(guild, user)`.

Assim, quem foi expulso/banido volta a passar pela verificação; quem só saiu por
conta própria reentra já verificado.

### UI no `/setup → Segurança`

Estender `SetupView.securityScreen` e `SetupComponentHandler`:
- Toggle `sectoggle:verify` (já existe).
- Novo toggle `sectoggle:verify-userselect`.
- Botão **"Editar perguntas"** → modal para adicionar/editar/remover as perguntas
  (CRUD em `verification_questions`; máx. 5).
- Botão **definir canal** de aprovação → slot `verificacao` (usa o seletor de canal
  padrão do setup).
- Botão **"Publicar painel"** (`verifypanel`, já existe) posta `VerificationView.panel`.

---

## Parte 2 — Canal Anti-spam

### Configuração (`SecurityConfig`, prefixo `sec:`)

| Chave | Tipo | Default | Onde vive |
|---|---|---|---|
| `sec:antispam` | toggle | `false` | `guild_config.toggles` |
| canal-armadilha → slot `anti-spam` | channel | — | `guild_config.channels` |

Punição (kick) e contagem (10) são constantes no código — sem chave de config.
Reusa `sec:exempt-roles` para isenção.

### Aviso permanente (`AntiSpamView`)

Container V2 de alerta: *"⚠️ **NÃO envie mensagens neste canal.** Qualquer mensagem
aqui resulta em **expulsão automática** do servidor."* Publicado por um botão
**"Publicar aviso"** no `/setup → Segurança` (namespace `sec`, ex.: `antispampanel`).
Como toda mensagem de membro é apagada na hora, o canal fica só com o aviso; se o
aviso sumir, o bot reposta ao tratar a próxima ocorrência.

### Listener (`AntiSpamListener` em `MessageReceivedEvent`)

Dispara apenas quando `sec:antispam = true` e a mensagem está no canal `anti-spam`.

**Ignora (não pune):**
- Autor bot/webhook ou o próprio bot.
- Dono do servidor.
- Membro com permissão **Administrador** ou com cargo em `sec:exempt-roles`.

**Para os demais** (delegado a `AntiSpamService`, assíncrono):
1. **Kick** do autor (reason "Anti-spam: mensagem no canal proibido"). Só se o bot
   puder expulsar (hierarquia + permissão); senão loga falha.
2. **Purga das 10 mensagens mais recentes** do autor:
   - Itera os canais de texto onde o bot tem `MESSAGE_HISTORY` + `MANAGE_MESSAGES`.
   - Em cada canal, varre um histórico recente limitado (ex.: últimas ~100 msgs)
     e coleta as do autor.
   - Junta tudo, ordena por data desc, pega as **10 mais recentes** (inclui a que
     disparou), e apaga (bulk delete por canal quando possível; mensagens com >14
     dias caem no delete individual).
   - Varredura limitada por canal para respeitar rate-limits; roda em `queue`.
3. **Log** no canal de log de segurança/moderação (`log-seguranca`/`log-moderacao`
   conforme o padrão de resolução de canais de log): quem, quantas mensagens
   removidas, em quantos canais.

### UI no `/setup → Segurança`

- Toggle `sectoggle:antispam`.
- Botão **definir canal** → slot `anti-spam`.
- Botão **"Publicar aviso"** (`antispampanel`).

### Arquivos (Parte 2)

- `AntiSpamListener` (event → guard → service).
- `AntiSpamService` (kick + purge, assíncrono).
- `AntiSpamView` (aviso permanente).

---

## Componentes novos (resumo)

**Verificação:** `VerificationRepository` (SQLite), `VerificationQuestionRepository`
(Postgres), `VerificationResetListener`, extensões de `VerificationView`,
`VerificationListener`, `SecurityComponentHandler`, `SecurityConfig`, `SetupView`,
`SetupComponentHandler`. Migração `038_verification.sql`. DDL Neon manual
`verification_questions`.

**Anti-spam:** `AntiSpamListener`, `AntiSpamService`, `AntiSpamView`, extensões de
`SecurityConfig`, `SetupView`, `SetupComponentHandler`.

## Registro / fiação

Listeners novos (`VerificationResetListener`, `AntiSpamListener`) e o repo
`VerificationRepository` são registrados onde os demais listeners de segurança e
repos são hoje (mesmo ponto de `AntiNukeListener`/`VerificationListener` e do
`DatabaseManager`). O `VerificationQuestionRepository` (Postgres) é criado a partir
do `PostgresPool`, como `ActionTypeRepository`.

## Intents / permissões necessárias

- `GUILD_MEMBERS` (join/remove) — já usado pela verificação/anti-raid.
- `MESSAGE_CONTENT` não é necessário para o anti-spam (basta o evento de mensagem no
  canal); a purga usa histórico. `GUILD_MESSAGES` sim.
- Permissões do bot: Gerenciar Cargos (verificação), Expulsar Membros + Gerenciar
  Mensagens + Ver Histórico (anti-spam), Ver Audit Log (reset por kick).

## Testes

Seguindo a stack sem Mockito (JUnit 5 + Proxy para JDA + SQLite em memória):
- `SecurityConfig` das novas chaves (defaults, toggles).
- `VerificationRepository` contra SQLite em memória: markVerified/isVerified/forget,
  openRequest/hasPending/closeRequest, unicidade da pendência.
- Lógica de seleção das 10 mensagens mais recentes (ordenação/limite) isolada em um
  método puro testável com listas de fixtures.
- Guardas de isenção do anti-spam (bot/owner/admin/cargo isento) em método puro.

## Fora de escopo (YAGNI)

- Memória de verificação global entre servidores.
- Botão de expulsar/banir na fila de aprovação.
- Punição/contagem configuráveis no anti-spam (kick e 10 são fixos).
- Reputação/score, cooldowns além da pendência única.
