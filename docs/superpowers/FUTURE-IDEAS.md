# BaseBot — Backlog de Ideias Futuras

> Backlog de features planejadas para o bot all-in-one. Cada item vira seu próprio spec → plan →
> implementação quando for a vez. Mapeado às fases do roadmap (ver memória `all-in-one-roadmap`).
> Ordem de build atual: 1) Moderação+Infrações ✅ · 2) Logging ✅ · 3) AutoMod+Segurança ·
> 4) Welcome/self-roles · 5) Leveling · 6) Economia (por-usuário) · 7) Fun · 8) Utilidades.

---

## 🛡️ Moderação Avançada (AutoMod Inteligente) — *fase 3*
Não só comandos de ban/kick. Proteção nativa:
- **Anti-raid** (detecção de entradas em massa / contas novas).
- **Anti-spam** (flood de mensagens/menções/repetição).
- **Filtro de links maliciosos** (e convites de outros servidores).
- **Sistema de warns que escala punições automaticamente** (ex.: 3 warns = mute 1h; 5 warns = ban).
  - *Obs.: o motor de escalonamento já existe no módulo de Infrações (`ModerationConfig` escalation). Aqui é o gatilho automático (automod) gerando os warns.*
- 💡 **Use o AutoMod NATIVO do Discord** em vez de ler toda mensagem com RegEx (caro de CPU e depende de `MESSAGE_CONTENT`). O bot **configura as regras** do AutoMod da guilda via API (`Guild.createAutoModRule(...)` — keyword/spam/mention/link); quando o Discord barra a mensagem, dispara um evento (`onAutoModExecution` / `AutoModExecutionEvent`) → aí sim o bot aplica o warn e escala a punição. Menos CPU, blocagem na fonte.

## 💰 Economia (por-usuário) — *fase 6*
Carteira/banco, `/daily`, `/trabalhar`, `/pagar`, `/depositar`/`/sacar`, ranking, loja de cargos, `/setup → Economia`.
- 💡 **Cuidado com a inflação.** Tem várias fontes de geração (`/daily`, `/trabalhar`, XP/moeda por call) mas precisa de **ralos de dinheiro** contínuos. A loja não pode ter só cargos permanentes (compra-uma-vez): inclua **itens temporários/consumíveis caros** (cargos que expiram, boosts de XP, itens de fun) pra dar onde gastar sempre — senão os números perdem o sentido rápido.

## 🎉 Sorteios e Eventos — *fase nova (entre 6 e 7)*
Sorteios (giveaways) com **requisitos avançados** para participar:
- Ter o cargo X.
- Estar no servidor há pelo menos X dias.
- Ter ficado em call por X horas.
- Ter ficado em call dentro de uma janela de horário (de X às Y).
- Painel de sorteio com botão de participar, contagem, e sorteio do(s) ganhador(es).
- 💡 **Tempo em call é o requisito mais delicado.** O rastreamento de voz (`GUILD_VOICE_STATES`) sofre com reinício do bot / oscilação da API. **Persistir `join_time`/`leave_time` no SQLite em tempo real** e calcular a diferença na saída — nunca segurar só na RAM. Reconciliar o estado no boot (ver apêndice **§Rastreamento de voz** abaixo). Esse mesmo sistema alimenta o **Leveling por voz** (fase 5).

## 🧰 Utilidades — *fase 8*
- **`/avatar`** e **`/banner`** — mostra a foto/banner do usuário em tamanho grande, com botão de baixar.
- **`/lembrete [tempo] [mensagem]`** — lembra o usuário via DM quando o tempo acabar (ex.: `/lembrete 2h Reunião da facção`).
- **`/afk [motivo]`** — se marcarem a pessoa enquanto AFK, o bot responde avisando ausência + motivo.
  - 💡 Adicionar um **listener de mensagens**: assim que o usuário AFK enviar qualquer mensagem em qualquer canal, o bot **remove o AFK automaticamente** e avisa "Bem-vindo de volta, removi seu AFK".
- **`/enquete`** — votação interativa com **botões** (não reações), bonita e contável.
- **`/userinfo`** e **`/serverinfo`** — dados detalhados (criação da conta, entrada no servidor, cargos, etc.).

## 🎲 Fun / Social — *fase 7*
- **`/dado [lados]`** — rola um dado virtual (ex.: D20 p/ RPG de mesa).
- **`/coinflip`** — cara ou coroa.
- **`/rep [@usuário]`** — reputação: 1 ponto por dia para quem ajudou (in-game ou no servidor). Tem ranking.
- **`/biscoito [@usuário]`** — elogios: dá um "biscoito" por dia para quem foi legal; ranking de quem tem mais.
- **`/ship [@u1] [@u2]`** — "compatibilidade" aleatória 0–100% com barrinha de carregamento + mensagem engraçada nos extremos.
- **`/toca_aqui [@usuário]`** / **`/abracar [@usuário]`** — manda um GIF de anime/desenho da ação, marcando a pessoa.
- **`/quiz`** — pergunta (conhecimentos gerais ou regras do servidor/facção) com 4 botões; quem acerta primeiro ganha XP/moedas.
- **`/jokenpo [@usuário]`** — pedra/papel/tesoura: convite ao amigo, escolhas secretas (ephemeral), bot revela o vencedor.
- **`/forca`** — palavra aleatória (ou temática do servidor); a galera digita letras no chat até acertar ou perder as vidas.

## 🖼️ Geradores de Imagem e Memes — *fase 7/8 (depende do pipeline de imagem)*
- **`/carta_reverso [@usuário]`** — manda a carta +4 / Reverso do Uno marcando a pessoa.
- **`/procurado [@usuário]`** — pega a foto de perfil e coloca num cartaz "Procurado — Recompensa $10.000", gerando a imagem na hora.
- 💡 **Manipulação de imagem é operação bloqueante e pesada.** Em Java seria `BufferedImage`/`Graphics2D` (ou Thumbnailator). **Processar fora da thread do gateway** (ex.: `ctx.scheduler().executor()` / worker), respondendo de forma assíncrona — senão um meme do UNO trava a resposta dos comandos de moderação. Reusar a regra de imagem do projeto (baixar → processar → re-upload → apagar bytes).

---

### Notas de implementação
- Vários itens precisam dos **intents privilegiados** já habilitados (`GUILD_MEMBERS`, `MESSAGE_CONTENT`) e/ou `GUILD_VOICE_STATES` (sorteios por tempo em call).
- **XP/moedas** (quiz, etc.) dependem dos módulos de **Leveling** (fase 5) e **Economia** (fase 6).
- Geradores de imagem reusam a regra de manuseio de imagem do projeto (baixar → processar → re-upload → apagar bytes) e podem usar o **cofre de anexos** se precisarem persistir.
- Toda mensagem segue as regras do projeto: **Components V2 container**, **emojis custom** (`Emojis`), house style das embeds, e confirmações públicas **auto-deletam** (`Replies.reply`).

---

## ⚙️ Apêndice — Rastreamento de voz: persistência + reconciliação no boot
Sistema **à prova de reinício** usado pelos **Sorteios por tempo de call** (fase 7) e pelo **Leveling por voz** (fase 5). A regra de ouro: gravar tudo no SQLite em tempo real e **reconciliar o estado** quando o bot sobe.

**Tabela** (ex.: `voice_sessions`): `guild_id`, `user_id`, `channel_id`, `join_time`, `leave_time` (NULL = sessão aberta).

**Em tempo real (eventos de voz):**
- Entrou na call → `INSERT` com `join_time = agora`, `leave_time = NULL`.
- Saiu/mudou de call → `UPDATE` a sessão aberta com `leave_time = agora` (mudança de canal = fecha a antiga + abre a nova).

**Na inicialização (`onReady` / `onGuildReady`) — reconciliação:**
1. **Buscar sessões abertas:** `SELECT` onde `leave_time IS NULL` (quem o bot achava que estava em call antes de cair).
2. **Coletar estado atual:** varrer os canais de voz da guilda (cache do gateway) → set dos `user_id` que estão em call **agora**.
3. **Cruzar (reconciliar):**
   - **Caso A — continua/entrou enquanto o bot estava off:** está na call agora mas sem sessão aberta (ou trocou de canal) → fecha a sessão antiga (com o timestamp aproximado da queda) e abre uma nova com o horário atual.
   - **Caso B — saiu enquanto o bot estava off:** o banco diz que estava em call mas não está na lista atual → fecha a sessão dele com o **timestamp de quando o bot caiu** (mais justo com a economia) — ou o horário do `ready` se preferir não penalizar.
4. **Limpeza:** quem está em call agora sem registro aberto → novo `join_time = agora`.

**⚠️ Sharding (futuro):** o cache de voz pode demorar segundos pra popular após o `ready`. Não rodar a varredura no milissegundo do connect — **dar um delay de 2–5s** (ou esperar a sincronização das guildas) pra a lista "quem está em call agora" vir 100% preenchida.

*(Guardar o "timestamp da queda" do bot: pode-se persistir um heartbeat periódico, ou usar o `updated_at` da última sessão ativa como aproximação.)*
