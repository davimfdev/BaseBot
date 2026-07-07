# Fun / Social (Base, fase 7) — jogos, social e ações

**Data:** 2026-07-01
**Módulo:** Base (`modules/base/fun/`).
**Status:** Design aprovado (revisão do spec pendente)

## Objetivo

Comandos de diversão e social: dados/coinflip/ship, reputação/biscoito com ranking, jokenpo, ações com GIF (carinho/abraço), **forca** e **quiz personalizado** (banco de perguntas gerido pelo servidor). **Fun é só diversão — não dá XP nem moedas.**

## Decisões (do brainstorming)

- **Grupos v1:** simples (dado/coinflip/ship), social+ranking (rep/biscoito), jokenpo, ações com GIF, **forca**, **quiz personalizado**.
- **Sem recompensa** (nem XP nem moedas) em nenhum comando Fun.
- **/ship estável por par** (hash determinístico).
- **GIF via API externa nekos.best** (grátis, sem chave; categorias `pat`/`hug`).
- **Quiz personalizado**: banco por servidor (admin cadastra perguntas), **distinto** do quiz dos eventos de chat.

## Escopo / decomposição

Um spec, **dois planos**:
- **Plano 1 — Fun básico:** simples + social (migração 031) + jokenpo + GIF.
- **Plano 2 — Jogos com estado/config:** forca (banco embutido) + quiz personalizado (migração 032 + gestão no `/setup → Fun`).

## Saídas

Resultados lúdicos são **públicos** (mensagem V2 normal, não auto-deletam): dado, coinflip, ship, jokenpo, GIF, forca, quiz. **Rankings** (`/rep`/`/biscoito` sem alvo) e erros são **efêmeros**. Confirmação de dar rep/biscoito usa `Replies.reply` (temporária).

---

## Plano 1 — Fun básico

### Simples (puros)

- **`/dado [lados]`** (default 6, faixa 2–1000): rola 1..lados.
- **`/coinflip`**: cara ou coroa.
- **`/ship @a @b`**: `ShipCalc.percent(idA, idB)` → 0–100 **estável por par** (hash da dupla ordenada); barrinha `█░` + mensagem por faixa. Puro/testado.

### Social + ranking (migração `031_social.sql`)

```sql
CREATE TABLE IF NOT EXISTS social_points (
    guild_id TEXT NOT NULL, user_id TEXT NOT NULL, type TEXT NOT NULL,
    points INTEGER NOT NULL DEFAULT 0, PRIMARY KEY (guild_id, user_id, type)
);
CREATE TABLE IF NOT EXISTS social_gifts (
    guild_id TEXT NOT NULL, giver_id TEXT NOT NULL, type TEXT NOT NULL,
    last_ts INTEGER NOT NULL, PRIMARY KEY (guild_id, giver_id, type)
);
```
- `SocialRepository`: `GiveResult give(guild, giver, target, type, now, cooldownMs)` (`record GiveResult(boolean ok, long newPoints, long readyAt)`). `long points(guild,user,type)`, `List<Entry> top(guild,type,limit)` (`record Entry(String userId, long points)`).
  - **⚠️ Gate de cooldown atômico (anti double-click/spam):** nada de `SELECT`+`UPDATE`. O gate é uma escrita condicional:
    1. `INSERT OR IGNORE INTO social_gifts (guild_id, giver_id, type, last_ts) VALUES (?,?,?,agora)` → se inseriu (`executeUpdate()>0`) = **primeira vez**, gate passou.
    2. senão, `UPDATE social_gifts SET last_ts=agora WHERE guild_id=? AND giver_id=? AND type=? AND (agora - last_ts) >= cooldownMs` → se `updated>0`, gate passou; se `0`, **em cooldown**.
    Como o SQLite serializa as escritas, dois cliques simultâneos: só um insere (1ª vez) / só um satisfaz o `WHERE` do UPDATE (o outro reavalia contra o `last_ts` já movido → 0). Só quando o gate passa é que `points += 1` no alvo (upsert). No caso `ok=false`, um `SELECT last_ts` (só no caminho de falha) calcula o `readyAt`.
- **`/rep [usuario]`**: com alvo → dá 1 rep/dia (sem bot/si mesmo) → confirma `<@alvo>` tem N; se em cooldown → informa `<t:…:R>`; sem alvo → ranking top 10 (efêmero). `type="rep"`, cooldown 24h.
- **`/biscoito [usuario]`**: idêntico com `type="cookie"`.

### Jokenpo (in-memory)

- **`/jokenpo @alvo`** (sem bot/si mesmo): posta desafio com 3 botões (pedra/papel/tesoura). Partida em `ConcurrentHashMap<messageId, Match>` (challenger, target, escolhas). Só os 2 jogadores clicam; escolha secreta (confirmação efêmera). Quando ambos escolhem → `JokenpoResult.decide(a,b)` (puro: WIN_A/WIN_B/TIE) → edita a mensagem revelando + vencedor. Expira em ~60s (`scheduler().once`). Namespace `jkp`.
  - **⚠️ Concorrência (revelar uma vez só):** cada clique registra o voto no `Match` (o registro do voto é `synchronized` no objeto da partida). A **revelação/decisão** roda uma única vez via remoção condicional atômica: `if (match.bothChosen() && matches.remove(messageId, match)) { revela(); }` — o `ConcurrentHashMap` sozinho não protege a mutação do `Match`, então o `synchronized` no voto + o `remove(key, value)` garantem que só uma thread decide.

### Ações com GIF (nekos.best)

- **`/toca_aqui @u`** (categoria `pat`), **`/abracar @u`** (`hug`). `GifClient` (em `modules/base/fun/`): `CompletableFuture<Optional<String>> fetch(category)` via `HttpClient.sendAsync` + `ObjectMapper` (stack do `TicketIngestClient`). `GifClient.extractUrl(json)` puro (lê `results[0].url`) — testado. Comando: `deferReply()` → em sucesso, hook posta container V2 com texto ("fez carinho em/abraçou") + `MediaGallery(gifUrl)`; falha → texto sem GIF. Público. `Message.MentionType.USER` liberado.

---

## Plano 2 — Forca + Quiz personalizado

### Forca (banco embutido)

- **`/forca`**: inicia um jogo no canal (**um ativo por canal**, in-memory). Sorteia uma palavra do `HangmanBank` (banco embutido PT). Mostra a palavra mascarada, letras erradas e vidas (6).
- **`HangmanState`** (puro/testado): `record HangmanState(String word, Set<Character> guessed, int lives)`; `guess(char)` → novo estado (revela ou perde vida); `masked()` (`_ a _ a`); `won()`/`lost()`.
  - **⚠️ Normalização de acentos/caixa:** comparar sempre a **forma dobrada** — `Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "")` + `toLowerCase()`. Vale para **a letra chutada E a palavra inteira** (o palpite da palavra completa passa pela mesma normalização antes do `.equals()` contra a palavra dobrada). Assim "Coração" revela com `c`/`C`, o `ç`/`ã` casam com `c`/`a`, e "coracao"/"coração" acertam a palavra toda. A exibição (`masked`) mostra a letra **original** (com acento) quando revelada; a comparação usa a forma dobrada.
- **`ForcaListener`** (`onMessageReceived` no canal do jogo): mensagem de **1 letra** → aplica `guess`; mensagem = palavra inteira → tenta acertar tudo. Atualiza/edita o painel; ao ganhar/perder encerra (revela a palavra). Requer `MESSAGE_CONTENT` (habilitado). In-memory `ConcurrentHashMap<channelId, HangmanState>`; a mutação do estado por chute é `compute(...)` (atômico no mapa); sem timeout (some no restart).
  - **⚠️ Rate-limit de edição:** com várias pessoas chutando ao mesmo tempo, o bot edita o painel várias vezes (limite ~5 edições/5s por mensagem). **v1:** aceita o risco (partidas curtas) e deixa o JDA enfileirar as edições naturalmente; um debounce do painel fica como melhoria futura se aparecer lentidão.
- Sem recompensa. Palavras custom do servidor = follow-up.

### Quiz personalizado (migração `032_quiz.sql`)

```sql
CREATE TABLE IF NOT EXISTS quiz_questions (
    id       TEXT PRIMARY KEY, guild_id TEXT NOT NULL,
    question TEXT NOT NULL, opt_a TEXT NOT NULL, opt_b TEXT NOT NULL,
    opt_c TEXT NOT NULL, opt_d TEXT NOT NULL, correct INTEGER NOT NULL
);
```
- `QuizQuestion` (record) + `QuizRepository`: `add(...)` (id gerado), `list(guild)`, `remove(id)`, `Optional<QuizQuestion> random(guild)`.
- **`/quiz`** (todos): sorteia uma pergunta **personalizada** do servidor; se não houver nenhuma, usa o banco embutido dos eventos (`events.QuizBank`) como fallback. Posta com 4 botões; **primeiro a clicar certo** ganha o reconhecimento ("🎉 acertou!") — **sem recompensa**. Clique errado → aviso efêmero. Um quiz ativo por canal (in-memory), expira em ~60s. Namespace `funquiz`.
- **`/setup → Fun`**: gestão do banco de quiz — **Adicionar** (modal: pergunta + 4 alternativas + qual é a correta A/B/C/D), **listar** e **remover**. Gate `MANAGE_SERVER`. Entrada no hub + moduleNav.

## Componentes

`fun/{ShipCalc, DadoCommand, CoinflipCommand, ShipCommand, SocialRepository, SocialView, RepCommand, BiscoitoCommand, JokenpoResult, JokenpoService, JokenpoCommand, JokenpoComponentHandler, GifClient, TocaAquiCommand, AbracarCommand, HangmanState, HangmanBank, ForcaService, ForcaListener, ForcaCommand, QuizQuestion, QuizRepository, QuizPlayService, QuizCommand, QuizComponentHandler}` + tela `SetupView.funScreen`/modal + wiring no `SetupComponentHandler`.

## Tratamento de erros / bordas

- Alvo bot ou si mesmo (ship/rep/biscoito/jokenpo/gif) → aviso efêmero.
- Rep/biscoito em cooldown → informa quando libera (`<t:…:R>`).
- Jokenpo/quiz: 2º a agir após resolvido → "já encerrou/alguém já ganhou" (efêmero).
- GIF: falha de rede/JSON → fallback em texto (nunca quebra).
- Forca: jogo já ativo no canal → avisa; letra repetida → ignora sem perder vida.
- Quiz sem perguntas custom → usa fallback embutido.

## Testes

- `ShipCalcTest` (estável por par; ordem A,B == B,A; faixa 0–100).
- `JokenpoResultTest` (todas as combinações: vitórias, empate).
- `SocialRepositoryTest` (dar respeita cooldown; pontos; top; migração 031).
- `GifClientTest` (`extractUrl` de um JSON de exemplo do nekos.best; JSON inválido → vazio).
- `HangmanStateTest` (guess certo revela; errado perde vida; won/lost; masked; acentos/caixa).
- `QuizRepositoryTest` (add/list/remove/random; migração 032).

## Fora de escopo v1

- Geradores de imagem (`/procurado`, `/carta_reverso`) — dependem do pipeline de imagem (fase 8).
- Palavras de forca personalizadas por servidor (banco embutido no v1).
- Recompensas em Fun; timeout de inatividade da forca; multiplayer avançado.
