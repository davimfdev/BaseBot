# Base — Boas-vindas / Despedida / Autorole + Self-roles · Design

**Module:** Base (standalone — sem dependência de facs)
**Status:** Design aprovado (brainstorming) → pronto para o plano.
**Intents:** `GUILD_MEMBERS` (privilegiado) é necessário para os eventos de entrada/saída. Habilitar no Dev Portal (já planejado no roadmap). Self-roles é por interação e não precisa do intent.

## Visão geral
Próximo item do roadmap all-in-one (após a Fase 3 Segurança). Dois sub-sistemas relacionados, num único spec/plano:

1. **Entrada/Saída** — ao entrar, o bot manda uma mensagem de **boas-vindas** (canal e/ou DM) e atribui um **autorole**; ao sair, manda uma **despedida** opcional num canal.
2. **Self-roles** — painéis publicados onde os membros **se auto-atribuem** cargos (botões **ou** menu), com modo **exclusivo** opcional por painel.

Tudo configurável em `/setup` (duas seções novas: **Boas-vindas** e **Auto-cargos**), no estilo Components V2 + emojis custom da casa, cor do servidor, mentions suprimidas exceto onde um ping é intencional.

## Restrições globais
- **Base ≠ facs:** nada de import de código facs. O default do autorole lê a **chave-string** `sem-set` em `guild_config.roles` (configurada quando a hierarquia de facs é montada) — degrada graciosamente quando ausente.
- Components V2 em toda mensagem do bot (helper `Replies`/`Panels`); emojis via `Emojis`; logs/telas seguem a house style ([[embed-design-system]]).
- Config vive em `guild_config`; estado relacional (painéis de self-role) vai em SQLite — **anexar a migração nova em `SqliteMigrator.MIGRATIONS`** (senão nunca aplica), próximo nº livre `025`.
- Degradar em silêncio quando faltar permissão/intent (padrão dos listeners do projeto).

---

## Sub-sistema A — Entrada/Saída (boas-vindas + autorole + despedida)

### Config (`guild_config`, prefixo `welcome:`)
| Chave | Tipo | Default | O quê |
|---|---|---|---|
| `welcome:enabled` | toggle | false | liga as boas-vindas |
| `welcome:channel` | channel | — | canal de boas-vindas (vazio = não posta em canal) |
| `welcome:dm` | toggle | false | também manda a boas-vindas no DM do membro |
| `welcome:message` | setting | (modelo padrão) | texto com placeholders |
| `welcome:image` | setting | — | referência do banner no vault: `vaultChannelId:vaultMessageId` (vazio = sem imagem) |
| `welcome:autorole` | role | — | cargo atribuído no join |
| `welcome:farewell-enabled` | toggle | false | liga a despedida |
| `welcome:farewell-channel` | channel | — | canal da despedida |
| `welcome:farewell-message` | setting | (modelo padrão) | texto com placeholders |

### Placeholders — `WelcomeText` (puro, testável)
`WelcomeText.render(String template, Member member, Guild guild)` substitui:
- `{user}` → nome de exibição do membro (sem ping)
- `{mention}` → `@` ping do membro
- `{server}` → nome da guilda
- `{count}` → nº de membros da guilda (`guild.getMemberCount()`)

Modelos padrão: boas-vindas `"Bem-vindo(a) ao **{server}**, {mention}! Você é o membro **{count}**."`; despedida `"**{user}** saiu do servidor. Agora somos **{count}**."`. Função pura → unit-testada (cada token + texto sem token + nulos).

### `WelcomeListener` (`ListenerAdapter`)
- **`onGuildMemberJoin`:**
  1. Lê `GuildConfig`. Se `welcome:enabled`:
     - Renderiza o texto. Monta um container V2 (cor do servidor) com o texto; se `welcome:image` setado, `AttachmentVault.retrieveUrls(...)` → `MediaGallery` (URL fresca).
     - Se `welcome:channel` resolvido → posta no canal (mentions: só `USER`, pra o ping funcionar). Se `welcome:dm` → abre canal privado e manda o mesmo container (best-effort; ignora DM fechada).
  2. **Autorole:** resolve `welcome:autorole`; se vazio, **fallback** para `cfg.role("sem-set")`. Se houver cargo e `getSelfMember().canInteract(role)` → `addRoleToMember(...).reason("Autorole")`. Degrada em silêncio.
- **`onGuildMemberRemove`:** se `welcome:farewell-enabled` e canal resolvido → posta a despedida renderizada (sem ping — o membro já saiu).

### Imagem de boas-vindas → vault (persistência)
Modais não aceitam upload de arquivo; a imagem é informada como **URL** no modal de boas-vindas (e o pipeline de imagem existente aceita link). No save:
1. Baixa a imagem (link-or-file, conforme [[image-input-handling-rule]]).
2. `AttachmentVault.store(originGuildId, label, FileUpload, BiConsumer<vaultChannelId,vaultMessageId>)` — **novo método** no vault: resolve o canal por-guilda (reusa `resolveChannel`), envia a imagem com **content = `label`** (ex.: `"welcome-banner <guildId>"` — o "pq da imagem", em vez de id de mensagem), chama de volta com a referência.
3. Persiste `welcome:image = "<vaultChannelId>:<vaultMessageId>"`. No join, `retrieveUrls(...)` dá uma URL assinada fresca → a imagem **sempre fica disponível** (sem expirar). Sem purge, como o resto do vault (ver PROGRESS §11).

> O `AttachmentVault.archive(Message,...)` existente fica intacto; `store(...)` é a variante genérica (imagem avulsa + rótulo descritivo).

---

## Sub-sistema B — Self-roles (painéis de auto-atribuição)

### Modelo de dados (SQLite, migração `025`)
- **`self_role_panels`**: `id` (TEXT PK), `guild_id` (TEXT), `title` (TEXT), `description` (TEXT null), `style` (TEXT: `buttons`|`menu`), `unique_choice` (INTEGER 0/1), `channel_id` (TEXT null, onde foi publicado), `message_id` (TEXT null).
- **`self_role_options`**: `panel_id` (TEXT FK), `role_id` (TEXT), `label` (TEXT), `emoji` (TEXT null), `position` (INTEGER). PK (`panel_id`,`role_id`).
- **`SelfRolePanelRepository`** (SQLite): `upsertPanel`, `findPanel(id)`, `listPanels(guildId)`, `setOptions(panelId, List<Option>)`, `listOptions(panelId)`, `setPublished(panelId, channelId, messageId)`, `delete(panelId)`. CRUD unit-testado (in-memory SQLite, como `MessageArchiveRepositoryTest`).

### `SelfRoleView`
- **Painel publicado:** container V2 (cor do servidor) com título + descrição + uma `ActionRow`/`StringSelectMenu` conforme `style`:
  - `buttons` → um `Button.secondary` por opção (id `ComponentId.of("selfrole","toggle", panelId, roleId)`), com emoji se houver. Limite ~25 (5 rows × 5).
  - `menu` → um `StringSelectMenu` (id `ComponentId.of("selfrole","select", panelId)`), `min=0`, `max=` (1 se `unique`, senão nº de opções). Cada opção = cargo+label+emoji.
- **Telas de setup:** lista de painéis + editor de painel (título/descrição/estilo/exclusivo + cargos).

### `SelfRoleComponentHandler` (namespace `selfrole`)
- `onButton "toggle"`: resolve painel+cargo; se o membro tem o cargo → remove; senão → adiciona (checa `canInteract`). Se o painel é `unique`, ao adicionar remove os **outros** cargos do painel. Confirma ephemeral (`✓ Cargo X adicionado/removido`).
- `onStringSelect "select"`: os cargos selecionados = estado desejado entre os do painel. Aplica diff: adiciona os marcados que faltam, remove os do painel que não estão marcados. Confirma ephemeral. (`unique` já é garantido pelo `max=1` do menu.)
- Degrada em silêncio / mensagem clara quando o cargo está acima do bot.

---

## Integração no `/setup`
- Hub + `moduleNav`: duas entradas novas — **Boas-vindas** (`boasvindas`) e **Auto-cargos** (`autocargos`).
- **`SetupView.welcomeScreen`**: overview (enabled/canal/DM/autorole/despedida) + toggles (`enabled`, `dm`, `farewell-enabled`) + botão **Editar** (modal: mensagem, imagem URL, mensagem de despedida) + entity-selects (canal de boas-vindas, canal de despedida, cargo autorole). Default do autorole mostra `Sem Set (facs)` quando aplicável.
- **`SetupView.selfRolesScreen`**: lista de painéis (botões pra abrir cada um) + **Novo painel**. Editor: modal (título/descrição/estilo/exclusivo) + entity-select de cargos + (por opção, label/emoji via modal) + **Publicar** (posta no canal atual, padrão do painel de verificação) + **Excluir**.
- Saves editam o painel de setup (nunca criam mensagem nova) — regra `edit(event, screen)` já estabelecida.

## Tratamento de erros
- Sem intent `GUILD_MEMBERS` → os eventos de join/leave simplesmente não chegam (boas-vindas/autorole/despedida ficam inertes); self-roles continua funcionando. Documentar no `/setup → Boas-vindas` (`-#` aviso) que requer o intent.
- Falta de permissão (Gerenciar Cargos / enviar no canal / cargo acima do bot) → degrada em silêncio (join/leave) ou mensagem ephemeral clara (self-role clicado).
- Vault desabilitado/ inacessível → boas-vindas sem imagem (texto normal); nunca crasha o listener.

## Testes
- **Unitários (puros):** `WelcomeText.render` (cada placeholder, texto literal, sem placeholders), `SelfRolePanelRepository` (CRUD: upsert/find/list/options/published/delete via SQLite in-memory).
- **Listeners + JDA + vault store:** `./gradlew build` + smoke manual num servidor de teste (sem harness de unidade, como os demais listeners).

## Migrações
- `025_self_roles.sql` (as duas tabelas), **anexada** a `SqliteMigrator.MIGRATIONS`. Cuidado com o gotcha de comentário inline após `;` ([[sqlite-migration-gotchas]]).

## Decisões resolvidas (brainstorming)
- Escopo: **ambos** (boas-vindas/autorole + self-roles), um spec/plano.
- Autorole default = **`sem-set`** quando configurado (facs em uso), via chave-string, sem dependência de facs.
- Entrega das boas-vindas: **canal e/ou DM**, configuráveis.
- **Despedida:** incluída.
- Self-roles: **botões ou menu por painel**, com **exclusivo** (flag por painel, default off).
- **Imagem de boas-vindas:** suportada, **persistida no vault** (`store(...)` com rótulo descritivo no lugar do id de mensagem).
- `/setup`: **duas seções separadas**.

## Gotchas técnicos (confirmados — endereçar no plano)
1. **DMs fechadas:** `member.getUser().openPrivateChannel().queue(...)` falha com `CANNOT_SEND_TO_USER` quando o membro tem DM fechada. **Todo** `.queue()` da DM de boas-vindas precisa de callback de erro que **engole** a `ErrorResponseException` (best-effort), senão o console é inundado de stack traces a cada join. A boas-vindas em canal não é afetada.
2. **Limites de componentes (validar/capar no `/setup`):**
   - `StringSelectMenu`: **máx. 25 opções**; range via `.setMinValues(int)`/`.setMaxValues(int)` (no `unique`, `max=1`).
   - `Button`: até **5 ActionRow × 5 botões = 25 botões** por painel. Capar nº de opções no editor de painel evita `IllegalArgumentException` no envio.
3. **`guild.getMemberCount()`:** rápido; pode ser o valor em cache (levemente defasado em guildas massivas). Para `{count}` nas boas-vindas isso é perfeitamente aceitável — é o melhor caminho.

## Decisões resolvidas (pós-spec)
- **Imagem por arquivo:** v1 = **só URL** no modal (download→vault no save). Comando `/boasvindas-imagem` com `attachment` (link-or-file) fica como follow-up barato, fora do v1.

## A confirmar via `javap` no plano
- DM: `member.getUser().openPrivateChannel()` → `PrivateChannel.sendMessageComponents(...)`.
- `StringSelectMenu`: `.setMinValues/.setMaxValues`, `SelectOption.of(label, value).withEmoji(...)`.
- `Guild.getMemberCount()`, e `Member.getEffectiveName()`/`getUser().getName()` para `{user}`.
