# Quick Log Setup Reconciliation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deduplicar canais padrao de log com seguranca e inicializar automaticamente guilds ainda ausentes do `guild_config`.

**Architecture:** `QuickLogSetup` ganha uma decisao pura de reconciliacao que escolhe os canais preservados e os IDs removiveis; o fluxo Discord persiste o keeper antes de excluir duplicatas. Um `InitialGuildSetup` isolado percorre as guilds no `onReady` depois do snapshot sync e chama o mesmo setup somente quando `find(guildId)` estiver vazio.

**Tech Stack:** Java 22, JDA 5, JUnit 5, Gradle.

## Global Constraints

- Nunca apagar canal com nome diferente do nome padrao do tipo.
- Preservar o canal configurado vivo e persistir antes de excluir.
- O bootstrap automatico so roda quando nao existe linha em `guild_config`.

---

### Task 1: Reconciliacao e deduplicacao

**Files:**
- Modify: `src/main/java/dev/davimf/basebot/modules/base/setup/QuickLogSetup.java`
- Test: `src/test/java/dev/davimf/basebot/modules/base/setup/QuickLogSetupTest.java`

**Interfaces:**
- Produces: `QuickLogSetup.reconcile(String configuredId, List<ChannelRef> channels)` retornando keeper configurado, keeper padrao e IDs duplicados.

- [ ] **Step 1: Escrever testes falhos** para prioridade do configurado padrao, fallback ao primeiro padrao e configurado vivo com nome diferente.
- [ ] **Step 2: Executar** `./gradlew test --tests dev.davimf.basebot.modules.base.setup.QuickLogSetupTest` e confirmar falha pela API ausente.
- [ ] **Step 3: Implementar a decisao pura minima** e integrar `run`: salvar o keeper quando necessario e chamar `delete().complete()` apenas nos IDs duplicados depois do save.
- [ ] **Step 4: Reexecutar o teste direcionado** e confirmar `BUILD SUCCESSFUL`.

### Task 2: Bootstrap de guilds ausentes

**Files:**
- Create: `src/main/java/dev/davimf/basebot/modules/base/setup/InitialGuildSetup.java`
- Create: `src/test/java/dev/davimf/basebot/modules/base/setup/InitialGuildSetupTest.java`
- Modify: `src/main/java/dev/davimf/basebot/modules/base/BaseModule.java`

**Interfaces:**
- Produces: `InitialGuildSetup.run(BotContext ctx)`, que percorre `ctx.jda().getGuilds()` e chama `QuickLogSetup.run` somente para `guildConfig().find(id).isEmpty()`.

- [ ] **Step 1: Escrever teste falho** da decisao `shouldSetup(Optional<GuildConfig>)`: verdadeiro apenas para vazio.
- [ ] **Step 2: Executar** `./gradlew test --tests dev.davimf.basebot.modules.base.setup.InitialGuildSetupTest` e confirmar falha pela classe ausente.
- [ ] **Step 3: Implementar `InitialGuildSetup`** com isolamento de excecao por guild e logging; chamá-lo no mesmo job do `onReady`, depois de `bootstrapInstance()` e `syncAll()`.
- [ ] **Step 4: Executar testes direcionados e depois `./gradlew test`**, exigindo `BUILD SUCCESSFUL` sem falhas.
