# Geradores de imagem / memes (`/procurado`, `/carta_reverso`) — design

**Data:** 2026-07-02
**Módulo:** Base (`modules/base/fun/`) — standalone, não depende de facs.
**Status:** Design aprovado no brainstorming (templates fornecidos; revisão do spec pendente).

## Objetivo

Comandos de meme que compõem o **avatar do alvo** (e texto) sobre **templates PNG** desenhados por IA (fornecidos pelo usuário, sem texto embutido — o bot desenha todo texto): **`/procurado [@usuario]`** (cartaz "Procurado") e **`/carta_reverso [@usuario]`** (carta reversa genérica). Diversão pura, sem XP/moedas.

## Decisões (do brainstorming)

- **Templates PNG só-arte** em `resources/memes/` (`procurado.png`, `carta_reverso.png` — já no repo). **Sem texto/marca** na arte; o bot desenha o texto com fonte nativa (nítido, sem depender da IA acertar letras). Carta é **genérica** (sem Uno/"+4"/logo).
- **Coordenadas fracionárias** (0.0–1.0 das dimensões reais do template) → independe do tamanho do PNG; caixa do avatar + âncoras de texto por meme.
- **Java puro:** `BufferedImage`/`Graphics2D`/`ImageIO` — **sem dependência nova**.
- **Pipeline assíncrono** (regra de imagem do projeto): baixar avatar → compor → re-upload → **`erase()`**; tudo fora da thread do gateway.
- **Sem config/migração.** Módulo Fun.

## Arquitetura / arquivos

- `resources/memes/procurado.png`, `resources/memes/carta_reverso.png` — templates (só arte).
- **`MemeTemplate`** (config imutável por meme): `String resourcePath` (`/memes/...`); `AvatarBox(double x, y, w, h, Shape shape)` com `enum Shape{RECT,CIRCLE}` (frações); `List<TextAnchor>`. Constantes `PROCURADO` e `CARTA_REVERSO`.
  - **`record TextAnchor(String key, double centerX, double centerY, double maxWidth, double fontSizeH, java.awt.Color color, String fontFamily, int fontStyle, Align align)`** — `maxWidth`/`centerX`/`centerY` em frações da largura/altura; `fontSizeH` = tamanho da fonte como fração da **altura** do template; `enum Align{LEFT,CENTER,RIGHT}`.
- **`MemeRender`** (puro, testável): `byte[] compose(BufferedImage template, BufferedImage avatar, MemeTemplate spec, Map<String,String> textos)`:
  1. **Nunca desenha no template cacheado.** Cria `out = new BufferedImage(tW, tH, TYPE_INT_ARGB)`, `g = out.createGraphics()`, liga os hints (`ANTIALIASING`, `INTERPOLATION_BICUBIC`, `TEXT_ANTIALIASING`), e `g.drawImage(template, 0, 0, null)`.
  2. **Avatar cover-crop (sem distorção):** `boxX=x*tW … boxW=w*tW …`; `scale = max(boxW/aW, boxH/aH)`; `drawW=aW*scale, drawH=aH*scale`; `drawX=boxX+(boxW-drawW)/2, drawY=boxY+(boxH-drawH)/2`. **`RECT`:** clip no `Rectangle2D` da caixa → desenha. **`CIRCLE`:** `oldClip=g.getClip(); g.setClip(new Ellipse2D.Double(boxX,boxY,boxW,boxH)); g.drawImage(...); g.setClip(oldClip)` (máscara circular com antialias).
  3. **Texto com fit** (só as `key` presentes no map): `drawCenteredTextFit(g, texto, boxFração, baseFont, cor, minFontSize)` — mede com `FontMetrics`, **reduz a fonte até caber** em `maxWidth*tW`; se ainda não couber no mínimo, corta com `…`. Alinha por `align`.
  4. `ImageIO.write(out, "png", baos)` → bytes. Não faz I/O de rede; recebe os `BufferedImage` já carregados.
- **`MemeService`** (orquestra o async): `void generate(SlashCommandInteractionEvent, BotContext, MemeTemplate, Member alvo, Map<String,String> textos)`:
  - `event.deferReply(false).queue(...)` (**defer público explícito**) → `ctx.scheduler().executor().execute(...)`.
  - Carrega o template do classpath (`getResourceAsStream("/memes/..")` → `ImageIO.read`), **cacheado** (imutável — o `compose` copia antes de desenhar). Baixa o avatar preferindo **PNG estático 256px** (`alvo.getEffectiveAvatarUrl()` normalizado pra `.png?size=256`; avatar animado → **primeiro frame/estático**) via `ImageMedia.fromUrl(...)` → `ImageIO.read`.
  - `byte[] png = MemeRender.compose(...)`. **Se `png.length > 8 MB`** → erro amigável (praticamente nunca ocorre nesses tamanhos).
  - Monta a resposta (ver Saída) via `getHook().editOriginalComponents(...).useComponentsV2()`. **`erase()`** os bytes do avatar no `finally`.
  - Qualquer falha (avatar indisponível, template ausente, decode) → `getHook().editOriginal(...)` com "não consegui gerar a imagem", sem stacktrace.
- **Comandos:** `ProcuradoCommand`, `CartaReversoCommand` (opção `usuario`, default = autor). Só resolvem o alvo + os textos e chamam `MemeService`.

## Templates + coordenadas (frações — calibradas na arte; afinar após render de teste)

**`procurado.png`** (retrato):
- Avatar: `RECT`, x≈`0.20`, y≈`0.28`, w≈`0.60`, h≈`0.33` (o slot cinza; leve inset da moldura). Cover-crop (avatar quadrado → preenche o slot landscape sem sobrar cinza).
- Textos (`Align.CENTER`, cor marrom-escuro `#3B2A1A`, `Font.SERIF` bold): `title` "PROCURADO" (centerX 0.5, y≈`0.19`, `fontSizeH`≈`0.055`, `maxWidth`≈`0.80`); `reward` "RECOMPENSA $10.000" (y≈`0.72`, `fontSizeH`≈`0.032`, `maxWidth`≈`0.80`); `name` = nome do alvo (y≈`0.82`, `fontSizeH`≈`0.030`, `maxWidth`≈`0.75`).

**`carta_reverso.png`** (carta vertical):
- Avatar: `CIRCLE`, centro x≈`0.47`, y≈`0.52`, raio≈`0.13` (o círculo cinza). Máscara circular.
- Sem texto.

## Comandos + saída

- **`/procurado [usuario]`** → `MemeService.generate(PROCURADO, alvo, { title:"PROCURADO", reward:"RECOMPENSA $10.000", name: alvo.getEffectiveName() })`.
- **`/carta_reverso [usuario]`** → `MemeService.generate(CARTA_REVERSO, alvo, {})`.
- **Saída:** um `Container` V2 com `TextDisplay` (legenda curta + **menção ao alvo**, `MentionType.USER`, padrão dos GIFs `/toca_aqui`) **+** `MediaGallery.of(MediaGalleryItem.fromFile(FileUpload.fromData(png, "meme.png")))`. Enviado via `editOriginalComponents(container).useComponentsV2().queue()` (o JDA coleta o arquivo anexado pelo componente V2). Seguir o padrão/`Panels` já usado no projeto pra montar o container; não presumir uma assinatura exata de uma linha só. Público (não efêmero).

## Assíncrono, segurança e bordas

- Download + compose **sempre fora da thread do gateway** (`scheduler().executor()`), após `deferReply()`.
- Avatar: bytes baixados via `ImageMedia` (cap 8 MB) e **`erase()`** no `finally`; o `BufferedImage` do avatar é descartado ao fim.
- Template carregado do classpath (`getResourceAsStream`) — pode ser **cacheado** em memória (é imutável) pra não reler o PNG a cada uso.
- Falhas (avatar indisponível, template ausente, erro de decode) → `editOriginal` com "não consegui gerar a imagem" (efêmero-ish via hook), sem stacktrace ao usuário.
- Alvo bot/self permitido (memes valem em qualquer um). Sem gate de economia.

## Robustez de render

- Templates em `src/main/resources/memes/` carregados pelo classpath (`/memes/procurado.png`, `/memes/carta_reverso.png`).
- **Template cacheado nunca é desenhado diretamente:** `compose` cria um `BufferedImage` novo (TYPE_INT_ARGB) e copia o template antes de desenhar avatar/texto — o cache fica imutável de verdade.
- **Avatar cover-crop sem distorção:** `scale = max(boxW/aW, boxH/aH)`, centralizado na caixa.
- **Avatar circular** usa `Ellipse2D` como clip + antialias (bordas suaves).
- **Texto com largura máxima por âncora** (`maxWidth`): reduz a fonte até caber; no mínimo, corta com `…`.
- **Avatares animados** são tratados como imagem estática (primeiro frame) no v1.
- **Hints de qualidade** ligados: `ANTIALIASING`, `INTERPOLATION_BICUBIC`, `TEXT_ANTIALIASING`.

## Testes

- `MemeRenderTest` (puro, sem rede/JDA): compõe com um **avatar sintético** (`BufferedImage` 128×128 preenchido) sobre um **template sintético** pequeno para cada `MemeTemplate`. Casos:
  - retorno é **PNG válido** (`ImageIO.read(new ByteArrayInputStream(bytes)) != null`) e com as **dimensões do template**;
  - `RECT` (cover) e `CIRCLE` (máscara) não lançam exceção;
  - **texto muito longo** (nome gigante) não lança e ainda gera PNG válido (o fit reduz/corta);
  - **template cacheado não é alterado** após `compose` (comparar pixels do template antes/depois, ou passar um template e reusá-lo em duas chamadas conferindo que a 2ª não herda a 1ª);
  - `compose` com **textos ausentes** no map ignora as âncoras sem erro.
- View/serviço/comandos via build (padrão do projeto).

## Fora de escopo

- Mais memes (carta +4 real, "chad", etc.) — v1 são os dois.
- Fonte de exibição custom (usa `Font` lógica; bundlar uma fonte fica pra depois).
- GIF animado / vídeo.
- Config por-guild (recompensa fixa "$10.000").
