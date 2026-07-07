<!-- [OUTLINE START]
Markdown Document: BaseBot
[OUTLINE END] -->



# BaseBot

Multi-module Discord bot ecosystem (JDA), rebuilt per `BOTSPECS.MD`. Guild-scoped
configuration is the golden rule — only `/bot-name` and `/bot-icon` touch the global
bot profile.

## Tech stack

| Concern        | Choice                                           |
|----------------|--------------------------------------------------|
| Language       | Java 22 (Gradle toolchain)                        |
| Discord        | JDA `6.4.2`, Components V2 (buttons/modals/selects)|
| Build          | Gradle + `com.gradleup.shadow` (fat jar)          |
| Config DB      | PostgreSQL via HikariCP (guild config, source of truth) |
| Local DB       | SQLite (tickets, action logs, fast state, WAL)    |
| Pix / QR       | ZXing                                             |
| Config         | YAML (`config.yml`) + env-var overrides           |
| Logging        | SLF4J + Logback                                   |

## Quick start

```bash
cp config.example.yml config.yml      # then fill in token + DB URLs (or use env vars)
./gradlew run                          # run from sources
./gradlew shadowJar                    # build build/libs/basebot.jar
java -jar build/libs/basebot.jar       # run the fat jar
```

Apply the Postgres schema once (it is shared with the web dashboard, so it is **not**
auto-migrated): `src/main/resources/db/postgres/001_guild_config.sql`. The SQLite schema
migrates automatically on boot.

### Build toolchain note

Gradle 8.14 cannot *run* on JDK 26, so `gradle.properties` pins the Gradle launch JDK to
the locally-installed **JDK 22** (`org.gradle.java.home`). The project itself compiles to
Java 22 via the Gradle toolchain regardless. Adjust that path if your JDK 22 lives
elsewhere.

## Architecture

```
dev.davimf.basebot
├─ BaseBot                main entrypoint
├─ BotApplication         bootstrap: config → DB → modules → JDA gateway
├─ config/                BotConfig (records) + ConfigLoader (YAML + env overrides)
├─ core/
│  ├─ BotContext          shared service container handed to all features
│  ├─ command/            SlashCommand + CommandManager (register & route)
│  ├─ component/          ComponentRouter + ComponentHandler + ComponentId
│  │                      (Components V2: namespace:action:args custom-ids)
│  └─ scheduler/          TaskScheduler (failure-isolated ScheduledExecutorService)
├─ ratelimit/             Debouncer (5s embed refresh) + BatchThrottler (ghost pings)
├─ crypto/                TicketCrypto — PBKDF2 + AES-256-GCM, matches the dashboard
├─ integration/           TicketIngestClient — POST transcripts to davimf.dev
├─ database/
│  ├─ DatabaseManager     owns both stores, runs SQLite migrations
│  ├─ postgres/           PostgresPool + GuildConfigRepository (swappable impl)
│  ├─ sqlite/             SqliteManager + migrator + Ticket/ActionLog repositories
│  └─ model/              GuildConfig, ActiveTicket
└─ modules/               feature modules (BotModule + ModuleRegistry)
   ├─ base/               Module 1 — Base & Utility (setup, moderation, embeds, logging)
   ├─ tickets/            Module 2 — AES-encrypted ticket transcripts
   ├─ sales/              Module 3 — Pix, budgets, catalog
   └─ facs/               Module 4 — FiveM hierarchy, actions, recruitment, punishments
```

### Design decisions

- **Single Gradle project, feature modules as packages.** BOTSPECS "modules" are
  feature areas, not separate deployables. One fat jar is the right shape for a bot.
  Each `BotModule` self-registers its commands/components/listeners; toggling a module
  off is removing it from the list in `BotApplication`.
- **Swappable guild-config store.** The web dashboard stores guild config in Supabase
  today and will migrate to a dedicated Neon schema. The bot talks plain JDBC behind the
  `GuildConfigRepository` interface, so the move is a config/URL change — no caller code
  changes. JSONB columns absorb the long tail of dashboard settings without per-feature
  migrations.
- **Rate-limit guards are first-class.** `Debouncer` (trailing-edge, per-key) coalesces
  bursty role events into one Hierarchy-panel refresh; `BatchThrottler` paces ghost pings
  (5 per 30s). Both share the bot's scheduler.
- **Crypto matches the dashboard byte-for-byte.** `TicketCrypto` mirrors
  `davimf.dev/src/lib/ticketCrypto.ts`: PBKDF2-HMAC-SHA256 → AES-256-GCM, two distinct
  salts (key + password verifier), Base64 fields. `TicketCryptoTest` proves the
  round-trip, so the browser will decrypt what the bot produces.

## Web dashboard integration

The ticket closure pipeline POSTs encrypted transcripts to
`POST https://davimf.dev/api/ticket-store` (header `x-ticket-secret`), matching the
`REQUIRED` field list in `netlify/functions/ticket-store.ts`. Users open the transcript
at `https://davimf.dev/ticket/{id}` and decrypt it in-browser with the one-time password.

## Status

The project builds, tests, and packages (`./gradlew test shadowJar` is green).

- **Module 1 — Base & Utility:** moderation, role management (hierarchy-validated), voice
  moderation, channel lock/unlock, `/addemoji`, `/listacargo`, bot profile, the `/setup`
  hub (logs/roles/tickets/bot + per-guild embed color), `/embed` + `/editembed` (webhook
  impersonation), `/formulario` (configurable forms), and full event logging (SQLite +
  per-type channel embeds).
- **Module 2 — Tickets:** multi-category setup, `/ticket painel`, creation flow, the full
  dashboard (Assumir/Criar Call/Membro/Notificar/Renomear/Fechar) and the closure pipeline
  (render → AES-encrypt → POST to davimf.dev → closure embed + DM → delete channels).
- **Module 3 — Sales:** `/pix` (BR Code/QR), `/tabela` catalog, and `/orçamento` budgets
  with client approval, Pix auto-dispatch, and 24h auto-cancel.
- **Module 4 — Facs/FiveM:** `/hierarquia`, `/pd`, `/solicitar-cargo`, `/punir` + `/punições`
  (ADV 20-day expiry), `/painel-financeiro`, `/farm`, `/produzir`, `/relatorio`,
  `/recrutamento`, and `/painel-acoes` (Elite-priority reservation queue).
