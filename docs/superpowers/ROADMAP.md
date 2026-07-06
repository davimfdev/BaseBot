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
3. **AutoMod + Segurança** — ⬜ anti-raid, anti-spam, filtro de links, automod que gera warns e escala punições. *(detalhes em FUTURE-IDEAS §Moderação Avançada)*
4. **Welcome / self-roles** — ⬜ boas-vindas + cargos por reação/botão (precisa `GUILD_MEMBERS`).
5. **Leveling** — ⬜ XP por mensagem/voz, ranking, cargos por nível. *(XP por voz usa o rastreamento de voz à prova de reinício — ver FUTURE-IDEAS §Apêndice Rastreamento de voz)*
6. **Economia (por-usuário)** — ⬜ carteira/banco, `/daily`, `/trabalhar`, `/pagar`, ranking, loja de cargos, `/setup → Economia`.
7. **Sorteios / Eventos** — ⬜ giveaways com requisitos avançados (cargo, dias no servidor, horas/janela em call). *(encaixa entre 6 e 7; tempo em call usa o mesmo rastreamento de voz — ver FUTURE-IDEAS §Apêndice)*
8. **Fun** — ⬜ `/dado`, `/coinflip`, `/rep`, `/biscoito`, `/ship`, `/quiz`, `/jokenpo`, `/forca`, GIFs de ação, memes. *(detalhes em FUTURE-IDEAS)*
9. **Utilidades** — ⬜ `/avatar`, `/banner`, `/lembrete`, `/afk`, `/enquete`, `/userinfo`, `/serverinfo`. *(detalhes em FUTURE-IDEAS)*

Specs e plans de cada subsistema ficam em `docs/superpowers/specs/` e `docs/superpowers/plans/`.
