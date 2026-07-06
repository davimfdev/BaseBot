# Base Moderation + Infractions — Design

**Date:** 2026-06-29
**Module:** Base (standalone — no facs dependency)
**Status:** Approved design, pending implementation plan

## Context & goals

First step of turning BaseBot into an all-in-one bot that replaces general-purpose
moderation bots (Dyno/Carl-bot style). This subsystem adds a complete, unified moderation
& **infractions** system to the **base** module.

**Guiding constraints (from brainstorming):**
- **Base is a standalone product.** The infractions system must NOT depend on the facs
  module (which may be split out later). It is gated on Discord permissions, not on
  `ManagerPermissions`. The faction `/punir` system stays facs-only and untouched.
- **Portuguese-first.** Command names in PT where natural (accents are allowed — `/punições`
  already ships); keep universally-understood terms (`/ban`, `/timeout`, `/purge`,
  `/slowmode`, `/nuke`). `un-` prefix for reversals (matches `/unmute`, `/unban`).
- **Privileged intents are enabled** (`MESSAGE_CONTENT` + `GUILD_MEMBERS`) — assumed
  available for the broader roadmap; this subsystem itself needs neither beyond what is
  already enabled.
- Config lives in `guild_config` (Postgres source of truth, dashboard-editable later);
  operational records live in SQLite (matches the existing split).

## Approach: unified case system

Every moderation action — existing `/ban` `/kick` `/mute` `/mutecall` **and** the new
`/avisar` `/timeout` `/tempban` `/softban` `/nota` — flows through one `ModerationService`
that records a numbered **case**. `/infrações @user` shows the complete history. The three
existing commands are refactored to route through the service (they already log to
`action_logs`; this adds the case row + DM + escalation check).

## Part 0 — `/setup` navigation addition (foundational, do first)

Purely additive change to the existing `/setup` wizard:
- Add a persistent module-picker `StringSelect` ("Ir para a seção…") to **every** setup
  screen, listing: Visão geral, Logs, Cargos, Tickets, Ações, Bot, Permissões, **Moderação**.
  The current section shows in the placeholder; picking any option jumps straight there.
- **Keep all existing buttons unchanged** — pagination (`◀ ▶`), `◀ Voltar`, sub-screen
  navigation. (Logs and Cargos span multiple pages and still need them.)
- Implementation: one shared `SetupView.moduleNav(currentSection)` `ActionRow` reused by
  every screen. `SetupComponentHandler`'s existing `section` select already routes to each
  module; it simply becomes present on every screen. No screen loses functionality.

## Part 1 — Architecture & files

Lives under `modules/base/moderation/` (alongside `Moderation.java`, `MessagePurge.java`).

- **`Infraction`** (record) + **`InfractionRepository`** — SQLite CRUD over new migration
  `022_infractions.sql`. Per-guild sequential **case numbers**.
- **`ModerationService`** — the single orchestrator. For every action: apply it, record a
  case, DM the target (if enabled), post to the modlog, run the escalation check. Existing
  `BanCommand`/`KickCommand`/`MuteCommand`/`MuteCallCommand` call into it.
- **`ModerationConfig`** — parse/serialize escalation rules + warn TTL + toggles from
  `guild_config`.
- **`InfractionView`** + a component handler — Components V2 history panel (paginated) and
  case detail with a **Revogar** button; the `/revogar @membro` select panel.
- **Commands (base):** `/avisar`, `/infrações`, `/caso`, `/revogar`, `/timeout`,
  `/untimeout`, `/tempban`, `/softban`, `/nota`, `/purge`, `/slowmode`, `/nuke`.
- **Scheduler:** extend the existing timed-mute sweep to expire **tempbans** (auto-unban)
  and decay expired **warns**.
- **Modlog:** new `log-moderacao` log type in `SetupLogTypes` (set in `/setup → Logs`).

## Part 2 — Data model

### `022_infractions.sql`

| column | meaning |
|---|---|
| `id` | internal PK (autoincrement) |
| `guild_id` | guild |
| `case_number` | per-guild sequential (e.g. "Caso #42") |
| `user_id` | target |
| `mod_id` | moderator id, or `system` for auto-escalation |
| `type` | `WARN` · `NOTE` · `TIMEOUT` · `MUTE` · `MUTECALL` · `KICK` · `BAN` · `TEMPBAN` · `SOFTBAN` |
| `reason` | nullable |
| `created_at` | epoch millis |
| `expires_at` | nullable — warn decay / timeout / tempban end |
| `duration_ms` | nullable — for display |
| `active` | counts toward escalation & "in effect"; cleared on revoke/expiry |

Index on `(guild_id, user_id)` and `(guild_id, case_number)`. `case_number` is assigned as
`MAX(case_number for guild) + 1`, computed and inserted in the **same transaction** so
concurrent actions in a guild can't collide (the bot is a single instance; the transaction
keeps it safe regardless).

Escalation counts **active, non-expired `WARN`** rows only. `NOTE` never counts. Reversals
(`/untimeout`, unban, `/revogar`) set `active = false` on the relevant case.

### Moderation config (in `guild_config`)

- `settings["mod:warn-ttl-days"]` — warns expire after N days (`0` = never).
- `settings["mod:escalation"]` — compact rules string, e.g. `3=timeout:1h,5=kick,7=ban`,
  parsed to `List<EscalationRule(threshold, action, durationMs)>`. Valid actions:
  `timeout:<dur>`, `tempban:<dur>`, `mute:<dur>` (role-mute), `kick`, `ban`. Durations via
  the existing `Durations` parser. Malformed entries are dropped (not fatal).
- `toggles["mod:dm-on-action"]` — DM the target on action (default **true**).
- `toggles["mod:require-reason"]` — require a reason on punitive actions (default **false**).

`ModerationConfig` exposes typed getters and serializers; persisted via the existing
`GuildConfigEdits.withSetting` / `withToggle`.

## Part 3 — Command surface

| Command | Action / case type |
|---|---|
| `/avisar @usuário [motivo]` | `WARN` + runs escalation |
| `/infrações @usuário` | full case history (paginated V2 panel) |
| `/caso <número>` | case detail + **Revogar** button |
| `/revogar @membro` | panel with a select of the member's revocable cases → revoke chosen |
| `/timeout @usuário <tempo> [motivo]` | native timeout → `TIMEOUT` (the default mute) |
| `/untimeout @usuário [motivo]` | removes timeout; marks case inactive |
| `/tempban @usuário <tempo> [motivo]` | `TEMPBAN`; scheduler auto-unbans at expiry |
| `/softban @usuário [motivo] [dias]` | ban+unban to wipe messages → `SOFTBAN` |
| `/nota @usuário <texto>` | `NOTE` (internal, never escalates) |
| `/purge [quantidade] [de:@user] [apenas:bots\|links\|anexos\|humanos] [contém:texto]` | filtered cleanup |
| `/slowmode <tempo> [canal]` | channel slowmode (`0` to clear) |
| `/nuke [canal]` | clone + delete the channel (with confirmation button) |

**Refactored to record cases:** `/ban`, `/kick`, `/mute` (role-mute stays as the *secondary*
mechanism, for permanent / >28d mutes), `/mutecall`. Their reversals `/unban`, `/unmute`,
`/unmutecall` flip the matching case to inactive. The existing `/clear` (last N) and `/cl`
(own messages) remain as quick tools alongside `/purge`.

## Part 4 — Behavior & flow

**Warn + escalation:** `/avisar` → create `WARN` case → DM target (if `dm-on-action`) →
modlog → count the member's active, non-expired warns → if the count matches an escalation
rule, auto-apply that action as a **`system`-issued case** (its own DM + modlog). If the
auto-action can't apply (bot below the target's role, missing perm), log a skip to the
modlog instead of failing.

**DM format:** "Você recebeu **{tipo}** em **{servidor}** — Caso #{n}. Motivo: {motivo}."
Silent if DMs are closed.

**Expiry/decay (scheduler, reusing the timed-mute sweep):**
- Warns: `expires_at = created_at + warn-ttl`; excluded from counting once past; sweep flips
  them `active = false`.
- Tempban: sweep auto-unbans at `expires_at`, marks case inactive.
- Timeout: Discord lifts it natively at `expires_at`; we mark the case inactive.

**Guards (every action):** Discord-perm gate (`MODERATE_MEMBERS` for warn/timeout/note/mute;
`BAN_MEMBERS` for ban/tempban/softban; `MANAGE_MESSAGES` for purge; `MANAGE_CHANNEL` for
slowmode/nuke), the existing `Moderation.canModerate` hierarchy check (can't action someone
above you or the bot), no self/bot targets, and `require-reason` enforced when toggled on.

## Part 5 — `/setup → Moderação`

A new **"Moderação"** option in the picker. Screen (Components V2, house style):
- Overview block: `⏳ Expiração de warns · 30d`, `📈 Escalonamento · 3=timeout:1h, 5=kick, 7=ban`,
  `✉️ DM ao infrator · on`, `📝 Exigir motivo · off`, and whether `log-moderacao` is set.
- **Editar regras** button → a modal with two fields: the escalation rule string and the
  warn-TTL in days. `ModerationConfig` validates; bad input is rejected with a hint.
- Two toggle buttons: **DM ao infrator** and **Exigir motivo** (`guild_config.toggles`).
- `-#` hint that the modlog channel is set in `/setup → Logs`, plus the module-picker and
  `◀ Voltar`.

## Part 6 — Testing

Pure-logic unit tests (no JDA mocking — matches the repo style):
- **`ModerationConfig`**: escalation string parse/serialize round-trips; durations
  (`1h`/`30m`/`7d`) parse via `Durations`; malformed rules dropped, not crashing.
- **Escalation engine**: given an active-warn count + rules → the correct action (exact
  threshold match; none when no rule matches).
- **Warn counting**: warns past `created_at + ttl` are excluded.
- **`InfractionRepository`** against a temp SQLite DB (existing `SqliteManager` + migrator):
  `case_number` increments per-guild; list-by-user; revoke flips `active`.
- **`InfractionView`** serialization smoke test (history panel + case detail), like
  `ActionViewTest`.

## Out of scope (later specs/iterations)

Appeals, modmail, reaction-role/welcome (separate module), automod (separate spec), and the
website config UI (the config already lives in `guild_config` so the dashboard can edit it
without bot changes).

## Files (anticipated)

- **New:** `modules/base/moderation/{Infraction,InfractionRepository,ModerationService,ModerationConfig,InfractionView,InfractionComponentHandler}.java`; the 11 command classes; `resources/db/sqlite/022_infractions.sql`; tests.
- **Edit:** `SetupView` (module-nav row on every screen + Moderação screen),
  `SetupComponentHandler` (Moderação actions), `SetupLogTypes` (`log-moderacao`),
  `BanCommand`/`KickCommand`/`MuteCommand`/`MuteCallCommand` (route through service +
  reversals mark inactive), the scheduler sweep, and the base module wiring/registration.
