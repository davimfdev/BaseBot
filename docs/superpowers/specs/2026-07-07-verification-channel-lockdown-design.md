# Lockdown de canais na verificação — Design

**Data:** 2026-07-07
**Branch:** feat/dashboard-config-a1
**Contexto:** Extensão da feature de Verificação (fila + memória por servidor). Servidores privados
querem que, ao ligar a verificação, o servidor fique fechado para quem ainda não é membro.

## Objetivo

Quando a verificação é **ligada** num servidor, todos os canais que **não** são de log e que
estavam **abertos** para `@everyone` devem passar a ficar invisíveis para `@everyone` e visíveis
para o cargo de membros. Ao **desligar**, reverter — mas apenas os canais que continuam exatamente
no estado que o bot configurou.

## Comportamento

### Ao LIGAR (toggle off→on no `/setup`, ou botão manual "Esconder canais")

Para cada canal do servidor:

1. Pular se for **excluído** (ver abaixo).
2. Pular se já estiver **fechado** para `@everyone` (não estava "aberto").
3. Caso contrário: **negar** `VIEW_CHANNEL` para `@everyone` e **conceder** `VIEW_CHANNEL` para o
   cargo `membro`.

### Ao DESLIGAR (toggle on→off, ou botão manual "Reabrir canais")

Para cada canal do servidor:

1. Pular se for **excluído** (mesmo conjunto do apply: logs + verificação + anti-spam). Logs,
   verificação e anti-spam **nunca são revertidos**, assim como nunca são escondidos.
2. Reverter **somente** se o canal tiver a **assinatura de lockdown**: `@everyone` com `VIEW_CHANNEL`
   **negado** **E** o cargo `membro` com `VIEW_CHANNEL` **permitido**. A reversão limpa (`clear`) o
   componente `VIEW_CHANNEL` dos dois overrides, devolvendo o canal ao estado herdado (aberto).
   Canais cuja assinatura não bate ficam **intactos**.

A reversão é **stateless**: a própria permissão é o marcador, então não há snapshot no banco.

> **Honestidade sobre a assinatura:** ela é uma **assinatura mínima compatível com o lockdown do
> bot**, não prova de que o bot foi quem configurou. Sem snapshot é impossível distinguir um canal
> que o bot escondeu de um que o admin configurou manualmente com `@everyone` negado + `membro`
> permitido — ambos seriam considerados reversíveis. Por isso os canais excluídos (logs/verificação/
> anti-spam) são protegidos **nos dois sentidos**, reduzindo o falso positivo mais provável (um log
> configurado manualmente com essa mesma assinatura).

## Definições (tudo já existe no código)

- **Aberto** = `@everyone` **não** tem `VIEW_CHANNEL` negado no override do canal.
  Detecção: `ch.getPermissionOverride(everyone)` é `null` ou não contém `VIEW_CHANNEL` em `getDenied()`.
- **Canais de log** = ids presentes em `guild_config.channels` sob qualquer uma das 25 chaves de
  `SetupLogTypes.ALL` (`log-*`). Resolvidos via `cfg.channel(key)`.
- **Excluídos sempre** (nunca escondidos):
  - todos os canais de log;
  - `cfg.channel(SecurityConfig.CHANNEL_VERIFY)` (`"verificacao"`) — precisa continuar visível ao
    não-verificado, senão ninguém consegue se verificar;
  - `cfg.channel(SecurityConfig.CHANNEL_ANTISPAM)` (`"anti-spam"`) — canal-isca, fica como está;
  - canais já fechados para `@everyone`.
- **Cargo de membros** = `cfg.role("membro")`. Se **não** estiver configurado, o sweep **aborta**
  sem tocar em nada e avisa o admin (senão esconderia todos os canais de todo mundo).
- **Alcance** = `guild.getChannels()` — inclui categorias e todos os tipos que implementam
  `IPermissionContainer`. Categorias entram no sweep; canais sincronizados herdam.

## Componentes

### 1. `VerificationLockdown` (nova classe em `modules.base.security`)

Serviço com a lógica de esconder/reverter. Roda as edições de permissão de forma **síncrona** com
`.complete()` numa thread do executor (mesmo padrão de `QuickLogSetup.run`), portanto **nunca** deve
ser chamado direto da thread do gateway/interação — sempre via `ctx.scheduler().executor()`.

```java
public final class VerificationLockdown {
    public record Summary(int changed, int skipped, boolean memberRoleMissing) {}

    // Efeitos colaterais JDA (não unit-testado):
    public static Summary apply(BotContext ctx, Guild guild);   // esconde
    public static Summary revert(BotContext ctx, Guild guild);  // reabre os de assinatura correspondente

    // Helpers PUROS (unit-testados). Aceitam Collection<Permission> (o JDA devolve EnumSet,
    // mas Collection é mais flexível) e tratam null como conjunto vazio:
    public static Set<String> excludedChannelIds(GuildConfig cfg); // logs + verificacao + anti-spam (ignora nulos, sem duplicatas)
    public static boolean isOpenForEveryone(Collection<Permission> everyoneDenied);
    public static boolean matchesLockdownSignature(Collection<Permission> everyoneDenied,
                                                   Collection<Permission> memberAllowed);
}
```

Regras internas:

- `apply`: resolve `membro` (`cfg.role("membro")` → `guild.getRoleById`); se ausente, retorna
  `Summary(0, 0, true)`. Monta o conjunto de excluídos via `excludedChannelIds`. Para cada
  `GuildChannel` que seja `IPermissionContainer`, não excluído, aberto e que o bot possa gerenciar
  (`getSelfMember().hasPermission(ch, MANAGE_PERMISSIONS)`): `upsertPermissionOverride(everyone).deny(VIEW_CHANNEL).complete()`
  e `upsertPermissionOverride(membro).grant(VIEW_CHANNEL).complete()`; incrementa `changed`. Canais
  pulados por permissão/erro contam em `skipped`.
- `revert`: resolve `membro`; se ausente, `Summary(0,0,true)` (não há como casar a assinatura).
  Monta **o mesmo conjunto de excluídos** e pula esses canais **antes** de checar a assinatura. Para
  cada canal não-excluído cuja assinatura casa (`matchesLockdownSignature`): limpa `VIEW_CHANNEL` dos
  overrides de `@everyone` e de `membro` (`upsertPermissionOverride(role).clear(VIEW_CHANNEL).complete()`);
  incrementa `changed`.
- Cada `.complete()` é protegido; falha de um canal vira `skipped` e o sweep continua.
- **Detecção de estado** usa `ch.getPermissionOverride(role)` (pode ser `null`) — quando `null`,
  passa-se um conjunto vazio aos helpers (`getDenied()`/`getAllowed()` do override, ou `Set.of()`).

### 2. `SetupView.verificationScreen`

- Ganha uma nova `ActionRow` com dois botões:
  - **"Esconder canais"** — `ComponentId.of(NS, "verifylock")`.
  - **"Reabrir canais"** — `ComponentId.of(NS, "verifyunlock")`.
- **Banner de aviso persistente:** quando `verify(cfg)` está ligado **e** `cfg.role("membro")` é
  `null`, exibe uma linha de aviso no painel: "⚠️ Cargo *membro* não configurado — canais não serão
  escondidos." Assim o admin que ligou pelo toggle vê o motivo de nada ter acontecido.

### 3. `SetupComponentHandler`

- **`sectoggle:verify`** (já existente): após `guildConfig().save(updated)`:
  - Se o novo estado é `on` **e** `cfg.role("membro")` existe → agendar `VerificationLockdown.apply`
    no executor. Se `membro` **não** existe → **não** agenda; o banner da sub-tela já comunica o motivo.
  - Se o novo estado é `off` → agendar `VerificationLockdown.revert` no executor.
  - A re-renderização da sub-tela continua imediata; o sweep roda em background. Ao terminar, o sweep
    registra `VERIFY_LOCKDOWN` / `VERIFY_UNLOCK` em `actionLogs` com `changed`/`skipped` — como é
    background e o admin pode só togglar, o log é a evidência do resultado.
- **`verifylock` / `verifyunlock`** (novos): `event.deferEdit()`, rodar `apply`/`revert` no executor,
  depois `editOriginalComponents(verificationScreen)` e um **resumo** ("`X` canais escondidos / `Y`
  pulados", ou "Configure o cargo *membro* primeiro." quando `memberRoleMissing`). Registrar em
  `actionLogs` (`VERIFY_LOCKDOWN` / `VERIFY_UNLOCK`).
  - **Entrega do resumo:** preferir follow-up efêmero via `getHook().sendMessage(...).setEphemeral(true)`
    após o `deferEdit`. Se, na versão de JDA do projeto, um follow-up efêmero não for possível após
    `deferEdit`, o resumo aparece temporariamente no próprio painel (linha no topo da
    `verificationScreen`). A escolha exata fica para o plano de implementação, conforme o build.

### 4. Teste — `VerificationLockdownTest`

JUnit 5 puro (sem Mockito), cobrindo os helpers puros:
- `excludedChannelIds` inclui os ids de log configurados + verificação + anti-spam, **sem duplicatas**
  e **ignorando** chaves nulas/ausentes.
- `isOpenForEveryone`:
  - `true` com conjunto negado vazio;
  - `true` com negados que **não** contêm `VIEW_CHANNEL` (ex.: só `MESSAGE_SEND`);
  - `false` quando `VIEW_CHANNEL` está negado;
  - `true` para `null` (tratado como vazio).
- `matchesLockdownSignature`:
  - `true` só quando `@everyone` nega `VIEW_CHANNEL` **e** `membro` permite `VIEW_CHANNEL`;
  - `false` quando `@everyone` nega mas `membro` não permite;
  - `false` quando `membro` permite mas `@everyone` não nega;
  - `false` para `(null, null)`.

Efeitos JDA (`apply`/`revert`) não são unit-testados — consistente com o restante do módulo
(`AntiSpamService.selectMostRecent` é o único trecho puro testado; o resto é side-effect JDA).

## Fluxo de dados

```
toggle sectoggle:verify  ──┐
botão verifylock/unlock  ──┤→ ctx.scheduler().executor()
                            │      └→ VerificationLockdown.apply/revert(guild)
                            │             └→ itera guild.getChannels()
                            │                   └→ upsertPermissionOverride(...).complete()  (best-effort)
                            │             └→ Summary(changed, skipped, memberRoleMissing)
                            └→ editOriginalComponents(verificationScreen) + resumo efêmero + actionLog
```

## Tratamento de erro / bordas

- **`membro` não configurado** → nada é alterado; aviso "Configure o cargo *membro* primeiro."
- **Bot sem `MANAGE_PERMISSIONS` no canal** → pula o canal, conta em `skipped`.
- **Bot sem Administrador** → ao negar `VIEW_CHANNEL` para `@everyone`, o próprio bot pode perder
  acesso ao canal (a menos que tenha um cargo com View acima). **Documentar:** recomenda-se o bot com
  Administrador. O sweep não concede View ao bot automaticamente (YAGNI; a maioria dos setups dá Admin).
- **Ligar/desligar pela dashboard** (escreve direto no Neon, sem hook no bot) **não** dispara o sweep.
  Por isso os botões manuais existem — o admin reexecuta quando quiser.
- **Idempotência:** rodar `apply` duas vezes não muda nada além do já feito (canais já fechados são
  pulados); rodar `revert` duas vezes idem (assinatura já não casa após a primeira).
- **Categorias e canais sincronizados:** como o sweep itera `guild.getChannels()`, ele processa a
  categoria **e** os canais. Um canal que estava "aberto" apenas por herdar da categoria recebe um
  override próprio explícito mesmo se a categoria também for fechada — ou seja, o sweep pode criar
  overrides redundantes. **Aceitável no v1** pela simplicidade e idempotência; não tentamos detectar
  sincronização de categoria. (Alternativa mais limpa — fechar só a categoria e pular canais
  sincronizados — fica fora de escopo.)
- **`clear(VIEW_CHANNEL)` pode deixar override vazio:** se o override só tinha `VIEW_CHANNEL`, após o
  revert ele fica sem allow/deny. Isso **não quebra** nada (só "lixo visual"). No v1 **não** deletamos
  o override — apagá-lo seria arriscado quando há outros componentes. Aceita-se o override vazio.
- **API de permissão do bot:** `guild.getSelfMember().hasPermission(channel, Permission.MANAGE_PERMISSIONS)`
  — confirmar no plano/implementação o tipo exato aceito na versão de JDA do projeto
  (`GuildChannel`/`IPermissionContainer`) e ajustar conforme o build.

## Fora de escopo (YAGNI)

- Snapshot/persistência do estado anterior dos canais (a reversão por assinatura dispensa).
- Conceder View ao bot automaticamente.
- Disparo automático a partir de mudanças feitas pela dashboard.
- Aplicar lockdown a canais criados **depois** de ligar (o botão manual cobre esse caso).

## Ajustes de segurança (resumo)

Consolidação dos pontos que endurecem o comportamento (todos já refletidos acima):

- `revert` também pula `excludedChannelIds(cfg)` antes de checar assinatura. Logs, verificação e
  anti-spam **nunca** são reabertos pelo sweep — proteção nos dois sentidos.
- A "assinatura" é stateless e baseada só em `VIEW_CHANNEL` (`@everyone` negado + `membro` permitido).
  Sem snapshot, um canal configurado manualmente com a mesma assinatura também seria revertível; por
  isso os excluídos são protegidos nos dois sentidos.
- No toggle automático, se o cargo `membro` não estiver configurado, o lockdown **aborta** e o admin
  recebe aviso claro (banner persistente na sub-tela). A verificação pode continuar ligada, mas
  canais não são escondidos.
- O resumo dos botões manuais é efêmero quando a API permitir; caso contrário, é renderizado no
  próprio painel.
- Overrides podem ficar vazios após o revert e overrides redundantes podem surgir em canais
  sincronizados — ambos aceitos no v1.
