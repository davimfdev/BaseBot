# Incremental Guild Command Registration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Registrar por guild somente comandos ainda ausentes.

**Architecture:** `CommandManager` consulta comandos remotos e usa uma funcao pura para selecionar os `SlashCommandData` ausentes por nome; cada ausente e criado individualmente. O fluxo deixa de fazer bulk overwrite e nao toca na guild vault.

**Tech Stack:** Java 22, JDA 5, JUnit 5, Gradle.

## Global Constraints

- Registro permanece por guild.
- Comandos existentes nao sao atualizados nem removidos.
- Startup sem comandos ausentes nao executa escrita de comandos.

---

### Task 1: Selecao e registro incremental

**Files:**
- Modify: `src/main/java/dev/davimf/basebot/core/command/CommandManager.java`
- Create: `src/test/java/dev/davimf/basebot/core/command/CommandManagerRegistrationTest.java`

**Interfaces:**
- Produces: `CommandManager.missingCommandNames(Set<String>, Set<String>)`.

- [ ] Escrever testes falhos para nenhum, alguns e todos ausentes.
- [ ] Executar o teste e confirmar falha pela API ausente.
- [ ] Trocar bulk overwrite por `retrieveCommands` seguido de `upsertCommand` apenas dos ausentes.
- [ ] Executar teste direcionado e suite completa, exigindo `BUILD SUCCESSFUL`.
