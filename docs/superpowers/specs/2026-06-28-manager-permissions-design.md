# Manager Permissions — Design

**Date:** 2026-06-28
**Module:** Facs (Module 4) + Base `/setup`
**Status:** Approved design, pending implementation plan

## Problem

Today every faction-management action is gated on raw Discord permissions
(`MANAGE_SERVER`, `MANAGE_ROLES`, `MODERATE_MEMBERS`, `KICK_MEMBERS`). The faction
hierarchy already defines `gerente-*` roles (Gerente Geral, Vendas, Elite, Recrutamento,
Farm) that carry **no** bot powers. We want a configurable, role-based permission system,
organized **by management category (gerência)**, where each category can be granted the
areas it may operate — and one gerência can be granted what another normally does
(cross-grants).

## Goals

- One configurable permission per management **area** (coarse granularity).
- Categories are the hierarchy `gerente-*` roles **plus** optional free server roles.
- Sensible per-domain defaults out of the box; Discord **Administrator** always bypasses.
- Configured in `/setup → Permissões`, oriented by category.
- No DB migration; stays editable by the web dashboard (Postgres source of truth).

## Non-goals

- Fine-grained sub-action permissions (deposit vs withdraw, etc.) — coarse, one per area.
- Governing base moderation (`/ban`, `/kick`, `/mute`, voice), Catálogo (`/tabela`), or
  ticket staff — those keep their current Discord-permission gates.

## Capabilities

Five capabilities, one per area. Each has a key, a label, and a default principal set.

| Capability key | Label | Default owners |
|---|---|---|
| `acoes` | Ações / Escalações | `gerente-elite` + liderança |
| `financeiro` | Financeiro | `gerente-vendas` + liderança |
| `farm` | Farm & Produção | `gerente-farm` + liderança |
| `recrutamento` | Recrutamento & Sets | `gerente-recrutamento` + liderança |
| `punicoes` | Punições | liderança only |

**liderança** = `lider`, `sub-lider`, `gerente-geral` — included in every capability's
default set. Defaults are **editable** (an admin may remove liderança from a capability);
the only un-removable bypass is Discord **Administrator**, which is the lockout safety net.

## Data model & storage

One entry per capability in `guild_config.settings`:

```
perm:financeiro -> "gerente-vendas,lider,sub-lider,gerente-geral,role:123456789012345678"
```

A **principal** is one of:
- a hierarchy role **key** (e.g. `gerente-farm`) — resolved live to a role ID via
  `cfg.role(key)`, so the grant survives remapping the role in `/setup → Cargos`;
- `role:<id>` — a raw server role ID, for free roles outside the hierarchy.

If `perm:<cap>` is **absent**, the capability falls back to its coded default set. Once a
capability is edited it is stored explicitly (including an empty string = "nobody but
Administrator"). No schema change — this rides the existing flexible `settings` map.

## `ManagerPermissions` service

A single well-bounded unit (new class, e.g. `modules/facs/perms/ManagerPermissions.java`)
with a `Capability` enum (key, label, default principals).

```
Set<String> allowedRoleIds(GuildConfig cfg, Capability cap)
    // Resolve principals -> live role IDs. Hierarchy key -> cfg.role(key) (skip if unset);
    // "role:id" -> id. If perm:<cap> absent, resolve the default set instead.

boolean can(Member member, GuildConfig cfg, Capability cap)
    // member == null            -> false
    // member.hasPermission(ADMINISTRATOR) -> true   (un-removable override)
    // member holds any allowedRoleIds(...) -> true
    // else                       -> false

String grant(GuildConfig cfg, Capability cap, String principal)   // -> updated CSV
String revoke(GuildConfig cfg, Capability cap, String principal)  // -> updated CSV
```

Decision logic is extracted into pure, JDA-free helpers for unit testing:
- `allowedRoleIds(cfg, cap)` (above), and
- `isAllowed(Set<String> memberRoleIds, boolean isAdministrator, Set<String> allowed)`.

Persistence reuses `GuildConfigEdits.withSetting(cfg, "perm:" + cap.key(), csv)` +
`ctx.database().guildConfig().save(cfg)`.

## `/setup → Permissões` UI (Components V2, by category)

New **"Permissões"** option in the hub section select.

**Permissions hub:**
- Header `## 🔐 Permissões de Gerência` + divider.
- `>` intro.
- `StringSelect` of categories: Líder, Sub-Líder, Gerente Geral, Gerente de Vendas,
  Gerente de Elite, Gerente de Recrutamento, Gerente de Farm.
- Buttons: `➕ Adicionar outro cargo` (reveals an `EntitySelect ROLE` to add a free-role
  category as `role:<id>`), `◀ Voltar`.
- Already-configured free-role categories are listed as extra select options, derived by
  scanning all `perm:*` settings for any `role:<id>` principals (deduplicated).

**Category detail** (after picking a principal):
- Header `## 🔐 <label>` + divider.
- `🎭 Cargo · <@&id>` (or `-# cargo não configurado em Cargos` when the hierarchy role
  isn't mapped — it still configures; it just matches nobody until mapped).
- A row of five **toggle buttons**, one per capability: granted = green/✅, not = grey/🔓.
  Pressing flips that one capability for this principal and re-renders.
- `◀ Voltar`. A free-role category drops out of the list once its last grant is removed.

**Handler** — new actions under the existing `setup` namespace in `SetupComponentHandler`:
- `permissoes` (nav to hub), `permcat` (string-select pick category),
  `permrole` (entity-select add free role), `permtoggle:<principal>:<cap>` (flip + render).
Follows the existing setup screen/handler pattern; persists via `GuildConfigEdits` + save.

## Integration — replacing the gates

Each gate below switches from a Discord-permission check to
`managerPermissions.can(member, cfg, <capability>)`, replying ephemerally on denial. The
commands also **drop their restrictive `setDefaultPermissions(...)`** so a gerência without
those Discord perms can still use them — the in-code `can()` is now the gate.

| Site | Capability |
|---|---|
| `PainelAcoesCommand`; `ActionService` manager gate (align, config, vitória/derrota/encerrar, backfill, remover membro, lock entradas, mudar horário) | `acoes` |
| `PainelFinanceiroCommand`; `FinanceService` buttons + modals; `RelatorioCommand` | `financeiro` |
| `FarmService` approve/reject; `ProduzirCommand` `receita` | `farm` |
| `RecrutamentoCommand`; `RecruitComponentHandler` accept/reject; `SetRequestComponentHandler` approve/reject | `recrutamento` |
| `PunirCommand`; `PdCommand`; `PunicoesCommand` | `punicoes` |

**Kept as-is on top of the capability:**
- Sets approval still also requires the approver to **outrank the requested role**
  (`Moderation.canManageRole`) and the bot to be able to assign it.
- All bot self-hierarchy / `canInteract` checks remain.

**Unchanged (out of scope):** `/farm` submit, `/produzir fazer` + `lista`,
`/solicitar-cargo` request, `/orçamento` (vendedor gate), Catálogo `/tabela`
(`MANAGE_SERVER`), base moderation, ticket staff.

## Migration / back-compat

- No DB migration — uses `guild_config.settings`. Defaults apply immediately, so existing
  guilds get sensible behavior with zero configuration.
- **Intended behavior change:** facs management no longer keys off `MANAGE_SERVER`/
  `MANAGE_ROLES`/`MODERATE_MEMBERS`; it keys off the capability (Administrator bypass). A
  non-admin who relied solely on `MANAGE_SERVER` loses access unless granted a gerência
  role — this is the point of the feature.

## Testing

Pure-logic unit tests on `ManagerPermissions`:
- default resolution when `perm:<cap>` unset;
- CSV grant/revoke round-trips (incl. revoke to empty = "Administrator only");
- principal → roleId resolution for both hierarchy keys and `role:id`;
- `allowedRoleIds` skips unconfigured hierarchy roles;
- `isAllowed` — Administrator bypass + role-membership match/no-match.

Plus a serialization smoke test for the new `SetupView.permissions` containers (V2 build),
matching the existing `ActionViewTest` style. No JDA mocking — decision logic is pure.

## Files (anticipated)

- **New:** `modules/facs/perms/ManagerPermissions.java`; test
  `ManagerPermissionsTest.java`; (optional) `SetupPermissionsViewTest`.
- **Edit:** `SetupView` (hub option + two new screens), `SetupComponentHandler` (new
  actions), and the gate sites listed in *Integration*. `BotContext`/wiring to expose a
  shared `ManagerPermissions` (it is stateless — can also be static).
