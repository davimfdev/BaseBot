# BaseBot — Roadmap

> Visão geral e ordem de construção do bot all-in-one. Documentos irmãos:
> - **`PROGRESS.md`** — o que já foi feito / handoff de sessão (leia primeiro ao retomar).
> - **`FUTURE-IDEAS.md`** — backlog detalhado de features (Fun, Utilidades, Sorteios, memes, etc.).

## Visão
O bot está virando um **all-in-one** que substitui os outros bots (moderação, segurança/automod, fun, leveling, economia, utilidades) — exceto música por enquanto. Construído um subsistema por vez: cada um com seu **spec → plan → implementação**.

## Decisões-chave
- **Base é um produto standalone**, desenhado independente do módulo **facs** (facção FiveM), que pode ser separado depois. Features do Base **não podem depender** do facs. Moderação do Base usa permissões do Discord (não `ManagerPermissions`, que é só do facs).
- **Intents privilegiados habilitados** (`GUILD_MEMBERS` + `MESSAGE_CONTENT`) — destrava automod, auto-responder, welcome/autorole, logs de membro, XP por voz/mensagem. (Ligar também no Dev Portal.)
- **Português-first**: nomes PT onde fizer sentido (`/punições`, `/infrações`), mantendo termos universais (`/ban`, `/timeout`, `/purge`); prefixo `un-` para reversões.
- **Duas economias coexistem**: a **tesouraria da facção** (facs, já existente) vs uma futura **economia geral por-usuário** (estilo UnbelievaBoat) — mantidas separadas.
- **Regras de UI**: toda mensagem é **Components V2 container**; **emojis custom** via `Emojis` (nunca unicode cru); house style das embeds (seções com `---`, footer de timestamp); confirmações públicas **auto-deletam** (`Replies.reply`).
- **Migrações SQLite** novas DEVEM ser anexadas a `SqliteMigrator.MIGRATIONS` (lista hardcoded) ou nunca aplicam.

## Ordem de build
1. **Moderação + Infrações** — ✅ feito (sistema de casos, escalonamento, `/setup → Moderação`).
2. **Logging completo** — ✅ feito (8 listeners focados, arquivo de mensagens + cofre de anexos, logs de membro ricas, layout "separado por linhas", footer global).
3. **AutoMod + Segurança** — ✅ feito (anti-raid, anti-spam, filtro de links/convites, verificação, anti-nuke; AutoMod nativo → warns que escalam via Infrações).
4. **Welcome / self-roles** — ✅ feito (boas-vindas/despedida/autorole + painéis de auto-cargos por botão/menu; precisa `GUILD_MEMBERS`).
5. **Leveling** — ✅ feito (XP por mensagem/voz à prova de reinício, ranking, cargos por nível).
6. **Economia (por-usuário)** — ✅ feito (carteira/banco, `/daily`/`/trabalhar`/`/crime`/`/roubar`/`/pagar`, ranking; **Loja** de cargos perm/temp + itens custom com `/loja` e sweep de expiração; `/setup → Economia` + `/setup → Loja`).
7. **Sorteios / Eventos** — ✅ feito (giveaways com requisitos avançados: cargo, dias no servidor, horas/janela em call; + eventos aleatórios de chat).
8. **Fun** — ✅ feito (`/dado`, `/coinflip`, `/rep`, `/biscoito`, `/ship`, `/quiz`, `/jokenpo`, `/forca`, GIFs de ação).
9. **Utilidades** — ✅ feito (`/avatar`, `/banner`, `/lembrete`, `/afk`, `/enquete`, `/userinfo`, `/serverinfo`).

**Fases 1–9 completas.** Pendências: smoke manual num servidor de teste (intents privilegiados) e backlog opcional em `FUTURE-IDEAS.md` (geradores de imagem/memes). Detalhes de cada subsistema em `PROGRESS.md` (§1–§22).

Specs e plans de cada subsistema ficam em `docs/superpowers/specs/` e `docs/superpowers/plans/`.
