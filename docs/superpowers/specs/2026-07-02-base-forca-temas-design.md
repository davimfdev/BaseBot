# `/forca` com tema/dica — design

**Data:** 2026-07-02
**Módulo:** Base (`modules/base/fun/`).
**Status:** Aprovado no brainstorming.

## Objetivo

Cada palavra da forca passa a ter um **tema** exibido como dica desde o início (ex.: "Tema · Animais"), pra o jogo não ser adivinhação sem pistas.

## Mudanças

- **`HangmanBank`** vira temático: `record Entry(String word, String theme)`; palavras agrupadas por tema (Animais, Natureza, Comida, Objetos, Lugares…), reaproveitando as 16 atuais + algumas novas. `Entry random()` sorteia palavra+tema. (`String random()` antigo é substituído.)
- **`ForcaService.Game`** guarda o `theme` junto do `state` (pega do `Entry` no `start`) e passa pro view em `panel`/`ended`.
- **`ForcaView.panel(accent, state, theme)`** e **`ForcaView.ended(accent, state, won, theme)`** ganham uma linha **`{Emojis.INFO 💡} Tema · {tema}`** (house style, emoji custom), visível desde o começo.
- **`HangmanState` fica intacto** — o tema é dica de exibição, fora da lógica pura de adivinhação.

## Testes

- `HangmanBankTest` — `random()` devolve `word`/`theme` não-vazios; o banco cobre ≥4 temas distintos.
- `HangmanState`/`ForcaView` existentes seguem válidos (assinaturas do view mudam → ajustar as chamadas no `ForcaService`).

## Fora de escopo

- Escolher tema pelo comando; dificuldade; temas configuráveis por guild (banco fixo embutido).
