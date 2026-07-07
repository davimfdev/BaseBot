# Pix — Painel único, normalização inteligente de chaves e correção do BR Code inválido

**Data:** 2026-07-01
**Módulo:** Sales (`modules/sales/pix`)
**Status:** Design aprovado

## Problema

1. **BR Code inválido no pagamento.** Ao testar (`tipo PHONE, chave +5562986089609, nome Davi Monteiro Fonseca, cidade Goiânia`), o Pix deu inválido para quem tentou pagar. Causa raiz: `PixPayload.emv()` declara o comprimento de cada campo TLV em **caracteres** (`value.length()`), mas o CRC e o parser do app pagador operam sobre **bytes**. Caracteres acentuados (`â` em `Goiânia`, `é` em `José`) ocupam 1 char porém 2 bytes em UTF-8 → o comprimento declarado diverge do real → o parser desalinha → payload inválido.
2. **Parâmetro `cidade` desnecessário.** O Pix só precisa da chave; o nome serve apenas para informar ao cliente quem recebe. A cidade não deveria ser pedida.
3. **Sem normalização de chave.** O bot exige o valor já formatado. Deveria aceitar variações (`62986089609`, `5562986089609`, CPF com pontos/traços, CNPJ formatado) e normalizar para o formato canônico do Pix automaticamente.
4. **Configuração espalhada em subcomandos.** `/pix registrar` + `/pix gerar`. A configuração deve ser feita por um painel, não por comandos separados.

## Decisões (do brainstorming)

- **Chave continua por vendedor** (por `user_id`), mas agora **múltiplas chaves por vendedor**.
- `/pix` **vira comando único** que abre um **painel efêmero**; não vai para `/setup` (que é restrito a `MANAGE_SERVER` e não caberia numa config por vendedor). O gate do cargo **vendedor** é mantido.
- **Cidade fixa `BRASIL`** internamente (campo EMV 60 é obrigatório).
- **Enviar cobrança** posta a cobrança **publicamente no canal** (como o `/pix gerar` atual); painel e seleção são efêmeros.
- **Editar chave** altera só o **valor** e o **nome**; o **tipo** fica fixo (para trocar o tipo: remover e cadastrar de novo).

## Arquitetura

Dois eixos: (A) correção + normalização puras e testáveis; (B) a UI de painel efêmero.

### A. Correção do BR Code (`PixPayload`)

Ao montar o payload, **dobrar para ASCII** o nome e a cidade: `Normalizer.normalize(s, NFD)` + remover marcas de combinação (`\p{M}`) + descartar qualquer byte não-ASCII restante, e recortar ao limite (nome 25, cidade 15). Assim char-length == byte-length e some a ambiguidade de encoding.

- `José` → `JOSE`; `Goiânia` → `GOIANIA`.
- O nome **armazenado/exibido** permanece legível (`José`); a dobra ASCII acontece **apenas** na geração do BR Code.
- Default de `merchantCity` no builder passa a ser `BRASIL`.
- CRC-16 (`Crc16`, CCITT-FALSE) e a estrutura TLV permanecem inalterados — já estavam corretos.

### B. Normalização de chaves (`PixKeyNormalizer` — classe pura nova)

`static Result normalize(String tipo, String valorBruto)`, onde `Result` é um record `(boolean ok, String value, String error)`. Erros são mensagens PT-BR amigáveis exibidas efemeramente.

- **CPF:** remove não-dígitos → exige 11 dígitos e valida os dígitos verificadores. `123.456.789-09` → `12345678909`.
- **CNPJ:** remove não-dígitos → exige 14 dígitos e valida os verificadores. `12.345.678/0001-95` → `12345678000195`.
- **PHONE:** remove não-dígitos, normaliza para E.164 `+55` + DDD + número:
  - 10 ou 11 dígitos (DDD + número) → prefixa `+55`.
  - 12 ou 13 dígitos começando com `55` → prefixa `+`.
  - já em `+55…` → mantém após limpar.
  - valida o total final (12 ou 13 dígitos após o `+55`).
- **EMAIL:** `trim` + minúsculas; valida formato básico (`algo@algo.tld`).
- **RANDOM (EVP):** `trim` + minúsculas; valida formato UUID (`8-4-4-4-12`).

### C. Modelo de dados — migração `026_pix_keys_multi.sql`

Recria `pix_keys` permitindo N chaves por vendedor e remove `merchant_city`:

```sql
CREATE TABLE pix_keys_new (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    guild_id      TEXT NOT NULL,
    user_id       TEXT NOT NULL,
    key_type      TEXT NOT NULL,
    key_value     TEXT NOT NULL,
    merchant_name TEXT NOT NULL,
    created_at    TEXT NOT NULL DEFAULT (datetime('now'))
);
INSERT INTO pix_keys_new (guild_id, user_id, key_type, key_value, merchant_name)
    SELECT guild_id, user_id, key_type, key_value, merchant_name FROM pix_keys;
DROP TABLE pix_keys;
ALTER TABLE pix_keys_new RENAME TO pix_keys;
CREATE INDEX idx_pix_keys_user ON pix_keys (guild_id, user_id);
```

- **Gotcha:** anexar `"/db/sqlite/026_pix_keys_multi.sql"` a `SqliteMigrator.MIGRATIONS`; cada statement termina com `;` no fim da linha, sem comentário inline após `;`.
- `PixKey` (model) passa a ser `(long id, String guildId, String userId, String keyType, String keyValue, String merchantName)` — **sem `merchantCity`**.
- `PixKeyRepository`: `List<PixKey> list(guildId, userId)`, `Optional<PixKey> find(long id)`, `long insert(PixKey)` (retorna id gerado), `void update(long id, keyValue, merchantName)`, `void delete(long id)`, `Optional<PixKey> findDefault(guildId, userId)` (primeira por `created_at`/`id`). Remove `upsert`/`findByUser`.

### D. UI de painel (`PixPanelView` novo + `PixComponentHandler` expandido)

`PixCommand` deixa de ter subcomandos; `/pix` valida o gate vendedor e responde com o painel raiz **efêmero**. `PixPanelView` renor­iza (Components V2, house style, emojis custom) as telas; `PixComponentHandler` (namespace `pix`) roteia. Fluxos:

- **Painel raiz:** botões **Enviar cobrança** (`pix:send`) e **Gerenciar chaves** (`pix:manage`).
- **Enviar (`pix:send`):** sem chaves → aviso "cadastre uma chave". Com chaves → `StringSelectMenu` (`pix:sendkey`) com as chaves do vendedor (rótulo = tipo + valor mascarado + nome). Ao escolher → `replyModal` (`pix:sendform:<id>`) com um campo **valor (opcional)**. No submit → posta a cobrança **pública** no canal via `PixDispatch.render(key, cents, accent, sellerId)` (copia-e-cola + QR + botão Confirmar restrito ao vendedor).
- **Gerenciar (`pix:manage`):** lista as chaves; por chave, botões **Editar** (`pix:edit:<id>`) e **Remover** (`pix:del:<id>`); botão **Cadastrar nova** (`pix:new`) e **Voltar** (`pix:root`).
  - **Cadastrar (`pix:new`):** `StringSelectMenu` de tipo (`pix:type:new`) → `replyModal` (`pix:form:new:<tipo>`) com **chave** + **nome**. No submit → `PixKeyNormalizer.normalize` → erro efêmero se inválido, senão `insert` e re-render.
  - **Editar (`pix:edit:<id>`):** `replyModal` (`pix:form:edit:<id>`) prefixado com chave + nome atuais (tipo fixo). No submit → normaliza (com o tipo existente) → `update` e re-render.
  - **Remover (`pix:del:<id>`):** `delete` imediato + re-render (feedback efêmero).
- Botão existente `pix:confirmar:<ownerId>` (dono confirma pagamento) é mantido inalterado.

### E. Orçamentos (`BudgetService`)

Troca `pixKeys.findByUser(guildId, sellerId)` por `pixKeys.findDefault(guildId, sellerId)` (a primeira chave do vendedor). Escolher a chave por orçamento fica como melhoria futura (fora de escopo). `PixDispatch.render` não passa mais cidade — usa o default `BRASIL`.

## Componentes e interfaces

| Unidade | Faz | Depende de |
|---|---|---|
| `PixPayload` (mod.) | Monta o BR Code EMV com dobra ASCII de nome/cidade | `Crc16`, `java.text.Normalizer` |
| `PixKeyNormalizer` (novo) | Normaliza/valida valor por tipo; puro | — |
| `PixKey` (mod.) | Record da chave com `id`, sem cidade | — |
| `PixKeyRepository` (mod.) | CRUD multi-chave por guild+user | `SqliteManager` |
| `PixPanelView` (novo) | Renderiza painel/telas efêmeras V2 | `Panels`, `Emojis`, `PixKey` |
| `PixCommand` (mod.) | `/pix` único, gate vendedor, abre painel | `PixPanelView`, `GuildConfig` |
| `PixComponentHandler` (mod.) | Roteia botões/selects/modais do painel + confirmar | `PixKeyRepository`, `PixKeyNormalizer`, `PixDispatch`, `PixPanelView` |
| `PixDispatch` (mod.) | Renderiza a cobrança (sem cidade) | `PixPayload`, `PixQrCode` |
| `BudgetService` (mod.) | Auto-dispatch usa `findDefault` | `PixKeyRepository` |

## Tratamento de erros

- Chave inválida na normalização → resposta efêmera PT-BR ("CPF inválido", "Telefone inválido", etc.); nada é salvo.
- Enviar sem chaves cadastradas → aviso efêmero orientando cadastrar.
- Falha ao postar a cobrança pública → erro efêmero (como hoje).
- Chave/painel não encontrado (id inexistente) → aviso efêmero e re-render.

## Testes

- **`PixKeyNormalizerTest` (novo):** CPF/CNPJ com e sem formatação e com verificador inválido; PHONE nas variações (11 díg, com 55, com +55); EMAIL trim/lowercase e inválido; RANDOM UUID válido/inválido.
- **`PixPayloadTest` (atualizar):** payload com nome/cidade acentuados (`José`, `Goiânia`) resulta em BR Code **somente ASCII** e com comprimentos TLV coerentes em bytes; CRC recomputável (teste existente mantido).
- **`PixKeyRepositoryTest` (reescrever):** múltiplas chaves por user (`insert`/`list`/`find`/`update`/`delete`/`findDefault`), escopo por guild, sem coluna de cidade.

## Fora de escopo

- Migrar outras configs (`/tabela`, orçamentos) para painéis — o princípio geral fica para follow-ups; este spec cobre só o Pix.
- Escolher a chave por orçamento (usa a padrão).
- Rótulo/apelido customizado por chave (o rótulo do select é derivado de tipo + valor mascarado + nome).
- Validação de tipo por auto-detecção (o tipo continua sendo escolhido explicitamente).
