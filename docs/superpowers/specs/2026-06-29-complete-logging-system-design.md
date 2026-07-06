# Sistema de Logging Completo — Design

**Data:** 2026-06-29
**Módulo:** Base
**Status:** Aprovado para planejamento

## Objetivo

Garantir que o bot registre, em canais de log por categoria (estilo Components V2 da
casa), todo o conjunto de eventos de servidor pedido pelo usuário — eventos nativos do
Discord (bans, kicks, mudanças de canal/cargo/servidor, etc.) **e** as próprias ações do
bot. Cada log mostra o **autor do evento** e o **alvo** (quando há). Toda ação do bot que
gere registro de auditoria deve embutir o moderador humano no `.reason()`.

Substitui o atual `GeneralLoggingListener` (único, parcial) por um conjunto de listeners
focados, sem perder nada do que já funciona.

## Princípios herdados (não mudam)

- **Um canal por categoria** de log, configurado em `/setup → Logs`, resolvido por
  `cfg.channel("<key>")` (ver `SetupLogTypes`). Canal não configurado → log é pulado em
  silêncio.
- **Todo embed é um Container V2** postado via o helper `ChannelLog.post` (menções
  suprimidas, estilo sectioned com título/divider, timestamp). Sem novo boilerplate de
  embed por call-site.
- **O bot não grava arquivos.** Anexos de mensagem são guardados como URL/metadados, nunca
  como bytes.

## 1. Arquitetura — listeners focados

O `GeneralLoggingListener` é dividido (substituído) por listeners de responsabilidade
única, todos registrados em `BaseModule` e todos postando via `ChannelLog.post`:

| Listener | Eventos cobertos | Canal(is) | Intent |
|---|---|---|---|
| `CommandLoggingListener` | execução de slash command | `log-comandos` | (default) |
| `MembershipLoggingListener` | entrou, saiu, kick, apelido, nome/global-name, cargos add/remove no membro, timeout/untimeout | `log-entradas`, `log-saidas`, `log-kicks`, `log-membros` | `GUILD_MEMBERS` |
| `BanLoggingListener` | ban / unban | `log-bans` | `GUILD_MODERATION` |
| `MessageLoggingListener` | apagada (1), deleção em massa, editada (antes→depois), fixada/desfixada | `log-mensagens` | `GUILD_MESSAGES` + `MESSAGE_CONTENT` |
| `VoiceLoggingListener` | entrar/sair/mudar call, **movido à força**, **server mute/deafen**, stream on/off, câmera on/off | `log-voz` | `GUILD_VOICE_STATES` |
| `ChannelLoggingListener` | canal criado / deletado / atualizado (nome, categoria, tópico, permissões) — texto e voz | `log-canais` | (default) |
| `RoleLoggingListener` | cargo criado / deletado / atualizado | `log-cargos` | (default) |
| `GuildLoggingListener` | nome/foto/banner do servidor; emojis/stickers add/del/rename; convites; eventos agendados; threads | `log-servidor` | `GUILD_INVITES`, `GUILD_SCHEDULED_EVENTS`, `GUILD_EXPRESSIONS` |

O comportamento atual (comandos, msg del/edit, join/leave, ban, kick, voz entrar/sair/mudar)
é preservado e migrado para os listeners acima — `GeneralLoggingListener` é removido.

## 2. Chaves de log (`SetupLogTypes`, módulo Base)

**Adicionar:** `log-mensagens`, `log-membros`, `log-canais`, `log-cargos`, `log-servidor`.
**Remover:** `log-msgdel`, `log-msgedit` (consolidados em `log-mensagens`).
**Mantém:** `log-comandos`, `log-entradas`, `log-saidas`, `log-voz`, `log-bans`,
`log-kicks`, `log-moderacao`, `log-formularios`.

Base passa a ter 13 tipos. A tela `/setup → Logs` já pagina por módulo a 8 selects/página
(`SetupLogTypes.pages(8)`), então o Base vira 2 páginas automaticamente — **sem mudança de
código de UI**, só de dados. Configs antigas em `log-msgdel`/`log-msgedit` deixam de ser
lidas (chaves órfãs no JSONB, inofensivas); o usuário reconfigura `log-mensagens` uma vez.

## 2.5 Setup rápido (auto-configuração)

Como são muitos canais, um botão **⚡ Setup rápido** na tela `/setup → Logs` faz o bot
configurar tudo sozinho: cria as categorias, cria os canais, restringe a visibilidade e
grava cada canal na chave de log correspondente. Deixa o bot "se configurar da maneira mais
eficiente para ele mesmo".

**Escopo = módulos ligados.** O bot só configura os módulos ativos *naquele bot*. Hoje os
módulos ativos são a lista hard-coded em `BotApplication.modules`; o conjunto é exposto via
`BotContext.activeModules()` (novo). Futuramente cada bot terá seus módulos conforme o plano
do cliente — o setup rápido herda esse filtro automaticamente, sem mudança. Mapeamento de
nome: `BotModule.name()` devolve `Base/Tickets/Sales/Facs`, enquanto `SetupLogTypes.module()`
usa `Base/Tickets/Vendas/Facs` → o setup rápido mapeia **`Sales` → `Vendas`** (resto idêntico).

**Padrão de nomes (fixo, pedido do usuário):**
- Categoria: `logs {modulo}` em minúsculas — `logs base`, `logs tickets`, `logs vendas`, `logs facs`.
- Canal: `📂・{log}`, onde `{log}` é a chave sem o prefixo `log-` — ex.: `📂・mensagens`,
  `📂・comandos`, `📂・voz`, `📂・membros`.

**Comportamento:**
1. Lê os módulos ativos via `ctx.activeModules()` e, para cada um, pega seus `LogType`s em `SetupLogTypes`.
2. **Categoria:** garante uma categoria `logs {modulo}` por módulo — reusa se já existir uma
   com esse nome exato; senão cria, **negando `VIEW_CHANNEL` ao `@everyone`** (canais de log
   são staff-only; os canais herdam a permissão da categoria).
3. **Canais:** para cada `LogType` cujo `cfg.channel(key)` está **vazio ou aponta para canal
   inexistente**, cria o canal `📂・{slug}` dentro da categoria e grava a chave via
   `GuildConfigEdits.withChannel(...)` + `guildConfig().save(...)`. Tipos já configurados com
   canal vivo são **pulados** → operação **idempotente** (rodar de novo não duplica).
4. **Confirmação:** re-renderiza a tela de Logs (selects já refletindo os canais criados) +
   resumo efêmero: "N canais criados em M categorias; X já estavam configurados".

**Permissões/limites:** o bot precisa de `MANAGE_CHANNELS` + `MANAGE_ROLES` (para o override
do `@everyone`). A criação de canais é **sequencial** (encadeando os `RestAction` via
`flatMap`/callbacks) para respeitar rate limit e a ordem; nunca `.complete()` na thread do JDA.

## 3. Matriz de eventos e conteúdo das embeds

Toda embed inclui autor do evento, alvo (quando há), e timestamp. Resumo do conteúdo:

**Membros (`log-membros`)**
- Apelido alterado: membro, antes → depois, responsável (audit `MEMBER_UPDATE`).
- Nome/global-name alterado: membro, antes → depois.
- Cargos atualizados: membro, cargos adicionados/removidos, responsável (audit `MEMBER_ROLE_UPDATE`).
- Timeout aplicado/removido: membro, até quando, responsável + motivo (audit `MEMBER_UPDATE`).

**Voz (`log-voz`)** — quando uma mudança de call foi **forçada por moderador** (audit
`MEMBER_VOICE_MOVE` recente), a embed de "movido por moderador" **substitui** a de "mudou
de call". Caso contrário, log normal de tráfego.
- Entrou / saiu / mudou de call (voluntário).
- Movido à força: membro, origem → destino, moderador.
- Server mute/deafen ligado/desligado: membro, moderador (audit `MEMBER_UPDATE`).
- Transmissão (stream) ligada/desligada; câmera ligada/desligada: membro, canal.

**Mensagens (`log-mensagens`)**
- 🗑️ Apagada (1): autor, canal, hora, conteúdo (do arquivo §4), anexos (URLs).
- 🧹 Deleção em massa (`MessageBulkDelete`): canal, quantidade, hora — embed distinta.
- ✏️ Editada: autor, canal, antes → depois (antes vem do arquivo §4).
- 📌 Fixada / desfixada: qual mensagem (link), canal, por quem (audit `MESSAGE_PIN`/`MESSAGE_UNPIN`).

**Canais (`log-canais`)**
- Criado: nome, tipo, categoria.
- Deletado: nome, tipo, categoria.
- Atualizado: nome, categoria, tópico/descrição, permissões — mostrando o que mudou.

**Cargos (`log-cargos`)**
- Criado / deletado: nome, cor, permissões.
- Atualizado: nome, cor, permissões, hoist/mencionável — o que mudou.

**Servidor (`log-servidor`)**
- Nome / foto / banner alterados.
- Emoji ou sticker adicionado / removido / renomeado.
- Convite criado: quem criou, qual canal, expiração, usos máximos.
- Evento agendado: criado / iniciado / cancelado / editado / removido.
- Thread/tópico: criada / deletada / arquivada / desarquivada.

**Moderação nativa**
- Ban/unban → `log-bans`; kick → `log-kicks`; ambos com moderador + motivo (audit).

## 4. Arquivo de mensagens (banco, retenção 90 dias)

Para recuperar o conteúdo de mensagens apagadas e o "antes" das editadas, o bot persiste as
mensagens recentes em SQLite (onde já vivem os `action_logs`).

**Tabela `message_archive`** (nova migração em `src/main/resources/db/sqlite/`):

| coluna | tipo | nota |
|---|---|---|
| `message_id` | TEXT PK | id da mensagem |
| `guild_id` | TEXT | |
| `channel_id` | TEXT | |
| `author_id` | TEXT | |
| `content` | TEXT | texto atual da mensagem |
| `attachments` | TEXT | URLs/metadados separados por quebra de linha (sem bytes) |
| `created_at` | INTEGER | epoch ms — base da expiração |
| `updated_at` | INTEGER | epoch ms da última edição |

Índice em `created_at` (purga) e em `(guild_id, channel_id)`.

**`MessageArchiveRepository`** (em `database/sqlite/`):
- `upsert(...)` — ao receber mensagem (`MessageReceivedEvent`, ignora bots/DM).
- `find(messageId)` — leitura no delete/edit.
- `updateContent(messageId, newContent, updatedAt)` — após logar a edição.
- `purgeOlderThan(cutoffMs)` — apaga linhas com `created_at < cutoff`.

**Fluxo:**
- Receber → `upsert`.
- Editar → `find` (antes) → log `antes → depois` → `updateContent` (novo).
- Apagar → `find` → log com conteúdo/autor/canal/hora. (linha permanece até a purga, ou é
  removida; decisão de implementação — purga garante o teto de 90d de qualquer modo.)

**Purga:** uma tarefa diária no `TaskScheduler` chama `purgeOlderThan(now - 90d)`. Segue o
padrão do `MuteService` (já roda no scheduler).

## 5. Atribuição do moderador no audit log do Discord

Como o **bot** é o ator no audit log quando executa ações, o moderador humano precisa ir
embutido na string do `.reason()`.

- **`ModReason.of(executor, motivo)`** (novo util) → `"{moderador} • {motivo}"` (ou só o
  moderador quando não há motivo). Padroniza o texto.
- Aplicar em **todos** os comandos de ação. Já têm `.reason(...)` mas sem o moderador:
  `BanCommand`, `KickCommand`, `TimeoutCommand`, `UntimeoutCommand`, `TempbanCommand`,
  `SoftbanCommand`, `MuteCommand`, `MuteCallCommand`, `UnmuteCallCommand`, `LockCommand`,
  `UnlockCommand`, `SlowmodeCommand`, `NukeCommand`, `ModerationService`, `PdCommand`.
- Faltando `.reason()` por completo (adicionar): `VoiceMoveCommand`, `AddCargoCommand`,
  `RemoveCargoCommand`, `UnmuteCommand`, `BotNickCommand`.

- **`AuditLookup`** (novo util) — generaliza o `logFromAudit` atual: dado `(guild, targetId,
  ActionType)`, busca a entrada recente (~15s) e devolve `{moderador, motivo}` para
  enriquecer logs de eventos nativos (movido à força, mute/deafen, pin/unpin, kick, ban,
  channel/role/emoji update, etc.). Requer `VIEW_AUDIT_LOG`; falha em silêncio.

## 6. Intents (`BotApplication`)

Descomentar / adicionar em `enableIntents(...)`:
- `GUILD_MEMBERS` (privilegiado) — eventos de membro, apelido, cargos, timeout.
- `MESSAGE_CONTENT` (privilegiado) — conteúdo para o arquivo de mensagens.
- `GUILD_INVITES` — eventos de convite.
- `GUILD_SCHEDULED_EVENTS` — eventos agendados.
- (`GUILD_VOICE_STATES`, `GUILD_MODERATION`, `GUILD_EXPRESSIONS`, `GUILD_MESSAGES` já ligados.)

Os dois privilegiados precisam ser habilitados pelo dono no **Discord Dev Portal**
(sem verificação abaixo de ~100 guildas). Sem eles, os listeners correspondentes
simplesmente não disparam — degrada em silêncio.

## 7. Fora de escopo (futuro)

- **Análise de imagem** (anti-ódio, pornografia, etc.) em anexos — anotado como evolução;
  não implementado agora. O arquivo de mensagens guarda só URLs de anexo, então a base para
  buscar a imagem depois já existe.

## Componentes novos (resumo)

- `modules/base/listeners/`: `CommandLoggingListener`, `MembershipLoggingListener`,
  `BanLoggingListener`, `MessageLoggingListener`, `VoiceLoggingListener`,
  `ChannelLoggingListener`, `RoleLoggingListener`, `GuildLoggingListener`
  (substituem `GeneralLoggingListener`).
- `util/`: `ModReason`, `AuditLookup`.
- `database/sqlite/`: `MessageArchiveRepository` + migração `0XX_message_archive.sql`.
- `database/`: registrar o repositório em `DatabaseManager`.
- Tarefa de purga diária (no padrão do `MuteService`/`TaskScheduler`).
- `SetupLogTypes`: novas chaves; `BaseModule`: registrar os listeners e a purga;
  `BotApplication`: intents.
- **Setup rápido (§2.5):** `BotContext.activeModules()` (+ wiring em `BotApplication`);
  `modules/base/setup/QuickLogSetup` (cria categorias/canais + grava chaves, idempotente);
  botão **⚡ Setup rápido** em `SetupView.logsPage` + caso `logquicksetup` em
  `SetupComponentHandler.onButton`.

## Verificação

- Compila (`./gradlew build`).
- Cada categoria gera log no canal certo quando configurado; pulado em silêncio quando não.
- Movido à força mostra o moderador (não o log de call normal).
- Editar mostra antes→depois; apagar mostra o conteúdo; ambos via arquivo.
- Linhas do arquivo > 90d somem após a purga.
- Ações do bot aparecem no audit log do Discord com o moderador no motivo.
- **Setup rápido** cria `logs {modulo}` + canais `📂・{log}` só para os módulos ativos,
  grava as chaves, é idempotente (rodar 2x não duplica) e deixa os canais ocultos do `@everyone`.
