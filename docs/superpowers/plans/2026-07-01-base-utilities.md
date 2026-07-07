# Utilidades — Plano 1 (info + afk + enquete)

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans (inline). Steps em checkbox.

**Goal:** `/avatar` `/banner` `/userinfo` `/serverinfo` (exibição); `/afk` (aviso de ausência, in-memory); `/enquete` (votação com botões, in-memory). (Lembrete = Plano 2.)

**Architecture:** Pacote `modules/base/utility/`. Puros/testáveis (`PollTally`, `AfkRegistry`) + listener de AFK + serviço de enquete in-memory + views de info.

**Tech Stack:** Java 22, JDA 6.4.2 (Components V2).

## Global Constraints

- **JDK 22.** **Sem commits.** Base ≠ facs. Components V2 + `Emojis` + house style. Sem migração neste plano.
- Info públicos (permanentes); AFK/enquete resultados públicos; confirmações efêmeras/temporárias.

**Símbolos confirmados:** `Replies.ephemeral/reply`; `Panels.container/text/divider`; `ComponentId.of/arg`; `EmbedColor.resolve`; `Emojis`; `MediaGallery/MediaGalleryItem` (ver `PixDispatch`); `Button.link(url,label)`; `event.deferReply()`+`getHook().editOriginalComponents`; `event.getMessageId()`; teste sem DB (puros).

---

## Task 1: `AfkRegistry` (in-memory) + teste

**Files:** Create `modules/base/utility/AfkRegistry.java`, `test/.../utility/AfkRegistryTest.java`.

- [ ] **Step 1: Teste**
```java
package dev.davimf.basebot.modules.base.utility;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AfkRegistryTest {
    @Test
    void setGetRemove() {
        AfkRegistry r = new AfkRegistry();
        assertTrue(r.get("g", "u").isEmpty());
        r.set("g", "u", "almoço", 1000L);
        assertEquals("almoço", r.get("g", "u").orElseThrow().reason());
        assertTrue(r.remove("g", "u").isPresent());
        assertTrue(r.get("g", "u").isEmpty());
    }

    @Test
    void scopedByGuildAndUser() {
        AfkRegistry r = new AfkRegistry();
        r.set("g1", "u", "x", 1L);
        assertTrue(r.get("g2", "u").isEmpty());
        assertTrue(r.get("g1", "other").isEmpty());
    }
}
```
- [ ] **Step 2: Run** `./gradlew test --tests "*.AfkRegistryTest"` → FAIL.
- [ ] **Step 3: Implementar**
```java
package dev.davimf.basebot.modules.base.utility;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Registro de ausências (AFK) em memória, por guild+usuário. */
public final class AfkRegistry {

    public record Afk(String reason, long since) {}

    private final ConcurrentHashMap<String, Afk> afk = new ConcurrentHashMap<>();

    private static String key(String guildId, String userId) {
        return guildId + ":" + userId;
    }

    public void set(String guildId, String userId, String reason, long since) {
        afk.put(key(guildId, userId), new Afk(reason, since));
    }

    public Optional<Afk> get(String guildId, String userId) {
        return Optional.ofNullable(afk.get(key(guildId, userId)));
    }

    public Optional<Afk> remove(String guildId, String userId) {
        return Optional.ofNullable(afk.remove(key(guildId, userId)));
    }
}
```
- [ ] **Step 4: Run** `./gradlew test --tests "*.AfkRegistryTest"` → PASS.

---

## Task 2: `/afk` + `AfkListener`

**Files:** Create `modules/base/utility/AfkListener.java`, `modules/base/commands/AfkCommand.java`.

- [ ] **Step 1:** `AfkCommand.java`
```java
package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.modules.base.utility.AfkRegistry;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/** /afk [motivo] — marca você como ausente. */
public final class AfkCommand implements SlashCommand {
    private final AfkRegistry registry;
    public AfkCommand(AfkRegistry registry) { this.registry = registry; }

    @Override public String name() { return "afk"; }

    @Override public SlashCommandData data() {
        return Commands.slash("afk", "Marca você como ausente (AFK).")
                .addOptions(new OptionData(OptionType.STRING, "motivo", "Motivo (opcional)", false));
    }

    @Override public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null || event.getMember() == null) {
            Replies.ephemeral(event, ctx, "Use este comando em um servidor.");
            return;
        }
        OptionMapping m = event.getOption("motivo");
        String motivo = m == null || m.getAsString().isBlank() ? "Ausente" : m.getAsString().trim();
        registry.set(event.getGuild().getId(), event.getUser().getId(), motivo, System.currentTimeMillis());
        Replies.reply(event, ctx, "💤 Você está **AFK**: " + motivo);
    }
}
```
- [ ] **Step 2:** `AfkListener.java`
```java
package dev.davimf.basebot.modules.base.utility;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.util.EmbedColor;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.List;
import java.util.concurrent.TimeUnit;

/** Remove o AFK de quem volta a falar e avisa quando alguém AFK é mencionado. */
public final class AfkListener extends ListenerAdapter {

    private final BotContext ctx;
    private final AfkRegistry registry;

    public AfkListener(BotContext ctx, AfkRegistry registry) {
        this.ctx = ctx;
        this.registry = registry;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (!event.isFromGuild() || event.getAuthor().isBot()) {
            return;
        }
        String guildId = event.getGuild().getId();
        int accent = EmbedColor.resolve(ctx.database().guildConfig().findOrEmpty(guildId));

        // 1. autor voltou
        if (registry.remove(guildId, event.getAuthor().getId()).isPresent()) {
            event.getChannel().sendMessageComponents(Panels.container(accent,
                            Panels.text("👋 Bem-vindo de volta, " + event.getAuthor().getAsMention() + "! Removi seu AFK.")))
                    .useComponentsV2().setAllowedMentions(List.of())
                    .queue(msg -> msg.delete().queueAfter(10, TimeUnit.SECONDS, null, err -> { }), err -> { });
        }

        // 2. mencionou alguém AFK
        for (Member m : event.getMessage().getMentions().getMembers()) {
            registry.get(guildId, m.getId()).ifPresent(afk -> event.getChannel().sendMessageComponents(
                            Panels.container(accent, Panels.text(m.getAsMention() + " está **AFK**: " + afk.reason()
                                    + " (desde <t:" + (afk.since() / 1000) + ":R>)")))
                    .useComponentsV2().setAllowedMentions(List.of(Message.MentionType.USER))
                    .queue(msg -> msg.delete().queueAfter(15, TimeUnit.SECONDS, null, e -> { }), e -> { }));
        }
    }
}
```
> **Verificar:** `event.getMessage().getMentions().getMembers()`; `msg.delete().queueAfter(...)`.
- [ ] **Step 3: Run** `./gradlew build` → BUILD SUCCESSFUL.

---

## Task 3: `PollTally` (puro) + teste

**Files:** Create `modules/base/utility/PollTally.java`, `test/.../utility/PollTallyTest.java`.

- [ ] **Step 1: Teste**
```java
package dev.davimf.basebot.modules.base.utility;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class PollTallyTest {
    @Test
    void countsVotesPerOption() {
        int[] c = PollTally.counts(Map.of("a", 0, "b", 0, "c", 1), 3);
        assertArrayEquals(new int[]{2, 1, 0}, c);
    }

    @Test
    void ignoresOutOfRange() {
        int[] c = PollTally.counts(Map.of("a", 5), 3);
        assertArrayEquals(new int[]{0, 0, 0}, c);
    }

    @Test
    void emptyVotes() {
        assertArrayEquals(new int[]{0, 0}, PollTally.counts(Map.of(), 2));
    }
}
```
- [ ] **Step 2: Run** `./gradlew test --tests "*.PollTallyTest"` → FAIL.
- [ ] **Step 3: Implementar**
```java
package dev.davimf.basebot.modules.base.utility;

import java.util.Map;

/** Apuração pura de enquete: votos → contagens e barrinha. */
public final class PollTally {

    private PollTally() {}

    public static int[] counts(Map<String, Integer> votes, int options) {
        int[] c = new int[options];
        for (int v : votes.values()) {
            if (v >= 0 && v < options) {
                c[v]++;
            }
        }
        return c;
    }

    public static String bar(int count, int total) {
        int filled = total == 0 ? 0 : Math.round(count * 10f / total);
        return "`[" + "█".repeat(filled) + "░".repeat(10 - filled) + "]` " + count;
    }
}
```
- [ ] **Step 4: Run** `./gradlew test --tests "*.PollTallyTest"` → PASS.

---

## Task 4: `/enquete` — view + service + comando + handler

**Files:** Create `modules/base/utility/{EnqueteView,EnqueteService,EnqueteComponentHandler}.java`, `modules/base/commands/EnqueteCommand.java`.

**Produces:** namespace `enq`; enquete in-memory por messageId.

- [ ] **Step 1:** `EnqueteView.java`
```java
package dev.davimf.basebot.modules.base.utility;

import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.util.Emojis;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;

import java.util.ArrayList;
import java.util.List;

public final class EnqueteView {
    public static final String NS = "enq";

    private EnqueteView() {}

    public static Container panel(int accent, String question, List<String> options, int[] counts, boolean ended) {
        int total = 0;
        for (int c : counts) {
            total += c;
        }
        StringBuilder sb = new StringBuilder("## " + Emojis.of(Emojis.LIST, "📊") + " " + question + "\n");
        for (int i = 0; i < options.size(); i++) {
            sb.append("\n**").append(options.get(i)).append("**\n").append(PollTally.bar(counts[i], total));
        }
        sb.append("\n\n-# ").append(total).append(" voto(s)").append(ended ? " · **encerrada**" : "");
        List<ContainerChildComponent> kids = new ArrayList<>();
        kids.add(Panels.text(sb.toString()));
        if (!ended) {
            kids.add(Panels.divider());
            List<Button> voteButtons = new ArrayList<>();
            for (int i = 0; i < options.size(); i++) {
                voteButtons.add(Button.secondary(ComponentId.of(NS, "vote", String.valueOf(i)),
                        trim(options.get(i))));
            }
            for (int i = 0; i < voteButtons.size(); i += 5) {
                kids.add(ActionRow.of(voteButtons.subList(i, Math.min(i + 5, voteButtons.size()))));
            }
            kids.add(ActionRow.of(Button.danger(ComponentId.of(NS, "end"), "Encerrar")));
        }
        return Panels.container(accent, kids.toArray(new ContainerChildComponent[0]));
    }

    private static String trim(String s) {
        return s.length() > 78 ? s.substring(0, 78) : s;
    }
}
```
- [ ] **Step 2:** `EnqueteService.java`
```java
package dev.davimf.basebot.modules.base.utility;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.util.EmbedColor;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/** Enquetes in-memory (uma por mensagem). */
public final class EnqueteService {

    private static final class Poll {
        final String question;
        final List<String> options;
        final String creatorId;
        final ConcurrentHashMap<String, Integer> votes = new ConcurrentHashMap<>();
        Poll(String question, List<String> options, String creatorId) {
            this.question = question;
            this.options = options;
            this.creatorId = creatorId;
        }
    }

    private final BotContext ctx;
    private final ConcurrentHashMap<String, Poll> polls = new ConcurrentHashMap<>();

    public EnqueteService(BotContext ctx) { this.ctx = ctx; }

    public void create(SlashCommandInteractionEvent event, String question, List<String> options) {
        if (!(event.getChannel() instanceof TextChannel channel)) {
            Replies.ephemeral(event, ctx, "Use num canal de texto.");
            return;
        }
        int accent = accent(event.getGuild() == null ? "0" : event.getGuild().getId());
        Poll poll = new Poll(question, options, event.getUser().getId());
        int[] zero = new int[options.size()];
        event.replyComponents(EnqueteView.panel(accent, question, options, zero, false)).useComponentsV2()
                .queue(hook -> hook.retrieveOriginal().queue(msg -> polls.put(msg.getId(), poll), err -> { }));
    }

    public void vote(ButtonInteractionEvent event, int idx) {
        Poll poll = polls.get(event.getMessageId());
        if (poll == null) {
            Replies.ephemeral(event, ctx, "Essa enquete já encerrou.");
            return;
        }
        if (idx < 0 || idx >= poll.options.size()) {
            return;
        }
        poll.votes.put(event.getUser().getId(), idx);
        Replies.ephemeral(event, ctx, "Voto registrado em **" + poll.options.get(idx) + "**.");
        edit(event, poll, false);
    }

    public void end(ButtonInteractionEvent event) {
        Poll poll = polls.get(event.getMessageId());
        if (poll == null) {
            Replies.ephemeral(event, ctx, "Essa enquete já encerrou.");
            return;
        }
        if (!event.getUser().getId().equals(poll.creatorId)) {
            Replies.ephemeral(event, ctx, "Só quem criou a enquete pode encerrá-la.");
            return;
        }
        polls.remove(event.getMessageId());
        edit(event, poll, true);
    }

    private void edit(ButtonInteractionEvent event, Poll poll, boolean ended) {
        int accent = accent(event.getGuild() == null ? "0" : event.getGuild().getId());
        int[] counts = PollTally.counts(poll.votes, poll.options.size());
        event.getMessage().editMessageComponents().useComponentsV2()
                .setComponents(EnqueteView.panel(accent, poll.question, poll.options, counts, ended))
                .queue(ok -> { }, err -> { });
    }

    private int accent(String guildId) {
        return EmbedColor.resolve(ctx.database().guildConfig().findOrEmpty(guildId));
    }

    public static List<String> parseOptions(String raw) {
        List<String> out = new ArrayList<>();
        for (String s : raw.split("\\|")) {
            if (!s.trim().isEmpty()) {
                out.add(s.trim());
            }
        }
        return out;
    }
}
```
> **Verificar:** editar a mensagem do próprio botão — usar `event.editComponents(container).useComponentsV2().queue()` (mais simples que `getMessage().editMessageComponents()`). **Ajuste na implementação:** trocar o `edit(...)` por `event.editComponents(EnqueteView.panel(...)).useComponentsV2().queue()`. Mas o `vote` já respondeu efêmero com `Replies.ephemeral` — não dá pra responder duas vezes. **Correção:** no `vote`, em vez de `Replies.ephemeral` + editar, usar `event.editComponents(painel).useComponentsV2().queue()` (atualiza o painel público) e **não** mandar efêmero; ou `event.deferEdit()` + `getHook`. Escolha na implementação: `vote` → `event.editComponents(painelAtualizado).useComponentsV2().queue()` (sem efêmero); `end` idem. Assim um único ACK por clique.
- [ ] **Step 3:** `EnqueteCommand.java`
```java
package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.modules.base.utility.EnqueteService;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.List;

/** /enquete — votação com botões. */
public final class EnqueteCommand implements SlashCommand {
    private final EnqueteService service;
    public EnqueteCommand(EnqueteService service) { this.service = service; }

    @Override public String name() { return "enquete"; }

    @Override public SlashCommandData data() {
        return Commands.slash("enquete", "Cria uma votação com botões.")
                .addOptions(new OptionData(OptionType.STRING, "pergunta", "A pergunta", true))
                .addOptions(new OptionData(OptionType.STRING, "opcoes", "Opções separadas por | (2 a 6)", true));
    }

    @Override public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null) {
            Replies.ephemeral(event, ctx, "Use este comando em um servidor.");
            return;
        }
        String pergunta = event.getOption("pergunta", OptionMapping::getAsString);
        List<String> options = EnqueteService.parseOptions(event.getOption("opcoes", "", OptionMapping::getAsString));
        if (options.size() < 2 || options.size() > 6) {
            Replies.ephemeral(event, ctx, "Informe de 2 a 6 opções separadas por `|`.");
            return;
        }
        service.create(event, pergunta, options);
    }
}
```
> **Verificar:** overload `event.getOption("opcoes", "", OptionMapping::getAsString)`; se não existir, `OptionMapping o = event.getOption("opcoes"); String raw = o==null?"":o.getAsString();`.
- [ ] **Step 4:** `EnqueteComponentHandler.java`
```java
package dev.davimf.basebot.modules.base.utility;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.component.ComponentHandler;
import dev.davimf.basebot.core.component.ComponentId;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;

public final class EnqueteComponentHandler implements ComponentHandler {
    private final EnqueteService service;
    public EnqueteComponentHandler(EnqueteService service) { this.service = service; }

    @Override public String namespace() { return EnqueteView.NS; }

    @Override public void onButton(ButtonInteractionEvent event, ComponentId id, BotContext ctx) {
        switch (id.action()) {
            case "vote" -> {
                try {
                    service.vote(event, Integer.parseInt(id.arg(0)));
                } catch (NumberFormatException ignored) { }
            }
            case "end" -> service.end(event);
            default -> { }
        }
    }
}
```
- [ ] **Step 5: Run** `./gradlew build` → BUILD SUCCESSFUL.

---

## Task 5: Info — `InfoView` + `/avatar` `/userinfo` `/serverinfo` `/banner`

**Files:** Create `modules/base/utility/InfoView.java`, `modules/base/commands/{AvatarCommand,UserInfoCommand,ServerInfoCommand,BannerCommand}.java`.

- [ ] **Step 1:** `InfoView.java`
```java
package dev.davimf.basebot.modules.base.utility;

import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.util.Emojis;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.mediagallery.MediaGallery;
import net.dv8tion.jda.api.components.mediagallery.MediaGalleryItem;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;

public final class InfoView {
    private InfoView() {}

    public static Container image(int accent, String title, String url) {
        return Panels.container(accent, Panels.text(title),
                MediaGallery.of(MediaGalleryItem.fromUrl(url)));
    }

    public static Container userInfo(int accent, Member m) {
        long created = m.getUser().getTimeCreated().toEpochSecond();
        long joined = m.getTimeJoined().toEpochSecond();
        String roles = m.getRoles().isEmpty() ? "nenhum"
                : m.getRoles().stream().limit(10).map(r -> r.getAsMention()).reduce((a, b) -> a + " " + b).orElse("");
        String body = "## " + Emojis.of(Emojis.MEMBER, "👤") + " " + m.getEffectiveName() + "\n"
                + Emojis.of(Emojis.ID, "🆔") + " `" + m.getId() + "`\n"
                + Emojis.of(Emojis.CALENDAR, "📅") + " **Conta criada** · <t:" + created + ":F> • <t:" + created + ":R>\n"
                + Emojis.of(Emojis.JOIN, "📥") + " **Entrou** · <t:" + joined + ":F> • <t:" + joined + ":R>\n"
                + Emojis.of(Emojis.ROLES, "🏷️") + " **Cargos** (`" + m.getRoles().size() + "`) · " + roles;
        return Panels.container(accent, Panels.text(body));
    }

    public static Container serverInfo(int accent, Guild g) {
        long created = g.getTimeCreated().toEpochSecond();
        String body = "## " + Emojis.of(Emojis.SERVER, "🏠") + " " + g.getName() + "\n"
                + Emojis.of(Emojis.ID, "🆔") + " `" + g.getId() + "`\n"
                + Emojis.of(Emojis.MEMBER, "👑") + " **Dono** · <@" + g.getOwnerId() + ">\n"
                + Emojis.of(Emojis.CALENDAR, "📅") + " **Criado** · <t:" + created + ":F> • <t:" + created + ":R>\n"
                + Emojis.of(Emojis.MEMBERS, "👥") + " **Membros** · `" + g.getMemberCount() + "`\n"
                + Emojis.of(Emojis.CHANNEL, "#") + " **Canais** · `" + g.getTextChannels().size() + "` texto / `"
                + g.getVoiceChannels().size() + "` voz\n"
                + Emojis.of(Emojis.ROLES, "🏷️") + " **Cargos** · `" + g.getRoles().size() + "`\n"
                + Emojis.of(Emojis.BOOST, "🚀") + " **Boosts** · `" + g.getBoostCount() + "` (nível "
                + g.getBoostTier().getKey() + ")";
        return Panels.container(accent, Panels.text(body));
    }
}
```
> **Verificar constantes `Emojis`:** `ID`, `CALENDAR`, `JOIN`, `ROLES`, `SERVER`, `MEMBER`, `MEMBERS`, `CHANNEL`, `BOOST` (todos existem no manifest: id, calendar, join, roles, server, member, members, channel, boost). `g.getBoostTier().getKey()` (int). `g.getOwnerId()`.
- [ ] **Step 2:** `AvatarCommand.java`
```java
package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.modules.base.utility.InfoView;
import dev.davimf.basebot.util.EmbedColor;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/** /avatar [usuario] — mostra o avatar em tamanho grande. */
public final class AvatarCommand implements SlashCommand {
    @Override public String name() { return "avatar"; }

    @Override public SlashCommandData data() {
        return Commands.slash("avatar", "Mostra o avatar de alguém.")
                .addOptions(new OptionData(OptionType.USER, "usuario", "Membro (opcional)", false));
    }

    @Override public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        OptionMapping o = event.getOption("usuario");
        User u = o == null ? event.getUser() : o.getAsUser();
        String url = u.getEffectiveAvatarUrl() + "?size=1024";
        int accent = EmbedColor.resolve(ctx.database().guildConfig()
                .findOrEmpty(event.getGuild() == null ? "0" : event.getGuild().getId()));
        event.replyComponents(InfoView.image(accent, "## Avatar de " + u.getName(), url)).useComponentsV2()
                .addComponents(ActionRow.of(Button.link(url, "Baixar")))
                .queue();
    }
}
```
> **Verificar:** `event.replyComponents(container).addComponents(ActionRow...)` — se não empilhar, incluir o `ActionRow` como filho do container em `InfoView.image` (adicionar um overload que recebe o botão-link). Alternativa segura: pôr o botão-link **dentro** do container (`InfoView.image` recebe a url e monta text + MediaGallery + ActionRow(link)).
- [ ] **Step 3:** `UserInfoCommand.java` e `ServerInfoCommand.java` — molde do `SaldoCommand`:
```java
// UserInfoCommand: name "userinfo", option USER "usuario" (false). execute →
//   Member alvo = opt==null? event.getMember() : opt.getAsMember(); if null → erro;
//   event.replyComponents(InfoView.userInfo(accent, alvo)).useComponentsV2().queue();
// ServerInfoCommand: name "serverinfo", sem opções. execute →
//   event.replyComponents(InfoView.serverInfo(accent, event.getGuild())).useComponentsV2().queue();
```
Escrever os dois arquivos completos.
- [ ] **Step 4:** `BannerCommand.java` (async)
```java
package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.modules.base.utility.InfoView;
import dev.davimf.basebot.util.EmbedColor;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/** /banner [usuario] — mostra o banner do perfil (async). */
public final class BannerCommand implements SlashCommand {
    @Override public String name() { return "banner"; }

    @Override public SlashCommandData data() {
        return Commands.slash("banner", "Mostra o banner de alguém.")
                .addOptions(new OptionData(OptionType.USER, "usuario", "Membro (opcional)", false));
    }

    @Override public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        OptionMapping o = event.getOption("usuario");
        User u = o == null ? event.getUser() : o.getAsUser();
        int accent = EmbedColor.resolve(ctx.database().guildConfig()
                .findOrEmpty(event.getGuild() == null ? "0" : event.getGuild().getId()));
        event.deferReply().queue();
        u.retrieveProfile().queue(profile -> {
            String url = profile.getBannerUrl();
            if (url == null) {
                event.getHook().editOriginalComponents(Panels.container(accent,
                        Panels.text(u.getName() + " não tem banner."))).useComponentsV2().queue();
            } else {
                event.getHook().editOriginalComponents(InfoView.image(accent, "## Banner de " + u.getName(),
                        url + "?size=1024")).useComponentsV2().queue();
            }
        }, err -> event.getHook().editOriginalComponents(Panels.container(accent,
                Panels.text("Não consegui buscar o banner."))).useComponentsV2().queue());
    }
}
```
> **Verificar:** `User.retrieveProfile()` → `RestAction<User.Profile>`; `Profile.getBannerUrl()`.
- [ ] **Step 5: Run** `./gradlew build` → BUILD SUCCESSFUL.

---

## Task 6: Registro no `BaseModule` + build + smoke

**Files:** Modify `modules/base/BaseModule.java`.

- [ ] **Step 1:** No `register(...)`, após o Fun:
```java
        // Utilidades (Base).
        registry.command(new dev.davimf.basebot.modules.base.commands.AvatarCommand());
        registry.command(new dev.davimf.basebot.modules.base.commands.BannerCommand());
        registry.command(new dev.davimf.basebot.modules.base.commands.UserInfoCommand());
        registry.command(new dev.davimf.basebot.modules.base.commands.ServerInfoCommand());
        dev.davimf.basebot.modules.base.utility.AfkRegistry afk =
                new dev.davimf.basebot.modules.base.utility.AfkRegistry();
        registry.command(new dev.davimf.basebot.modules.base.commands.AfkCommand(afk));
        registry.listener(new dev.davimf.basebot.modules.base.utility.AfkListener(ctx, afk));
        dev.davimf.basebot.modules.base.utility.EnqueteService enquete =
                new dev.davimf.basebot.modules.base.utility.EnqueteService(ctx);
        registry.command(new dev.davimf.basebot.modules.base.commands.EnqueteCommand(enquete));
        registry.component(new dev.davimf.basebot.modules.base.utility.EnqueteComponentHandler(enquete));
```
- [ ] **Step 2: Run** `./gradlew build` → BUILD SUCCESSFUL (todos os testes: `AfkRegistryTest`, `PollTallyTest`).
- [ ] **Step 3: Smoke (servidor de teste):**
  - `/avatar` e `/avatar @alt` (imagem + botão baixar); `/banner @alt` (ou "sem banner"); `/userinfo @alt`; `/serverinfo`.
  - `/afk almoço` → mencionar você por outra conta → aviso; você falar → "bem-vindo de volta".
  - `/enquete pergunta:"Melhor mapa?" opcoes:"A | B | C"` → votar (troca de voto atualiza), Encerrar (só o criador).

## Self-Review
- **Cobertura do spec (Plano 1):** AfkRegistry (T1); /afk + listener (T2); PollTally (T3); enquete completa (T4); info avatar/userinfo/serverinfo/banner (T5); registro (T6). Lembrete = Plano 2. ✓
- **Consistência:** `AfkRegistry.set/get/remove` + `Afk`, `PollTally.counts/bar`, `EnqueteView.NS`, `EnqueteService.create/vote/end/parseOptions`, `InfoView.image/userInfo/serverInfo`. ✓
- **Ajuste anotado:** no `EnqueteService.vote/end`, usar `event.editComponents(...).useComponentsV2().queue()` (um único ACK por clique; sem `Replies.ephemeral` + edição juntos).
- **Pontos a confirmar no build (inline):** constantes `Emojis` de info; `Button.link`; `retrieveProfile`; overload de `getOption` com default; empilhar `ActionRow` no `replyComponents`.
