# Eventos aleatórios de chat + integração nível→dinheiro (Base)

**Data:** 2026-07-01
**Módulo:** Base (`modules/base/events/`) — usa **leveling** + **economy** (todos Base).
**Status:** Design aprovado (revisão do spec pendente)

## Objetivo

O servidor tem um **canal principal** configurado. De tempos em tempos (intervalo aleatório), o bot dispara um **evento** nesse canal — quiz, corrida de digitação, matemática ou coleta — e o **primeiro a acertar** ganha **moedas (escaladas pelo nível) + XP**. Uma **pequena integração** entre leveling e economia: quanto maior o nível, mais moedas a pessoa ganha (nos eventos e no `/daily`), com **teto** pra não desbalancear.

## Decisões (do brainstorming)

- **4 tipos de evento:** quiz (botões), corrida de digitação, matemática, coleta (botão "Pegar!").
- **Recompensa:** moedas base `100` (escaladas por nível, se economia ligada) + `50 XP` (se leveling ligado). Fixas no v1.
- **Multiplicador nível→dinheiro (`LevelBonus`):** aplica nas **moedas dos eventos** e no **`/daily`**. `+2%/nível`, **teto no nível 50** (máx +100%).
- **Canal dedicado** ("chat principal") no `/setup → Eventos`; sem canal → módulo inativo.
- **Intervalo aleatório configurável** (default 30–120 min); só dispara com **atividade recente** no canal.

## Integração nível→dinheiro (`LevelBonus` — puro, em `modules/base/leveling`)

`scale(long base, int level)` → `base + base · min(level, CAP) · PCT / 100`, com `CAP=50`, `PCT=2`. Ex.: nível 0 → base; nível 25 → +50%; nível ≥50 → +100% (dobro). Puro/testável.
- **Eventos:** moedas = `LevelBonus.scale(100, nívelDoVencedor)`.
- **`/daily`:** `EconomyService.daily` passa a `LevelBonus.scale(EconomyConfig.daily(cfg), nívelDoUsuário)`. O serviço lê o nível via `UserLevelRepository` (leveling) + `LevelFormula`. Acoplamento pequeno, dentro do Base.

## Eventos

`ChatEventType` (enum): `QUIZ`, `TYPING`, `MATH`, `GRAB`. Cada disparo sorteia um tipo.
- **QUIZ:** banco estático de perguntas (pergunta + 4 alternativas, índice correto). 4 botões; 1º a clicar no certo ganha; clique errado → aviso efêmero "resposta errada" (não elimina, mas não ganha). Não lê o chat.
- **TYPING:** banco estático de palavras. "Primeiro a digitar: **PALAVRA**". 1º a mandar a palavra (normalizada) ganha.
- **MATH:** gera `a op b` (op ∈ +,−,×; números pequenos) + resposta. 1º a mandar o número ganha.
- **GRAB:** botão "Pegar!"; 1º a clicar ganha. Sem pergunta.

**Estado do evento ativo** (`ChatEvent`, in-memory): tipo, guildId, channelId, messageId, resposta (para TYPING/MATH), prompt, `expiresAt`. **Um evento ativo por guild** (`ChatEventRegistry`, mapa thread-safe). Restart perde o evento ativo (aceitável).

**Checagem de resposta** (`ChatEventAnswer.matches(input, answer)` — puro): trim + case-insensitive; para MATH compara número.

## Runtime

- **`ChatEventScheduler`** (agendado a cada 60s em `onReady`): para cada guild com `event:enabled` + canal configurado, se `agora ≥ nextFireAt[guild]` **e** houve atividade recente (`lastActivityAt[guild]` dentro dos últimos 15 min), dispara um evento e sorteia `nextFireAt = agora + rand(min,max)`. Se passou do tempo mas sem atividade, adia (curto retry) sem gastar o intervalo cheio. Mapas `nextFireAt`/`lastActivityAt` in-memory.
- **`ChatEventService`**: `fire(guild)` (gera + posta no canal + registra ativo + agenda expiração via `scheduler().once` ~60s); `resolve(guild, member, channel)` (valida evento ativo + vencedor, credita, anuncia, limpa); `expire(guild)` (edita a msg "ninguém acertou", limpa).
- **`ChatEventListener`** (`onMessageReceived`): (a) marca `lastActivityAt` quando a msg é no canal de eventos; (b) para eventos TYPING/MATH ativos naquele canal, se a msg casa a resposta → `resolve(...)`.
- **`ChatEventComponentHandler`** (namespace `chatevt`): botões de QUIZ (`ans:<idx>`) e GRAB (`grab`) → resolve/《resposta errada》.

**Recompensa (`ChatEventService.reward`)**: `nível = LevelFormula.levelForXp(userLevels.xp(g,u))`; se economia ligada → `wallets.addCash(g,u, LevelBonus.scale(100, nível))`; se leveling ligado → `leveling.award(guild, member, 50, eventChannel)` (pode dar level-up). Anúncio no canal com o vencedor + o que ganhou.

## Config (`guild_config`, prefixo `event:`)

`event:enabled` (toggle), canal `event-channel`, `event:min-interval` (min, default 30), `event:max-interval` (min, default 120). `/setup → Eventos`: toggle, seletor de canal, modal de intervalos.

## Tratamento de erros / bordas

- Sem canal configurado ou `!event:enabled` → não dispara.
- Canal inexistente/sem permissão → pula silenciosamente.
- Clique/menção de bot → ignorado. Evento já resolvido (corrida) → o 2º a acertar recebe aviso efêmero "alguém já ganhou".
- Expiração: se ninguém acerta em ~60s, evento encerra sem vencedor.
- `min > max` na config → normaliza (usa min..max ordenados; piso de 1 min).

## Testes

- `LevelBonusTest` (base sem nível; +50% no 25; teto +100% no 50 e acima).
- `ChatEventConfigTest` (defaults + leitura; normalização min/max).
- `MathEventTest` (gera problema resolvível; resposta confere).
- `ChatEventAnswerTest` (trim/caixa; número no MATH; não-casa).
- `NextIntervalTest` (sorteio dentro de [min,max], piso 1).

## Fora de escopo

- Persistir evento/agendamento entre restarts (in-memory).
- Ranking/estatística de eventos; recompensas configuráveis (fixas no v1).
- Novos tipos de evento além dos 4; bancos de perguntas/palavras editáveis pelo usuário (estáticos no v1).
