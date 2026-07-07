# Roadmap: Ponte WebSocket Dashboard ↔ Bot (Live/RPC Bridge)

## Data
**7 de julho de 2026**

> Documento destinado ao **projeto do site (dashboard)**, não ao repositório do bot. Descreve a arquitetura e as tasks para o dashboard obter, sob demanda, dados que vivem **apenas** no bot (SQLite local / estado em memória do JDA), respeitando as restrições de hospedagem.

---

## 1. Restrições reais de hospedagem (e o que elas implicam)

| Componente | Hospedagem | Pode aceitar conexão de entrada? | Pode conectar para fora? | Consequência |
| :--- | :--- | :--- | :--- | :--- |
| **Bot** | Pterodactyl (host de game server) | ❌ Não (sem abrir portas) | ✅ Sim (outbound livre) | Só pode ser **cliente** WebSocket. |
| **Site / Dashboard** | Netlify (estático + Functions) | ❌ Não (Functions são efêmeras e stateless) | ✅ Sim (fetch/WSS a partir do browser ou da Function) | **Não pode** segurar um WebSocket aberto do lado servidor. |
| **SQLite** | Disco local do bot no Pterodactyl | — | — | Inacessível diretamente de fora. Fonte da verdade **transiente**. |

### Conclusão arquitetural (não-negociável)
Como **nenhum dos dois lados** pode aceitar conexões de entrada confiáveis, **não existe** conexão WebSocket direta “site ↔ bot”. É necessário um **terceiro componente público — o Relay (broker)** — para o qual **ambos discam para fora**:

```
   Bot (Pterodactyl)                 Relay (host público)                 Dashboard (Netlify)
 ┌──────────────────┐   WSS out    ┌────────────────────┐   WSS out    ┌───────────────────────┐
 │  WS CLIENT        │ ───────────▶ │  WS SERVER + broker │ ◀─────────── │  Browser (WS client)  │
 │  RPC dispatcher   │ ◀─────────── │  correlação/rota    │ ───────────▶ │  ou Netlify Function  │
 │  acesso SQLite    │              │  auth + rate limit  │              │  (proxy HTTPS→relay)  │
 └──────────────────┘              └────────────────────┘              └───────────────────────┘
```

O Relay **não** precisa que você abra porta no bot nem na Netlify — ele é um serviço separado, num host que **naturalmente** aceita entrada (Cloudflare, Railway, Fly.io, VPS). Só ele expõe porta pública.

---

## 2. Reconciliação com a arquitetura existente (importante)

O documento [MODULE_AUDIT_AND_ROADMAP.md](MODULE_AUDIT_AND_ROADMAP.md) estabelece explicitamente:

> *“O dashboard web opera fora do servidor do bot e **não deve realizar conexões diretas ou leitura de arquivos locais SQLite**. O PostgreSQL é a fonte da verdade… O SQLite é mantido estritamente para caches locais, filas temporárias, tokens efêmeros e estados transientes do gateway.”*

**Esta ponte WebSocket não contradiz isso — ela o complementa.** A regra de ouro:

| Tipo de dado | Como o dashboard obtém | Justificativa |
| :--- | :--- | :--- |
| **Dados de negócio persistentes** (vendas confirmadas, punições, config de guild, farm consolidado) | **Leitura direta no PostgreSQL** (como já planejado) | Fonte da verdade. Não passa pela ponte. |
| **Estado ao vivo / efêmero** (tickets abertos agora, sessões de bate-ponto em aberto, filas de verificação/aprovação em memória, presença, contadores em tempo real) | **RPC via ponte WebSocket** | Só existe no bot (SQLite transiente / memória do JDA). Postgres não tem. |
| **Comandos / ações em tempo real** (fechar ticket, forçar refresh de painel, kickar, reenviar captcha) | **Comando via ponte WebSocket** (bidirecional) | Executado pelo processo do bot conectado ao gateway. |

> Regra prática: **se o Postgres já tem o dado, o dashboard lê do Postgres.** A ponte é para o que o Postgres *não* tem — o estado vivo do processo do bot e os comandos que só o bot pode executar.

---

## 3. Escolha do host do Relay

O Relay é o único componente novo que precisa de porta pública. Opções, da mais recomendada à mais trabalhosa:

| Opção | Custo | WebSocket persistente | Esforço | Observações |
| :--- | :--- | :--- | :--- | :--- |
| **Cloudflare Workers + Durable Objects** ⭐ | Grátis → baixíssimo | ✅ (WebSocket Hibernation — conexão dorme sem custo enquanto ociosa) | Médio | Melhor custo/benefício. O DO mantém a conexão do bot; o browser conecta via WSS ao Worker. Escala global, TLS incluso. |
| **Railway / Render / Fly.io** (Node `ws`) | ~US$5/mês | ✅ | Baixo | Servidor Node comum com a lib `ws`. Simples de codar, processo sempre ligado. |
| **VPS própria** (ex.: Hetzner/Oracle Free) | US$0–5/mês | ✅ | Médio-alto | Máximo controle, mas você gerencia TLS (Caddy/Nginx), systemd, uptime. |
| **Supabase Realtime / Ably / Pusher** | Grátis → pago | ✅ (pub/sub) | Baixo | Pub/sub gerenciado. Bom para *broadcast*, menos natural para RPC request/response — dá pra fazer com canais de correlação, mas foge do modelo. |

**Recomendação:** começar com **Cloudflare Durable Objects** (grátis, WebSocket Hibernation elimina custo ocioso, TLS e escala resolvidos). Se preferir o caminho mais familiar de codar, um **worker Node no Railway/Fly** com a lib `ws` é o plano B direto.

---

## 4. Protocolo da ponte (RPC sobre WebSocket)

Modelo **request/response com IDs de correlação** (RPC assíncrono multiplexado sobre uma única conexão).

### 4.1. Envelope de mensagem (JSON)
```jsonc
{
  "v": 1,                       // versão do protocolo
  "type": "req",                // req | res | event | ping | pong | auth
  "id": "c1a2...",              // correlação (UUID); res ecoa o mesmo id
  "guild": "123456789",         // escopo obrigatório
  "method": "tickets.live.list",// método whitelisted (nunca SQL cru)
  "params": { "status": "open" },
  "ts": 1751856000000
}
```

Resposta:
```jsonc
{ "v": 1, "type": "res", "id": "c1a2...", "ok": true, "data": { /* ... */ } }
{ "v": 1, "type": "res", "id": "c1a2...", "ok": false, "error": { "code": "BOT_OFFLINE", "msg": "..." } }
```

### 4.2. Registro de métodos (whitelist no bot)
O site **nunca** envia SQL. Ele chama métodos nomeados que o bot mapeia para queries parametrizadas e seguras. Exemplos:

| Método | Direção | Retorna / faz |
| :--- | :--- | :--- |
| `tickets.live.list` | leitura | Tickets abertos agora (SQLite transiente) |
| `verification.queue.count` | leitura | Tamanho da fila de verificação em memória |
| `police.clock.active` | leitura | Sessões de bate-ponto em aberto |
| `sales.approval.pending` | leitura | Fila de aprovação de comprovantes ainda não persistida |
| `panel.refresh` | comando | Regenera um painel V2 no Discord |
| `ticket.close` | comando | Fecha ticket X (com autorização) |

### 4.3. Fluxo de uma requisição (sequência)
```
[Browser/dashboard] --WSS--> [Relay] --WSS--> [Bot]
       │  req id=abc, method=tickets.live.list, guild=123
       │                         │  valida sessão/escopo, encaminha
       │                         │────────────────────────▶ dispatcher
       │                         │                          consulta SQLite
       │                         │◀──────────────────────── res id=abc, data=[...]
       │◀────────────────────────│  encaminha de volta
   resolve a Promise (correlação por id)
```

Timeout do lado do site (ex.: 8s). Se o bot estiver offline, o Relay responde imediatamente `BOT_OFFLINE` e o dashboard mostra estado degradado.

---

## 5. Segurança (obrigatória — SQLite tem dados sensíveis)

1. **TLS fim a fim** em todas as pernas (WSS). Nunca `ws://`.
2. **Autenticação do bot no Relay**: segredo compartilhado / token assinado (o bot prova identidade ao conectar). Guardado em variável de ambiente no Pterodactyl, **nunca** commitado.
3. **Autenticação do dashboard no Relay**: sessão/JWT do usuário logado do dashboard. O Relay (ou uma Netlify Function que faz o proxy) valida a sessão **antes** de encaminhar.
4. **Autorização por escopo de guild**: toda requisição carrega `guild`. O Relay/bot verifica se o usuário logado **administra aquela guild** (permissões do Discord). Um usuário nunca lê dados de guild que não gerencia.
5. **Whitelist de métodos**: o site só invoca métodos registrados; **jamais** SQL arbitrário. Parâmetros validados por schema (ex.: Zod no relay, validação no bot).
6. **Rate limiting** por sessão e por guild no Relay.
7. **O Relay vê o tráfego em trânsito.** Para dados muito sensíveis, considerar payload assinado/cifrado ponta-a-ponta (bot↔dashboard) além do TLS — avaliar custo/benefício; começar com TLS + autorização rígida é aceitável.

> Opcional recomendado: rotear as chamadas do dashboard **através de uma Netlify Function** que injeta a autenticação do usuário e assina a requisição, em vez do browser falar direto com o Relay. Isso mantém o segredo de autorização fora do cliente. Para *streams* ao vivo (many updates), o browser conectando direto ao Relay via WSS é mais eficiente — nesse caso, autentique a abertura da conexão com um token curto emitido por uma Function.

---

## 6. Resiliência e operação

- **Reconexão do bot** com backoff exponencial + jitter; heartbeat `ping`/`pong` (ex.: a cada 30s) para detectar conexão morta.
- **Hibernação** (se Cloudflare DO): a conexão do bot dorme sem custo enquanto ociosa e acorda na próxima mensagem.
- **Timeout de requisição** no site; UI mostra “bot offline” em vez de travar.
- **Identidade do bot / sharding**: se houver múltiplas instâncias/shards, registrar por `botId`/guild para o Relay rotear à instância correta.
- **Observabilidade**: métricas de conexões ativas, latência de RPC, taxa de erro, requisições por método; logs no Relay.
- **Idempotência de comandos**: comandos (ex.: `ticket.close`) devem tolerar reenvio sem efeito duplicado.

---

## 7. Roadmap quebrado em tasks

### Fase 0 — Decisões e contrato
- **Entrega**: escolher host do Relay (recomendado: Cloudflare DO); versionar o **contrato do protocolo** (envelope + lista inicial de métodos) num arquivo compartilhado (ex.: `protocol/v1.md` ou um pacote de tipos).
- **Critério de pronto**: documento de protocolo aprovado e host do Relay provisionado (endpoint WSS público respondendo).
- **Risco**: Baixo.

---

### Fase 1 — Relay MVP (broker público)
- **Onde**: projeto do site / repositório separado do Relay.
- **Entrega**: servidor WS que (a) aceita conexão do bot e autentica por segredo; (b) aceita conexões de cliente; (c) faz roteamento request/response por `id` e `guild`; (d) responde `BOT_OFFLINE` quando não há bot conectado.
- **Critério de pronto**: um cliente de teste envia `req` e recebe `res` ecoado de um bot mock; sem bot conectado, recebe `BOT_OFFLINE`.
- **Risco**: Médio (correlação de mensagens e ciclo de vida das conexões).

---

### Fase 2 — Cliente WebSocket no bot (Java)
- **Onde**: repositório do bot — `integration/ws/` (novo pacote, ao lado do cliente HTTP de transcripts existente).
- **Entrega**: cliente WSS persistente (ex.: `java.net.http.WebSocket` ou `nv-websocket-client`), autenticação no Relay, heartbeat, reconexão com backoff, e um **dispatcher** de RPC que roteia `method` → handler.
- **Critério de pronto**: bot conecta ao Relay, aparece “online”, responde a um método de eco (`ping.echo`), reconecta após queda simulada.
- **Risco**: Médio (concorrência: responder RPC sem bloquear a thread do gateway JDA — usar o scheduler existente).

---

### Fase 3 — Registro de métodos de leitura (SQLite → RPC)
- **Onde**: repositório do bot — handlers por módulo (tickets/verificação/vendas/polícia).
- **Entrega**: métodos whitelisted mapeando para queries parametrizadas no SQLite/estado em memória. Começar com 3–4 de maior valor ao vivo (ex.: `tickets.live.list`, `sales.approval.pending`, `police.clock.active`, `verification.queue.count`).
- **Critério de pronto**: cada método retorna dado real via Relay, com validação de parâmetros e escopo de guild.
- **Risco**: Baixo-Médio.

---

### Fase 4 — Integração no dashboard (Netlify)
- **Onde**: projeto do site.
- **Entrega**: cliente WS no browser (hook `useBotRPC`/`useLiveQuery`) **ou** proxy via Netlify Function; correlação de `id` por Promise; timeout; estados de loading/offline na UI.
- **Critério de pronto**: uma tela do dashboard exibe dados ao vivo vindos do bot (ex.: lista de tickets abertos agora), atualizando sob demanda.
- **Risco**: Baixo.

---

### Fase 5 — Segurança e autorização
- **Onde**: Relay + Netlify Functions + bot.
- **Entrega**: validação de sessão do usuário, autorização por administração de guild, whitelist/validação de schema por método, rate limiting, segredos em env vars, tokens curtos para abertura de WS no browser.
- **Critério de pronto**: usuário sem permissão na guild recebe `FORBIDDEN`; método fora da whitelist é rejeitado; segredos ausentes do código-fonte e do cliente.
- **Risco**: Alto (é a camada que protege dados sensíveis — não pular).

---

### Fase 6 — Canal de comandos bidirecional
- **Onde**: bot + dashboard.
- **Entrega**: métodos de **ação** (`panel.refresh`, `ticket.close`, etc.) com idempotência e autorização reforçada; feedback de resultado ao dashboard.
- **Critério de pronto**: uma ação disparada no dashboard reflete no Discord e retorna resultado.
- **Risco**: Médio-Alto (ações com efeito colateral exigem autorização e idempotência sólidas).

---

### Fase 7 — Resiliência, observabilidade e produção
- **Onde**: Relay + bot.
- **Entrega**: métricas (conexões, latência RPC, erros por método), alerta de bot offline, dashboards internos, backoff/jitter afinados, tratamento de sharding se aplicável.
- **Critério de pronto**: painel operacional mostra saúde da ponte; queda do bot é visível e recuperável automaticamente.
- **Risco**: Baixo.

---

## 8. Resumo em uma frase

Como Netlify e Pterodactyl não aceitam conexões de entrada, coloque um **Relay público** (recomendado: Cloudflare Durable Objects) para o qual **bot e site discam para fora**; o bot vira cliente WS com um **dispatcher de RPC whitelisted** sobre o SQLite/estado vivo, o dashboard chama esses métodos por correlação de `id`, e tudo que já vive no **PostgreSQL continua sendo lido direto do PostgreSQL** — a ponte serve apenas ao **estado ao vivo e aos comandos** que só o processo do bot possui.