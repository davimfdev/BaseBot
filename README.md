# BaseBot

Multi-tenant Discord bot built on JDA. One instance per client, four feature
modules (Base, Tickets, Sales, Facs), 70 slash commands, and every setting scoped
to the guild it belongs to. Guild configuration lives in PostgreSQL and is shared
with the [davimf.dev](https://davimf.dev) dashboard, so the bot and the web panel
always read the same source of truth.

---

## Stack

| Concern      | Choice                                                            |
| ------------ | ----------------------------------------------------------------- |
| Language     | Java 22 (Gradle toolchain)                                        |
| Discord      | JDA 6.4.2, Components V2 (containers, buttons, selects, modals)   |
| Build        | Gradle 8.14 + `com.gradleup.shadow` (single fat jar)              |
| Config store | PostgreSQL via HikariCP, source of truth for guild configuration  |
| Local store  | SQLite (WAL) for tickets, logs, economy and fast runtime state    |
| Config file  | YAML (`config.yml`) with environment-variable overrides           |
| Pix / QR     | ZXing                                                             |
| JSON / YAML  | Jackson                                                           |
| Logging      | SLF4J + Logback                                                   |
| Tests        | JUnit 5 (135 test files, no mocking framework)                    |

**Guild-scoped is the golden rule.** Every command reads the configuration of the
guild it was invoked in. Only `/bot-name`, `/bot-nick` and `/bot-icon` touch the
global bot profile.

---

## Architecture

```
dev.davimf.basebot
├─ BaseBot                 main entrypoint
├─ BotApplication          bootstrap: config -> databases -> modules -> gateway
├─ ConsoleControl          local console commands for the running process
├─ GracefulShutdown        ordered shutdown of JDA, pools and scheduler
├─ config/                 BotConfig records + ConfigLoader (YAML + env overrides)
├─ core/
│  ├─ BotContext           shared service container handed to every feature
│  ├─ command/             SlashCommand + CommandManager (register and route)
│  ├─ component/           ComponentRouter + ComponentId (namespace:action:args)
│  └─ scheduler/           TaskScheduler, failure-isolated
├─ ratelimit/              Debouncer (embed refresh) + BatchThrottler (ghost pings)
├─ crypto/                 TicketCrypto, PBKDF2 + AES-256-GCM
├─ integration/            TicketIngestClient, posts transcripts to davimf.dev
├─ database/
│  ├─ DatabaseManager      owns both stores, runs migrations
│  ├─ postgres/            pool, migrator, guild-config / VIP / snapshot repos
│  ├─ sqlite/              manager, migrator, ticket and action-log repos
│  └─ model/               GuildConfig, ActiveTicket
├─ tools/                  ApplyPostgresSchema, applies the Postgres migrations
└─ modules/                feature modules (BotModule + ModuleRegistry)
   ├─ base/                Module 1, the bulk of the bot
   ├─ tickets/             Module 2, encrypted ticket transcripts
   ├─ sales/               Module 3, Pix, catalog, budgets
   └─ facs/                Module 4, FiveM faction management
```

### Design decisions

- **Single Gradle project, feature modules as packages.** The modules are feature
  areas, not separate deployables. Each `BotModule` self-registers its commands,
  component handlers and listeners; turning one off means removing it from the
  list in `BotApplication`. One fat jar is the right shape for a bot.
- **Swappable guild-config store.** The bot talks plain JDBC behind the
  `GuildConfigRepository` interface, with `CachingGuildConfigRepository` in front
  of it. JSONB columns absorb the long tail of dashboard settings without a
  migration per feature, so moving the database is a URL change.
- **Rate-limit guards are first-class.** `Debouncer` coalesces bursty role events
  into a single panel refresh; `BatchThrottler` paces ghost pings. Both share the
  bot scheduler.
- **Crypto matches the dashboard byte for byte.** `TicketCrypto` mirrors the
  browser implementation: PBKDF2-HMAC-SHA256 into AES-256-GCM, two distinct salts
  (key and password verifier), Base64 fields. `TicketCryptoTest` proves the round
  trip, so the browser decrypts exactly what the bot produced.
- **Snapshots are incremental.** The dashboard mirror publishes per-entity diffs
  instead of whole-guild dumps, with a periodic full sync as the safety net, to
  keep managed-Postgres compute cost flat.

---

## Modules

### Module 1 - Base

The bulk of the bot: 55 commands across the areas below.

| Area         | What it ships                                                                                  |
| ------------ | ---------------------------------------------------------------------------------------------- |
| `setup`      | The `/setup` hub: log channel per event type, roles, tickets, embed color, module toggles      |
| `moderation` | Ban, softban, tempban, kick, mute, timeout, purge, lock, slowmode, plus a case/infraction file |
| `security`   | AutoMod sync, anti-spam, anti-raid with lockdown, anti-nuke with audit lookup, verification    |
| `voice`      | Voice moderation, forced moves, disconnects, and voice-time tracking                           |
| `leveling`   | Message and voice XP, rank cards, leaderboards                                                 |
| `economy`    | Balance, daily, work, bank, shop, inventory, market, crime and jobs                            |
| `fun`        | Dice, coinflip, jokenpo, hangman, quiz, polls, interactions                                    |
| `giveaway`   | Scheduled giveaways with entry panels and automatic drawing                                    |
| `welcome`    | Join and leave messages                                                                        |
| `selfroles`  | Self-assignable role panels                                                                    |
| `vip`        | VIP plans and grants with expiry, member panel and audit                                       |
| `message`    | `/mensagem`, the Components V2 message builder with image and thumbnail support                |
| `forms`      | `/formulario`, configurable forms answered through modals                                      |
| `listeners`  | Full event logging, one channel per log type                                                   |
| `snapshot`   | Incremental guild mirror for the web dashboard                                                 |
| `utility`    | Ping, serverinfo, userinfo, avatar, banner, notes, reminders, AFK, scheduled messages          |

### Module 2 - Tickets

Multi-category setup, `/ticket painel`, the creation flow, and the full dashboard
(claim, create call, add member, notify, rename, close). Closing renders the
transcript, encrypts it, posts it to davimf.dev, sends the closure embed and DM,
then deletes the channels.

### Module 3 - Sales

`/pix` (BR Code and QR image), `/tabela` product catalog, and `/orçamento`
budgets with client approval, automatic Pix dispatch and 24h auto-cancel.

### Module 4 - Facs

FiveM faction management: `/hierarquia`, `/pd`, `/solicitar-cargo`, `/punir` and
`/punições` with expiring warnings, `/painel-financeiro`, `/farm`, `/produzir`,
`/relatorio`, `/recrutamento`, and `/painel-acoes` with an Elite-priority
reservation queue.

---

## Data

| Store      | Role                                                                          |
| ---------- | ----------------------------------------------------------------------------- |
| PostgreSQL | Guild configuration, VIP plans and grants, dashboard access, audit, snapshots |
| SQLite     | Tickets, action logs, economy, punishments, forms, drafts, voice sessions     |

SQLite migrates itself on boot (`src/main/resources/db/sqlite/`, 42 migrations,
each applied at most once and tracked in `schema_migrations`). The Postgres
schema is shared with the dashboard, so it is **not** auto-migrated: apply
`src/main/resources/db/postgres/*.sql` yourself, or run the bundled
`dev.davimf.basebot.tools.ApplyPostgresSchema`.

---

## Getting started

Requirements: JDK 22 and a PostgreSQL database (any provider, the bot speaks
plain JDBC).

```bash
cp config.example.yml config.yml   # then fill in the token and the DB URL
./gradlew run                      # run from sources
./gradlew shadowJar                # build build/libs/basebot.jar
java -jar build/libs/basebot.jar   # run the fat jar
./gradlew test                     # run the test suite
```

The bot requests the **Server Members** and **Message Content** privileged
intents, so enable both in the Discord Developer Portal. `BotApplication` lists
every intent it asks for and why.

### Build toolchain note

Gradle 8.14 cannot *run* on JDK 26, so `gradle.properties` pins the Gradle launch
JDK to a local **JDK 22** through `org.gradle.java.home`. The project compiles to
Java 22 via the Gradle toolchain either way. Adjust that line if your JDK 22
lives somewhere else.

---

## Configuration

Every key in `config.yml` can be overridden by an environment variable, and the
environment always wins. `config.example.yml` documents each key; `.env.example`
lists the variables.

| Variable                                    | For                                                   |
| ------------------------------------------- | ----------------------------------------------------- |
| `BOT_TOKEN`                                 | Discord bot token                                     |
| `VAULT_GUILD_ID`                            | Optional storage-only guild used to stash attachments |
| `POSTGRES_URL`                              | Guild configuration database (libpq URI or JDBC URL)  |
| `POSTGRES_MAX_POOL`, `POSTGRES_SCHEMA`      | Pool size and schema for the config database          |
| `SQLITE_PATH`                               | Local database file, defaults to `data/basebot.db`    |
| `TICKET_INGEST_URL`, `TICKET_INGEST_SECRET` | Transcript ingest endpoint and its shared secret      |
| `TICKET_VIEW_BASE`, `TICKET_SOURCE`         | Public transcript URL base and the stored source tag  |
| `EMBED_DEBOUNCE_MS`                         | Debounce window before refreshing public embeds       |
| `GHOST_PING_BATCH`, `GHOST_PING_INTERVAL_S` | Ghost-ping batch size and the gap between batches      |
| `BOT_INSTANCE_ID`, `BOT_CLIENT_NAME`        | Instance identity; without it snapshots stay off      |

**No secret is versioned.** `config.yml` and `.env` are gitignored;
`config.example.yml` and `.env.example` carry names and comments only.

---

## Dashboard integration

Two links to [davimf.dev](https://davimf.dev):

- **Ticket transcripts.** The closure pipeline POSTs the encrypted transcript to
  `POST /api/ticket-store` with an `x-ticket-secret` header. The customer opens
  `/ticket/{id}` and decrypts it in the browser with a one-time password. The bot
  never sends plaintext, and the server never holds the key.
- **Guild snapshots.** When `BOT_INSTANCE_ID` is set, the bot mirrors channels,
  roles and members into the shared Postgres schema so the dashboard can render
  its pickers without touching the Discord API. Writes are per-entity diffs, with
  a periodic full reconciliation.

---

## License

Copyright © 2026 Davi Monteiro Fonseca. All rights reserved.
See [LICENSE](LICENSE) for details.
