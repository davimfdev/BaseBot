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

Para cada canal do servidor, reverter **somente** se ele tiver a **assinatura exata** que o bot cria:
`@everyone` com `VIEW_CHANNEL` **negado** **E** o cargo `membro` com `VIEW_CHANNEL` **permitido**.
A reversão limpa (`clear`) o componente `VIEW_CHANNEL` dos dois overrides, devolvendo o canal ao
estado herdado (aberto). Canais que o admin alterou depois (assinatura não bate) ficam **intactos**.

A reversão é **stateless**: a própria permissão é o marcador, então não há snapshot no banco.

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

    // Helpers PUROS (unit-testados):
    public static Set<String> excludedChannelIds(GuildConfig cfg); // logs + verificacao + anti-spam (ignora nulos)
    public static boolean isOpenForEveryone(EnumSet<Permission> everyoneDenied);
    public static boolean matchesLockdownSignature(EnumSet<Permission> everyoneDenied,
                                                   EnumSet<Permission> memberAllowed);
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
  Para cada canal cuja assinatura casa (`matchesLockdownSignature`): limpa `VIEW_CHANNEL` dos
  overrides de `@everyone` e de `membro` (`upsertPermissionOverride(role).clear(VIEW_CHANNEL).complete()`);
  incrementa `changed`.
- Cada `.complete()` é protegido; falha de um canal vira `skipped` e o sweep continua.

### 2. `SetupView.verificationScreen`

Ganha uma nova `ActionRow` com dois botões:
- **"Esconder canais"** — `ComponentId.of(NS, "verifylock")`.
- **"Reabrir canais"** — `ComponentId.of(NS, "verifyunlock")`.

### 3. `SetupComponentHandler`

- **`sectoggle:verify`** (já existente): após `guildConfig().save(updated)`, agendar no executor
  `VerificationLockdown.apply(ctx, guild)` se o novo estado é `on`, ou `.revert(...)` se é `off`.
  A re-renderização da sub-tela continua imediata; o sweep roda em background.
- **`verifylock` / `verifyunlock`** (novos): `event.deferEdit()`, rodar `apply`/`revert` no executor,
  depois `editOriginalComponents(verificationScreen)` + resumo efêmero via `getHook()`
  ("`X` canais escondidos / `Y` pulados", ou "Configure o cargo *membro* primeiro." quando
  `memberRoleMissing`). Registrar em `actionLogs` (`VERIFY_LOCKDOWN` / `VERIFY_UNLOCK`).

### 4. Teste — `VerificationLockdownTest`

JUnit 5 puro (sem Mockito), cobrindo os helpers puros:
- `excludedChannelIds` inclui os ids de log configurados + verificação + anti-spam e ignora chaves nulas.
- `isOpenForEveryone` — `true` quando `VIEW_CHANNEL` não está negado; `false` quando está.
- `matchesLockdownSignature` — `true` só quando `@everyone` nega `VIEW_CHANNEL` **e** `membro` permite.

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

## Fora de escopo (YAGNI)

- Snapshot/persistência do estado anterior dos canais (a reversão por assinatura dispensa).
- Conceder View ao bot automaticamente.
- Disparo automático a partir de mudanças feitas pela dashboard.
- Aplicar lockdown a canais criados **depois** de ligar (o botão manual cobre esse caso).
