# Spec para IA — Auditoria Completa, Modularização e Roadmap do Bot + Dashboard

## 1. Objetivo geral

Você é uma IA/agente de engenharia responsável por analisar todo o código atual deste projeto e produzir um diagnóstico técnico completo, comparando o estado atual do bot/dashboard com o produto desejado.

O projeto é um bot Discord com dashboard web. Atualmente o objetivo é ter um **bot base completo**, capaz de substituir a maioria dos bots comuns de Discord, e futuramente separar as funcionalidades em módulos comerciais/funcionais.

A arquitetura futura desejada é:

- **Módulo Base**
  - Bot de uso geral para Discord.
  - Tickets, moderação, verificação, economia, diversão, logs, autorole, anti-link, sugestões, automações etc.

- **Módulo Facs**
  - Tudo relacionado a facções de FiveM/GTA RP.
  - Gestão de membros, hierarquia, farm, baú, ações, ausências, parcerias, punições, blacklist, saúde da facção, rankings etc.

- **Módulo Polícia**
  - Tudo relacionado a corporações policiais de FiveM/GTA RP.
  - BOPM, patentes, relatórios, bate-ponto, advertências, promoções, rebaixamentos, exonerações, painel do oficial e painel administrativo.

- **Módulo Vendas**
  - Tudo relacionado a vendas pelo Discord e/ou dashboard.
  - Catálogo de produtos, registro de vendas, prints/comprovantes, aprovações, relatórios, repasses, parcerias, ranking de vendedores etc.

O resultado final da sua análise deve ser um roadmap técnico, incremental e implementável, com visão de produto, arquitetura, banco de dados, APIs, comandos Discord, dashboard, permissões, integrações e prioridades.

---

## 2. Regra importante sobre o concorrente

As referências enviadas sobre o concorrente devem ser usadas somente para entender:

- funcionalidades;
- fluxos;
- nível de acabamento;
- organização de dashboard;
- módulos que fazem sentido comercialmente;
- expectativas de clientes de Discord/FiveM.

Não copie:

- nome;
- identidade visual;
- textos exatos;
- imagens;
- logos;
- banners;
- design pixel-a-pixel;
- branding;
- nomes comerciais específicos.

O objetivo é criar funcionalidades equivalentes ou superiores, com identidade própria do projeto.

---

## 3. Contexto técnico conhecido do projeto

Antes de assumir qualquer coisa, confirme no código.

Contexto provável:

- Linguagem principal do bot: Java.
- Biblioteca Discord: JDA.
- Banco já usado em partes do projeto: SQLite.
- Existem comandos prefixados e slash commands.
- Existe um sistema de permissões próprio, possivelmente usando `BigInteger` para flags.
- Já existem ou existiram sistemas como:
  - tickets;
  - verificação;
  - moderação;
  - setup;
  - comandos administrativos;
  - permissões por cargo;
  - sistema de itens/economia;
  - integração futura com dashboard/pagamentos/licenças.

O dashboard deve ser tratado como parte essencial do produto, não como complemento secundário.

Se a stack do dashboard não estiver clara, identifique:

- framework frontend;
- framework backend;
- autenticação;
- rotas;
- banco;
- ORM;
- estrutura de componentes;
- integração com Discord OAuth;
- integração com o bot.

---

## 4. Sua tarefa principal

Você deve fazer uma varredura completa no projeto e produzir/editar um arquivo de análise.

Crie ou atualize este arquivo:

`docs/MODULE_AUDIT_AND_ROADMAP.md`

Caso ele já exista, preserve o conteúdo útil e acrescente uma seção de atualização com data/hora.

O arquivo deve conter:

1. Resumo executivo.
2. Mapa do projeto atual.
3. Features encontradas no código.
4. Features incompletas.
5. Features ausentes.
6. Features extras encontradas que não estavam neste spec.
7. Comparação por módulo:
   - Base;
   - Facs;
   - Polícia;
   - Vendas.
8. Roadmap por prioridade.
9. Roadmap por fases.
10. Recomendações de arquitetura.
11. Recomendações de banco de dados.
12. Recomendações de dashboard.
13. Recomendações de comandos e interações Discord.
14. Riscos técnicos.
15. Sugestões de melhoria além do concorrente.
16. Plano incremental de implementação sem quebrar o bot atual.

Além disso, se durante a análise você encontrar uma funcionalidade existente que não esteja descrita neste spec, adicione-a na seção:

`## Achados extras da varredura`

Para cada achado extra, informe:

### Nome da feature encontrada

- Status: existente / parcial / quebrada / experimental
- Onde foi encontrada:
  - arquivos/classes/rotas
- O que ela faz hoje:
- Como ela se encaixa no produto:
- Se deve entrar no módulo Base, Facs, Polícia, Vendas ou outro módulo futuro:
- Melhorias recomendadas:

---

## 5. Metodologia obrigatória da varredura

Analise pelo menos:

### Bot Discord

- comandos prefixados;
- slash commands;
- listeners/eventos;
- botões;
- selects;
- modais;
- embeds;
- sistemas de permissões;
- handlers;
- services;
- repositories;
- models/entities;
- arquivos de configuração;
- inicialização do bot;
- registro de comandos;
- integrações com banco;
- logs;
- tratamento de erro;
- agendamentos/tarefas recorrentes;
- sistemas dependentes de servidor/guild.

### Dashboard

- páginas;
- layouts;
- componentes;
- rotas;
- APIs;
- autenticação;
- autorização;
- middleware;
- integração Discord OAuth;
- tela de seleção de servidor;
- permissões por guild;
- formulários;
- tabelas;
- gráficos;
- upload de imagens/arquivos;
- logs/auditoria;
- configurações por servidor;
- integração com bot e banco.

### Banco de dados

- tabelas existentes;
- migrations;
- schemas;
- índices;
- chaves estrangeiras;
- entidades;
- DAOs/repositories;
- risco de dados duplicados;
- ausência de histórico/auditoria;
- campos que precisam virar multi-servidor/multi-módulo.

### Produto/licenciamento

Verifique se há:

- sistema de planos;
- license key;
- validade de licença;
- pagamento único;
- assinatura;
- integração com Stripe/Mercado Pago/Pix;
- liberação por servidor;
- liberação por módulo.

Recomende uma estrutura para habilitar/desabilitar módulos por guild.

---

## 6. Modelo de status das features

Para cada feature, classifique como:

- **Existente e funcional**
- **Existente, mas incompleta**
- **Existente, mas acoplada/difícil de escalar**
- **Existente, mas sem dashboard**
- **Existente, mas sem logs**
- **Existente, mas sem permissões granulares**
- **Parcial**
- **Ausente**
- **Precisa ser refatorada antes de expandir**
- **Deve virar módulo genérico reutilizável**

Use este formato:

### Feature: Nome da feature

- Módulo: Base / Facs / Polícia / Vendas / Compartilhado
- Status atual:
- Arquivos encontrados:
  - `path/do/arquivo`
- Comandos encontrados:
- Rotas/páginas encontradas:
- Tabelas encontradas:
- O que já funciona:
- O que falta:
- Riscos:
- Recomendação:
- Prioridade: P0 / P1 / P2 / P3

Prioridades:

- **P0**: fundação obrigatória, bloqueia vários módulos.
- **P1**: feature essencial para vender/usar.
- **P2**: melhoria importante.
- **P3**: acabamento, automação ou diferencial.

---

## 7. Arquitetura modular desejada

O projeto hoje pode estar monolítico, mas o roadmap deve preparar separação futura em módulos.

Evite recomendar duplicação como:

- `FactionAbsenceService`
- `PoliceAbsenceService`
- `FactionTicketService`
- `PoliceTicketService`

Prefira uma base genérica com especializações:

- `Organization`
- `OrganizationType`
- `OrganizationMember`
- `HierarchyRole`
- `PermissionProfile`
- `AbsenceRequest`
- `TicketConfig`
- `LogConfig`
- `SetRequest`
- `ReportTemplate`
- `ReportEntry`
- `Inventory`
- `InventoryMovement`
- `ActionEvent`
- `SalesRecord`

Tipos iniciais de organização:

- `GENERAL_SERVER`
- `FACTION`
- `POLICE`
- `STORE`
- `COMMUNITY`

Módulos comerciais/funcionais:

- `BASE`
- `FACS`
- `POLICE`
- `SALES`

Estrutura conceitual recomendada:

```text
Guild
 └── Modules enabled
      ├── BaseModule
      ├── FacsModule
      ├── PoliceModule
      └── SalesModule

Organization
 ├── type: FACTION | POLICE | COMMUNITY | STORE
 ├── guildId
 ├── name
 ├── publicSlug
 ├── logo
 ├── theme
 └── settings
```

O dashboard deve permitir configurar módulos por servidor, mas o backend deve impedir acesso a módulo não habilitado/licenciado.

---

# 8. Módulo Base

## 8.1 Objetivo

O Módulo Base é o bot principal de uso geral. Ele deve substituir, sozinho, vários bots comuns de Discord.

Ele deve ser útil para servidores comuns, comunidades, lojas, facções, organizações, suporte e servidores privados.

---

## 8.2 Sistemas essenciais do Módulo Base

### 8.2.1 Setup geral

Verificar se já existe `/setup`.

Funcionalidades desejadas:

- setup guiado por slash command;
- setup pelo dashboard;
- criação automática de canais/categorias opcionais;
- seleção de idioma;
- seleção de tema visual dos embeds;
- canais de logs;
- cargos administrativos;
- cargos de membro;
- prefixo, se ainda existir comando prefixado;
- ativar/desativar módulos;
- salvar configuração por servidor;
- validação de permissões do bot;
- painel de diagnóstico mostrando permissões faltantes.

Dashboard:

- página de configurações gerais;
- status de conexão com Discord;
- status dos módulos;
- botão de ressincronizar servidor;
- preview dos embeds;
- testes de envio em canal.

---

### 8.2.2 Sistema de permissões

O projeto já parece ter permissões próprias.

Audite:

- como as permissões são armazenadas;
- se usam `BigInteger`;
- como os cargos recebem flags;
- se há owner bypass;
- se existe diferença entre permissão do Discord e permissão interna;
- se slash commands respeitam o sistema;
- se dashboard respeita o sistema.

Funcionalidades desejadas:

- permissões granulares por módulo;
- permissões por cargo;
- permissões por usuário;
- templates de permissão;
- perfis prontos:
  - Dono;
  - Administrador;
  - Moderador;
  - Suporte;
  - Gerente de Facção;
  - Oficial Admin;
  - Vendedor;
  - Visualizador;
- auditoria de alterações;
- dashboard para editar permissões.

Exemplo de permissões:

- `BASE_ADMIN`
- `BASE_MODERATION`
- `BASE_TICKET_MANAGE`
- `BASE_LOG_VIEW`
- `BASE_ECONOMY_MANAGE`
- `FACS_MANAGE_MEMBERS`
- `FACS_MANAGE_FARM`
- `FACS_MANAGE_CHEST`
- `FACS_MANAGE_ACTIONS`
- `POLICE_MANAGE_OFFICERS`
- `POLICE_MANAGE_REPORTS`
- `POLICE_MANAGE_TIME_CLOCK`
- `SALES_REGISTER`
- `SALES_APPROVE`
- `SALES_VIEW_ALL`
- `SALES_CONFIGURE_PRODUCTS`

---

### 8.2.3 Tickets

Sistema geral de tickets.

Audite se já existe e qual estado.

Funcionalidades desejadas:

- painel de tickets com botão/menu;
- múltiplas categorias;
- perguntas pré-ticket;
- modal de abertura;
- criar canal privado;
- adicionar/remover membros;
- assumir ticket;
- transferir ticket;
- prioridade;
- tags;
- fechamento com motivo;
- transcript;
- avaliação pós-atendimento;
- logs;
- limite de tickets abertos por usuário;
- cooldown;
- tickets anônimos opcionais para denúncias;
- integração com dashboard.

Dashboard:

- configurar painel;
- configurar categorias;
- configurar cargos atendentes;
- visualizar tickets abertos;
- histórico;
- transcripts;
- métricas:
  - tempo médio de atendimento;
  - tickets por categoria;
  - atendente mais ativo;
  - tickets resolvidos.

Diferenciais criativos:

- SLA por categoria.
- Respostas rápidas configuráveis.
- Macros de atendimento.
- Sistema de tags internas.
- Reabertura controlada.
- Exportação de transcript em HTML.

---

### 8.2.4 Moderação

Funcionalidades esperadas:

- ban;
- unban;
- kick;
- mute/timeout;
- unmute;
- warn/advertência;
- clear/purge;
- slowmode;
- lock/unlock canal;
- lockdown do servidor;
- histórico de punições;
- motivos obrigatórios;
- provas/anexos;
- expiração automática de punições;
- escalonamento automático por reincidência;
- logs;
- dashboard de punições.

Diferenciais criativos:

- sistema de strikes configurável;
- automod com regras;
- moderação por contexto;
- painel “casos de moderação”;
- recurso de apelação via ticket;
- mod notes internas por usuário.

---

### 8.2.5 Verificação

O bot já possui verificação, mas deve ser auditada.

Funcionalidades desejadas:

- painel de verificação;
- cargo de verificado;
- cargos removidos/aplicados;
- canal de verificação;
- logs;
- bloqueio de canais para não verificados;
- restauração segura de permissões;
- integração com dashboard;
- reset/reenvio de painel;
- bypass por cargo.

Também considerar a feature já planejada de lockdown de canais:

- ao ligar verificação, esconder canais abertos para `@everyone`;
- deixar visíveis apenas para membros/verificados;
- não alterar canais de log ou excluídos;
- ao desligar, reverter somente canais que continuam exatamente como o bot deixou;
- armazenar snapshot por canal;
- evitar sobrescrever alterações manuais de admins.

---

### 8.2.6 Anti-Robô / Captcha

Este sistema deve ser complementar à verificação atual.

Não substitua a verificação existente automaticamente.

Funcionalidades desejadas:

- captcha em imagem;
- código aleatório;
- resposta por select/modal;
- expiração;
- limite de tentativas;
- logs de acerto/erro;
- bloqueio por excesso de erro;
- modo:
  - independente;
  - antes da verificação;
  - depois da verificação;
- dificuldade configurável;
- estatísticas.

Dashboard:

- ativar/desativar;
- configurar canal/painel;
- configurar tentativas;
- configurar ação em falha:
  - logar;
  - timeout;
  - kick;
  - ban;
- taxa de sucesso;
- usuários suspeitos.

---

### 8.2.7 Anti-links e automod

Funcionalidades esperadas:

- bloquear links;
- whitelist de domínios;
- blacklist de domínios;
- cargos ignorados;
- canais ignorados;
- punição configurável;
- logs;
- detectar convite Discord;
- detectar spam;
- detectar flood;
- detectar caps lock excessivo;
- detectar menções em massa;
- detectar palavras proibidas;
- detectar raid básica.

Diferenciais criativos:

- modo “aprendizado”, apenas loga sem punir.
- regras por canal.
- regra temporária, por exemplo durante evento.
- score de risco por usuário.
- proteção anti-conta-nova.

---

### 8.2.8 Logs gerais

Sistema unificado de logs.

Eventos desejados:

- entrada/saída de membros;
- mensagem editada/apagada;
- punições;
- cargos adicionados/removidos;
- canais criados/editados/removidos;
- tickets;
- verificação;
- anti-robô;
- anti-link;
- sugestões;
- economia;
- alterações no dashboard;
- alterações de permissões;
- erros internos do bot.

Dashboard:

- configurar canais de log por tipo;
- ativar/desativar tipos;
- consultar logs internos;
- filtros por usuário/evento/data.

---

### 8.2.9 Entrada/Saída, boas-vindas e autorole

Funcionalidades esperadas:

- mensagem de boas-vindas;
- mensagem de saída;
- autorole;
- autorole por condição;
- detectar reentrada;
- registrar histórico;
- DM de boas-vindas;
- embed customizável;
- placeholders:
  - `{user}`;
  - `{server}`;
  - `{member_count}`;
  - `{date}`;
- logs.

Diferenciais criativos:

- onboarding com botões;
- seleção de cargos por interesses;
- cargos temporários para novos membros;
- alerta para conta recém-criada.

---

### 8.2.10 Sugestões

Sistema inspirado na referência enviada.

Funcionalidades esperadas:

- usuário envia sugestão;
- bot publica embed no canal configurado;
- dois botões:
  - voto positivo;
  - voto negativo;
- exibir porcentagem de votos;
- exibir total de votos;
- impedir voto duplicado;
- permitir trocar voto se configurado;
- criar thread/tópico de debate automaticamente;
- botão/link para debater;
- status:
  - aberta;
  - em análise;
  - aprovada;
  - recusada;
  - implementada;
  - arquivada;
- logs.

Dashboard:

- listar sugestões;
- buscar por autor/texto/status;
- ver votos;
- ver thread;
- aprovar/recusar/arquivar;
- configurar canal;
- configurar cargos moderadores;
- permitir sugestões anônimas ou não.

Diferenciais criativos:

- categorias de sugestão;
- votação com peso por cargo;
- changelog automático para sugestões implementadas;
- ranking de usuários com sugestões aprovadas.

---

### 8.2.11 Economia

Audite o que já existe.

Funcionalidades desejadas:

- carteira;
- banco;
- saldo;
- daily;
- work;
- pay;
- ranking;
- loja;
- inventário;
- itens;
- aliases de itens;
- transferências;
- logs;
- anti-abuso;
- cooldown;
- configuração por servidor.

Dashboard:

- editar moeda;
- editar loja;
- editar itens;
- ver ranking;
- ajustar saldo;
- histórico de transações.

Diferenciais criativos:

- cargos compráveis;
- itens consumíveis;
- recompensas por atividade;
- integração com tickets/vendas/eventos;
- imposto configurável;
- leilões/sorteios.

---

### 8.2.12 Diversão e engajamento

Funcionalidades possíveis:

- comandos de interação;
- roleta;
- sorteios;
- enquetes;
- ranking de mensagens;
- XP/level;
- reputação;
- casamento/amizade;
- perfil do usuário;
- aniversários;
- lembretes;
- AFK.

Dashboard:

- ativar/desativar comandos;
- configurar cooldowns;
- configurar canais permitidos;
- ranking e estatísticas.

---

### 8.2.13 Avisos/anúncios

Funcionalidades desejadas:

- criar aviso;
- agendar aviso;
- mencionar cargo;
- fixar mensagem;
- expirar aviso;
- aviso recorrente;
- preview de embed;
- logs.

Dashboard:

- criar/editar avisos;
- calendário de avisos;
- histórico;
- estatísticas de leitura se possível.

---

### 8.2.14 Sistema de formulários

Recomenda-se criar um sistema genérico de formulários para reaproveitar em:

- tickets;
- pedir set;
- recrutamento;
- ausências;
- vendas;
- BOPM;
- relatórios policiais;
- solicitações internas.

Funcionalidades:

- campos customizáveis;
- tipos de campo:
  - texto curto;
  - texto longo;
  - número;
  - data;
  - select;
  - checkbox;
  - upload;
  - usuário Discord;
  - cargo Discord;
  - canal Discord;
- validações;
- obrigatoriedade;
- respostas salvas;
- aprovação/recusa opcional.

---

# 9. Módulo Facs

## 9.1 Objetivo

O Módulo Facs é focado em facções de FiveM/GTA RP.

Ele deve permitir que líderes e staffs controlem a facção de forma centralizada, tanto pelo Discord quanto pelo dashboard.

---

## 9.2 Dashboard geral da facção

Referência enviada mostra uma home com:

- membros ativos;
- farms registrados;
- parcerias ativas;
- ações da semana;
- saúde da facção;
- ranking histórico;
- top 5 farm;
- top 5 ações;
- avisos ativos.

Funcionalidades desejadas:

### Cards principais

- membros ativos;
- membros totais;
- membros em ausência;
- farms da semana;
- ações da semana;
- vendas do mês;
- movimentações do baú;
- parcerias ativas;
- punições ativas;
- alertas críticos.

### Saúde da facção

Pontuação 0-100 baseada em critérios configuráveis:

- farm semanal;
- presença em ações;
- ausências ok;
- punições;
- vendas;
- atividade de membros;
- participação geral;
- cumprimento de metas.

Dashboard deve exibir:

- score geral;
- barras por critério;
- status:
  - saudável;
  - atenção;
  - crítico;
- sugestões automáticas de melhoria.

Diferencial criativo:

- “Diagnóstico da facção” com recomendações:
  - “muitos membros sem farm”;
  - “baixa presença em ações”;
  - “ranking concentrado em poucos membros”;
  - “muitas ausências pendentes”.

---

## 9.3 Informações da facção

Página com:

- regras;
- uniforme;
- numeração da rádio;
- localização do QG.

Funcionalidades adicionais:

- hierarquia visível;
- contatos importantes;
- links úteis;
- código de conduta;
- aviso fixo;
- mapa/imagem do QG;
- imagens de uniformes;
- histórico de alterações;
- configuração por permissão.

Dashboard:

- editar todas as informações;
- rich text/markdown;
- upload de imagens;
- preview da página pública.

---

## 9.4 Hierarquia da facção

Referência enviada mostra:

- cargos da hierarquia;
- arrastar para reordenar;
- adicionar cargo;
- atualizar cargos;
- configurar permissões;
- deletar cargo.

Funcionalidades desejadas:

- cargos/patentes da facção;
- cor;
- ícone;
- ordem;
- permissões por cargo;
- sincronização com cargos do Discord;
- criar cargo no Discord opcionalmente;
- atualizar cargos existentes;
- histórico de promoções/rebaixamentos;
- logs;
- importação de cargos do Discord.

Diferenciais criativos:

- templates de hierarquia por tipo de facção;
- limite de membros por cargo;
- requisitos para promoção;
- trilha de progressão automática sugerida.

---

## 9.5 Membros

Referência enviada mostra:

- lista de membros;
- total;
- ativos;
- com avisos;
- busca;
- filtro por status;
- filtro por cargo;
- filtro por metas;
- ordenação por nome;
- data de entrada;
- sincronizar Discord;
- detalhes do membro.

Funcionalidades desejadas:

- perfil interno do membro;
- nome Discord;
- ID Discord;
- nome RP;
- passaporte/ID FiveM;
- telefone RP;
- cargo/patente;
- status:
  - ativo;
  - ausente;
  - punido;
  - inativo;
  - exonerado;
  - blacklist;
- data de entrada;
- recrutador;
- histórico de cargos;
- histórico de farm;
- histórico de ações;
- histórico de vendas;
- histórico de punições;
- observações internas;
- anexos/provas.

Dashboard:

- listar;
- buscar;
- filtrar;
- exportar CSV;
- ver detalhes;
- editar;
- promover/rebaixar;
- exonerar;
- aplicar punição;
- colocar/remover blacklist;
- sincronizar com Discord.

Diferencial criativo:

- score individual do membro;
- alerta de inatividade;
- alerta de meta atrasada;
- timeline do membro.

---

## 9.6 Recrutamento

Mesmo que não tenha sido detalhado nos prints, apareceu no menu.

Funcionalidades desejadas:

- formulário de recrutamento;
- perguntas configuráveis;
- canal de logs;
- aprovação/recusa;
- aplicação automática de cargos;
- registro como membro;
- histórico de candidatos;
- integração com blacklist;
- entrevista por ticket;
- status:
  - novo;
  - em análise;
  - aprovado;
  - recusado;
  - desistiu.

Diferenciais criativos:

- formulário público;
- pontuação de candidatura;
- checklist de entrevista;
- mensagem automática ao aprovado.

---

## 9.7 Blacklist

Funcionalidades desejadas:

- adicionar usuário à blacklist;
- motivo;
- provas;
- responsável;
- data;
- duração opcional;
- blacklist permanente ou temporária;
- impedir recrutamento;
- alertar ao tentar entrar;
- logs;
- integração com entrada/saída.

Dashboard:

- listar;
- buscar;
- filtrar;
- remover;
- exportar;
- ver provas.

---

## 9.8 Punições

Funcionalidades desejadas:

- advertências;
- suspensões;
- multas internas;
- avisos;
- remoção automática após prazo, se configurado;
- histórico por membro;
- provas/anexos;
- responsável;
- gravidade;
- logs.

Dashboard:

- aplicar punição;
- remover punição;
- aprovar/recusar recurso;
- histórico;
- ranking de reincidência.

Diferencial criativo:

- sistema de pontos de punição;
- punição expira automaticamente;
- escalonamento:
  - 1 advertência: aviso;
  - 2 advertências: suspensão;
  - 3 advertências: exoneração sugerida.

---

## 9.9 Ausências

Referência enviada mostra:

- total;
- pendentes;
- ativas;
- aprovadas;
- registrar minha ausência;
- data de início;
- data de término;
- motivo;
- histórico;
- busca;
- filtro por status.

Funcionalidades desejadas:

- solicitar ausência;
- aprovar/recusar;
- motivo da recusa;
- ausência ativa automaticamente entre datas;
- isentar meta durante ausência;
- logs;
- notificação ao membro;
- notificação à staff;
- histórico.

Diferenciais criativos:

- limite de dias por mês;
- necessidade de comprovação opcional;
- calendário de ausências;
- alerta de ausência vencendo.

---

## 9.10 Perímetros / Ações

Referência enviada mostra perímetros de ação com:

- nome;
- imagem/mapa;
- quantidade de policiais;
- quantidade de bandidos;
- tags;
- armamento permitido;
- busca;
- adicionar perímetro.

Funcionalidades desejadas:

- cadastrar perímetros;
- imagem do local;
- nome;
- descrição;
- tipo de ação;
- quantidade mínima/máxima por lado;
- armamentos permitidos;
- regras;
- tags:
  - negociação;
  - refém;
  - confronto;
  - fuga;
  - banco;
  - lojinha;
  - arsenal etc.
- anexos;
- logs.

Diferencial criativo:

- biblioteca de estratégias por perímetro;
- checklist antes da ação;
- mapa com zonas;
- estatísticas por perímetro.

---

## 9.11 Registros de ações

Referência enviada mostra:

- aba registros;
- aba estatísticas;
- buscar ação;
- filtrar status;
- ações em aberto;
- card da ação;
- status aberta;
- tempo restante;
- participantes;
- botão detalhes;
- botão participar;
- botão concluir ação;
- deletar;
- estatísticas com vitórias/derrotas;
- desempenho por tipo;
- destaques;
- top vencedores;
- mais participativos.

Funcionalidades desejadas:

- criar ação;
- participar da ação;
- limitar participantes;
- horário;
- duração;
- perímetro;
- tipo;
- status:
  - aberta;
  - em andamento;
  - concluída;
  - cancelada;
- resultado:
  - vitória;
  - derrota;
  - empate;
  - inconclusiva;
- presença;
- reação/botão para ação no Discord;
- logs;
- ranking;
- histórico.

Dashboard:

- criar ação;
- listar ações;
- estatísticas;
- ranking;
- gráficos;
- filtros por data/tipo/status/resultado;
- ver participantes;
- concluir ação;
- editar resultado.

Diferenciais criativos:

- avaliação pós-ação;
- MVP da ação;
- observações estratégicas;
- relatório automático;
- taxa de vitória por perímetro;
- membros mais ausentes em ação.

---

## 9.12 Farm

Referência enviada mostra:

- total farmado;
- metas atingidas;
- itens semanais;
- registrar farm;
- gerenciar itens;
- abas:
  - meu farm;
  - ranking;
  - histórico;
  - todos;
- progresso atual;
- registros recentes.

Funcionalidades desejadas:

- cadastro de itens de farm;
- metas semanais;
- metas por cargo;
- metas individuais;
- registro de farm;
- aprovação opcional;
- print/prova opcional;
- histórico;
- ranking semanal/mensal;
- reset semanal;
- gráficos;
- logs.

Dashboard:

- registrar farm;
- gerenciar itens;
- configurar metas;
- ver ranking;
- ver histórico;
- exportar.

Discord:

- comando para registrar farm;
- botão/modal;
- logs em canal;
- resumo semanal.

Diferenciais criativos:

- cálculo de meta proporcional por ausência;
- alerta de meta incompleta;
- previsão de quem vai bater meta;
- ranking por eficiência;
- medalhas/badges.

---

## 9.13 Baú da facção

Referência enviada mostra:

- total de itens;
- tipos diferentes;
- movimentações hoje;
- entrada;
- saída;
- busca;
- abas:
  - inventário;
  - histórico;
  - configurações;
- itens no baú.

Funcionalidades desejadas:

- inventário da facção;
- entrada de item;
- saída de item;
- responsável;
- motivo;
- quantidade;
- print/prova opcional;
- histórico auditável;
- busca;
- filtros;
- logs;
- configuração de itens.

Dashboard:

- inventário;
- histórico;
- configurações;
- entrada/saída;
- relatórios.

Diferenciais criativos:

- estoque mínimo;
- alerta de item acabando;
- movimentação pendente de aprovação;
- categorias de itens;
- valor estimado do baú;
- integração com farm e vendas.

---

## 9.14 Parcerias

Referência enviada cita parcerias ativas e contatos protegidos por permissão.

Funcionalidades desejadas:

- cadastro de parceiros;
- nome;
- contato;
- tipo;
- status;
- observações;
- termos da parceria;
- percentual de venda/parceria;
- histórico;
- permissões para visualizar contato;
- logs.

Dashboard:

- listar;
- cadastrar;
- editar;
- pausar/encerrar;
- vincular vendas;
- relatórios.

Diferenciais criativos:

- contatos protegidos/mascarados;
- vencimento de parceria;
- ranking de parceiros;
- alertas de parceria inativa.

---

## 9.15 Avisos da facção

Funcionalidades desejadas:

- avisos ativos;
- avisos agendados;
- avisos expirados;
- prioridade;
- público-alvo;
- logs;
- publicação no Discord.

Dashboard:

- criar;
- editar;
- agendar;
- expirar;
- preview.

---

## 9.16 Snapshot mensal / ranking histórico

Referência enviada cita snapshot mensal automático.

Funcionalidades desejadas:

- congelar ranking no dia configurado;
- salvar mês/ano;
- MVP;
- ranking de farm;
- ranking de ações;
- ranking de baú;
- ranking de vendas;
- ranking geral;
- histórico mensal;
- comparação entre meses.

Diferenciais criativos:

- premiações configuráveis;
- mural de destaques;
- exportação de relatório mensal;
- gráfico de evolução da facção.

---

## 9.17 Página pública da facção

Referência enviada cita página pública personalizável `/suafaccao`.

Funcionalidades desejadas:

- slug público;
- identidade visual;
- logo;
- banner;
- regras públicas;
- status da facção;
- ranking público opcional;
- formulário de recrutamento;
- membros públicos opcionais;
- parcerias públicas opcionais.

Dashboard:

- personalizar tema;
- controlar o que é público;
- preview;
- domínio/slug.

---

## 9.18 Banco financeiro interno da facção

Além do baú e das vendas, o módulo Facs deve ter um sistema financeiro interno.

Funcionalidades desejadas:

- saldo da facção;
- entradas;
- saídas;
- motivo da movimentação;
- responsável;
- comprovante opcional;
- categoria:
  - venda;
  - multa;
  - doação;
  - compra de armamento;
  - pagamento de membro;
  - parceria;
  - outro;
- aprovação opcional;
- histórico auditável;
- relatórios.

Integrações:

- vendas aprovadas geram entrada/repasse;
- multas internas podem gerar entrada;
- baú pode gerar movimentação financeira se itens tiverem valor;
- dashboard mostra saúde financeira.

---

# 10. Módulo Polícia

## 10.1 Objetivo

O Módulo Polícia é uma área nova no projeto, pois atualmente não existe nada de polícia no bot.

Ele deve atender corporações policiais de FiveM/GTA RP com sistemas integrados e configuráveis.

---

## 10.2 Sistemas principais

### 10.2.1 Registro de BOPM

Sistema de boletim/registro policial.

Funcionalidades desejadas:

- criar BOPM;
- número automático;
- oficial responsável;
- envolvidos;
- descrição;
- local;
- data/hora;
- artigos/crimes;
- provas/anexos;
- status;
- histórico;
- logs.

Dashboard:

- listar BOPMs;
- buscar;
- filtrar por oficial/status/data/tipo;
- ver detalhes;
- editar se permitido;
- exportar.

Diferenciais criativos:

- template configurável;
- anexos múltiplos;
- assinatura de oficial;
- revisão por superior;
- PDF/exportação.

---

### 10.2.2 Hierarquia / Patentes

Funcionalidades desejadas:

- cadastro de patentes;
- ordem hierárquica;
- permissões por patente;
- sincronização com Discord;
- promoção;
- rebaixamento;
- exoneração;
- histórico;
- logs.

Pode reutilizar a base genérica de hierarquia do Módulo Facs.

---

### 10.2.3 Painel do Oficial

Funcionalidades desejadas:

- ver próprios relatórios;
- ver próprias horas;
- ver BOPMs criados;
- ver advertências;
- ver ausências;
- ver histórico de patente;
- ver metas;
- registrar relatório;
- bater ponto;
- solicitar ausência.

Permissão:

- oficial comum vê apenas seus dados;
- superior pode ver subordinados se tiver permissão.

---

### 10.2.4 Painel Administrativo

Funcionalidades desejadas:

- listar oficiais;
- promover;
- rebaixar;
- exonerar;
- aplicar advertência;
- remover advertência;
- aprovar ausências;
- aprovar pedidos de set;
- consultar horas;
- consultar relatórios;
- configurar patentes;
- configurar permissões;
- configurar canais/logs;
- gerar relatórios.

Diferenciais criativos:

- visão de produtividade da corporação;
- alerta de oficial inativo;
- ranking de horas;
- ranking de relatórios;
- painel de corregedoria.

---

### 10.2.5 Sistema de Relatórios

Tipos citados:

- ação;
- apreensão;
- prisão;
- multa.

Recomende arquitetura flexível com templates de relatório.

Campos comuns:

- oficial responsável;
- envolvidos;
- data/hora;
- local;
- descrição;
- provas/anexos;
- observações.

Relatório de prisão:

- preso;
- ID/passaporte;
- crimes;
- tempo;
- multa;
- itens apreendidos;
- provas.

Relatório de apreensão:

- item;
- quantidade;
- suspeito;
- local;
- destino do item;
- provas.

Relatório de multa:

- cidadão;
- valor;
- motivo;
- artigo;
- status de pagamento.

Relatório de ação:

- tipo de ação;
- participantes;
- resultado;
- perímetro;
- observações.

Diferenciais criativos:

- campos customizáveis por corporação;
- aprovação de relatório;
- correção solicitada por superior;
- exportação mensal;
- estatísticas por oficial.

---

### 10.2.6 Bate ponto manual

Funcionalidades desejadas:

- iniciar ponto;
- encerrar ponto;
- totalizar horas;
- histórico diário/semanal/mensal;
- ajuste manual por admin;
- justificativa de ajuste;
- logs;
- ranking de horas.

Dashboard:

- meu ponto;
- ponto dos oficiais;
- relatórios de horas;
- exportar CSV;
- metas de horas.

Diferenciais criativos:

- alerta de ponto aberto há muito tempo;
- limite máximo por sessão;
- pausa;
- correção solicitada pelo oficial;
- aprovação de correção.

---

### 10.2.7 Ausências

Reutilizar arquitetura do sistema de ausências de Facs.

Adaptar para:

- oficiais;
- isenção de horas/metas;
- aprovação por superior;
- calendário de escala.

---

### 10.2.8 Pedir Set

Funcionalidades desejadas:

- solicitação de set/cargo;
- formulário;
- aprovação;
- recusa com motivo;
- aplicar cargo no Discord;
- criar registro de oficial;
- logs.

Campos possíveis:

- nome RP;
- passaporte;
- telefone;
- patente solicitada;
- recrutador;
- observações;
- prova/anexo.

---

### 10.2.9 Entrada/Saída

Funcionalidades desejadas:

- registrar entrada na corporação;
- registrar saída;
- detectar saída do Discord;
- manter histórico;
- exonerar;
- readmitir;
- logs;
- autorole.

---

### 10.2.10 Advertências e corregedoria

Além do que foi enviado, recomenda-se adicionar:

- advertências;
- sindicâncias;
- processos internos;
- denúncias contra oficiais;
- status de apuração;
- provas;
- responsáveis;
- histórico;
- logs;
- recurso/apelação.

---

### 10.2.11 Escalas e plantões

Diferencial criativo recomendado:

- criar escalas;
- plantões;
- responsáveis por turno;
- presença em operação;
- faltas;
- integração com bate ponto;
- calendário.

---

### 10.2.12 Dashboard geral da corporação

Funcionalidades desejadas:

- oficiais ativos;
- oficiais ausentes;
- horas registradas na semana;
- relatórios do mês;
- prisões/multas/apreensões;
- advertências ativas;
- solicitações pendentes;
- ranking de horas;
- ranking de relatórios;
- alertas administrativos.

Diferenciais:

- saúde da corporação;
- mapa de produtividade;
- alertas de baixa presença;
- relatório mensal automático.

---

# 11. Módulo Vendas

## 11.1 Objetivo

O Módulo Vendas é focado em vendas pelo Discord e pelo dashboard.

Ele pode ser usado por facções, lojas, comunidades RP ou qualquer servidor que venda itens/serviços.

---

## 11.2 Registro de vendas

Referência enviada mostra:

- página Vendas;
- botão Registrar venda;
- abas:
  - minhas vendas;
  - aprovações;
  - todas as vendas;
  - configurar;
- modal com:
  - item vendido;
  - quantidade;
  - cliente opcional;
  - parceria;
  - print obrigatório;
  - observação opcional.

Funcionalidades desejadas:

- registrar venda;
- selecionar produto do catálogo;
- quantidade;
- cliente;
- parceria;
- upload de print/comprovante;
- observações;
- cálculo automático;
- status:
  - pendente;
  - aprovada;
  - recusada;
  - cancelada;
- logs.

Discord:

- comando/modal para registrar venda;
- upload de print;
- notificação para aprovadores;
- botão aprovar/recusar;
- DM ao vendedor com resultado.

Dashboard:

- minhas vendas;
- aprovações;
- todas as vendas;
- configurar produtos;
- filtros;
- relatórios.

---

## 11.3 Catálogo de produtos

Funcionalidades desejadas:

- produto;
- descrição;
- preço;
- categoria;
- estoque opcional;
- comissão;
- percentual da facção/loja;
- percentual do vendedor;
- percentual de parceria;
- status ativo/inativo;
- imagem opcional.

Diferenciais criativos:

- combos/pacotes;
- desconto;
- preço por quantidade;
- variações;
- produtos com estoque ligado ao baú.

---

## 11.4 Aprovação de vendas

Funcionalidades desejadas:

- fila de aprovação;
- aprovar;
- recusar;
- pedir correção;
- motivo;
- visualizar print;
- logs;
- permissão granular;
- histórico auditável.

Diferenciais criativos:

- aprovação automática abaixo de certo valor;
- dupla aprovação para valores altos;
- detecção de comprovante duplicado;
- marcação de venda suspeita.

---

## 11.5 Relatórios e filtros

Referência enviada mostra:

- busca por vendedor, item, cliente, parceria ou observação;
- filtro por vendedor;
- filtro por item;
- filtro por status;
- filtro por parceria;
- data de/até;
- total vendido aprovado;
- devido à facção;
- pago aos membros;
- top vendedores.

Funcionalidades desejadas:

- relatório diário;
- semanal;
- mensal;
- por vendedor;
- por produto;
- por parceria;
- por status;
- exportação CSV;
- gráficos;
- ranking.

Diferenciais criativos:

- previsão de fechamento do mês;
- comparação com mês anterior;
- metas de vendas;
- comissão pendente/paga;
- fechamento financeiro mensal.

---

## 11.6 Repasses e financeiro

Funcionalidades desejadas:

- calcular automaticamente:
  - total da venda;
  - parte da organização;
  - parte do vendedor;
  - parte da parceria;
- marcar como pago;
- marcar como depositado;
- histórico;
- logs;
- integração com banco financeiro da facção.

Diferenciais criativos:

- carteira interna do vendedor;
- solicitação de saque;
- fechamento por lote;
- recibo automático;
- ranking por lucro líquido.

---

## 11.7 Parcerias comerciais

Funcionalidades desejadas:

- parceiro;
- percentual;
- contato protegido;
- vendas vinculadas;
- comissão;
- histórico;
- status;
- contrato/observação.

---

## 11.8 Vendas como módulo independente

O Módulo Vendas não deve depender obrigatoriamente do Módulo Facs.

Ele deve funcionar em:

- servidor comum;
- servidor de loja;
- servidor RP;
- servidor de facção;
- servidor de polícia, se houver venda interna;
- comunidade que vende serviços/produtos digitais.

Quando integrado ao Módulo Facs, pode puxar membros, parcerias e banco financeiro.

Quando usado sozinho, deve criar sua própria organização do tipo `STORE` ou operar diretamente por `guildId`.

---

# 12. Sistemas compartilhados entre módulos

Alguns sistemas não devem pertencer exclusivamente a um módulo.

Crie recomendações para torná-los compartilhados:

## 12.1 Formulários

Usado em:

- ticket;
- recrutamento;
- pedir set;
- ausência;
- venda;
- BOPM;
- relatório policial;
- punição;
- sugestão.

## 12.2 Aprovações

Fluxo genérico:

- entidade pendente;
- aprovador;
- decisão;
- motivo;
- data;
- histórico;
- logs.

Usado em:

- vendas;
- ausências;
- pedidos de set;
- relatórios;
- recrutamento;
- correções de ponto.

## 12.3 Anexos/provas

Sistema único para:

- prints de venda;
- provas de punição;
- anexos de relatório;
- comprovantes;
- imagens de perímetro;
- imagens de uniforme;
- transcripts.

## 12.4 Logs/auditoria

Sistema interno mais logs no Discord.

Cada ação crítica deve salvar:

- guildId;
- organizationId, se houver;
- module;
- actorId;
- targetId;
- action;
- before;
- after;
- metadata;
- timestamp.

## 12.5 Permissões

Sistema único por módulo e organização.

## 12.6 Notificações

Notificar por:

- Discord channel;
- DM;
- dashboard notification;
- webhook.

Eventos:

- venda pendente;
- ausência pendente;
- set pendente;
- punição aplicada;
- ação criada;
- meta atrasada;
- ticket aberto;
- sugestão aprovada.

---

# 13. Dashboard desejado

O dashboard deve ser completo e profissional.

## 13.1 Estrutura geral

Páginas globais:

- login Discord;
- seleção de servidor;
- dashboard do servidor;
- módulos habilitados;
- configurações;
- permissões;
- logs;
- billing/licença;
- minha conta.

Páginas por módulo:

### Base

- visão geral;
- tickets;
- moderação;
- verificação;
- anti-robô;
- logs;
- autorole;
- anti-link;
- sugestões;
- economia;
- avisos;
- diversão/engajamento;
- permissões.

### Facs

- dashboard;
- informações;
- hierarquia;
- membros;
- recrutamento;
- blacklist;
- punições;
- ausências;
- perímetros;
- ações;
- farm;
- baú;
- vendas, se módulo habilitado;
- parcerias;
- avisos;
- rankings;
- página pública.

### Polícia

- dashboard;
- oficiais;
- patentes;
- BOPM;
- relatórios;
- bate ponto;
- ausências;
- pedir set;
- advertências;
- corregedoria;
- escalas;
- logs;
- configurações.

### Vendas

- minhas vendas;
- aprovações;
- todas as vendas;
- produtos;
- parcerias;
- financeiro;
- relatórios;
- configurações.

---

## 13.2 Requisitos de UX

- dark mode;
- responsivo;
- sidebar com módulos;
- breadcrumbs;
- cards de métricas;
- tabelas com busca/filtro/paginação;
- gráficos;
- modais;
- toasts;
- empty states bem escritos;
- loading states;
- skeletons;
- tratamento de erro;
- confirmação para ações destrutivas;
- preview de embeds;
- logs de alteração.

---

## 13.3 Segurança do dashboard

Audite e recomende:

- autenticação via Discord OAuth;
- verificação se o usuário tem permissão no servidor;
- checar permissões internas do bot;
- impedir acesso a guild sem autorização;
- CSRF, se aplicável;
- rate limit;
- validação no backend;
- logs de ações administrativas;
- evitar confiar só no frontend;
- separar permissões por módulo.

---

## 13.4 Página de configuração por módulo

Cada módulo deve ter uma página de configuração com:

- status ativo/inativo;
- licença/plano;
- permissões;
- canais de logs;
- cargos administrativos;
- comandos habilitados;
- textos/embeds;
- integrações;
- reset/republicar painéis;
- diagnóstico de permissões do bot.

---

# 14. Licenciamento e planos

O produto será modular.

Recomende arquitetura para planos:

- `Plan`
- `PlanModule`
- `GuildSubscription`
- `LicenseKey`
- `ModuleEntitlement`

Estados:

- trial;
- ativo;
- vencido;
- suspenso;
- cancelado;
- vitalício, se existir;
- teste interno.

Cada guild pode ter:

- módulo Base;
- módulo Facs;
- módulo Polícia;
- módulo Vendas;
- combinações.

Comportamento desejado:

- se licença expirar, bloquear alterações críticas;
- manter dados salvos;
- permitir visualizar aviso de expiração;
- impedir uso de comandos pagos;
- dashboard mostrar status do plano;
- logs não devem quebrar;
- módulos podem ficar read-only em atraso.

Integrações futuras possíveis:

- Stripe;
- Mercado Pago;
- Pix;
- licença manual;
- cupom;
- teste gratuito.

---

# 15. Banco de dados — recomendações

Durante a auditoria, proponha tabelas ou entidades necessárias.

Estrutura conceitual mínima:

```text
guilds
guild_settings
modules
guild_modules
licenses
subscriptions

organizations
organization_members
organization_roles
organization_permissions

audit_logs
discord_log_configs
attachments

tickets
ticket_messages
ticket_transcripts
ticket_categories

verification_configs
verification_attempts
captcha_attempts

moderation_cases
punishments

suggestions
suggestion_votes

economy_wallets
economy_transactions
items
item_aliases
shop_items
inventories

absence_requests
set_requests

faction_info
faction_farm_items
faction_farm_records
faction_chest_items
faction_chest_movements
faction_actions
faction_action_participants
faction_perimeters
faction_partnerships
faction_monthly_snapshots
faction_financial_movements

sales_products
sales_records
sales_approvals
sales_partners
sales_payouts

police_bopm
police_reports
police_report_templates
police_time_clock_sessions
police_warnings
police_shifts
police_internal_affairs_cases
```

Não implemente tudo de uma vez. Use o roadmap para priorizar.

---

# 16. Roadmap esperado

Produza um roadmap dividido em fases.

## Fase 0 — Auditoria e estabilização

- mapear código;
- mapear banco;
- mapear dashboard;
- mapear comandos;
- identificar bugs;
- identificar duplicações;
- documentar features existentes;
- criar `MODULE_AUDIT_AND_ROADMAP.md`.

## Fase 1 — Fundação modular

- criar conceito de módulos;
- criar permissões granulares;
- criar logs/auditoria;
- criar configurações por guild;
- criar base de dashboard;
- criar sistema genérico de formulários;
- criar sistema genérico de aprovações;
- preparar licenças por módulo.

## Fase 2 — Base forte

- tickets;
- moderação;
- verificação;
- anti-robô;
- anti-link;
- logs;
- autorole;
- sugestões;
- economia;
- avisos;
- dashboard base.

## Fase 3 — Facs MVP

- organizações tipo facção;
- membros;
- hierarquia;
- informações;
- farm;
- ações;
- ausências;
- baú;
- logs;
- dashboard facção.

## Fase 4 — Vendas MVP

- produtos;
- registrar venda;
- prints;
- aprovação;
- relatórios;
- repasses;
- integração opcional com facção.

## Fase 5 — Polícia MVP

- organizações tipo polícia;
- patentes;
- oficiais;
- BOPM;
- relatórios;
- bate ponto;
- advertências;
- pedir set;
- painel oficial/admin.

## Fase 6 — Diferenciais premium

- página pública;
- snapshot mensal;
- gráficos avançados;
- automações inteligentes;
- diagnóstico de facção;
- relatórios exportáveis;
- alertas preditivos;
- mobile polish;
- multi-tenant robusto.

---

# 17. Saída final obrigatória

Ao terminar a análise, atualize o arquivo:

`docs/MODULE_AUDIT_AND_ROADMAP.md`

Com este formato:

# Auditoria Modular do Bot e Dashboard

## Data da análise

## Resumo executivo

## Stack detectada

## Estrutura atual do projeto

## Mapa de comandos Discord

## Mapa de eventos/listeners

## Mapa de páginas do dashboard

## Mapa do banco de dados

## Features existentes

## Features parciais

## Features ausentes

## Achados extras da varredura

## Análise por módulo

### Base

### Facs

### Polícia

### Vendas

## Recomendações de arquitetura

## Recomendações de banco

## Recomendações de dashboard

## Recomendações de permissões

## Roadmap priorizado

## Plano de implementação incremental

## Riscos técnicos

## Próximos passos sugeridos

---

# 18. Critérios de qualidade

A análise deve ser prática, técnica e acionável.

Não entregue apenas uma lista genérica.

Para cada recomendação importante, informe:

- por que é necessária;
- onde provavelmente será implementada;
- quais arquivos/classes/tabelas podem ser impactados;
- dependências;
- riscos;
- prioridade.

Sempre que possível, cite caminhos reais encontrados no projeto.

Não invente arquivos. Se não tiver certeza, diga que não foi encontrado.

---

# 19. Direção de produto

O objetivo final não é apenas igualar concorrente.

O objetivo é construir uma plataforma própria, modular, mais organizada e mais escalável, com:

- bot Discord robusto;
- dashboard completo;
- planos por módulo;
- gestão de servidores;
- gestão de organizações;
- automações;
- logs;
- permissões;
- relatórios;
- produto vendável para comunidades, facções, polícias e lojas.

Ao sugerir novas ideias, priorize funcionalidades que aumentem:

- valor comercial;
- retenção de clientes;
- facilidade de configuração;
- segurança;
- automação;
- clareza para administradores;
- redução de trabalho manual.

---

# 20. Orientação final para a IA/agente

Não comece implementando tudo.

Primeiro:

1. Leia este spec.
2. Varra o projeto.
3. Identifique o que já existe.
4. Identifique o que está incompleto.
5. Identifique o que falta.
6. Atualize `docs/MODULE_AUDIT_AND_ROADMAP.md`.
7. Só depois proponha a primeira fase de implementação.

O foco inicial é clareza, modularidade e roadmap realista baseado no código existente.
