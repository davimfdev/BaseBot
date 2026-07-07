# Auditoria Modular do Bot e Dashboard

## Data da análise
**7 de julho de 2026**

---

## Resumo executivo

Esta auditoria apresenta um diagnóstico técnico completo do estado atual do **BaseBot** em relação às especificações desejadas no documento [AI_AUDIT_AND_ROADMAP_SPEC.md](AI_AUDIT_AND_ROADMAP_SPEC.md). 

Atualmente, o projeto é um bot Discord em Java que implementa um conjunto de funcionalidades organizadas em pacotes de módulos. A fonte da verdade para todas as configurações e recursos que interagem com o **Dashboard Web** é o banco de dados centralizado **PostgreSQL**. O **SQLite** é mantido estritamente para caches locais de alta performance, filas temporárias de processamento, tokens temporários, rascunhos de mensagens e estados efêmeros/transientes de JDA (para evitar perdas sob quedas de gateway).

O **Dashboard Web** está **completamente ausente** deste repositório e opera de forma isolada (anteriormente sob Supabase/Netlify na URL `davimf.dev`). O bot realiza integração com esse dashboard externo por meio de requisições HTTPS para exportação de transcripts de tickets (criptografados com PBKDF2 + AES-256-GCM) e compartilha a mesma estrutura de tabelas do PostgreSQL para as configurações das Guilds.

O principal foco da arquitetura atual é a modularização conceitual. Cada módulo (Base, Tickets, Sales, Facs) implementa uma classe que herda de `BotModule` e se registra de forma autônoma no inicializador do bot (`BotApplication`). No entanto, a separação de licenças por módulo, o sistema modular de permissões granulares e o módulo Polícia estão ausentes e devem ser implementados nas próximas fases.

---

### ⚠️ Divergência de Contexto Mapeada (Sistema de Verificação)
Durante a auditoria local, identificamos uma divergência importante no repositório:
1. **Branch `main`**: Na branch ativa local de desenvolvimento, não existe qualquer arquivo de código ou tabela de banco relacionado a verificação de segurança ou captcha (a pasta `security/` está completamente ausente e não há comandos associados).
2. **Branch `feat/dashboard-config-a1`**: Ao inspecionar o histórico de ramificações remotas, descobrimos que a branch `remotes/main/feat/dashboard-config-a1` contém a implementação de verificação do bot no pacote `src/main/java/dev/davimf/basebot/modules/base/security/`. 
   * Esta branch de feature contém arquivos cruciais como `VerificationListener.java`, `VerificationRepository.java`, `VerificationView.java`, `AntiRaidService.java` e scripts como `038_verification.sql` no SQLite, somando mais de 38 mil linhas de código alteradas.
   * **Conclusão**: O sistema de verificação **já existe no repositório**, mas está sob a branch de feature `feat/dashboard-config-a1` e ainda não foi integrado (merged) na branch de desenvolvimento principal `main`.

---

## Stack detectada

* **Linguagem Principal**: Java 22 (Gradle Toolchain).
* **Biblioteca Discord**: JDA (`6.4.2`), utilizando componentes interativos V2 (botões, menus de seleção e modais).
* **Gerenciador de Dependências**: Gradle com o plugin `com.gradleup.shadow` para empacotamento em um Fat JAR executável.
* **Banco de Dados Centralizado (Configurações e Negócios)**: PostgreSQL via pool de conexões **HikariCP** (`6.2.1`) e driver nativo JDBC (`42.7.4`). Fonte única da verdade para dados compartilhados com o dashboard.
* **Banco de Dados Local (Cache e Estado Efêmero)**: SQLite via driver JDBC (`3.47.1.0`) e um migrador automático customizado (`SqliteMigrator`). Utilizado apenas para armazenamento transiente, filas temporárias e caches locais.
* **Criptografia e Segurança**: PBKDF2-HMAC-SHA256 e AES-256-GCM em `TicketCrypto` para cifragem de transcripts, espelhando a implementação em TypeScript do dashboard.
* **Biblioteca de Imagem e Códigos**: ZXing (`3.5.3`) para geração de QR Codes do Pix.
* **Logging**: SLF4J com implementação **Logback** (`1.5.12`).
* **Dashboard Web**: **Ausente do repositório**. A especificação indica uma arquitetura externa rodando em TypeScript e Netlify Functions (em `davimf.dev`).

---

## Estrutura atual do projeto (Branch main)

A estrutura do projeto está organizada como um único projeto Java focado em divisão de pacotes por responsabilidade técnica e de negócios:

* `src/main/java/dev/davimf/basebot`
  * [BaseBot.java](../src/main/java/dev/davimf/basebot/BaseBot.java): Ponto de entrada do sistema (`main`).
  * [BotApplication.java](../src/main/java/dev/davimf/basebot/BotApplication.java): Inicialização dos bancos de dados, registro de módulos e conexão ao Gateway do Discord.
  * `config/`: Leitura e parse de arquivos YAML (`config.yml`) e variáveis de ambiente em [BotConfig.java](../src/main/java/dev/davimf/basebot/config/BotConfig.java).
  * `core/`: Núcleo da lógica do bot.
    * `command/`: Barramento de registro e roteamento de comandos de barra (Slash Commands).
    * `component/`: Roteador de interações de botões, modais e selects via namespaces customizados.
    * `scheduler/`: Agendador de tarefas concorrentes isoladas a nível de thread.
  * `crypto/`: Mecanismos de segurança para criptografia de transcrições de tickets.
  * `database/`: Conectores das duas stacks de banco e mapeamento de modelos.
    * `postgres/`: Pool do PostgreSQL e repositório JDBC para configurações de servidores.
    * `sqlite/`: Migrador SQLite e repositórios locais para tickets e logs.
  * `integration/`: Cliente HTTP responsável pelo envio de transcripts criptografados à API do dashboard.
  * `ratelimit/`: Utilitários de controle de fluxo de eventos (debouncers e batch throttlers).
  * `modules/`: Definições dos pacotes de funcionalidades comerciais do bot.
    * `base/`: Configuração de setup, moderação de texto/voz, logs no Discord e construtores de formulários/embeds.
    * `tickets/`: Criação, gestão e encerramento de tickets com logs criptografados.
    * `sales/`: Catálogo de produtos, orçamentos interativos e cobrança automatizada via Pix.
    * `facs/`: Painéis de facções FiveM (hierarquia, ações, farm, produção e recrutamento).

---

## 1. Matriz de comparação direta de funcionalidades (Estado na branch main)

A tabela abaixo apresenta o mapeamento detalhado entre as features desejadas no spec de produto, o estado atual de código, arquivos implicados, gaps críticos identificados e próximas ações técnicas prioritárias:

| Feature Desejada | Módulo | Existe no Código? | Status Atual | Arquivos / Classes Encontrados | Gap em Relação ao Concorrente | Prioridade | Próxima Ação Técnica |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Setup do Servidor** | Base | Parcial | Existente, mas sem dashboard | `dev/davimf/basebot/modules/base/setup/*` | O setup é feito apenas por comandos do Discord (`/setup`). Sem painel no dashboard. | **P0** | Integrar com as colunas JSONB do PostgreSQL no painel web. |
| **Moderação Básica** | Base | Sim | Existente e funcional | `dev/davimf/basebot/modules/base/commands/{Kick,Ban,Unban,Clear}Command.java` | Funciona apenas por Discord. Falta histórico visual de punições aplicadas no dashboard. | **P0** | Migrar armazenamento de `action_logs` para o PostgreSQL. |
| **Controle de Voz** | Base | Sim | Existente e funcional | `dev/davimf/basebot/modules/base/voice/*` | Mutes persistentes operam em DB local SQLite, inviabilizando visualização web. | **P1** | Sincronizar tabela `timed_mutes` no PostgreSQL para exibição na web. |
| **Verificação** | Base | Sim (na branch feat) | **Parcial** (Ausente na main) | Branch `feat/dashboard-config-a1` em `dev/davimf/basebot/modules/base/security/*` | Código existe apenas no branch de feature, mas não foi integrado. Fila de verificação opera localmente. | **P1** | Realizar o merge da branch `feat/dashboard-config-a1` para a branch `main`. |
| **Anti-Robô / Captcha** | Base | Não | **Ausente** | Nenhum | Sem barreira contra automações e self-bots. | **P2** | Desenvolver gerador de imagem de captcha efêmero em Java com resposta por modal. |
| **Anti-link e Automod** | Base | Não | **Ausente** | Nenhum | Não monitora mensagens nem filtra spam ou domínios inseguros. | **P2** | Desenvolver listener de mensagem e tabela de whitelist de links no PostgreSQL. |
| **Logs Gerais** | Base | Parcial | Existente, mas sem logs na web | `dev/davimf/basebot/modules/base/listeners/GeneralLoggingListener.java` | Logs são enviados apenas para canais do Discord. Sem histórico no banco central. | **P1** | Criar tabela `audit_logs` no PostgreSQL e sincronizar em nuvem. |
| **Sugestões** | Base | Não | **Ausente** | Nenhum | Sem painel de envio, votações com botões ou threads de debates. | **P2** | Criar comando `/sugestao` e salvar votos e status no PostgreSQL. |
| **Economia de Engajamento** | Base | Não | **Ausente** | Nenhum | Sem carteira geral, XP, level, ranking global ou compras de cargos. | **P3** | Criar tabelas globais de economia de membros em PostgreSQL. |
| **Avisos / Anúncios** | Base | Não | **Ausente** | Nenhum | Não há disparos agendados ou recorrentes configurados. | **P3** | Projetar comando `/anunciar` com agendamento no PostgreSQL. |
| **Tickets de Suporte** | Tickets | Sim | Existente e funcional | `dev/davimf/basebot/modules/tickets/*` | Funcional com transcripts criptografados. Sem estatísticas agregadas na web. | **P1** | Implementar rota de API no dashboard para ler métricas de tickets fechados. |
| **Vendas - Pix** | Vendas | Sim | Existente e funcional | `dev/davimf/basebot/modules/sales/pix/*` | Chaves Pix armazenadas em formato aberto no SQLite local. | **P1** | Migrar chaves Pix para o PostgreSQL com criptografia simétrica. |
| **Vendas - Orçamentos** | Vendas | Sim | Existente e funcional | `dev/davimf/basebot/modules/sales/budget/*` | Faltam regras de repasses de comissão de vendas e parcerias comerciais. | **P1** | Migrar a persistência de orçamentos para o PostgreSQL. |
| **Vendas - Registro manual** | Vendas | Não | **Ausente** | Nenhum | Sem comandos para lançar venda em dinheiro físico ou fora do Pix do bot. | **P1** | Criar comando `/venda registrar` e salvar em tabelas do PostgreSQL. |
| **Vendas - Fila de aprovação** | Vendas | Não | **Ausente** | Nenhum | Comprovantes físicos/prints não passam por fila de validação humana. | **P1** | Criar fila de aprovação de comprovantes integrada ao PostgreSQL. |
| **Vendas - Parcerias** | Vendas | Não | **Ausente** | Nenhum | Sem associação de parcerias ou taxas a produtos e orçamentos. | **P2** | Adicionar tabelas de parcerias e cálculo de repasse no PostgreSQL. |
| **Hierarquia de Facção** | Facs | Sim | Existente e funcional | `dev/davimf/basebot/modules/facs/hierarchy/*` | Painel atualiza via Discord. Sem editor de hierarquia visual na web. | **P1** | Criar tela web para reordenar cargos da facção por drag-and-drop. |
| **Demissão (/pd)** | Facs | Sim | Existente e funcional | `dev/davimf/basebot/modules/facs/commands/PdCommand.java` | Funciona apenas por botões e logs no Discord. | **P1** | Logar ações de demissão na tabela de punições com status inativo. |
| **Punições (ADV/Blacklist)** | Facs | Sim | Existente e funcional | `dev/davimf/basebot/modules/facs/punish/*` | Punições expiram via bot. Sem controle de recursos e histórico na web. | **P1** | Migrar tabelas de punições para o PostgreSQL para visão gerencial. |
| **Solicitação de Cargo** | Facs | Sim | Existente e funcional | `dev/davimf/basebot/modules/facs/sets/*` | Solicitação aprovada via botões no Discord. Sem painel web de histórico. | **P1** | Migrar aprovações de cargo para o banco PostgreSQL compartilhado. |
| **Financeiro de Facção** | Facs | Sim | Existente e funcional | `dev/davimf/basebot/modules/facs/economy/*` | Comandos operam em SQLite local. Sem visibilidade web de extrato. | **P1** | Migrar tabelas financeiras de facção para o PostgreSQL. |
| **Farm e Estoque** | Facs | Sim | Existente e funcional | `dev/davimf/basebot/modules/facs/economy/*` | Resets semanais e metas proporcionais a ausências não estão codificados. | **P1** | Migrar registros de farm e estoque para o PostgreSQL. |
| **Produzir (/produzir)** | Facs | Sim | Existente e funcional | `dev/davimf/basebot/modules/facs/commands/ProduzirCommand.java` | Receitas e crafts apenas locais. | **P1** | Migrar tabelas de receitas e histórico de produção para o PostgreSQL. |
| **Módulo Polícia** | Polícia | Não | **Completamente Ausente** | Nenhum | Sem suporte para patentes, relatórios, BOPM ou bate-ponto. | **P1** | Desenvolver do zero baseado no modelo conceitual de organizações no PostgreSQL. |

---

## 2. Revisão do sistema de verificação e captcha

### Fluxo de Verificação Existente (Na branch `feat/dashboard-config-a1`)
Na branch `feat/dashboard-config-a1`, o sistema opera da seguinte forma:
1. **Join**: Quando um novo membro entra, o `VerificationListener` detecta. Se ele já constar na tabela `verified_members`, recebe automaticamente o cargo `membro` (bypass). Se não, recebe o cargo `nao-verificado`.
2. **Painel**: O bot renderiza um painel fixo de boas-vindas com o botão **"Verificar"** (gerado por `VerificationView`).
3. **Validação**: Ao clicar no botão, o JDA gerencia o clique. Atualmente, o fluxo direciona para liberação direta ou modal de perguntas.

### Correção do Fluxo de Captcha / Anti-Robô (Sem substituir a Verificação)
O Discord **não suporta exibição de imagens em caixas de diálogo Modal** (modais são estritamente para campos de input de texto e texto longo). Portanto, o fluxo correto de captcha interativo será o seguinte:

```
[Membro clica em "Verificar"]
        │
        ▼ (Bot cria código aleatório em memória)
[Bot envia Mensagem Efêmera]
  ├── Anexa a imagem gerada do Captcha (BufferedImage/Graphics2D)
  └── Exibe botão "Inserir Código" (Custom ID: `captcha:input`)
        │
        ▼ (Membro clica em "Inserir Código")
[Bot abre um Modal de Texto]
  └── Campo curto: "Digite os caracteres da imagem"
        │
        ▼ (Membro preenche e envia o Modal)
[Validação do Bot]
  ├── Se correto: Remove cargo "Não Verificado" + Concede "Membro" + Salva no Banco PostgreSQL
  └── Se incorreto: Mensagem efêmera de erro (Reduz tentativa de 1/3)
```

Essa abordagem garante que a imagem do captcha seja visível (como arquivo anexo na mensagem temporária) e a digitação seja limpa (via modal).

---

## 3. Revisão do Módulo Vendas

### 1. Estado atual do Módulo no Código (Branch `main`)
*   **Chaves Pix**: O comando `/pix registrar` armazena no SQLite a chave Pix associada ao ID de usuário do vendedor.
*   **Orçamentos**: O comando `/orçamento` cria propostas que expiram em 24h e geram o Pix QR Code do ZXing se aprovados.
*   **Catálogo**: O comando `/tabela` lista itens cadastrados no SQLite local.

### 2. Módulo de Vendas Desejado (Fonte da Verdade em PostgreSQL)
Para aproximar o bot do produto final de mercado, o módulo deve ser incrementado com as seguintes regras de vendas:
*   **Registro de Venda**: Comando `/venda registrar` contendo:
    *   `item`: Produto do catálogo.
    *   `quantidade`: Inteiro positivo.
    *   `cliente` (opcional): Usuário Discord.
    *   `parceria` (opcional): Id de parceria comercial cadastrada.
    *   `print_comprovante`: Arquivo de imagem em anexo (obrigatório).
    *   `observação` (opcional): Texto livre.
*   **Divisão de Lucros (Cálculo de Repasse)**:
    *   `Parte da Facção` = Valor total × (% Fação/Loja configurado)
    *   `Comissão do Vendedor` = Valor total × (% Vendedor configurado)
    *   `Taxa da Parceria` = Valor total × (% Parceria configurado)
*   **Controle de Status da Venda**:
    *   `PENDENTE`: Aguardando gerente de vendas validar o comprovante anexo.
    *   `APROVADA`: Confirmada por um gerente. Atualiza o saldo financeiro da facção e adiciona o saldo do vendedor no PostgreSQL.
    *   `RECUSADA`: Negada com justificativa enviada ao vendedor por DM.
    *   `CANCELADA`: Expirada ou anulada administrativamente.

### 3. Roadmap Específico para Evolução de Vendas

```
[Módulo Atual] 
  ├── Pix individual por usuário
  ├── Orçamentos interativos (24h)
  └── Catálogo local (SQLite)
        │
        ▼ (Task 1: Migração de Estruturas para PostgreSQL)
[Migração de Dados]
  ├── Criação da tabela `sales_records` (valor, print, status, taxas) no PostgreSQL
  └── Criação da tabela `sales_partners` (comissões e contatos) no PostgreSQL
        │
        ▼ (Task 2: Implementar Fluxo de Comprovantes e Fila de Validação)
[Fluxo Operacional]
  ├── Comando `/venda registrar` com upload de imagem obrigatório
  ├── Fila de aprovação interativa no Discord (botões aprovar/recusar) com persistência no Postgres
  └── Notificação ao vendedor e integração com a tesouraria no Postgres
        │
        ▼ (Task 3: Painel Web de Repasses e Comissão)
[Dashboard Completo]
  ├── Configuração do catálogo de produtos via painel no PostgreSQL
  ├── Extratos de repasses (due to faction / due to seller) direto na nuvem
  └── Fechamento financeiro mensal automático via rotina no banco de dados central
```

---

## 4. Estratégia de banco de dados e sincronização Nuvem-Local

### 1. Independência do Dashboard em Relação ao SQLite
O dashboard web opera fora do servidor do bot e **não deve realizar conexões diretas ou leitura de arquivos locais SQLite**. 

*   O **PostgreSQL** é a fonte da verdade de todas as funcionalidades de negócios do bot e do dashboard.
*   O **SQLite** é mantido estritamente para caches locais de alta performance, filas de concorrência temporárias, tokens efêmeros de transação, rascunhos de mensagens e estados transientes do gateway do bot.

### 2. Mapeamento de Sincronização de Entidades

| Entidade | Banco Fonte da Verdade | Papel do SQLite (Se houver) | Fluxo de Integração / Sincronização |
| :--- | :--- | :--- | :--- |
| **Vendas** | PostgreSQL | Apenas cache local de catálogo | Criado no bot, gravado e atualizado diretamente no PostgreSQL. O dashboard lê e altera o banco central. |
| **Farm de Itens** | PostgreSQL | Nenhum | Membro entrega pelo Discord, gestor aprova via dashboard ou Discord, gravando na nuvem. |
| **Inventário (Baú)** | PostgreSQL | Nenhum | Movimentações de entrada/saída gravam diretamente no PostgreSQL central para refletir no estoque web ativo. |
| **Punições** | PostgreSQL | Cache de membros na blacklist | Registro permanente de advertências (ADVs) e blacklist gravados na nuvem. |
| **Ausências** | PostgreSQL | Nenhum | Solicitações feitas pela web e aprovadas por líderes gravam diretamente no PostgreSQL. |
| **Bate Ponto** | PostgreSQL | Fila temporária de ponto aberto | Registros de ponto iniciados no bot gravam a entrada. Ao sair, o ponto é encerrado e consolidado no Postgres. |
| **Logs Auditáveis** | PostgreSQL | Nenhum | Logs administrativos gravam diretamente no PostgreSQL central. |
| **Sugestões** | PostgreSQL | Nenhum | Sugestões, votos e threads rastreados diretamente no PostgreSQL. |

---

### 3. Evidências e Auditoria de Tabelas Candidatas a Remoção
Conforme revisado nas bases do bot, as tabelas `warnings` e `stock` não possuem referências no código-fonte Java na branch `main`:
*   `warnings`: Mapeada apenas na DDL `001_init.sql`. O código utiliza a tabela `punishments`.
*   `stock`: Mapeada na DDL `001_init.sql`. O código utiliza a tabela `fac_stock`.

#### Protocolo de Segurança e Backup Antes da Remoção:
Antes de aplicar os comandos `DROP TABLE`, criamos um fluxo seguro de proteção de dados:
1.  **Backup Físico do Banco**: Cópia local de segurança do arquivo `data/basebot.db` para `data/basebot.db.bak`.
2.  **Confirmação de Ausência de Dados**: Execução de query de contagem para garantir que não existam registros antigos nestas tabelas:
    ```sql
    SELECT COUNT(*) FROM warnings;
    SELECT COUNT(*) FROM stock;
    ```
3.  **Remoção Controlada**: Se e somente se o retorno de ambas as consultas for exatamente `0`, a migração com `DROP TABLE` poderá ser executada no SQLite local.

---

## 5. Roadmap quebrado em Tasks implementáveis

### Task 1: Integração e Sincronização de Segurança (Merge Seguro da Branch feat)
*   **Módulo**: Base / Segurança
*   **Arquivos prováveis**: `src/main/java/dev/davimf/basebot/modules/base/security/*`
*   **Banco / Migration**: Criação das tabelas `verified_members` e `verification_requests` no PostgreSQL (sincronizáveis com o dashboard).
*   **Comandos / Interações Discord**: Início da escuta do comando `/verificacao painel` na branch `main`.
*   **Telas / API do dashboard**: Tela de logs de verificação de usuários.
*   **Critério de Pronto**: Merge completo da branch `feat/dashboard-config-a1` para a branch `main` com 100% dos testes unitários de segurança passando com sucesso.
*   **Risco**: Alto. Pode introduzir conflitos em arquivos comuns de configuração do banco de dados e do JDA.

---

### Task 2: Implementação do Captcha com Mensagem Efêmera e Imagem
*   **Módulo**: Base / Segurança
*   **Arquivos prováveis**:
    *   `src/main/java/dev/davimf/basebot/modules/base/security/CaptchaService.java`
    *   `src/main/java/dev/davimf/basebot/modules/base/security/VerificationComponentHandler.java`
*   **Banco / Migration**: Tabela temporária de sessões em memória do bot para controle de tokens de captcha ativos.
*   **Comandos / Interações Discord**: 
    *   Clique no botão "Verificar" envia a mensagem efêmera anexando o captcha gráfico.
    *   Botão "Responder Captcha" dispara o modal contendo a pergunta do código.
*   **Telas / API do dashboard**: Toggle de ativação do captcha na web.
*   **Critério de Pronto**: O usuário recebe a imagem do captcha, digita o código no modal e recebe o cargo correspondente. Erros sucessivos causam recusa de validação.
*   **Risco**: Baixo. Requer apenas testes de concorrência de geração de imagens com a biblioteca AWT.

---

### Task 3: Proteção de Dados: Backup e Remoção Segura de Tabelas Obsoletas
*   **Módulo**: Compartilhado / Banco de Dados
*   **Arquivos prováveis**:
    *   `src/main/resources/db/sqlite/019_safe_cleanup.sql`
    *   `src/main/java/dev/davimf/basebot/database/sqlite/SqliteMigrator.java`
*   **Banco / Migration**:
    *   A migration deve rodar dentro de bloco de transação segura contendo a deleção de tabelas inativas no SQLite:
    ```sql
    -- Executar somente após validação de dados
    DROP TABLE IF EXISTS warnings;
    DROP TABLE IF EXISTS stock;
    ```
*   **Comandos / Interações Discord**: Nenhum.
*   **Telas / API do dashboard**: Nenhuma.
*   **Critério de Pronto**: Geração do script de backup físico do SQLite automatizado em `DatabaseManager.java` antes de disparar o migrador do SQLite, executando a remoção apenas se os registros das tabelas forem vazios.
*   **Risco**: Médio. Uma remoção sem confirmação em bases de servidores reais ativos poderia causar perda de dados legados.

---

### Task 4: Evolução do Módulo Vendas - Registro Manual, Repasses e Status
*   **Módulo**: Vendas
*   **Arquivos prováveis**:
    *   `src/main/java/dev/davimf/basebot/modules/sales/commands/VendaCommand.java`
    *   `src/main/java/dev/davimf/basebot/modules/sales/records/SalesService.java`
*   **Banco / Migration**: Criação de tabelas no PostgreSQL (para acesso do dashboard): `sales_records`, `sales_products`, `sales_partners`.
*   **Comandos / Interações Discord**:
    *   Comando `/venda registrar <produto> <quantidade> [cliente] [parceria] <comprovante_anexo> [observacao]`
    *   Envio do painel de aprovação interativo no canal de gerência do Discord.
*   **Telas / API do dashboard**: Tela de aprovação e recusa de comprovantes e gráficos de repasse financeiro de taxas de divisão de lucros.
*   **Critério de Pronto**: Registrar a venda insere os dados no banco PostgreSQL com status `PENDENTE`. A aprovação realiza a divisão das porcentagens (vendedor, facção, parceria) e atualiza o status para `APROVADA`.
*   **Risco**: Alto. Processamento matemático de frações decimais em repasses exige uso correto de `BigDecimal` em Java para evitar desvios de arredondamento de centavos.

---

### Task 5: Módulo Polícia - Bate Ponto e Patentes no PostgreSQL
*   **Módulo**: Polícia
*   **Arquivos prováveis**:
    *   `src/main/java/dev/davimf/basebot/modules/police/commands/PontoCommand.java`
    *   `src/main/java/dev/davimf/basebot/modules/police/PoliceModule.java`
*   **Banco / Migration**: Criação de tabelas no PostgreSQL: `police_officers`, `police_time_clock_sessions` e `police_warnings`.
*   **Comandos / Interações Discord**:
    *   Comando `/ponto entrar` e `/ponto sair` via botões no canal de bate-ponto do Discord.
*   **Telas / API do dashboard**: Grid de oficiais com controle de patentes e gráficos de totalização de horas agregadas em tempo real.
*   **Critério de Pronto**: Gravação e expiração de sessões de ponto direto no PostgreSQL compartilhado, viabilizando o consumo imediato dos relatórios pela interface web do dashboard.
*   **Risco**: Médio. A latência de escrita no PostgreSQL pode causar gargalo temporário se o gateway do bot enviar muitas requisições simultâneas de oficiais entrando e saindo de plantão em horários de pico.
