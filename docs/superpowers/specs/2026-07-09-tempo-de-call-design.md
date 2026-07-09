# Tempo de call: contagem, ranking semanal e correção do XP em mute

Data: 2026-07-09
Status: aprovado (revisado), pronto para plano de implementação

## Problema

Dois problemas relacionados, no mesmo caminho de código.

1. **XP de voz ignora mute.** `VoiceEligibility.isEligible` exige não-bot, ≥2 humanos no
   canal, não ensurdecido e fora do canal AFK. Mute nenhum é checado, então quem fica de
   microfone fechado acumula XP igual a quem está conversando.

2. **Não existe contagem de tempo de call.** `voice_sessions` guarda `join_time`/`leave_time`,
   mas ninguém agrega isso num total por período, não há ranking, e o site não tem de onde ler.

## Objetivo

- XP de voz só acumula com o microfone aberto.
- Todo tempo "ativo" em call é contabilizado por semana, com ranking no bot e no site.
- A semana zera na segunda 00:00 (America/Sao_Paulo).

## Decisões

| Decisão | Escolha |
|---|---|
| Self-mute | Pausa XP **e** tempo |
| Server-mute | Não pausa nada |
| Deafen (self ou servidor) | Pausa XP e tempo |
| Canal AFK | Pausa XP e tempo |
| ≥2 humanos no canal | Exigido só para XP; tempo conta sozinho |
| Escopo de canais | Só para tempo; padrão = canais públicos |
| Precedência do escopo | Canal > categoria > padrão |
| "Público" | `@everyone` tem `VIEW_CHANNEL` e `VOICE_CONNECT` **efetivos** no canal |
| Reset semanal | Linhas por semana; sem job de reset |
| Fuso da semana | `America/Sao_Paulo`, fixo no código |
| Persistência | SQLite acumula; flusher periódico sobe para o Postgres |
| Retenção | 90 dias, em `voice_sessions` e nos baldes semanais |

Server-mute não pausa por decisão explícita do usuário: quem foi mutado por um moderador não
deve perder progresso por isso.

## Invariantes

Estes são os pontos onde uma implementação descuidada gera bugs silenciosos de contagem.

1. **Watermarks independentes.** `xp_credited_until` e `time_credited_until` avançam separadamente,
   e avançam **mesmo quando a janela não gerou crédito**. Sem isso, alguém mutado por 10 minutos
   receberia os 10 minutos retroativos ao desmutar.

2. **Flags de deafen separadas.** Nunca inverter o agregado `isDeafened()` para reconstruir o
   estado anterior. Manter `selfDeafened` e `guildDeafened` distintos, com
   `deafened = selfDeafened || guildDeafened`. Contraexemplo: antes `selfDeaf=false,
   guildDeaf=true` (agregado `true`); a pessoa dá self-deafen; o agregado continua `true` depois.
   Inverter o agregado daria `deafened=false` para a janela anterior — errado.

3. **O valor anterior vem do evento, não do estado vivo.** Cada evento de mute/deafen carrega o
   valor **novo** da flag que mudou (`event.isSelfMuted()`, `event.isSelfDeafened()`,
   `event.isGuildDeafened()`). O valor anterior daquela flag é a negação disso; as demais flags
   vêm do `GuildVoiceState` atual. Não inverter o estado vivo, que já reflete a mudança.

4. **Move de canal avalia a janela pendente com o canal antigo.** Escopo, AFK e contagem de
   humanos da janela que está sendo fechada saem de `event.getChannelLeft()`, não do canal novo.
   A contagem de humanos do canal antigo já **não inclui** o membro que saiu, então soma-se 1 a
   ela para reconstruir a população durante a janela.

5. **`VoiceWeek.splitByWeek` é a única forma de creditar tempo.** Todo crédito passa por ela,
   mesmo janelas de milissegundos, para que nenhum caminho reimplemente a divisão na fronteira.

6. **O flusher só limpa `dirty` se o valor não mudou.** Ver "Corrida do flush", abaixo.

7. **`time_credited_until` não é reiniciado na virada da semana.** Ele avança continuamente ao
   longo da sessão; quem separa as semanas é o `splitByWeek`.

## Arquitetura

### Snapshot de estado de voz

Um record puro, imutável, é a entrada de toda decisão de elegibilidade:

```java
record VoiceStateSnapshot(
        boolean bot,
        long humanCount,
        boolean selfMuted,
        boolean selfDeafened,
        boolean guildDeafened,
        boolean afkChannel,
        boolean inScope) {

    boolean deafened() {
        return selfDeafened || guildDeafened;
    }
}
```

`guildMuted` não entra: server-mute não afeta nenhum dos dois predicados. `humanCount` fica
`long` porque vem de `Stream.count()`.

### Elegibilidade

Dois predicados puros, sem estado e sem I/O, sobre o snapshot:

```java
static boolean xpEligible(VoiceStateSnapshot s);    // !bot && humanCount >= 2 && !deafened && !afkChannel && !selfMuted
static boolean timeEligible(VoiceStateSnapshot s);  // !bot && !deafened && !afkChannel && !selfMuted && inScope
```

`xpEligible` é a regra atual mais `!selfMuted`. `timeEligible` troca a exigência de
`humanCount >= 2` pela de escopo do canal.

### Escopo de canais

`VoiceScope` é uma função pura sobre `(AudioChannel, GuildConfig)`:

1. Canal em `voicetime.exclude_channels` → **não** conta.
   Senão, canal em `voicetime.include_channels` → conta.
2. Senão, categoria do canal em `voicetime.exclude_categories` → **não** conta.
   Senão, categoria em `voicetime.include_categories` → conta.
3. Senão, o padrão público.

Dentro de cada nível a exclusão é avaliada primeiro, então um ID presente nas duas listas do mesmo
nível não conta (configuração contraditória nunca abre acesso). Entre níveis vale o mais
específico: canal incluído dentro de categoria excluída **conta**; canal excluído dentro de
categoria incluída **não conta**. Canal sem categoria pula direto para o passo 3.

**Padrão público** = `@everyone` tem `VIEW_CHANNEL` e `VOICE_CONNECT` **efetivos** no canal,
considerando herança de categoria e overrides — não apenas o override local:

```java
guild.getPublicRole().hasPermission(channel, Permission.VIEW_CHANNEL, Permission.VOICE_CONNECT)
```

`Role` implementa `IPermissionHolder`, cujo `hasPermission(GuildChannel, Permission...)` já é a
permissão efetiva. Não usar `getPermissionOverride(...)`, que é só o override local, nem
`PermissionUtil` — na JDA 6.4.2 ele vive em `net.dv8tion.jda.internal.utils` e é API interna.

**Parser das listas.** As quatro chaves ficam em `GuildConfig.settings`, o `Map<String,String>`
livre que o dashboard já usa, como IDs separados por vírgula. Um único parser tolerante:

```java
static Set<String> parseIds(String raw)   // null/blank -> vazio; split ','; trim;
                                          // descarta vazios; LinkedHashSet (sem duplicatas)
```

Aceita `"123,456"`, `"123, 456"`, `""`, `null` e `",,,"` sem explodir.

### Semana

```java
static long weekStart(long epochMillis);              // segunda 00:00 America/Sao_Paulo que contém o instante
static List<Slice> splitByWeek(long from, long to);   // divide a janela nas fronteiras de semana

record Slice(long weekStart, long from, long to) {
    long durationMs() { return to - from; }
}
```

`splitByWeek` garante que a soma das `durationMs()` é exatamente `to - from`. O Brasil não tem
horário de verão desde 2019, mas o cálculo usa `ZonedDateTime` e continua correto se voltar.

Não existe job de reset. A virada da semana só faz as escritas caírem noutra linha, o que
sobrevive ao bot estar offline no momento da virada e ainda preserva o histórico.

### Persistência

**SQLite — migration `039_voice_time.sql`:**

```sql
ALTER TABLE voice_sessions ADD COLUMN time_credited_until INTEGER NOT NULL DEFAULT 0;
UPDATE voice_sessions SET time_credited_until = xp_credited_until WHERE time_credited_until = 0;

CREATE TABLE IF NOT EXISTS voice_weekly_time (
    guild_id   TEXT    NOT NULL,
    user_id    TEXT    NOT NULL,
    week_start INTEGER NOT NULL,
    ms         INTEGER NOT NULL DEFAULT 0,
    dirty      INTEGER NOT NULL DEFAULT 1,
    PRIMARY KEY (guild_id, user_id, week_start)
);
CREATE INDEX IF NOT EXISTS idx_voice_weekly_rank ON voice_weekly_time (guild_id, week_start, ms DESC);
CREATE INDEX IF NOT EXISTS idx_voice_weekly_dirty ON voice_weekly_time (dirty) WHERE dirty = 1;
```

Notas de implementação:

- **O arquivo precisa ser acrescentado à lista `SqliteMigrator.MIGRATIONS`.** Só existir em
  `resources/db/sqlite/` não basta: a lista é explícita porque varrer diretório não funciona
  dentro do fat jar. Esquecer disso faz a migration nunca rodar, em silêncio.
- Cada statement termina em `;` no fim da linha, e não há comentário depois do `;` — os dois
  requisitos do `splitStatements`.
- O `ALTER TABLE` **não é idempotente**, e isso é aceitável: o migrator controla por
  `schema_migrations` e é forward-only. Existe uma janela estreita — `applyScript` commita antes
  de `recordApplied` gravar — em que um crash deixaria a coluna criada sem o registro, e o próximo
  boot falharia. É um risco pré-existente do migrator, compartilhado por toda migration com
  `ALTER TABLE`, e não vamos resolvê-lo aqui.

**Postgres — migration `010_voice_weekly_time.sql`:**

```sql
CREATE TABLE IF NOT EXISTS voice_weekly_time (
    guild_id   TEXT   NOT NULL,
    user_id    TEXT   NOT NULL,
    week_start BIGINT NOT NULL,
    ms         BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (guild_id, user_id, week_start)
);
CREATE INDEX IF NOT EXISTS idx_voice_weekly_time_rank ON voice_weekly_time (guild_id, week_start, ms DESC);
```

Aplicada com a `ApplyPostgresSchema` (não há `psql` nesta máquina).

O flusher envia o **total absoluto** da semana, nunca um delta:

```sql
INSERT INTO voice_weekly_time (guild_id, user_id, week_start, ms, updated_at)
VALUES (?,?,?,?, now())
ON CONFLICT (guild_id, user_id, week_start) DO UPDATE SET ms = EXCLUDED.ms, updated_at = now()
```

Assim o flush é idempotente: reenviar a mesma linha após um timeout parcial não duplica tempo.

### Corrida do flush

O ticker incrementa `ms` enquanto o flusher está no meio de um round-trip para o Neon. Limpar
`dirty` incondicionalmente perderia o incremento:

1. flusher lê `ms=1000, dirty=1` e começa o upsert;
2. ticker soma 1000 → `ms=2000, dirty=1`;
3. flusher termina e zera `dirty` → a linha fica `ms=2000, dirty=0` e os 1000 novos nunca sobem.

A limpeza é portanto condicionada ao valor enviado:

```sql
UPDATE voice_weekly_time SET dirty = 0
 WHERE guild_id = ? AND user_id = ? AND week_start = ? AND ms = ?
```

Se `ms` mudou, a linha continua suja e sobe na próxima rodada. Como `ms` é monotonicamente
crescente, igualdade significa "não mudou". Não é preciso coluna de versão.

## Fluxo de dados

O invariante central: para cada sessão aberta, `time_credited_until` é o instante até o qual o
tempo já foi contabilizado. Todo crédito é da janela `[watermark, agora]`, avaliada com o estado
de voz que vigorou **durante** essa janela, e dividida por `splitByWeek`.

- **Tick (60s)** — `VoiceXpTicker`, que já existe. Para cada sessão aberta, monta o snapshot do
  estado atual, avalia os dois predicados, credita as duas janelas, avança as duas watermarks e
  soma o tempo elegível nos baldes semanais. Tudo na transação única que o `VoiceXpBatch` já abre.
  Efeitos de level-up continuam fora da transação.

- **Mudança de estado de voz** — `VoiceStateListener`, novo:

  | Evento | Snapshot anterior |
  |---|---|
  | `GuildVoiceSelfMuteEvent` | estado atual com `selfMuted = !event.isSelfMuted()` |
  | `GuildVoiceSelfDeafenEvent` | estado atual com `selfDeafened = !event.isSelfDeafened()` |
  | `GuildVoiceGuildDeafenEvent` | estado atual com `guildDeafened = !event.isGuildDeafened()` |
  | `GuildVoiceGuildMuteEvent` | idem, sem efeito prático |

  Credita a janela até o instante do evento com o snapshot anterior, e avança as watermarks. É
  isto que faz a contagem parar no exato momento do mute, e não só no tick seguinte.

  `GuildVoiceGuildMuteEvent` não muda elegibilidade (server-mute não pausa nada), mas é tratado
  pelo mesmo caminho: o crédito com o snapshot anterior é idêntico ao que seria com o atual, então
  é inofensivo, e manter o padrão evita que uma mudança futura na regra de server-mute deixe um
  buraco.

- **Entrar / sair / mover** — `VoiceSessionListener` credita a janela pendente antes de fechar a
  sessão, avaliando-a com `event.getChannelLeft()` (invariante 4). A sessão nova abre com as duas
  watermarks em `now` e só usa o canal novo nas janelas futuras.

- **Boot** — `VoiceReconciler` já fecha órfãs e reabre sessões; passa a ancorar
  `time_credited_until` em `now`. Tempo de bot offline não é creditado.

- **Flush (5 min)** — `VoiceTimeFlusher` lê as linhas `dirty` do SQLite, faz o upsert no Postgres
  e limpa a marca com a condição de valor descrita acima.

### Retenção: 90 dias

`voice_sessions` cresce sem poda desde que existe, e o ranking só aumenta o volume. Um
`VoiceRetentionSweeper` roda uma vez por dia e apaga:

- de `voice_sessions`, as sessões **fechadas** (`leave_time IS NOT NULL`) com `leave_time`
  anterior a 90 dias atrás. Sessões abertas nunca são tocadas, por mais longas que sejam.
- de `voice_weekly_time` (SQLite e Postgres), as linhas cujo `week_start` é anterior a 90 dias
  atrás **e** que não estão `dirty` — uma linha ainda não sincronizada nunca é apagada antes de
  chegar ao Postgres.

O sweeper roda depois do flusher na ordem de agendamento, para que uma linha suja recém-criada
tenha chance de subir antes de ser considerada para poda.

`VoiceSessionRepository.totalVoiceMs` e `sessionsOf`, que hoje somam a tabela inteira, passam a
refletir só os últimos 90 dias. Isso é aceitável: o ranking usa os baldes semanais, não essas
somas. A constante fica num único lugar, `VoiceRetention.DAYS`.

## Componentes

| Componente | Responsabilidade | Depende de |
|---|---|---|
| `VoiceStateSnapshot` | Estado imutável de voz, com flags de deafen separadas | — |
| `VoiceEligibility` | `xpEligible` / `timeEligible` sobre o snapshot | `VoiceStateSnapshot` |
| `VoiceScope` | Canal no escopo? Precedência, `parseIds`, padrão público efetivo | `GuildConfig`, JDA |
| `VoiceWeek` | `weekStart`, `splitByWeek` | — |
| `VoiceTimeRepository` | Baldes semanais no SQLite: somar, ranquear, ler usuário, `dirty` | `SqliteManager` |
| `VoiceXpBatch` | Transação única: avança watermarks, soma XP e tempo | `SqliteManager`, `VoiceWeek` |
| `VoiceStateListener` | Credita a janela anterior a cada mudança de estado de voz | `VoiceXpBatch` |
| `VoiceTimeFlusher` | SQLite `dirty` → Postgres, idempotente, limpeza condicional | `PostgresPool` |
| `VoiceRetentionSweeper` | Poda diária de 90 dias no SQLite e no Postgres | ambos |
| `TopCallCommand` / `TopCallView` | Ranking semanal paginado | `VoiceTimeRepository` |
| `TempoCallCommand` | Tempo semanal de um membro | `VoiceTimeRepository` |
| Tela `/setup` | Edita as quatro listas de escopo | `GuildConfigRepository` |

`/topcall` e `/tempocall` leem o **SQLite**: é a fonte de verdade e não depende do Neon estar de
pé. O site lê o Postgres.

## Comandos

Ambos usam `weekStart = VoiceWeek.weekStart(System.currentTimeMillis())` e formatam a duração
como `12h 34m` (`VoiceFormat.duration(ms)`; abaixo de 1h, `34m`; abaixo de 1min, `menos de 1m`).

**`/topcall`** — ranking paginado (10/página), no mesmo formato visual do `TopView`. Usuários com
`ms = 0` **não** aparecem:

```sql
SELECT user_id, ms FROM voice_weekly_time
 WHERE guild_id = ? AND week_start = ? AND ms > 0
 ORDER BY ms DESC LIMIT ? OFFSET ?
```

```
1. @fulano — 12h 34m
2. @beltrano — 8h 10m
```

O site aplica o mesmo `ms > 0`.

**`/tempocall [membro]`** — sem argumento, o próprio autor:

```
Tempo em call esta semana: 12h 34m
```

Quem não tem linha na semana vê `0m`, não um erro.

## Erros

- **Neon indisponível:** o flusher falha e loga; as linhas continuam `dirty` e a próxima rodada
  reenvia. Contagem e comandos do bot seguem funcionando. O SQLite nunca depende do Postgres.
- **Flush parcial:** o upsert grava total absoluto, então reenviar é seguro. `dirty` só é limpo
  das linhas cujo upsert foi confirmado **e** cujo `ms` não mudou nesse meio-tempo.
- **Membro ou canal sumiu** entre o tick e a leitura: a sessão é creditada com delta zero e as
  watermarks avançam, como o ticker já faz hoje.
- **Bot offline:** o tempo não é creditado. `VoiceReconciler` reancora as watermarks em `now`,
  então a volta não credita retroativamente as horas em que o bot esteve fora.

## Testes

Plain JUnit 5, sem Mockito (padrão do projeto; interfaces da JDA são mockadas com
`java.lang.reflect.Proxy` quando necessário).

**`VoiceEligibility`** — tabela-verdade dos dois predicados: self-mute bloqueia ambos; server-mute
bloqueia nenhum; deafen e AFK bloqueiam ambos; um humano sozinho bloqueia XP mas não tempo;
`selfDeaf=false, guildDeaf=true` resulta em `deafened()` verdadeiro.

**`VoiceScope`** — canal incluído dentro de categoria excluída conta; canal excluído dentro de
categoria incluída não conta; ID nas duas listas do mesmo nível não conta; sem listas, cai no
padrão público; canal sem categoria; `parseIds` com espaços, vazios, `null` e duplicatas.

**`VoiceWeek`** — segunda 00:00:00 pertence à própria semana; domingo 23:59:59 à anterior;
`splitByWeek` de uma janela que cruza a fronteira devolve duas fatias cuja soma é a duração
original; janela inteiramente dentro de uma semana devolve uma fatia só.

**`VoiceXpBatch`** (SQLite em memória) — usuário self-muted por uma janela avança as watermarks sem
somar XP nem tempo; usuário sozinho soma tempo mas não XP; janela cruzando a semana grava em duas
linhas; `dirty` sobe.

**`VoiceStateListener`** — self-mute credita a janela anterior como desmutado; self-unmute não
credita retroativo do período mutado; self-deafen com `guildDeaf=true` já ativo credita a janela
anterior como ensurdecida (regressão do invariante 2).

**`VoiceTimeFlusher`** — upsert repetido não duplica `ms`; `dirty` não é limpo se `ms` mudou entre
a leitura e a confirmação.

**`VoiceRetentionSweeper`** — sessão aberta antiga sobrevive; sessão fechada há 91 dias some;
sessão fechada há 89 dias fica; linha semanal `dirty` e antiga não é apagada.

## Fora de escopo

- A página de ranking no site (outro repositório). Aqui só entregamos a tabela no Neon.
- Ranking de todos os tempos. O histórico por semana fica disponível na tabela (90 dias), mas
  nenhum comando o expõe.
