# Fase 3 — Segurança (AutoMod + Anti-raid + Verificação + Anti-nuke) · Design

**Data:** 2026-06-30
**Fase do roadmap:** 3 (Segurança). Standalone na **Base** — não depende de facs.
**Status:** design aprovado; planos de implementação serão escritos por módulo (separados).

## Objetivo
Proteção nativa e proativa do servidor, integrada ao sistema de **Infrações** já existente: o servidor se defende sozinho de spam, links/menções abusivas, raids, abuso de admin (nuke) e entradas não verificadas — com tudo configurável em `/setup → Segurança` e cada ação virando um caso no modlog.

## Princípio central
**Usar o AutoMod NATIVO do Discord** para conteúdo de mensagem (o bot *configura as regras* da guilda e *reage* aos bloqueios), em vez de ler toda mensagem com RegEx (custo de CPU + dependência de `MESSAGE_CONTENT`). O bot só processa o **evento de execução** que o Discord já entrega.

## Escopo (4 subsistemas) — grande demais para um plano único
Este documento é o **design guarda-chuva**. Cada módulo abaixo terá seu próprio plano de implementação, construído **nesta ordem**:

1. **AutoMod nativo** (núcleo; já integra com o motor de warns)
2. **Anti-raid** (lockdown + alerta)
3. **Verificação** (gate de entrada; casa com o anti-raid)
4. **Anti-nuke** (audit log; o mais isolado)

**Fora de escopo:** captcha/verificação por imagem (a verificação é só um botão), scanning custom por RegEx (delegado ao AutoMod nativo), música.

---

## Infra compartilhada

### Configuração (`/setup → Segurança`)
Nova seção no `/setup` (entrada "Segurança" no hub e no `moduleNav`). Estado em `guild_config` (`settings`/`toggles`), prefixo `sec:`, lido/escrito por uma classe `SecurityConfig` (parse/serialização puros e testados, espelhando `ModerationConfig`).

Chaves:
- **AutoMod:** `sec:automod` (toggle), `sec:automod-warn` (toggle: gerar warn na violação), `sec:automod-warn-per` (int: violações por warn, **default 1**), `sec:automod-window-s` (int: janela p/ o modo "X em Y", default 0 = imediato), `sec:automod-mention-limit` (int, default 5), `sec:automod-keywords` (CSV custom), `sec:automod-block-invites` (toggle).
- **Anti-raid:** `sec:antiraid` (toggle), `sec:antiraid-joins` (default 8), `sec:antiraid-window-s` (default 10), `sec:antiraid-min-age-days` (default 7), `sec:antiraid-lock-level` (nível de verificação no lockdown, default `HIGH` — ver gotcha #4).
- **Verificação:** `sec:verify` (toggle); cargos via chaves já existentes — `membro` (cargo de verificado, reusa o de `/setup → Cargos`) + novo slot `nao-verificado`.
- **Anti-nuke:** `sec:antinuke` (toggle), `sec:antinuke-max` (default 5), `sec:antinuke-window-s` (default 60), `sec:antinuke-whitelist` (CSV de `role:<id>`/`user:<id>`; o dono é sempre isento).
- **Isenções globais:** `sec:exempt-roles` (CSV role ids) e `sec:exempt-channels` (CSV channel ids) — imunes ao AutoMod e não contam nas detecções.

### Integração com Infrações (já existente)
Toda punição automática passa por **`ModerationService.warn(guild, member, "system", reason)`** (registra o caso WARN + DM + modlog + **escalonamento** automático `3=timeout:1h,5=ban`, conforme `/setup → Moderação`). Nada de nova lógica de punição: a segurança só *gera os warns*. Alertas operacionais (raid/nuke) vão pro modlog (`log-moderacao`) via `ChannelLog`.

### Intents/permissões
- `AUTO_MODERATION_EXECUTION` + `AUTO_MODERATION_CONFIGURATION` (verificar nomes exatos no JDA 6.4.2 na fase de plano).
- `GUILD_MEMBERS` (join/anti-raid/verificação — já habilitado), `GUILD_MODERATION` (já habilitado).
- Permissões do bot na guilda: **Gerenciar Servidor** (criar regras de AutoMod + nível de verificação), **Gerenciar Cargos** (verificação/anti-nuke), **Ver Registro de Auditoria** (anti-nuke). Degradar em silêncio quando faltar.

---

## Módulo 1 — AutoMod nativo

**Componentes:**
- `AutoModManager` — no `onReady`/`onGuildReady` e ao salvar a config, **sincroniza** as regras de AutoMod da guilda com a config. Cria/atualiza regras com prefixo de identidade (ex.: `BaseBot · Spam`) via `Guild.createAutoModRule(AutoModRuleData)`; remove as que foram desligadas. Regras geradas:
  - **Anti-spam** → trigger `SPAM`, resposta `BLOCK_MESSAGE`.
  - **Mention spam** → `MENTION_SPAM` com `mentionLimit = sec:automod-mention-limit`.
  - **Links/convites** → `KEYWORD` com padrões de convite (`discord.gg/*`, `discord.com/invite/*`) + (se `block-invites`) e padrões de link suspeito; mais a lista `sec:automod-keywords`.
  - **Palavrões** → `KEYWORD_PRESET` (profanidade/sexual/insultos) + custom keywords.
  - Em todas: `exemptRoles`/`exemptChannels` = isenções globais.
- `AutoModExecutionListener` — `onAutoModExecution`: se o usuário não é isento e `sec:automod-warn` está ligado → conta a violação numa **janela deslizante por usuário** (em memória); ao atingir `warn-per` dentro de `window-s` (default 1/imediato) → `ModerationService.warn(...)` com motivo `AutoMod: <trigger> — "<matchedKeyword/Content>"`. Sempre posta um resumo no modlog.

**Dados/Interfaces:**
- `record AutoModRuleSpec(...)` puro (descreve cada regra) → testável sem JDA.
- Contador de violações: `ViolationWindow` (mapa `userId → timestamps`), puro/testável.
- Consome: `SecurityConfig`, `ModerationService.warn`, `ChannelLog`.

**Decisão (default escolhido):** `warn-per = 1` (cada violação = 1 warn). O modo "X em Y min" fica disponível (mexendo nas chaves) para suavizar depois — útil porque o AutoMod bloqueia *cada* mensagem que casa, então 1/violação escala rápido.

---

## Módulo 2 — Anti-raid

**Componentes:**
- `AntiRaidListener` — `onGuildMemberJoin`: registra o timestamp da entrada numa **janela deslizante por guilda** (memória). Se entradas-na-janela ≥ `sec:antiraid-joins` em `window-s` → dispara **lockdown** (uma vez; com cooldown pra não repetir). Conta de idade < `min-age-days` reforça a detecção.
- **Lockdown:** sobe o nível de verificação da guilda para `sec:antiraid-lock-level` (default `HIGH`; `VERY_HIGH` exige telefone — ver gotcha #4), guardando o nível anterior em `sec:antiraid-prev-level` para restaurar. Alerta no modlog com botão **"Desativar lockdown"** (restaura o nível). (Opcional, em iteração futura: negar `MESSAGE_SEND` do `@everyone` em canais selecionados.)
- `AntiRaidComponentHandler` — botão de desativar o lockdown.

**Dados/Interfaces:** `JoinWindow` (puro/testável). Consome `SecurityConfig`, `ChannelLog`, JDA guild manager.

**Defaults:** 8 entradas/10s; conta < 7 dias. Resposta = **lockdown + alerta** (não auto-kick/ban — evita falso-positivo).

---

## Módulo 3 — Verificação

**Componentes:**
- `VerificationListener` — `onGuildMemberJoin` (se `sec:verify`): atribui o cargo `nao-verificado`. (O membro só ganha o cargo `membro` ao verificar.)
- **Painel de verificação:** publicado num canal por um botão em `/setup → Segurança` ("Publicar painel"), espelhando o painel de tickets. Contém um botão **Verificar**.
- `VerifyComponentHandler` — botão "Verificar": concede o cargo `membro`, remove `nao-verificado`, responde efêmero.

**Sinergia:** com o lockdown ativo, a verificação é o gate efetivo (nível de verificação alto exige a ação). Reusa os slots de cargo do `/setup → Cargos` (`membro`) + o novo `nao-verificado`.

**Defaults:** botão simples (sem captcha).

---

## Módulo 4 — Anti-nuke

**Componentes:**
- `AntiNukeListener` — escuta eventos destrutivos (canal/cargo **deletado**, **ban**, **kick**, webhook criado, remoção de cargos altos). Para cada, resolve o **ator** no audit log (helper estilo `AuditLookup`) e conta ações por ator numa **janela** (memória). Se o ator (não-isento, não-dono) passar de `sec:antinuke-max` em `window-s` → **neutraliza**: remove os cargos do ator + alerta os donos (ping/DM) no modlog. (Não desfaz as ações já feitas — fora de escopo; foco é parar o sangramento.)
- Whitelist: dono sempre isento + `sec:antinuke-whitelist`.

**Dados/Interfaces:** `ActorWindow` (puro/testável). Consome audit log, `ChannelLog`, guild manager.

**Defaults:** > 5 ações destrutivas/60s → remove cargos + alerta.

---

## Tratamento de erros
- Falta de permissão/intent → degrada em silêncio (log interno), nunca crasha o listener (padrão do projeto: cada handler em try/catch no `ComponentRouter`/`CommandManager`).
- Sincronização de regras de AutoMod idempotente (reusa regra existente pelo nome; não duplica).
- Janelas em memória: aceitável perder o estado no restart (são janelas de segundos); nada que precise de persistência aqui (≠ rastreamento de voz da fase 7).

## Testes
- **Unitários (puros):** `SecurityConfig` (parse/serialize de cada chave), `ViolationWindow`/`JoinWindow`/`ActorWindow` (contagem em janela), checagem de isenção, e o `AutoModRuleSpec` (mapeamento config → regras).
- **Listeners/JDA + sync de regras nativas:** validados por `./gradlew build` + smoke test manual num servidor de teste (sem harness de unidade, como os demais listeners).

## Migrações
Sem tabela nova prevista (config vive em `guild_config`). Se algum módulo precisar de estado persistente, **anexar** o `.sql` em `SqliteMigrator.MIGRATIONS` (senão nunca aplica) — próximo número livre é `025`.

## Decisões em aberto (resolver no plano de cada módulo)
- Nome exato dos intents de AutoMod no JDA 6.4.2 e do `AutoModRuleData`/`AutoModResponse`/`setMentionLimit` (confirmar via `javap`).
- Se o lockdown deve também trancar `MESSAGE_SEND` do `@everyone` (iteração 2) ou só subir o nível de verificação (v1).
- Onde guardar o "nível de verificação anterior" para restaurar (setting `sec:antiraid-prev-level`).

## ⚠️ Pontos de atenção (gotchas) na implementação
Riscos conhecidos do Discord que cada plano de módulo DEVE tratar:

1. **Anti-nuke × delay do Audit Log.** O evento destrutivo chega pelo WebSocket quase instantâneo, mas o registro de *quem* fez no Audit Log pode atrasar alguns ms (ou mais, sob instabilidade). O helper de lookup do anti-nuke **não pode** fazer um único `retrieveAuditLogs` e desistir — precisa de **retry curto** (ex.: tentar de novo após ~300–800ms, 2–3 vezes) antes de concluir que não achou o autor. *(Diferente do `AuditLookup` atual, que faz uma busca única — o anti-nuke precisa de uma variante com retry.)*
2. **Hierarquia de cargos no anti-nuke.** O bot só consegue neutralizar (remover cargos de) um ator se **o cargo mais alto do bot estiver acima do cargo mais alto do ator** (`self.canInteract(target)`). Contra um dono/admin-supremo acima do bot, ele é impotente. Documentar essa limitação no `/setup → Segurança` e **alertar a staff** quando o anti-nuke detectar um ator que o bot não consegue tocar (em vez de falhar silenciosamente).
3. **Limites do AutoMod nativo.** O Discord impõe limites rígidos por regra: tamanho da lista de keywords, quantidade de padrões e complexidade do RegEx permitido. Ao montar `sec:automod-keywords`, **validar/capar** a lista (rejeitar excesso e padrões inválidos) e dividir em múltiplas regras se necessário — senão o `createAutoModRule` falha. Confirmar os limites atuais na fase de plano.
4. **Anti-raid × `VERY_HIGH`.** Subir o nível de verificação ao máximo exige **telefone verificado** dos novos membros — ótima defesa contra bots, mas frustra usuários reais que tentam entrar durante o lockdown. Por isso: tornar o **nível de lockdown configurável** (`sec:antiraid-lock-level`, default `HIGH` em vez de `VERY_HIGH`), e deixar claro no alerta que o lockdown está ativo + por quanto tempo / como reverter.

## Ordem de entrega
Plano + implementação por módulo, na ordem 1→4. Specs/plans em `docs/superpowers/`. Cada módulo é independente o suficiente para shippar sozinho atrás de seu toggle em `/setup → Segurança`.
