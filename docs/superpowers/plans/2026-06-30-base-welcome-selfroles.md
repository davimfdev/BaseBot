# Base — Boas-vindas / Despedida / Autorole + Self-roles · Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans (inline). Steps em checkbox.

**Goal:** No join, postar boas-vindas (canal e/ou DM) + atribuir autorole; no leave, despedida opcional; e painéis de self-role (botões ou menu, com modo exclusivo) auto-atribuíveis. Tudo em `/setup`.

**Architecture:** Dois sub-sistemas no pacote `modules.base`. **Welcome:** `WelcomeConfig` (leitor puro) + `WelcomeText` (placeholders, puro) + `WelcomeListener` (join/leave), imagem persistida no **vault** (novo `AttachmentVault.store`). **Self-roles:** tabela SQLite (migração `025`) + `SelfRolePanelRepository` + `SelfRoleView` + `SelfRoleComponentHandler` (namespace `selfrole`). Setup ganha duas seções; segue o padrão dos módulos de segurança (config em `guild_config`, telas V2, `edit(event, screen)`).

**Tech Stack:** Java 22, JDA 6.4.2 (Components V2), SQLite.

## Global Constraints
- **JDK 22.** **Base ≠ facs:** sem import de código facs; autorole faz fallback para a chave-string `sem-set`. Components V2 + emojis custom (`Emojis`); house style. Config em `guild_config`; estado relacional em SQLite (**anexar migração a `SqliteMigrator.MIGRATIONS`**, próximo nº `025`). Degrada em silêncio sem permissão/intent. **Sem commits** (regra do projeto — cada task termina em build/test, não em commit). Precisa do intent `GUILD_MEMBERS` para join/leave.

**Símbolos JDA confirmados (javap/código):**
- `Member.getEffectiveName()`, `Member.getUser()`, `Member.getRoles()`, `Guild.getMemberCount()` (int), `User.openPrivateChannel()` → `CacheRestAction<PrivateChannel>`; `PrivateChannel.sendMessageComponents(...)` (de `MessageChannel`). DM fechada → `ErrorResponseException CANNOT_SEND_TO_USER`: **sempre** passar callback de erro que engole.
- `StringSelectMenu.create(id)` → `.setMinValues(int)`, `.setMaxValues(int)`, `.setPlaceholder(...)`, `.addOption(label, value, Emoji)`/`.addOptions(SelectOption...)`, `.build()`. `SelectOption.of(label, value).withEmoji(Emoji).withDescription(...)`. `StringSelectInteractionEvent.getValues()` → `List<String>`. **Máx 25 opções.**
- Botões: `ActionRow.of(Button...)` (≤5 por row, ≤5 rows = **25** por painel).
- `MediaGallery.of(List<MediaGalleryItem>)`, `MediaGalleryItem.fromUrl(url)`.
- `FileUpload.fromData(byte[], String fileName)`; `ImageMedia.fromUrl(url)` → `Image{ bytes(), fileName(), erase() }` (bloqueia → off-thread).
- `GuildConfigEdits.withToggle/withSetting/withRole/withChannel`; `cfg.toggle/setting/role/channel`.

---

## Sub-sistema A — Boas-vindas / Despedida / Autorole

### Task 1: `WelcomeConfig` (leitor puro) + teste

**Files:** Create `src/main/java/dev/davimf/basebot/modules/base/welcome/WelcomeConfig.java`, `src/test/java/dev/davimf/basebot/modules/base/welcome/WelcomeConfigTest.java`.

**Interfaces — Produces:** chaves `KEY_*` e leitores `enabled/dm/channel/message/imageRef/autoroleKey + DEFAULT_WELCOME/DEFAULT_FAREWELL` usados pelo `WelcomeListener` (Task 4) e pelo setup (Task 5).

- [ ] **Step 1: Teste**
```java
package dev.davimf.basebot.modules.base.welcome;

import dev.davimf.basebot.database.model.GuildConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WelcomeConfigTest {
    private static GuildConfig cfg(Map<String, String> settings, Map<String, Boolean> toggles) {
        return new GuildConfig("g1", null, null, Map.of(), Map.of(), toggles, List.of(), settings);
    }

    @Test
    void defaults() {
        GuildConfig c = cfg(Map.of(), Map.of());
        assertFalse(WelcomeConfig.enabled(c));
        assertFalse(WelcomeConfig.dm(c));
        assertFalse(WelcomeConfig.farewellEnabled(c));
        assertEquals(WelcomeConfig.DEFAULT_WELCOME, WelcomeConfig.message(c));
        assertEquals(WelcomeConfig.DEFAULT_FAREWELL, WelcomeConfig.farewellMessage(c));
        assertNull(WelcomeConfig.imageRef(c));
    }

    @Test
    void imageRefSplitsChannelAndMessage() {
        GuildConfig c = cfg(Map.of(WelcomeConfig.KEY_IMAGE, "111:222"), Map.of());
        assertArrayEquals(new String[]{"111", "222"}, WelcomeConfig.imageRef(c));
    }
}
```
- [ ] **Step 2: Run** `./gradlew test --tests "*.WelcomeConfigTest"` → FAIL (classe não existe).
- [ ] **Step 3: Implementar**
```java
package dev.davimf.basebot.modules.base.welcome;

import dev.davimf.basebot.database.model.GuildConfig;

/** Leitor puro da config de boas-vindas/despedida (prefixo {@code welcome:}). */
public final class WelcomeConfig {

    public static final String KEY_ENABLED = "welcome:enabled";
    public static final String KEY_CHANNEL = "welcome:channel";
    public static final String KEY_DM = "welcome:dm";
    public static final String KEY_MESSAGE = "welcome:message";
    public static final String KEY_IMAGE = "welcome:image";
    public static final String KEY_AUTOROLE = "welcome:autorole";
    public static final String KEY_FAREWELL_ENABLED = "welcome:farewell-enabled";
    public static final String KEY_FAREWELL_CHANNEL = "welcome:farewell-channel";
    public static final String KEY_FAREWELL_MESSAGE = "welcome:farewell-message";

    /** Slot de cargo de fallback do autorole quando o módulo facs está em uso. */
    public static final String FALLBACK_AUTOROLE_KEY = "sem-set";

    public static final String DEFAULT_WELCOME =
            "Bem-vindo(a) ao **{server}**, {mention}! Você é o membro **{count}**.";
    public static final String DEFAULT_FAREWELL =
            "**{user}** saiu do servidor. Agora somos **{count}**.";

    private WelcomeConfig() {}

    public static boolean enabled(GuildConfig cfg) { return cfg.toggle(KEY_ENABLED, false); }
    public static boolean dm(GuildConfig cfg) { return cfg.toggle(KEY_DM, false); }
    public static boolean farewellEnabled(GuildConfig cfg) { return cfg.toggle(KEY_FAREWELL_ENABLED, false); }

    public static String message(GuildConfig cfg) { return orDefault(cfg.setting(KEY_MESSAGE), DEFAULT_WELCOME); }
    public static String farewellMessage(GuildConfig cfg) { return orDefault(cfg.setting(KEY_FAREWELL_MESSAGE), DEFAULT_FAREWELL); }

    /** {@code [vaultChannelId, vaultMessageId]} ou {@code null} quando não há imagem. */
    public static String[] imageRef(GuildConfig cfg) {
        String raw = cfg.setting(KEY_IMAGE);
        if (raw == null || raw.isBlank() || !raw.contains(":")) {
            return null;
        }
        String[] parts = raw.split(":", 2);
        return new String[]{parts[0].trim(), parts[1].trim()};
    }

    private static String orDefault(String v, String def) {
        return v == null || v.isBlank() ? def : v;
    }
}
```
- [ ] **Step 4: Run** `./gradlew test --tests "*.WelcomeConfigTest"` → PASS.

---

### Task 2: `WelcomeText` (placeholders, puro) + teste

**Files:** Create `modules/base/welcome/WelcomeText.java`, `test/.../welcome/WelcomeTextTest.java`.

**Interfaces — Produces:** `static String render(String template, Member member, Guild guild)` usado no `WelcomeListener` (Task 4).

- [ ] **Step 1: Teste** (usa Mockito, já no classpath — ver `ManagerPermissionsTest`/outros; se preferir, monte com mocks simples)
```java
package dev.davimf.basebot.modules.base.welcome;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WelcomeTextTest {

    private Member member(String name, String mention) {
        Member m = mock(Member.class);
        when(m.getEffectiveName()).thenReturn(name);
        when(m.getAsMention()).thenReturn(mention);
        return m;
    }

    private Guild guild(String name, int count) {
        Guild g = mock(Guild.class);
        when(g.getName()).thenReturn(name);
        when(g.getMemberCount()).thenReturn(count);
        return g;
    }

    @Test
    void substitutesAllTokens() {
        String out = WelcomeText.render("{user}/{mention}/{server}/{count}",
                member("Davi", "<@1>"), guild("Casa", 42));
        assertEquals("Davi/<@1>/Casa/42", out);
    }

    @Test
    void leavesLiteralTextUntouched() {
        assertEquals("Olá!", WelcomeText.render("Olá!", member("x", "<@1>"), guild("g", 1)));
    }

    @Test
    void nullTemplateBecomesEmpty() {
        assertEquals("", WelcomeText.render(null, member("x", "<@1>"), guild("g", 1)));
    }
}
```
- [ ] **Step 2: Run** `./gradlew test --tests "*.WelcomeTextTest"` → FAIL.
- [ ] **Step 3: Implementar**
```java
package dev.davimf.basebot.modules.base.welcome;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;

/** Substitui os placeholders de boas-vindas/despedida. Puro/testável. */
public final class WelcomeText {

    private WelcomeText() {}

    public static String render(String template, Member member, Guild guild) {
        if (template == null) {
            return "";
        }
        return template
                .replace("{user}", member.getEffectiveName())
                .replace("{mention}", member.getAsMention())
                .replace("{server}", guild.getName())
                .replace("{count}", String.valueOf(guild.getMemberCount()));
    }
}
```
- [ ] **Step 4: Run** `./gradlew test --tests "*.WelcomeTextTest"` → PASS.

---

### Task 3: `AttachmentVault.store(...)` — guardar imagem avulsa com rótulo

**Files:** Modify `modules/base/listeners/AttachmentVault.java`.

**Interfaces — Produces:** `void store(String originGuildId, String label, FileUpload file, BiConsumer<String,String> onStored)` (callback = `vaultChannelId, vaultMessageId`), reusado no setup (Task 5).

- [ ] **Step 1: Implementar** — adicionar após `archive(...)`:
```java
    /**
     * Re-hospeda uma imagem avulsa (não vinda de uma mensagem) no canal do vault da guilda de
     * origem, usando {@code label} como conteúdo (ex.: "welcome-banner <guildId>"). Em sucesso
     * chama {@code onStored(vaultChannelId, vaultMessageId)}. Best-effort; nunca lança.
     */
    public void store(String originGuildId, String label, FileUpload file,
                      java.util.function.BiConsumer<String, String> onStored) {
        if (!enabled()) {
            closeQuietly(java.util.List.of(file));
            return;
        }
        Guild vault = ctx.jda().getGuildById(vaultGuildId);
        if (vault == null) {
            closeQuietly(java.util.List.of(file));
            return;
        }
        resolveChannel(vault, originGuildId, channel -> {
            if (channel == null) {
                closeQuietly(java.util.List.of(file));
                return;
            }
            channel.sendMessage(label).setFiles(file).queue(
                    sent -> onStored.accept(channel.getId(), sent.getId()),
                    err -> closeQuietly(java.util.List.of(file)));
        });
    }
```
> `resolveChannel`/`closeQuietly`/`enabled` já existem no arquivo. `FileUpload` já está importado.
- [ ] **Step 2: Run** `./gradlew build` → BUILD SUCCESSFUL.

---

### Task 4: `WelcomeListener` (join/leave: mensagem + autorole + despedida)

**Files:** Create `modules/base/welcome/WelcomeListener.java`.

**Interfaces — Consumes:** `WelcomeConfig.*`, `WelcomeText.render`, `AttachmentVault.retrieveUrls`. **Produces:** registrado no `BaseModule` (Task 11).

- [ ] **Step 1: Implementar**
```java
package dev.davimf.basebot.modules.base.welcome;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.modules.base.listeners.AttachmentVault;
import dev.davimf.basebot.util.EmbedColor;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.mediagallery.MediaGallery;
import net.dv8tion.jda.api.components.mediagallery.MediaGalleryItem;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.ArrayList;
import java.util.List;

/** Boas-vindas (canal e/ou DM) + autorole no join; despedida opcional no leave. Requer GUILD_MEMBERS. */
public final class WelcomeListener extends ListenerAdapter {

    private final BotContext ctx;
    private final AttachmentVault vault;

    public WelcomeListener(BotContext ctx) {
        this.ctx = ctx;
        this.vault = new AttachmentVault(ctx, ctx.config().discord().vaultGuildId());
    }

    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(event.getGuild().getId());
        autorole(event.getMember(), cfg);
        if (!WelcomeConfig.enabled(cfg)) {
            return;
        }
        String text = WelcomeText.render(WelcomeConfig.message(cfg), event.getMember(), event.getGuild());
        int accent = EmbedColor.resolve(cfg);
        String[] img = WelcomeConfig.imageRef(cfg);
        if (img == null) {
            deliver(event.getMember(), cfg, container(accent, text, null));
        } else {
            vault.retrieveUrls(img[0], img[1], urls ->
                    deliver(event.getMember(), cfg, container(accent, text, urls.isEmpty() ? null : urls.get(0))));
        }
    }

    @Override
    public void onGuildMemberRemove(GuildMemberRemoveEvent event) {
        if (event.getMember() == null) {
            return; // sem cache do membro não há nome; pula
        }
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(event.getGuild().getId());
        if (!WelcomeConfig.farewellEnabled(cfg)) {
            return;
        }
        TextChannel ch = channel(event.getGuild(), cfg.channel(WelcomeConfig.KEY_FAREWELL_CHANNEL));
        if (ch == null) {
            return;
        }
        String text = WelcomeText.render(WelcomeConfig.farewellMessage(cfg), event.getMember(), event.getGuild());
        ch.sendMessageComponents(container(EmbedColor.resolve(cfg), text, null))
                .useComponentsV2().setAllowedMentions(List.of()).queue(ok -> {}, err -> {});
    }

    private void autorole(Member member, GuildConfig cfg) {
        String roleId = cfg.role(WelcomeConfig.KEY_AUTOROLE);
        if (roleId == null || roleId.isBlank()) {
            roleId = cfg.role(WelcomeConfig.FALLBACK_AUTOROLE_KEY); // facs em uso
        }
        if (roleId == null || roleId.isBlank()) {
            return;
        }
        Role role = member.getGuild().getRoleById(roleId);
        if (role != null && member.getGuild().getSelfMember().canInteract(role)) {
            member.getGuild().addRoleToMember(member, role).reason("Autorole").queue(ok -> {}, err -> {});
        }
    }

    private void deliver(Member member, GuildConfig cfg, Container panel) {
        TextChannel ch = channel(member.getGuild(), cfg.channel(WelcomeConfig.KEY_CHANNEL));
        if (ch != null) {
            ch.sendMessageComponents(panel).useComponentsV2()
                    .setAllowedMentions(List.of(Message.MentionType.USER)).queue(ok -> {}, err -> {});
        }
        if (WelcomeConfig.dm(cfg)) {
            member.getUser().openPrivateChannel().queue(
                    pc -> pc.sendMessageComponents(panel).useComponentsV2().queue(ok -> {}, err -> {}),
                    err -> { /* DM fechada (CANNOT_SEND_TO_USER): engole */ });
        }
    }

    private static Container container(int accent, String text, String imageUrl) {
        List<ContainerChildComponent> kids = new ArrayList<>();
        kids.add(Panels.text(text));
        if (imageUrl != null) {
            kids.add(MediaGallery.of(List.of(MediaGalleryItem.fromUrl(imageUrl))));
        }
        return Panels.container(accent, kids.toArray(new ContainerChildComponent[0]));
    }

    private static TextChannel channel(Guild guild, String id) {
        return id == null || id.isBlank() ? null : guild.getTextChannelById(id);
    }
}
```
> **Verificar no build:** `ctx.config().discord().vaultGuildId()` (igual ao uso em `BaseModule`), `Container`/`ContainerChildComponent` imports, `Message.MentionType.USER`.
- [ ] **Step 2: Run** `./gradlew build` → BUILD SUCCESSFUL.

---

### Task 5: `/setup → Boas-vindas` (tela + modal + selects + wiring)

**Files:** Modify `modules/base/setup/SetupView.java`, `modules/base/setup/SetupComponentHandler.java`.

**Interfaces — Consumes:** `WelcomeConfig.*`, `AttachmentVault.store`, `ImageMedia.fromUrl`. **Produces:** seção navegável `boasvindas`.

- [ ] **Step 1:** `SetupView` — adicionar `welcomeScreen` e `welcomeModal`. Reusa os helpers privados `channelSelect`/`roleSelect` (já no arquivo). Inserir antes de `securityScreen`:
```java
    public static Container welcomeScreen(GuildConfig cfg) {
        int accent = EmbedColor.resolve(cfg);
        boolean on = WelcomeConfig.enabled(cfg);
        boolean dm = WelcomeConfig.dm(cfg);
        boolean fw = WelcomeConfig.farewellEnabled(cfg);
        String autorole = cfg.role(WelcomeConfig.KEY_AUTOROLE);
        String autoroleLabel = autorole != null ? "<@&" + autorole + ">"
                : (cfg.role(WelcomeConfig.FALLBACK_AUTOROLE_KEY) != null ? "Sem Set (facs)" : "nenhum");
        String overview = Emojis.of(Emojis.MEMBER, "👋") + " **Boas-vindas** · " + (on ? "ligado" : "desligado") + "\n"
                + Emojis.of(Emojis.MEMBER, "✉️") + " **Também no DM** · " + (dm ? "sim" : "não") + "\n"
                + Emojis.of(Emojis.MEMBERS, "🏷️") + " **Autorole** · " + autoroleLabel + "\n"
                + Emojis.of(Emojis.MEMBER, "👋") + " **Despedida** · " + (fw ? "ligada" : "desligada");
        return Panels.container(accent,
                Panels.text("## " + Emojis.of(Emojis.MEMBER, "👋") + " Boas-vindas"),
                Panels.divider(),
                Panels.text(overview),
                Panels.text("-# Placeholders: `{user}` `{mention}` `{server}` `{count}`. Requer o intent **GUILD_MEMBERS**."),
                Panels.divider(),
                ActionRow.of(channelSelect("welcomechan", WelcomeConfig.KEY_CHANNEL, "Canal de boas-vindas…",
                        cfg.channel(WelcomeConfig.KEY_CHANNEL))),
                ActionRow.of(roleSelect("welcomerole", WelcomeConfig.KEY_AUTOROLE, "Cargo automático (autorole)…", autorole)),
                ActionRow.of(channelSelect("welcomechan", WelcomeConfig.KEY_FAREWELL_CHANNEL, "Canal de despedida…",
                        cfg.channel(WelcomeConfig.KEY_FAREWELL_CHANNEL))),
                ActionRow.of(
                        Button.secondary(ComponentId.of(NS, "welctoggle", "enabled"), "Boas-vindas: " + (on ? "on" : "off")).withEmoji(Emojis.button(Emojis.MEMBER)),
                        Button.secondary(ComponentId.of(NS, "welctoggle", "dm"), "DM: " + (dm ? "on" : "off")).withEmoji(Emojis.button(Emojis.MEMBER)),
                        Button.secondary(ComponentId.of(NS, "welctoggle", "farewell"), "Despedida: " + (fw ? "on" : "off")).withEmoji(Emojis.button(Emojis.MEMBER))),
                ActionRow.of(
                        Button.primary(ComponentId.of(NS, "welcedit"), "Editar mensagens").withEmoji(Emojis.button(Emojis.EDIT)),
                        Button.secondary(ComponentId.of(NS, "nav", "hub"), "◀ Voltar")),
                moduleNav("boasvindas"));
    }

    public static Modal welcomeModal(GuildConfig cfg) {
        TextInput.Builder msg = TextInput.create("message", TextInputStyle.PARAGRAPH)
                .setPlaceholder("Mensagem de boas-vindas (use os placeholders)").setRequired(false).setMaxLength(1000);
        msg.setValue(WelcomeConfig.message(cfg));
        TextInput.Builder fw = TextInput.create("farewell", TextInputStyle.PARAGRAPH)
                .setPlaceholder("Mensagem de despedida").setRequired(false).setMaxLength(1000);
        fw.setValue(WelcomeConfig.farewellMessage(cfg));
        TextInput.Builder img = TextInput.create("image", TextInputStyle.SHORT)
                .setPlaceholder("URL da imagem/banner (PNG/JPG/GIF/WEBP) — vazio remove").setRequired(false).setMaxLength(500);
        return Modal.create(ComponentId.of(NS, "welcomeform"), "Boas-vindas — mensagens")
                .addComponents(Label.of("Boas-vindas", msg.build()), Label.of("Despedida", fw.build()),
                        Label.of("Imagem (URL)", img.build()))
                .build();
    }
```
> Import a adicionar no `SetupView`: `import dev.davimf.basebot.modules.base.welcome.WelcomeConfig;`. `Container/ActionRow/Button/Modal/Label/TextInput/TextInputStyle/ComponentId/Emojis/Panels/EmbedColor` já estão importados.
- [ ] **Step 2:** `SetupView.moduleNav` — adicionar as duas entradas (logo após `addNav(menu, current, "Segurança", "seguranca");`):
```java
        addNav(menu, current, "Boas-vindas", "boasvindas");
        addNav(menu, current, "Auto-cargos", "autocargos");
```
E no `hub()` (mesma lista de seções do select), adicionar as duas entradas equivalentes (procurar onde "Segurança"/"seguranca" é adicionada no hub e replicar o par). *(Se o hub reusa `moduleNav`, este passo já está coberto — confirmar e não duplicar.)*
- [ ] **Step 3:** `SetupComponentHandler.onButton` — rotear a navegação e os botões. No switch de nav (onde tem `case "seguranca" -> ... securityScreen`), adicionar:
```java
            case "boasvindas" -> edit(event, SetupView.welcomeScreen(config(ctx, guildId)));
            case "autocargos" -> edit(event, selfRolesScreen(ctx, guildId)); // Task 9
```
E nos `case` de botão (junto de `secedit`/`raidedit`):
```java
            case "welcedit" -> event.replyModal(SetupView.welcomeModal(config(ctx, guildId))).queue();
            case "welctoggle" -> {
                GuildConfig cfg = config(ctx, guildId);
                String key = switch (id.arg(0)) {
                    case "dm" -> WelcomeConfig.KEY_DM;
                    case "farewell" -> WelcomeConfig.KEY_FAREWELL_ENABLED;
                    default -> WelcomeConfig.KEY_ENABLED;
                };
                GuildConfig updated = GuildConfigEdits.withToggle(cfg, key, !cfg.toggle(key, false));
                ctx.database().guildConfig().save(updated);
                edit(event, SetupView.welcomeScreen(updated));
            }
```
- [ ] **Step 4:** `SetupComponentHandler.onStringSelect`/`onEntitySelect` — os selects de canal/cargo. No `onEntitySelect` switch, adicionar:
```java
            case "welcomechan" -> { saveChannel(event, ctx, id.arg(0), firstChannelId(event)); edit(event, SetupView.welcomeScreen(config(ctx, event.getGuild().getId()))); }
            case "welcomerole" -> { saveRole(event, ctx, id.arg(0), firstRoleId(event)); edit(event, SetupView.welcomeScreen(config(ctx, event.getGuild().getId()))); }
```
> `saveChannel(event, ctx, key, channelId)`, `saveRole(...)`, `firstChannelId`, `firstRoleId` já existem no handler (usados por `setlogchannel`/`setrole`).
- [ ] **Step 5:** `SetupComponentHandler.onModal` — após o bloco de outro form, adicionar o roteamento + o save com download da imagem:
```java
        if ("welcomeform".equals(id.action())) {
            saveWelcome(event, ctx);
            return;
        }
```
e o helper:
```java
    private void saveWelcome(ModalInteractionEvent event, BotContext ctx) {
        String guildId = event.getGuild().getId();
        GuildConfig u = config(ctx, guildId);
        u = GuildConfigEdits.withSetting(u, WelcomeConfig.KEY_MESSAGE, nullToEmpty(value(event, "message")));
        u = GuildConfigEdits.withSetting(u, WelcomeConfig.KEY_FAREWELL_MESSAGE, nullToEmpty(value(event, "farewell")));
        String imageUrl = value(event, "image");
        if (imageUrl == null || imageUrl.isBlank()) {
            // vazio = remover imagem
            u = GuildConfigEdits.withSetting(u, WelcomeConfig.KEY_IMAGE, "");
            ctx.database().guildConfig().save(u);
            edit(event, SetupView.welcomeScreen(u));
            return;
        }
        ctx.database().guildConfig().save(u);
        event.deferEdit().queue();
        final String url = imageUrl.trim();
        ctx.scheduler().executor().execute(() -> {
            try {
                dev.davimf.basebot.util.ImageMedia.Image img = dev.davimf.basebot.util.ImageMedia.fromUrl(url);
                net.dv8tion.jda.api.utils.FileUpload file =
                        net.dv8tion.jda.api.utils.FileUpload.fromData(img.bytes(), img.fileName());
                AttachmentVault vault = new AttachmentVault(ctx, ctx.config().discord().vaultGuildId());
                vault.store(guildId, "welcome-banner " + guildId, file, (vChan, vMsg) -> {
                    GuildConfig saved = GuildConfigEdits.withSetting(config(ctx, guildId),
                            WelcomeConfig.KEY_IMAGE, vChan + ":" + vMsg);
                    ctx.database().guildConfig().save(saved);
                    img.erase();
                    event.getHook().editOriginalComponents(SetupView.welcomeScreen(saved)).useComponentsV2().queue();
                });
            } catch (Exception e) {
                event.getHook().editOriginalComponents(SetupView.welcomeScreen(config(ctx, guildId)))
                        .useComponentsV2().queue();
            }
        });
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s.trim(); }
```
> Imports a adicionar no handler: `dev.davimf.basebot.modules.base.welcome.WelcomeConfig`, `dev.davimf.basebot.modules.base.listeners.AttachmentVault`. `value(event,key)`, `config`, `edit`, `GuildConfigEdits` já existem.
- [ ] **Step 6: Run** `./gradlew build` → BUILD SUCCESSFUL. (A `selfRolesScreen` referenciada no Step 3 vem na Task 9 — se rodar o build antes da Task 9, comentar temporariamente esse `case`.)

---

## Sub-sistema B — Self-roles

### Task 6: Migração `025` + `SelfRolePanelRepository` + teste

**Files:** Create `src/main/resources/db/sqlite/025_self_roles.sql`, `modules/base/selfroles/SelfRolePanel.java`, `modules/base/selfroles/SelfRolePanelRepository.java`, `test/.../selfroles/SelfRolePanelRepositoryTest.java`. Modify `database/sqlite/SqliteMigrator.java`.

**Interfaces — Produces:** records `SelfRolePanel`/`SelfRolePanel.Option`; repo `createPanel/find/list/updatePanel/setOptions/setPublished/delete` usados em Tasks 7–9.

- [ ] **Step 1: Migração** `025_self_roles.sql` (sem comentário inline após `;` — gotcha do migrator):
```sql
CREATE TABLE IF NOT EXISTS self_role_panels (
    id            TEXT PRIMARY KEY,
    guild_id      TEXT NOT NULL,
    title         TEXT NOT NULL,
    description   TEXT,
    style         TEXT NOT NULL DEFAULT 'buttons',
    unique_choice INTEGER NOT NULL DEFAULT 0,
    channel_id    TEXT,
    message_id    TEXT,
    created_at    TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS self_role_options (
    panel_id TEXT NOT NULL,
    role_id  TEXT NOT NULL,
    label    TEXT NOT NULL,
    emoji    TEXT,
    position INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (panel_id, role_id)
);
```
- [ ] **Step 2:** Anexar à lista `MIGRATIONS` em `SqliteMigrator.java` (após `"/db/sqlite/024_message_archive_vault.sql"`, com vírgula antes):
```java
            "/db/sqlite/024_message_archive_vault.sql",
            "/db/sqlite/025_self_roles.sql"
```
- [ ] **Step 3: Record** `SelfRolePanel.java`:
```java
package dev.davimf.basebot.modules.base.selfroles;

import java.util.List;

/** Um painel de auto-atribuição e suas opções (cargo + rótulo + emoji). */
public record SelfRolePanel(String id, String guildId, String title, String description,
                            String style, boolean unique, String channelId, String messageId,
                            List<Option> options) {

    public static final String STYLE_BUTTONS = "buttons";
    public static final String STYLE_MENU = "menu";

    public record Option(String roleId, String label, String emoji, int position) {}

    public List<String> roleIds() {
        return options.stream().map(Option::roleId).toList();
    }
}
```
- [ ] **Step 4: Teste** (mirror de `MessageArchiveRepositoryTest` — SQLite in-memory via `SqliteManager`):
```java
package dev.davimf.basebot.modules.base.selfroles;

import dev.davimf.basebot.database.sqlite.SqliteManager;
import dev.davimf.basebot.database.sqlite.SqliteMigrator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SelfRolePanelRepositoryTest {

    private SqliteManager sqlite;
    private SelfRolePanelRepository repo;

    @BeforeEach
    void setUp() {
        sqlite = new SqliteManager("jdbc:sqlite::memory:");
        new SqliteMigrator(sqlite).migrate();
        repo = new SelfRolePanelRepository(sqlite);
    }

    @AfterEach
    void tearDown() {
        sqlite.close();
    }

    @Test
    void createFindUpdateOptionsPublishDelete() {
        String id = repo.createPanel("g1", "Cores", "Escolha", SelfRolePanel.STYLE_MENU, true);
        SelfRolePanel p = repo.find(id).orElseThrow();
        assertEquals("Cores", p.title());
        assertTrue(p.unique());
        assertEquals(SelfRolePanel.STYLE_MENU, p.style());
        assertTrue(p.options().isEmpty());

        repo.setOptions(id, List.of(
                new SelfRolePanel.Option("100", "Azul", null, 0),
                new SelfRolePanel.Option("200", "Verde", "🟢", 1)));
        assertEquals(List.of("100", "200"), repo.find(id).orElseThrow().roleIds());

        repo.setPublished(id, "555", "999");
        SelfRolePanel pub = repo.find(id).orElseThrow();
        assertEquals("555", pub.channelId());
        assertEquals("999", pub.messageId());

        assertEquals(1, repo.list("g1").size());
        repo.delete(id);
        assertEquals(Optional.empty(), repo.find(id));
    }
}
```
> Confirmar o construtor de `SqliteManager` no projeto (ver `MessageArchiveRepositoryTest`); ajustar a chamada se a assinatura diferir (ex.: `SqliteManager.inMemory()`).
- [ ] **Step 5: Run** `./gradlew test --tests "*.SelfRolePanelRepositoryTest"` → FAIL (repo não existe).
- [ ] **Step 6: Implementar** `SelfRolePanelRepository.java`:
```java
package dev.davimf.basebot.modules.base.selfroles;

import dev.davimf.basebot.database.postgres.RepositoryException;
import dev.davimf.basebot.database.sqlite.SqliteManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** SQLite store para painéis de self-role (migração 025). */
public final class SelfRolePanelRepository {

    private final SqliteManager sqlite;

    public SelfRolePanelRepository(SqliteManager sqlite) {
        this.sqlite = sqlite;
    }

    public String createPanel(String guildId, String title, String description, String style, boolean unique) {
        String id = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO self_role_panels (id, guild_id, title, description, style, unique_choice) VALUES (?,?,?,?,?,?)")) {
            ps.setString(1, id);
            ps.setString(2, guildId);
            ps.setString(3, title);
            ps.setString(4, description);
            ps.setString(5, style);
            ps.setInt(6, unique ? 1 : 0);
            ps.executeUpdate();
            return id;
        } catch (SQLException e) {
            throw new RepositoryException("create self-role panel for " + guildId, e);
        }
    }

    public void updatePanel(String id, String title, String description, String style, boolean unique) {
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE self_role_panels SET title=?, description=?, style=?, unique_choice=? WHERE id=?")) {
            ps.setString(1, title);
            ps.setString(2, description);
            ps.setString(3, style);
            ps.setInt(4, unique ? 1 : 0);
            ps.setString(5, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("update self-role panel " + id, e);
        }
    }

    public void setOptions(String panelId, List<SelfRolePanel.Option> options) {
        try (Connection c = sqlite.getConnection()) {
            boolean prevAuto = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                try (PreparedStatement del = c.prepareStatement("DELETE FROM self_role_options WHERE panel_id=?")) {
                    del.setString(1, panelId);
                    del.executeUpdate();
                }
                try (PreparedStatement ins = c.prepareStatement(
                        "INSERT INTO self_role_options (panel_id, role_id, label, emoji, position) VALUES (?,?,?,?,?)")) {
                    for (SelfRolePanel.Option o : options) {
                        ins.setString(1, panelId);
                        ins.setString(2, o.roleId());
                        ins.setString(3, o.label());
                        ins.setString(4, o.emoji());
                        ins.setInt(5, o.position());
                        ins.addBatch();
                    }
                    ins.executeBatch();
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(prevAuto);
            }
        } catch (SQLException e) {
            throw new RepositoryException("set options for panel " + panelId, e);
        }
    }

    public void setPublished(String panelId, String channelId, String messageId) {
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE self_role_panels SET channel_id=?, message_id=? WHERE id=?")) {
            ps.setString(1, channelId);
            ps.setString(2, messageId);
            ps.setString(3, panelId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("publish panel " + panelId, e);
        }
    }

    public void delete(String panelId) {
        try (Connection c = sqlite.getConnection()) {
            try (PreparedStatement o = c.prepareStatement("DELETE FROM self_role_options WHERE panel_id=?")) {
                o.setString(1, panelId);
                o.executeUpdate();
            }
            try (PreparedStatement p = c.prepareStatement("DELETE FROM self_role_panels WHERE id=?")) {
                p.setString(1, panelId);
                p.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RepositoryException("delete panel " + panelId, e);
        }
    }

    public Optional<SelfRolePanel> find(String id) {
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM self_role_panels WHERE id=?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(c, rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RepositoryException("find panel " + id, e);
        }
    }

    public List<SelfRolePanel> list(String guildId) {
        List<SelfRolePanel> out = new ArrayList<>();
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM self_role_panels WHERE guild_id=? ORDER BY created_at")) {
            ps.setString(1, guildId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(map(c, rs));
                }
            }
            return out;
        } catch (SQLException e) {
            throw new RepositoryException("list panels for " + guildId, e);
        }
    }

    private static SelfRolePanel map(Connection c, ResultSet rs) throws SQLException {
        String id = rs.getString("id");
        return new SelfRolePanel(id, rs.getString("guild_id"), rs.getString("title"),
                rs.getString("description"), rs.getString("style"), rs.getInt("unique_choice") == 1,
                rs.getString("channel_id"), rs.getString("message_id"), loadOptions(c, id));
    }

    private static List<SelfRolePanel.Option> loadOptions(Connection c, String panelId) throws SQLException {
        List<SelfRolePanel.Option> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT role_id, label, emoji, position FROM self_role_options WHERE panel_id=? ORDER BY position")) {
            ps.setString(1, panelId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new SelfRolePanel.Option(rs.getString("role_id"), rs.getString("label"),
                            rs.getString("emoji"), rs.getInt("position")));
                }
            }
        }
        return out;
    }
}
```
- [ ] **Step 7: Run** `./gradlew test --tests "*.SelfRolePanelRepositoryTest"` → PASS.

---

### Task 7: `SelfRoleView` (render do painel publicado)

**Files:** Create `modules/base/selfroles/SelfRoleView.java`.

**Interfaces — Produces:** `static final String NS = "selfrole"`; `static Container panel(int accent, SelfRolePanel p)` usado em Tasks 8–9.

- [ ] **Step 1: Implementar**
```java
package dev.davimf.basebot.modules.base.selfroles;

import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.core.component.Panels;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.selections.SelectOption;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.entities.emoji.Emoji;

import java.util.ArrayList;
import java.util.List;

/** Render do painel público de self-roles (botões ou menu). */
public final class SelfRoleView {

    public static final String NS = "selfrole";

    private SelfRoleView() {}

    public static Container panel(int accent, SelfRolePanel p) {
        List<ContainerChildComponent> kids = new ArrayList<>();
        kids.add(Panels.text("## " + p.title()));
        if (p.description() != null && !p.description().isBlank()) {
            kids.add(Panels.divider());
            kids.add(Panels.text(p.description()));
        }
        kids.add(Panels.divider());
        if (SelfRolePanel.STYLE_MENU.equals(p.style())) {
            kids.add(ActionRow.of(menu(p)));
        } else {
            kids.addAll(buttonRows(p));
        }
        return Panels.container(accent, kids.toArray(new ContainerChildComponent[0]));
    }

    private static StringSelectMenu menu(SelfRolePanel p) {
        StringSelectMenu.Builder b = StringSelectMenu.create(ComponentId.of(NS, "select", p.id()))
                .setPlaceholder("Escolha seus cargos…")
                .setMinValues(0)
                .setMaxValues(p.unique() ? 1 : Math.max(1, p.options().size()));
        for (SelfRolePanel.Option o : p.options()) {
            SelectOption opt = SelectOption.of(o.label(), o.roleId());
            if (o.emoji() != null && !o.emoji().isBlank()) {
                opt = opt.withEmoji(Emoji.fromFormatted(o.emoji()));
            }
            b.addOptions(opt);
        }
        return b.build();
    }

    private static List<ActionRow> buttonRows(SelfRolePanel p) {
        List<Button> buttons = new ArrayList<>();
        for (SelfRolePanel.Option o : p.options()) {
            Button btn = Button.secondary(ComponentId.of(NS, "toggle", p.id(), o.roleId()), o.label());
            if (o.emoji() != null && !o.emoji().isBlank()) {
                btn = btn.withEmoji(Emoji.fromFormatted(o.emoji()));
            }
            buttons.add(btn);
        }
        List<ActionRow> rows = new ArrayList<>();
        for (int i = 0; i < buttons.size(); i += 5) {
            rows.add(ActionRow.of(buttons.subList(i, Math.min(i + 5, buttons.size()))));
        }
        return rows;
    }
}
```
> **Verificar:** `Emoji.fromFormatted(...)` (mesmo usado em `Emojis.button`), `SelectOption.of(label, value)`. `ComponentId.of(NS,"toggle",panelId,roleId)` cabe no limite de 100 chars (ids de 12 + 2 snowflakes ≈ 50).
- [ ] **Step 2: Run** `./gradlew build` → BUILD SUCCESSFUL.

---

### Task 8: `SelfRoleComponentHandler` (toggle / select / exclusivo)

**Files:** Create `modules/base/selfroles/SelfRoleComponentHandler.java`.

**Interfaces — Consumes:** `SelfRolePanelRepository`, `SelfRoleView.NS`. **Produces:** registrado no `BaseModule` (Task 11).

- [ ] **Step 1: Implementar**
```java
package dev.davimf.basebot.modules.base.selfroles;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.component.ComponentHandler;
import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;

import java.util.List;
import java.util.Optional;

/** Runtime dos painéis de self-role (namespace "selfrole"): alterna cargos no clique. */
public final class SelfRoleComponentHandler implements ComponentHandler {

    @Override
    public String namespace() {
        return SelfRoleView.NS;
    }

    @Override
    public void onButton(ButtonInteractionEvent event, ComponentId id, BotContext ctx) {
        if (!"toggle".equals(id.action()) || event.getGuild() == null || event.getMember() == null) {
            return;
        }
        Guild guild = event.getGuild();
        Role role = guild.getRoleById(id.arg(1));
        if (role == null) {
            Replies.ephemeral(event, ctx, "Cargo não encontrado.");
            return;
        }
        if (!guild.getSelfMember().canInteract(role)) {
            Replies.ephemeral(event, ctx, "Não consigo gerenciar esse cargo (acima do meu).");
            return;
        }
        Member m = event.getMember();
        if (m.getRoles().contains(role)) {
            guild.removeRoleFromMember(m, role).reason("Self-role").queue(ok -> {}, err -> {});
            Replies.ephemeral(event, ctx, "Cargo " + role.getAsMention() + " removido.");
            return;
        }
        SelfRolePanel panel = repo(ctx).find(id.arg(0)).orElse(null);
        if (panel != null && panel.unique()) {
            for (String otherId : panel.roleIds()) {
                Role other = guild.getRoleById(otherId);
                if (other != null && !other.equals(role) && m.getRoles().contains(other)
                        && guild.getSelfMember().canInteract(other)) {
                    guild.removeRoleFromMember(m, other).reason("Self-role exclusivo").queue(ok -> {}, err -> {});
                }
            }
        }
        guild.addRoleToMember(m, role).reason("Self-role").queue(ok -> {}, err -> {});
        Replies.ephemeral(event, ctx, "Cargo " + role.getAsMention() + " adicionado.");
    }

    @Override
    public void onStringSelect(StringSelectInteractionEvent event, ComponentId id, BotContext ctx) {
        if (!"select".equals(id.action()) || event.getGuild() == null || event.getMember() == null) {
            return;
        }
        Optional<SelfRolePanel> found = repo(ctx).find(id.arg(0));
        if (found.isEmpty()) {
            Replies.ephemeral(event, ctx, "Painel não encontrado.");
            return;
        }
        Guild guild = event.getGuild();
        Member m = event.getMember();
        List<String> selected = event.getValues();
        for (String roleId : found.get().roleIds()) {
            Role role = guild.getRoleById(roleId);
            if (role == null || !guild.getSelfMember().canInteract(role)) {
                continue;
            }
            boolean want = selected.contains(roleId);
            boolean has = m.getRoles().contains(role);
            if (want && !has) {
                guild.addRoleToMember(m, role).reason("Self-role").queue(ok -> {}, err -> {});
            } else if (!want && has) {
                guild.removeRoleFromMember(m, role).reason("Self-role").queue(ok -> {}, err -> {});
            }
        }
        Replies.ephemeral(event, ctx, "Cargos atualizados.");
    }

    private static SelfRolePanelRepository repo(BotContext ctx) {
        return new SelfRolePanelRepository(ctx.database().sqlite());
    }
}
```
> **Verificar:** `ctx.database().sqlite()` (mesmo usado para construir os repos em `BaseModule`). `Replies.ephemeral(event, ctx, msg)`.
- [ ] **Step 2: Run** `./gradlew build` → BUILD SUCCESSFUL.

---

### Task 9: `/setup → Auto-cargos` (lista, editor, publicar)

**Files:** Modify `modules/base/setup/SetupComponentHandler.java` (telas vivem aqui, perto dos outros `*Screen` privados como `ticketsScreen`), `modules/base/setup/SetupView.java` (modal).

**Interfaces — Consumes:** `SelfRolePanelRepository`, `SelfRoleView.panel`. **Produces:** `selfRolesScreen(ctx, guildId)` (referenciado na Task 5 Step 3).

- [ ] **Step 1:** `SetupComponentHandler` — helpers de tela (privados; usam `SelfRolePanelRepository` construído de ctx):
```java
    private SelfRolePanelRepository selfRoles(BotContext ctx) {
        return new SelfRolePanelRepository(ctx.database().sqlite());
    }

    private Container selfRolesScreen(BotContext ctx, String guildId) {
        int accent = EmbedColor.resolve(config(ctx, guildId));
        List<SelfRolePanel> panels = selfRoles(ctx).list(guildId);
        StringBuilder sb = new StringBuilder("## " + Emojis.of(Emojis.MEMBERS, "🏷️") + " Auto-cargos\n");
        List<ActionRow> rows = new java.util.ArrayList<>();
        if (panels.isEmpty()) {
            sb.append("-# Nenhum painel ainda. Crie um para os membros se auto-atribuírem cargos.");
        } else {
            for (SelfRolePanel p : panels) {
                sb.append("\n• **").append(p.title()).append("** · `").append(p.style())
                        .append(p.unique() ? "/exclusivo" : "").append("` · ").append(p.options().size()).append(" cargo(s)");
                rows.add(ActionRow.of(
                        Button.primary(ComponentId.of(NS, "sredit", p.id()), "Editar: " + trim(p.title())).withEmoji(Emojis.button(Emojis.EDIT)),
                        Button.success(ComponentId.of(NS, "srpublish", p.id()), "Publicar").withEmoji(Emojis.button(Emojis.SEND)),
                        Button.danger(ComponentId.of(NS, "srdelete", p.id()), "Excluir")));
                if (rows.size() >= 4) {
                    break; // no máx. ~4 painéis editáveis por tela (limite de rows)
                }
            }
        }
        List<ContainerChildComponent> kids = new java.util.ArrayList<>();
        kids.add(Panels.text(sb.toString()));
        kids.add(Panels.divider());
        kids.addAll(rows);
        kids.add(ActionRow.of(
                Button.primary(ComponentId.of(NS, "srnew"), "Novo painel").withEmoji(Emojis.button(Emojis.EDIT)),
                Button.secondary(ComponentId.of(NS, "nav", "hub"), "◀ Voltar")));
        kids.add(SetupView.moduleNav("autocargos"));
        return Panels.container(accent, kids.toArray(new ContainerChildComponent[0]));
    }

    private static String trim(String s) { return s.length() > 20 ? s.substring(0, 20) : s; }

    private Container selfRoleEditor(BotContext ctx, String guildId, SelfRolePanel p) {
        int accent = EmbedColor.resolve(config(ctx, guildId));
        String body = "## " + Emojis.of(Emojis.MEMBERS, "🏷️") + " Editar painel\n---\n"
                + "**Título** · " + p.title() + "\n"
                + "**Estilo** · `" + p.style() + (p.unique() ? "/exclusivo" : "") + "`\n"
                + "**Cargos** · " + (p.options().isEmpty() ? "*nenhum*"
                    : p.roleIds().stream().map(r -> "<@&" + r + ">").collect(java.util.stream.Collectors.joining(" ")));
        EntitySelectMenu roles = EntitySelectMenu.create(ComponentId.of(NS, "srroles", p.id()), EntitySelectMenu.SelectTarget.ROLE)
                .setPlaceholder("Cargos do painel (substitui a lista)…").setRequiredRange(1, 25).build();
        return Panels.container(accent,
                Panels.text(body),
                Panels.divider(),
                ActionRow.of(roles),
                ActionRow.of(
                        Button.secondary(ComponentId.of(NS, "srdetails", p.id()), "Título/estilo").withEmoji(Emojis.button(Emojis.EDIT)),
                        Button.secondary(ComponentId.of(NS, "srstyle", p.id()), "Alternar estilo"),
                        Button.secondary(ComponentId.of(NS, "srunique", p.id()), "Exclusivo: " + (p.unique() ? "on" : "off"))),
                ActionRow.of(
                        Button.success(ComponentId.of(NS, "srpublish", p.id()), "Publicar").withEmoji(Emojis.button(Emojis.SEND)),
                        Button.secondary(ComponentId.of(NS, "nav", "autocargos"), "◀ Voltar")));
    }
```
> Imports a garantir no handler: `dev.davimf.basebot.modules.base.selfroles.{SelfRolePanel,SelfRolePanelRepository,SelfRoleView}`, `net.dv8tion.jda.api.components.container.{Container,ContainerChildComponent}`, `EntitySelectMenu` (já importado), `ActionRow`, `Button`. **v1: o rótulo de cada opção = nome do cargo; emoji vazio.** (Rótulo/emoji custom por opção = follow-up.)
- [ ] **Step 2:** `SetupView` — `selfRoleDetailsModal` (título/descrição):
```java
    public static Modal selfRoleDetailsModal(String panelId, String title, String description) {
        TextInput.Builder t = TextInput.create("title", TextInputStyle.SHORT)
                .setPlaceholder("Título do painel").setRequired(true).setMaxLength(80);
        if (title != null && !title.isBlank()) { t.setValue(title); }
        TextInput.Builder d = TextInput.create("description", TextInputStyle.PARAGRAPH)
                .setPlaceholder("Descrição (opcional)").setRequired(false).setMaxLength(400);
        if (description != null && !description.isBlank()) { d.setValue(description); }
        return Modal.create(ComponentId.of(NS, "srdetailsform", panelId), "Painel de cargos")
                .addComponents(Label.of("Título", t.build()), Label.of("Descrição", d.build()))
                .build();
    }
```
- [ ] **Step 3:** `SetupComponentHandler.onButton` — casos do editor (junto dos outros):
```java
            case "srnew" -> {
                String pid = selfRoles(ctx).createPanel(guildId, "Novo painel", null, SelfRolePanel.STYLE_BUTTONS, false);
                edit(event, selfRoleEditor(ctx, guildId, selfRoles(ctx).find(pid).orElseThrow()));
            }
            case "sredit" -> edit(event, selfRoleEditor(ctx, guildId, selfRoles(ctx).find(id.arg(0)).orElseThrow()));
            case "srdelete" -> { selfRoles(ctx).delete(id.arg(0)); edit(event, selfRolesScreen(ctx, guildId)); }
            case "srdetails" -> {
                SelfRolePanel p = selfRoles(ctx).find(id.arg(0)).orElseThrow();
                event.replyModal(SetupView.selfRoleDetailsModal(p.id(), p.title(), p.description())).queue();
            }
            case "srstyle" -> {
                SelfRolePanel p = selfRoles(ctx).find(id.arg(0)).orElseThrow();
                String next = SelfRolePanel.STYLE_MENU.equals(p.style()) ? SelfRolePanel.STYLE_BUTTONS : SelfRolePanel.STYLE_MENU;
                selfRoles(ctx).updatePanel(p.id(), p.title(), p.description(), next, p.unique());
                edit(event, selfRoleEditor(ctx, guildId, selfRoles(ctx).find(p.id()).orElseThrow()));
            }
            case "srunique" -> {
                SelfRolePanel p = selfRoles(ctx).find(id.arg(0)).orElseThrow();
                selfRoles(ctx).updatePanel(p.id(), p.title(), p.description(), p.style(), !p.unique());
                edit(event, selfRoleEditor(ctx, guildId, selfRoles(ctx).find(p.id()).orElseThrow()));
            }
            case "srpublish" -> publishSelfRole(event, ctx, guildId, id.arg(0));
```
e o helper de publicação (perto de `publishVerify`):
```java
    private void publishSelfRole(ButtonInteractionEvent event, BotContext ctx, String guildId, String panelId) {
        SelfRolePanel p = selfRoles(ctx).find(panelId).orElse(null);
        if (p == null || p.options().isEmpty()) {
            Replies.ephemeral(event, ctx, "Adicione ao menos um cargo antes de publicar.");
            return;
        }
        if (!(event.getChannel() instanceof net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel ch)) {
            Replies.ephemeral(event, ctx, "Use num canal de texto.");
            return;
        }
        int accent = EmbedColor.resolve(config(ctx, guildId));
        ch.sendMessageComponents(dev.davimf.basebot.modules.base.selfroles.SelfRoleView.panel(accent, p))
                .useComponentsV2()
                .queue(sent -> {
                    selfRoles(ctx).setPublished(panelId, ch.getId(), sent.getId());
                    Replies.ephemeral(event, ctx, "Painel publicado.");
                }, err -> Replies.ephemeral(event, ctx, "Falha ao publicar: " + err.getMessage()));
    }
```
- [ ] **Step 4:** `SetupComponentHandler.onEntitySelect` — adicionar o caso `srroles` (substitui os cargos do painel; label = nome do cargo):
```java
            case "srroles" -> {
                List<net.dv8tion.jda.api.entities.Role> picked = event.getMentions().getRoles();
                List<SelfRolePanel.Option> opts = new java.util.ArrayList<>();
                for (int i = 0; i < picked.size(); i++) {
                    opts.add(new SelfRolePanel.Option(picked.get(i).getId(), picked.get(i).getName(), null, i));
                }
                selfRoles(ctx).setOptions(id.arg(0), opts);
                edit(event, selfRoleEditor(ctx, event.getGuild().getId(), selfRoles(ctx).find(id.arg(0)).orElseThrow()));
            }
```
- [ ] **Step 5:** `SetupComponentHandler.onModal` — roteamento do `srdetailsform`:
```java
        if ("srdetailsform".equals(id.action())) {
            SelfRolePanel p = selfRoles(ctx).find(id.arg(0)).orElseThrow();
            String title = value(event, "title");
            selfRoles(ctx).updatePanel(p.id(), title == null || title.isBlank() ? "Painel" : title.trim(),
                    value(event, "description"), p.style(), p.unique());
            edit(event, selfRoleEditor(ctx, event.getGuild().getId(), selfRoles(ctx).find(p.id()).orElseThrow()));
            return;
        }
```
- [ ] **Step 6: Run** `./gradlew build` → BUILD SUCCESSFUL. (Agora a `selfRolesScreen` da Task 5 Step 3 resolve.)

---

### Task 10: Registrar listener + handler no `BaseModule`

**Files:** Modify `modules/base/BaseModule.java`.

- [ ] **Step 1:** No `register(...)`, junto dos outros `registry.*`:
```java
        // Boas-vindas / despedida / autorole (Base) — requer GUILD_MEMBERS.
        registry.listener(new dev.davimf.basebot.modules.base.welcome.WelcomeListener(ctx));
        // Self-roles (Base) — painéis de auto-atribuição (runtime).
        registry.component(new dev.davimf.basebot.modules.base.selfroles.SelfRoleComponentHandler());
```
- [ ] **Step 2: Run** `./gradlew build` → BUILD SUCCESSFUL.

---

### Task 11: Build + suíte completa + smoke manual

- [ ] **Step 1: Run** `./gradlew build` → BUILD SUCCESSFUL (todas as tasks de teste verdes, incluindo `WelcomeConfigTest`, `WelcomeTextTest`, `SelfRolePanelRepositoryTest`).
- [ ] **Step 2: Smoke (servidor de teste, com `GUILD_MEMBERS` ligado):**
  - `/setup → Boas-vindas`: ligar, escolher canal, autorole, editar mensagens (com uma URL de imagem) → entrar com um alt → recebe a mensagem (com banner) no canal e/ou DM + ganha o autorole (ou `Sem Set` se facs e autorole vazio). Sair → despedida no canal de despedida.
  - `/setup → Auto-cargos`: **Novo painel** → adicionar cargos (entity-select) → título/estilo/exclusivo → **Publicar** num canal → clicar botões/menu como membro comum → cargos alternam (exclusivo remove os outros).

## Self-Review
- **Cobertura do spec:** boas-vindas canal/DM + placeholders (Tasks 1,2,4); autorole c/ fallback sem-set (Task 4); despedida (Task 4); imagem→vault c/ rótulo (Tasks 3,5); self-roles botões/menu/exclusivo (Tasks 6–9); duas seções no setup (Tasks 5,9); migração 025 (Task 6); `base ≠ facs` (fallback por chave-string); gotchas DM/limites/memberCount (Tasks 4,7); testes puros (1,2,6). ✓
- **Consistência de tipos:** `WelcomeConfig.KEY_*`/leitores (T1) usados em T4/T5; `WelcomeText.render(template,Member,Guild)` (T2) em T4; `AttachmentVault.store(String,String,FileUpload,BiConsumer)` (T3) em T5; `SelfRolePanel`/`Option` + repo `createPanel/find/list/updatePanel/setOptions/setPublished/delete` (T6) em T7/T8/T9; `SelfRoleView.NS`/`panel(int,SelfRolePanel)` (T7) em T8/T9. ✓
- **Placeholders:** nenhum "TODO/TBD"; v1 simplifica rótulo de opção = nome do cargo (decisão explícita, não lacuna). ✓
- **Pontos a confirmar no build (anotados inline):** assinatura de `SqliteManager` in-memory no teste; `ctx.database().sqlite()`; `hub()` reusa ou não `moduleNav`; `Emoji.fromFormatted`.
