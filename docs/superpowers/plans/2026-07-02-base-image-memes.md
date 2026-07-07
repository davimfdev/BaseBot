# Geradores de imagem / memes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `/procurado [@user]` e `/carta_reverso [@user]` — compõem o avatar do alvo (+ texto) sobre templates PNG só-arte, com pipeline de imagem assíncrono.

**Architecture:** `MemeRender` (puro, Graphics2D — copia o template, encaixa o avatar cover-crop/circular, escreve texto com fit) é o núcleo testado; `MemeTemplate` guarda as coordenadas fracionárias; `MemeService` orquestra o async (deferReply público → executor → baixa avatar via `ImageMedia` → compõe → MediaGallery → erase).

**Tech Stack:** Java 22 (BufferedImage/Graphics2D/ImageIO — sem dependência nova), JDA 6.4.2 (Components V2), JUnit 5 (sem Mockito).

## Global Constraints

- **Base ≠ facs.** Módulo Fun (`modules/base/fun/`). Sem config/migração. Sem gate de economia.
- **Regra de imagem:** download+processamento **fora da thread do gateway** (`ctx.scheduler().executor()` após `event.deferReply(false)`); avatar via `ImageMedia` (cap 8 MB) e **`erase()`** no `finally`.
- **Template cacheado nunca é desenhado diretamente:** `compose` cria um `BufferedImage` novo (TYPE_INT_ARGB) e copia o template antes.
- **Cover-crop sem distorção:** `scale = max(boxW/aW, boxH/aH)`, centralizado. **Círculo:** clip `Ellipse2D` + antialias.
- **Texto:** fit por `maxWidth` (reduz fonte até caber; senão corta com `…`). Fontes lógicas (`Serif` bold). Cor do cartaz `#3B2A1A`.
- **PNG final > 8 MB → erro amigável.** Falhas → `editOriginal` sem stacktrace. Avatar animado → 1º frame estático.
- **Emojis** (se usar) via `Emojis.of(...)`. Templates já em `src/main/resources/memes/procurado.png` e `carta_reverso.png`.
- **Spec:** `docs/superpowers/specs/2026-07-02-base-image-memes-design.md`.

---

### Task 1: `MemeTemplate` + `MemeRender` (puro) + teste

**Files:**
- Create: `src/main/java/dev/davimf/basebot/modules/base/fun/MemeTemplate.java`
- Create: `src/main/java/dev/davimf/basebot/modules/base/fun/MemeRender.java`
- Test: `src/test/java/dev/davimf/basebot/modules/base/fun/MemeRenderTest.java`

**Interfaces:**
- Produces:
  - `MemeTemplate` — `enum Shape{RECT,CIRCLE}`, `enum Align{LEFT,CENTER,RIGHT}`; `record AvatarBox(double centerX, double centerY, double w, double h, Shape shape)`; `record TextAnchor(String key, double centerX, double centerY, double maxWidth, double fontSizeH, java.awt.Color color, String fontFamily, int fontStyle, Align align)`; `record MemeTemplate(String resourcePath, AvatarBox avatar, java.util.List<TextAnchor> texts)`; constantes `PROCURADO`, `CARTA_REVERSO`.
  - `MemeRender.compose(BufferedImage template, BufferedImage avatar, MemeTemplate spec, Map<String,String> textos) throws IOException → byte[]` (PNG).

- [ ] **Step 1: Write the failing test**

`MemeRenderTest.java`:
```java
package dev.davimf.basebot.modules.base.fun;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MemeRenderTest {

    private static BufferedImage solid(int w, int h, Color c) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(c);
        g.fillRect(0, 0, w, h);
        g.dispose();
        return img;
    }

    private static BufferedImage readPng(byte[] bytes) throws Exception {
        return ImageIO.read(new ByteArrayInputStream(bytes));
    }

    @Test
    void procuradoProducesValidPngOfTemplateSize() throws Exception {
        BufferedImage tpl = solid(200, 300, new Color(220, 200, 160));
        BufferedImage avatar = solid(128, 128, Color.BLUE);
        byte[] png = MemeRender.compose(tpl, avatar, MemeTemplate.PROCURADO,
                Map.of("title", "PROCURADO", "reward", "RECOMPENSA $10.000", "name", "Fulano"));
        BufferedImage out = readPng(png);
        assertNotNull(out);
        assertEquals(200, out.getWidth());
        assertEquals(300, out.getHeight());
    }

    @Test
    void cartaCircleDoesNotThrow() throws Exception {
        BufferedImage tpl = solid(200, 300, Color.WHITE);
        BufferedImage avatar = solid(128, 128, Color.RED);
        byte[] png = MemeRender.compose(tpl, avatar, MemeTemplate.CARTA_REVERSO, Map.of());
        assertNotNull(readPng(png));
    }

    @Test
    void hugeTextStillValid() throws Exception {
        BufferedImage tpl = solid(200, 300, Color.WHITE);
        BufferedImage avatar = solid(64, 64, Color.GREEN);
        String huge = "Nome absurdamente gigante ".repeat(20);
        byte[] png = MemeRender.compose(tpl, avatar, MemeTemplate.PROCURADO, Map.of("name", huge));
        assertNotNull(readPng(png));
    }

    @Test
    void cachedTemplateNotMutated() throws Exception {
        BufferedImage tpl = solid(200, 300, new Color(1, 2, 3));
        int before = tpl.getRGB(10, 10);
        MemeRender.compose(tpl, solid(64, 64, Color.RED), MemeTemplate.PROCURADO, Map.of("title", "X"));
        assertEquals(before, tpl.getRGB(10, 10), "o template cacheado não pode ser alterado");
    }

    @Test
    void missingTextKeysAreIgnored() throws Exception {
        BufferedImage tpl = solid(200, 300, Color.WHITE);
        // PROCURADO tem 3 âncoras; passamos nenhuma → não deve lançar
        byte[] png = MemeRender.compose(tpl, solid(64, 64, Color.RED), MemeTemplate.PROCURADO, Map.of());
        assertNotNull(readPng(png));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "dev.davimf.basebot.modules.base.fun.MemeRenderTest"`
Expected: FAIL — `MemeTemplate`/`MemeRender` não existem.

- [ ] **Step 3: Write the implementation**

`MemeTemplate.java`:
```java
package dev.davimf.basebot.modules.base.fun;

import java.awt.Color;
import java.awt.Font;
import java.util.List;

/** Config fracionária (0–1) de um meme: onde vai o avatar e o texto. */
public record MemeTemplate(String resourcePath, AvatarBox avatar, List<TextAnchor> texts) {

    public enum Shape { RECT, CIRCLE }
    public enum Align { LEFT, CENTER, RIGHT }

    /** centerX/centerY/w/h em frações. CIRCLE usa {@code w} como diâmetro (fração da largura). */
    public record AvatarBox(double centerX, double centerY, double w, double h, Shape shape) {}

    /** centerX/centerY/maxWidth em frações; fontSizeH = tamanho da fonte como fração da ALTURA. */
    public record TextAnchor(String key, double centerX, double centerY, double maxWidth,
                             double fontSizeH, Color color, String fontFamily, int fontStyle, Align align) {}

    private static final Color PARCH = new Color(0x3B, 0x2A, 0x1A);

    public static final MemeTemplate PROCURADO = new MemeTemplate(
            "/memes/procurado.png",
            new AvatarBox(0.50, 0.445, 0.60, 0.33, Shape.RECT),
            List.of(
                    new TextAnchor("title",  0.5, 0.19, 0.80, 0.055, PARCH, Font.SERIF, Font.BOLD, Align.CENTER),
                    new TextAnchor("reward", 0.5, 0.72, 0.80, 0.032, PARCH, Font.SERIF, Font.BOLD, Align.CENTER),
                    new TextAnchor("name",   0.5, 0.82, 0.75, 0.030, PARCH, Font.SERIF, Font.BOLD, Align.CENTER)));

    public static final MemeTemplate CARTA_REVERSO = new MemeTemplate(
            "/memes/carta_reverso.png",
            new AvatarBox(0.47, 0.52, 0.26, 0.26, Shape.CIRCLE),
            List.of());
}
```

`MemeRender.java`:
```java
package dev.davimf.basebot.modules.base.fun;

import dev.davimf.basebot.modules.base.fun.MemeTemplate.Align;
import dev.davimf.basebot.modules.base.fun.MemeTemplate.AvatarBox;
import dev.davimf.basebot.modules.base.fun.MemeTemplate.Shape;
import dev.davimf.basebot.modules.base.fun.MemeTemplate.TextAnchor;

import javax.imageio.ImageIO;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

/** Composição pura de memes: template (copiado) + avatar (cover-crop/círculo) + texto (fit). */
public final class MemeRender {

    private static final float MIN_FONT = 10f;

    private MemeRender() {}

    public static byte[] compose(BufferedImage template, BufferedImage avatar, MemeTemplate spec,
                                 Map<String, String> textos) throws IOException {
        int tW = template.getWidth();
        int tH = template.getHeight();
        BufferedImage out = new BufferedImage(tW, tH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.drawImage(template, 0, 0, null); // cópia — não mexe no template cacheado

        drawAvatar(g, avatar, spec.avatar(), tW, tH);

        for (TextAnchor a : spec.texts()) {
            String text = textos.get(a.key());
            if (text != null && !text.isBlank()) {
                drawText(g, text, a, tW, tH);
            }
        }

        g.dispose();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(out, "png", baos);
        return baos.toByteArray();
    }

    private static void drawAvatar(Graphics2D g, BufferedImage avatar, AvatarBox box, int tW, int tH) {
        int boxW, boxH;
        if (box.shape() == Shape.CIRCLE) {
            boxW = boxH = (int) Math.round(box.w() * tW);
        } else {
            boxW = (int) Math.round(box.w() * tW);
            boxH = (int) Math.round(box.h() * tH);
        }
        int boxX = (int) Math.round(box.centerX() * tW) - boxW / 2;
        int boxY = (int) Math.round(box.centerY() * tH) - boxH / 2;

        int aW = avatar.getWidth();
        int aH = avatar.getHeight();
        double scale = Math.max((double) boxW / aW, (double) boxH / aH);
        int drawW = (int) Math.round(aW * scale);
        int drawH = (int) Math.round(aH * scale);
        int drawX = boxX + (boxW - drawW) / 2;
        int drawY = boxY + (boxH - drawH) / 2;

        var oldClip = g.getClip();
        if (box.shape() == Shape.CIRCLE) {
            g.setClip(new Ellipse2D.Double(boxX, boxY, boxW, boxH));
        } else {
            g.setClip(new Rectangle2D.Double(boxX, boxY, boxW, boxH));
        }
        g.drawImage(avatar, drawX, drawY, drawW, drawH, null);
        g.setClip(oldClip);
    }

    private static void drawText(Graphics2D g, String text, TextAnchor a, int tW, int tH) {
        int maxW = (int) Math.round(a.maxWidth() * tW);
        float size = (float) (a.fontSizeH() * tH);
        Font font = new Font(a.fontFamily(), a.fontStyle(), 12).deriveFont(size);
        FontMetrics fm = g.getFontMetrics(font);
        while (fm.stringWidth(text) > maxW && size > MIN_FONT) {
            size -= 1f;
            font = font.deriveFont(size);
            fm = g.getFontMetrics(font);
        }
        String draw = text;
        if (fm.stringWidth(draw) > maxW) {
            draw = ellipsize(draw, fm, maxW);
        }
        int strW = fm.stringWidth(draw);
        int cx = (int) Math.round(a.centerX() * tW);
        int tx = switch (a.align()) {
            case CENTER -> cx - strW / 2;
            case LEFT -> cx;
            case RIGHT -> cx - strW;
        };
        int ty = (int) Math.round(a.centerY() * tH) + (fm.getAscent() - fm.getDescent()) / 2;
        g.setFont(font);
        g.setColor(a.color());
        g.drawString(draw, tx, ty);
    }

    private static String ellipsize(String text, FontMetrics fm, int maxW) {
        String ell = "…";
        if (fm.stringWidth(ell) > maxW) {
            return "";
        }
        StringBuilder sb = new StringBuilder(text);
        while (sb.length() > 0 && fm.stringWidth(sb + ell) > maxW) {
            sb.deleteCharAt(sb.length() - 1);
        }
        return sb + ell;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "dev.davimf.basebot.modules.base.fun.MemeRenderTest"`
Expected: PASS.

- [ ] **Step 5: Commit** *(pular no modo no-commit)*

```bash
git add src/main/java/dev/davimf/basebot/modules/base/fun/MemeTemplate.java \
        src/main/java/dev/davimf/basebot/modules/base/fun/MemeRender.java \
        src/test/java/dev/davimf/basebot/modules/base/fun/MemeRenderTest.java
git commit -m "feat(fun): MemeRender + MemeTemplate (composição de meme, pura)"
```

---

### Task 2: `MemeService` + `/procurado` + `/carta_reverso` + wiring

**Files:**
- Create: `src/main/java/dev/davimf/basebot/modules/base/fun/MemeService.java`
- Create: `src/main/java/dev/davimf/basebot/modules/base/commands/ProcuradoCommand.java`
- Create: `src/main/java/dev/davimf/basebot/modules/base/commands/CartaReversoCommand.java`
- Modify: `src/main/java/dev/davimf/basebot/modules/base/BaseModule.java`

**Interfaces:**
- Consumes: Task 1 (`MemeTemplate`, `MemeRender`); `ImageMedia` (`fromUrl`→`Image` com `bytes()`/`erase()`), `BotContext` (`scheduler().executor()`), `Panels`/`Replies`/`EmbedColor`, JDA (`FileUpload`, `MediaGallery`, `MediaGalleryItem`, `Container`, `deferReply`, `getHook`).
- Produces: `MemeService(BotContext)` — `void generate(SlashCommandInteractionEvent event, MemeTemplate spec, Member alvo, Map<String,String> textos, String caption)`. Comandos `ProcuradoCommand`/`CartaReversoCommand` (opção `usuario`, default autor), recebem `MemeService`.

> Build-verified (a lógica testável está em Task 1). **Referência obrigatória:** leia `src/main/java/dev/davimf/basebot/modules/base/commands/TocaAquiCommand.java` (e o `GifClient`) — é o análogo exato: `deferReply` → trabalho async → `getHook().editOriginalComponents(...)` com `MediaGallery`. Copie o padrão de montar o `Container` V2 com `MediaGallery` de lá; adapte o `FileUpload` (dados gerados) no lugar do GIF.

- [ ] **Step 1: Write `MemeService`**

Estrutura (adaptar o padrão do `TocaAquiCommand` pra montar a resposta V2):
```java
package dev.davimf.basebot.modules.base.fun;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.util.ImageMedia;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.utils.FileUpload;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Pipeline assíncrono dos memes: baixa avatar → compõe → MediaGallery → erase. */
public final class MemeService {

    private static final long MAX_OUT = 8L * 1024 * 1024;
    private final BotContext ctx;
    private final Map<String, BufferedImage> templateCache = new ConcurrentHashMap<>();

    public MemeService(BotContext ctx) { this.ctx = ctx; }

    private BufferedImage template(String resourcePath) {
        return templateCache.computeIfAbsent(resourcePath, p -> {
            try (InputStream in = MemeService.class.getResourceAsStream(p)) {
                if (in == null) {
                    throw new IllegalStateException("template ausente: " + p);
                }
                return ImageIO.read(in);
            } catch (Exception e) {
                throw new IllegalStateException("falha ao carregar " + p, e);
            }
        });
    }

    public void generate(SlashCommandInteractionEvent event, MemeTemplate spec, Member alvo,
                         Map<String, String> textos, String caption) {
        event.deferReply(false).queue(hook -> ctx.scheduler().executor().execute(() -> {
            ImageMedia.Image av = null;
            try {
                BufferedImage tpl = template(spec.resourcePath());
                String url = alvo.getEffectiveAvatarUrl().replace(".gif", ".png") + "?size=256";
                av = ImageMedia.fromUrl(url);
                BufferedImage avatar = ImageIO.read(new ByteArrayInputStream(av.bytes()));
                if (avatar == null) {
                    throw new IllegalStateException("avatar ilegível");
                }
                byte[] png = MemeRender.compose(tpl, avatar, spec, textos);
                if (png.length > MAX_OUT) {
                    hook.editOriginal("A imagem ficou grande demais. Tenta de novo.").queue();
                    return;
                }
                // Monta o Container V2 (TextDisplay com a menção + MediaGallery), no padrão do TocaAquiCommand.
                // Ex.: Panels.container(accent, Panels.text(caption + " " + alvo.getAsMention()),
                //        MediaGallery.of(MediaGalleryItem.fromFile(FileUpload.fromData(png, "meme.png"))))
                hook.editOriginalComponents(MemeResponse.build(ctx, event, caption, alvo, png))
                        .useComponentsV2().queue();
            } catch (Exception e) {
                hook.editOriginal("Não consegui gerar a imagem 😕").queue(null, x -> {});
            } finally {
                if (av != null) {
                    av.erase();
                }
            }
        }));
    }
}
```
> **Nota:** `MemeResponse.build(...)` acima é um placeholder pra você **inlinar** conforme o padrão real do `TocaAquiCommand`: montar um `Container` V2 com um `TextDisplay` (legenda + `alvo.getAsMention()`, suprimindo mentions indesejadas como o projeto faz) **e** `MediaGallery.of(MediaGalleryItem.fromFile(FileUpload.fromData(png, "meme.png")))`, e passar esse container pro `editOriginalComponents(...).useComponentsV2()`. Use `Panels`/`EmbedColor` do projeto. Não crie a classe `MemeResponse` se puder montar inline; ela só ilustra o ponto de extensão.

- [ ] **Step 2: Write the commands**

`ProcuradoCommand` (mirror `TocaAquiCommand`/`ShipCommand` pra a option `usuario`):
```java
package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.modules.base.fun.MemeService;
import dev.davimf.basebot.modules.base.fun.MemeTemplate;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.Map;

/** /procurado — cartaz "Procurado" com o avatar do alvo. */
public final class ProcuradoCommand implements SlashCommand {
    private final MemeService memes;
    public ProcuradoCommand(MemeService memes) { this.memes = memes; }

    @Override public String name() { return "procurado"; }

    @Override public SlashCommandData data() {
        return Commands.slash("procurado", "Coloca alguém num cartaz de 'Procurado'.")
                .addOption(OptionType.USER, "usuario", "Alvo (padrão: você)", false);
    }

    @Override public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null || event.getMember() == null) {
            Replies.ephemeral(event, ctx, "Use este comando em um servidor.");
            return;
        }
        OptionMapping opt = event.getOption("usuario");
        Member alvo = opt == null ? event.getMember() : opt.getAsMember();
        if (alvo == null) {
            Replies.ephemeral(event, ctx, "Alvo inválido.");
            return;
        }
        memes.generate(event, MemeTemplate.PROCURADO, alvo,
                Map.of("title", "PROCURADO", "reward", "RECOMPENSA $10.000", "name", alvo.getEffectiveName()),
                "🤠 Procurado!");
    }
}
```
`CartaReversoCommand` — idêntico trocando: name `"carta_reverso"`, descrição, `MemeTemplate.CARTA_REVERSO`, `Map.of()` (sem texto), legenda `"🔄 Levou reverso!"`.

- [ ] **Step 3: Wire into BaseModule**

Em `BaseModule.register(...)`, no bloco de Fun (perto de `TocaAquiCommand`/`AbracarCommand`):
```java
        dev.davimf.basebot.modules.base.fun.MemeService memes =
                new dev.davimf.basebot.modules.base.fun.MemeService(ctx);
        registry.command(new dev.davimf.basebot.modules.base.commands.ProcuradoCommand(memes));
        registry.command(new dev.davimf.basebot.modules.base.commands.CartaReversoCommand(memes));
```

- [ ] **Step 4: Full build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL; `MemeRenderTest` verde + suíte.

- [ ] **Step 5: Commit** *(pular no modo no-commit)*

```bash
git add src/main/java/dev/davimf/basebot/modules/base/fun/MemeService.java \
        src/main/java/dev/davimf/basebot/modules/base/commands/ProcuradoCommand.java \
        src/main/java/dev/davimf/basebot/modules/base/commands/CartaReversoCommand.java \
        src/main/java/dev/davimf/basebot/modules/base/BaseModule.java
git commit -m "feat(fun): /procurado + /carta_reverso (geradores de imagem)"
```

---

## Self-review (cobertura do spec)
- `MemeTemplate` (frações + TextAnchor com maxWidth) + `MemeRender` (copia template, cover-crop, círculo com clip+antialias, text-fit) → Task 1 (testado: PNG válido, dims, texto longo, template não-mutado, texto ausente).
- `MemeService` async (deferReply público, template cache, avatar via ImageMedia+erase, cap 8 MB, erro amigável, avatar animado→png) + `/procurado`/`/carta_reverso` + wiring → Task 2 (build-verified, espelha `TocaAquiCommand`).
- Sem config/migração; módulo Fun; templates em resources/memes/ (já presentes).
- **Calibração:** as frações de `MemeTemplate` são estimadas da arte — após o primeiro render real, ajustar as constantes se algo sair torto (não bloqueia o build).
