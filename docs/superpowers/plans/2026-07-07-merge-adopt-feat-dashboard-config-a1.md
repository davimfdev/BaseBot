# Merge: Adoção da branch feat/dashboard-config-a1 como novo main — Plano de Implementação

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Integrar toda a linha de desenvolvimento da branch `feat/dashboard-config-a1` na `main`, tornando-a a nova base — o que inclui o subsistema de segurança (verificação, anti-raid, anti-spam, automod, anti-nuke) exigido pela Task 1 do roadmap.

**Architecture:** A `main` está desatualizada: seu `src/` é idêntico ao merge-base; ela só acrescentou docs. A `feat` é a base + 691 arquivos (10 áreas de módulo novas + 20 migrations 019→038 + suíte de segurança). O merge é, na prática, adotar a `feat` como novo `main`. A simulação com `git merge-tree` mostrou **um único conflito real: `.gitignore`** (o do `main` está corrompido com marcadores de conflito antigos); o `README.md` faz auto-merge. Todo o trabalho ocorre numa branch de merge isolada, com gates de verificação (compilação, suíte de testes, migrations em DB limpo) antes de tocar a `main`.

**Tech Stack:** Java 22 · Gradle (wrapper, plugin shadow) · JDA 6.4.2 · SQLite (JDBC 3.47) com `SqliteMigrator` · PostgreSQL/HikariCP · JUnit 5 (sem Mockito; JDA mockado via `java.lang.reflect.Proxy`; repos SQLite testados com DB temporário).

## Global Constraints

- **JDK 22 obrigatório.** O toolchain do Gradle está fixado em Java 22 e falha em JDK mais novo (ex.: 26). Verifique antes de compilar.
- **Migrations são forward-only e ordem-sensível.** A lista `MIGRATIONS` em `SqliteMigrator.java` (`/db/sqlite/001_init.sql` … `/db/sqlite/038_verification.sql`) **nunca** deve ser reordenada ou renumerada. O merge adota a lista da `feat` verbatim — não edite.
- **A `main` só é atualizada via PR** depois que TODOS os gates passam. Nunca commite o merge direto na `main`.
- **Não execute o merge com a árvore de trabalho suja.** `git status --porcelain` deve estar vazio antes de iniciar.
- **Resolução do `.gitignore`:** adote a versão da `feat` (`--theirs`) verbatim. É o superset limpo; a do `main` contém marcadores de conflito não resolvidos.
- **Ref da branch:** o remote chama-se literalmente `main`; a branch de feature resolve-se como `main/feat/dashboard-config-a1`. Confirme com `git rev-parse --verify` antes de usar.
- **Efeito colateral do `.gitignore` da feat:** ele ignora `docs/`, `.claude`, `.codex`, `.agents`. Arquivos de `docs/` já rastreados na `main` (incluindo este plano) permanecem versionados após o merge (o Git mantém arquivos já rastreados mesmo que passem a ser ignorados). Docs novos criados depois não serão auto-adicionados — use `git add -f` se precisar versioná-los.

---

### Task 1: Preparação — árvore limpa, docs commitados e branch de merge

**Files:**
- Modify (commit): `docs/` (arquivos de roadmap/plano hoje não rastreados)
- Git refs apenas (nenhum arquivo de código)

**Interfaces:**
- Produces: a branch de trabalho `merge/adopt-feat-dashboard-config-a1` partindo de `main`, com árvore limpa — consumida por todas as tasks seguintes.

- [ ] **Step 1: Confirmar JDK 22 disponível para o Gradle**

Run: `./gradlew --version`
Expected: linha `JVM:` reportando uma versão 22.x, ou o toolchain do Gradle configurado para auto-provisionar o JDK 22. Se aparecer JDK 26 e não houver toolchain 22, **pare** e disponibilize o JDK 22 antes de prosseguir.

- [ ] **Step 2: Commitar os docs não rastreados na `main`**

Antes do merge, a árvore precisa estar limpa. Os documentos de roadmap/plano estão soltos:

```bash
git switch main
git add docs/
git commit -m "docs: roadmaps (module audit, websocket bridge) e plano de merge da feat"
```

- [ ] **Step 3: Verificar árvore 100% limpa**

Run: `git status --porcelain`
Expected: saída **vazia**. Se houver qualquer arquivo restante (ex.: `basebot.jar` modificado), decida commitar ou descartar até a saída ficar vazia.

- [ ] **Step 4: Atualizar os remotes e confirmar que a ref da feat resolve**

```bash
git fetch main
git rev-parse --verify main/feat/dashboard-config-a1
```
Expected: um SHA é impresso (ex.: `4c969b3…`). Se der `fatal: Needed a single revision`, liste os remotes com `git remote -v` e ajuste o nome antes de continuar.

- [ ] **Step 5: Criar a branch de merge a partir de `main`**

```bash
git switch -c merge/adopt-feat-dashboard-config-a1 main
```
Expected: `Switched to a new branch 'merge/adopt-feat-dashboard-config-a1'`.

- [ ] **Step 6: Commit (checkpoint de partida — nada a commitar, apenas registra o ponto)**

Nenhum arquivo muda nesta task além do Step 2 (já commitado). A branch em si é o entregável. Prossiga para a Task 2.

---

### Task 2: Executar o merge e resolver o conflito do `.gitignore`

**Files:**
- Modify: `.gitignore` (resolução do conflito → versão da `feat`)
- Cria o commit de merge na branch de trabalho

**Interfaces:**
- Consumes: branch `merge/adopt-feat-dashboard-config-a1` (Task 1)
- Produces: um commit de merge contendo todo o código da `feat` (10 módulos novos + 20 migrations + segurança), sem marcadores de conflito — consumido pelos gates das Tasks 3–5.

- [ ] **Step 1: Iniciar o merge sem commitar**

```bash
git merge --no-ff --no-commit main/feat/dashboard-config-a1
```
Expected: mensagem incluindo `CONFLICT (content): Merge conflict in .gitignore` e `Automatic merge failed; fix conflicts and then commit the result.` O `README.md` deve aparecer como `Auto-merging README.md` **sem** linha de CONFLICT.

- [ ] **Step 2: Confirmar que o único conflito é o `.gitignore`**

Run: `git diff --name-only --diff-filter=U`
Expected: exatamente uma linha — `.gitignore`. Se aparecer qualquer outro arquivo, **pare** e reavalie (o estado do repo mudou desde a análise); não force a resolução.

- [ ] **Step 3: Resolver o `.gitignore` adotando a versão da `feat`**

```bash
git checkout --theirs .gitignore
git add .gitignore
```

- [ ] **Step 4: Verificar o conteúdo resolvido do `.gitignore`**

Run: `cat .gitignore`
Expected: a versão da `feat` — começa com o bloco `# [OUTLINE START]`, contém as seções `# Build output`, `# Local runtime data`, `# Secrets & local config`, `# IDE`, `# OS`, `# JAVA`, `# IA SHIT` (`.agents/.claude/.codex`) e, ao final, `# Documentation Specs/Plans` seguido de `docs/`. **Não** deve conter nenhum `<<<<<<<`, `=======` ou `>>>>>>>`.

- [ ] **Step 5: Garantir que não sobrou marcador de conflito em nenhum arquivo**

Run: `git grep -nE '^(<<<<<<<|=======|>>>>>>>)' -- . ':!docs/' || echo "SEM MARCADORES"`
Expected: `SEM MARCADORES`. (Exclui `docs/` porque o próprio `.gitignore` do `main` — que este plano cita — contém a string; qualquer resultado FORA de docs indica conflito real não resolvido → pare e resolva.)

- [ ] **Step 6: Verificar que o `README.md` mesclou limpo**

Run: `git grep -nE '^(<<<<<<<|>>>>>>>)' -- README.md || echo "README OK"`
Expected: `README OK`.

- [ ] **Step 7: Commitar o merge**

```bash
git commit --no-edit
```
Expected: cria o commit de merge (mensagem padrão `Merge remote-tracking branch 'main/feat/dashboard-config-a1' into merge/adopt-feat-dashboard-config-a1`). Confirme com `git log --oneline -1` e `git rev-parse HEAD^2` (deve existir o segundo pai = tip da feat).

---

### Task 3: Gate de compilação

**Files:**
- Nenhum (apenas compila a árvore mesclada)

**Interfaces:**
- Consumes: commit de merge (Task 2)
- Produces: garantia de que todo o código mesclado compila (main + feat juntos).

- [ ] **Step 1: Compilar produção e testes sem executar os testes**

Run: `./gradlew clean compileJava compileTestJava`
Expected: `BUILD SUCCESSFUL`. Se falhar com erro de compilação, é uma incompatibilidade real trazida pelo merge — registre o(s) arquivo(s), diagnostique e corrija na branch antes de prosseguir. Não avance para os testes com a compilação quebrada.

- [ ] **Step 2: Commit (só se houve correção de compilação)**

Se o Step 1 exigiu ajustes, commite-os:
```bash
git add -A
git commit -m "fix: corrige compilação pós-merge da feat"
```
Se compilou de primeira, não há o que commitar — prossiga.

---

### Task 4: Gate de migrations — smoke test da cadeia completa em DB limpo

**Files:**
- Create: `src/test/java/dev/davimf/basebot/database/sqlite/FullMigrationSmokeTest.java`

**Interfaces:**
- Consumes: `SqliteManager(new BotConfig.Sqlite(String path))`, `new SqliteMigrator(sqlite).migrate()`, `VerificationRepository(sqlite)` com `boolean isVerified(String guildId, String userId)` — todos trazidos pela `feat` (assinaturas confirmadas em `VerificationRepositoryTest`).
- Produces: um guard permanente de que as 38 migrations aplicam em ordem, sem erro, num arquivo SQLite novo, e que `038_verification.sql` cria a tabela usada pelo `VerificationRepository`. Cobre o item do roadmap "migrations aplicam em DB limpo".

- [ ] **Step 1: Escrever o teste de smoke de migração**

Create `src/test/java/dev/davimf/basebot/database/sqlite/FullMigrationSmokeTest.java`:

```java
package dev.davimf.basebot.database.sqlite;

import dev.davimf.basebot.config.BotConfig;
import dev.davimf.basebot.modules.base.security.VerificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;

class FullMigrationSmokeTest {

    @Test
    void fullChainAppliesOnFreshDb(@TempDir Path dir) {
        SqliteManager sqlite = new SqliteManager(
                new BotConfig.Sqlite(dir.resolve("smoke.db").toString()));
        try {
            assertDoesNotThrow(
                    () -> new SqliteMigrator(sqlite).migrate(),
                    "A cadeia 001..038 deve aplicar sem erro em um DB SQLite limpo");

            // 038_verification.sql precisa ter criado a tabela usada pelo repositório.
            VerificationRepository repo = new VerificationRepository(sqlite);
            assertFalse(repo.isVerified("g1", "u1"),
                    "Consulta na tabela de verificação deve funcionar após migrar");
        } finally {
            sqlite.close();
        }
    }
}
```

- [ ] **Step 2: Rodar o smoke test e confirmar que passa**

Run: `./gradlew test --tests "dev.davimf.basebot.database.sqlite.FullMigrationSmokeTest"`
Expected: `BUILD SUCCESSFUL`, 1 teste, 0 falhas. Se falhar com erro de SQL, uma migration não aplica em cadeia limpa (cf. gotcha conhecido do `SqliteMigrator` ao dividir statements por `;`) — diagnostique a migration culpada antes de prosseguir.

- [ ] **Step 3: Commitar o teste**

```bash
git add src/test/java/dev/davimf/basebot/database/sqlite/FullMigrationSmokeTest.java
git commit -m "test: smoke da cadeia completa de migrations (001..038) em DB limpo"
```

---

### Task 5: Gate da suíte completa + testes de segurança

**Files:**
- Nenhum (executa a suíte; correções só se algum teste falhar)

**Interfaces:**
- Consumes: árvore mesclada e compilável (Tasks 2–4)
- Produces: evidência de que 100% da suíte passa, incluindo os testes de segurança — o Critério de Pronto da Task 1 do roadmap.

- [ ] **Step 1: Rodar os testes de segurança e infrações (Critério de Pronto)**

Run:
```bash
./gradlew test --tests "dev.davimf.basebot.modules.base.security.*" --tests "dev.davimf.basebot.modules.base.moderation.Infraction*"
```
Expected: `BUILD SUCCESSFUL`. Cobre `ActorWindowTest`, `AntiSpamServiceTest`, `JoinWindowTest`, `SecurityConfigTest`, `VerificationLockdownTest`, `VerificationRepositoryTest`, `ViolationWindowTest`, `InfractionRepositoryTest`, `InfractionViewTest`.

- [ ] **Step 2: Rodar a suíte completa**

Run: `./gradlew test`
Expected: `BUILD SUCCESSFUL` com todos os testes (100+ arquivos de teste) verdes. Se algum teste que **não** existia no `main` falhar, é um defeito pré-existente da `feat` exposto agora — registre-o. Falhas não podem ser ignoradas: ou corrija na branch, ou (se for um teste comprovadamente flaky/ambiental) documente explicitamente no PR. Não declare o gate como passado sem a saída verde.

- [ ] **Step 3: Commit (só se houve correção de teste)**

Se algum teste exigiu correção de código de produção:
```bash
git add -A
git commit -m "fix: corrige <descrição> exposto pela suíte pós-merge"
```
Caso contrário, nada a commitar.

---

### Task 6: Smoke test de boot (manual, pré-merge — requer token real)

**Files:**
- Nenhum (verificação operacional)

**Interfaces:**
- Consumes: fat JAR / `./gradlew run` da branch de merge
- Produces: confirmação de que o bot sobe, aplica migrations e registra os comandos de segurança contra um servidor de teste.

> Este gate **não é automatizável** no CI porque exige um token do Discord e rede. Execute-o localmente contra um **servidor de teste** antes de integrar em produção. Se não houver ambiente de teste disponível agora, marque como pendente e destaque no PR que o boot ainda não foi validado.

- [ ] **Step 1: Configurar credenciais de teste**

Garanta um `config.yml`/`.env` local apontando para um bot e um servidor de teste (nunca produção). Faça backup de qualquer `data/basebot.db` de teste antes, pois o boot aplicará as novas migrations 019→038.

- [ ] **Step 2: Subir o bot**

Run: `./gradlew run` (ou `./gradlew shadowJar && java -jar build/libs/<artefato>.jar`)
Expected nos logs: conexão ao gateway com sucesso; log do `SqliteMigrator` aplicando as migrations até `038_verification.sql` sem erro; nenhum stacktrace no startup.

- [ ] **Step 3: Verificar o subsistema de segurança no Discord**

No servidor de teste, confirme que o comando de painel de verificação (`/verificacao painel` ou equivalente registrado pela `feat`) aparece e responde, e que um join de conta de teste dispara o fluxo de verificação. Registre o resultado (ok / problema) para o corpo do PR.

---

### Task 7: Abrir o Pull Request para a `main`

**Files:**
- Nenhum (operação de git/GitHub, voltada para fora)

**Interfaces:**
- Consumes: branch `merge/adopt-feat-dashboard-config-a1` com todos os gates das Tasks 3–5 verdes (e Task 6 registrada)
- Produces: um PR `main ← merge/adopt-feat-dashboard-config-a1` para revisão humana.

> Passo voltado para fora. **Não** faça push nem abra o PR sem o aval explícito do usuário. Não faça merge direto na `main`.

- [ ] **Step 1: Push da branch de merge**

```bash
git push -u origin merge/adopt-feat-dashboard-config-a1
```

- [ ] **Step 2: Abrir o PR**

```bash
gh pr create --base main --head merge/adopt-feat-dashboard-config-a1 \
  --title "Adota feat/dashboard-config-a1 como novo main (traz segurança + 10 módulos + 20 migrations)" \
  --body "$(cat <<'EOF'
## Escopo
Este PR **não é só segurança**: adota toda a linha de desenvolvimento da `feat/dashboard-config-a1` como novo `main`. O `src/` do `main` estava idêntico ao merge-base; a `feat` traz base + 691 arquivos.

### Traz
- Subsistema de segurança: verificação, anti-raid, anti-spam, automod, anti-nuke (`modules/base/security/`)
- Módulos novos em `base/`: economy, events, fun, giveaway, leveling, selfroles, snapshot, utility, welcome
- 20 migrations SQLite novas (019→038, incluindo `038_verification.sql`)

### Conflitos resolvidos
- `.gitignore`: adotada a versão da `feat` (a do `main` continha marcadores de conflito antigos). `README.md` fez auto-merge.

### Gates
- [x] Compila (`compileJava` + `compileTestJava`)
- [x] Smoke de migrations em DB limpo (`FullMigrationSmokeTest`)
- [x] Testes de segurança/infrações verdes (Critério de Pronto do roadmap)
- [x] Suíte completa verde
- [ ] Boot manual contra servidor de teste (ver descrição — preencher)

### Atenção do revisor
- O `.gitignore` da feat passa a ignorar `docs/`, `.claude`, `.codex`, `.agents`. Docs já rastreados permanecem; docs novos precisarão de `git add -f`.
- Features que o roadmap lista como "ausentes/P3" (economy, leveling, giveaways, fun, shop) passam a existir no `main` — confirmar maturidade de cada uma em fase posterior.
EOF
)"
```
Expected: URL do PR criada.

- [ ] **Step 3: Registrar o resultado do boot manual (Task 6) no PR** e aguardar revisão. Não faça merge sem aprovação.

---

## Rollback

- Antes do commit de merge (Task 2, Step 7): `git merge --abort` restaura a branch ao estado pré-merge.
- Depois do commit, ainda na branch de trabalho: `git reset --hard main` descarta o merge; a `main` nunca foi tocada.
- A `main` só muda quando o PR (Task 7) for aprovado e mesclado — reversível pelo próprio GitHub.

## Self-Review (cobertura do Critério de Pronto da Task 1 do roadmap)

- "Merge completo da branch `feat/dashboard-config-a1` para `main`" → Tasks 2 + 7.
- "100% dos testes unitários de segurança passando" → Task 5, Step 1 (subset de segurança) + Step 2 (suíte completa).
- "Risco: Alto — conflitos em config comum de DB/JDA" → mitigado: a análise (`merge-tree`) provou que o único conflito é `.gitignore`; o `src/` do `main` é idêntico ao merge-base, então não há conflito de código. Os gates de compilação/testes/migrations capturam qualquer incompatibilidade semântica remanescente.
- "Início da escuta de `/verificacao painel` na `main`" → Task 6, Step 3 (verificação manual do comando).
