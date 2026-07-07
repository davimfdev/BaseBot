package dev.davimf.basebot.modules.base.events;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.modules.base.economy.EconomyConfig;
import dev.davimf.basebot.modules.base.economy.EconomyFormat;
import dev.davimf.basebot.modules.base.economy.EconomyService;
import dev.davimf.basebot.modules.base.leveling.LevelBonus;
import dev.davimf.basebot.modules.base.leveling.LevelFormula;
import dev.davimf.basebot.modules.base.leveling.LevelingConfig;
import dev.davimf.basebot.modules.base.leveling.LevelingService;
import dev.davimf.basebot.util.EmbedColor;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/** Coordenador stateful (in-memory) dos eventos de chat: dispara, resolve e recompensa. */
public final class ChatEventService {

    private static final long EVENT_COIN_BASE = 100;
    private static final long EVENT_XP = 50;
    private static final long TIMEOUT_S = 60;
    private static final long ACTIVITY_WINDOW_MS = 15 * 60_000L;
    private static final long RETRY_MS = 5 * 60_000L;

    private final BotContext ctx;
    private final LevelingService leveling;
    private final EconomyService economy;

    private final ConcurrentHashMap<String, ChatEvent> active = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> lastActivity = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> nextFire = new ConcurrentHashMap<>();

    public ChatEventService(BotContext ctx, LevelingService leveling, EconomyService economy) {
        this.ctx = ctx;
        this.leveling = leveling;
        this.economy = economy;
    }

    private GuildConfig cfg(Guild g) { return ctx.database().guildConfig().findOrEmpty(g.getId()); }

    // --- scheduler tick --------------------------------------------------------

    public void tick() {
        if (ctx.jda() == null) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Guild g : ctx.jda().getGuilds()) {
            GuildConfig cfg = cfg(g);
            if (!ChatEventConfig.enabled(cfg) || ChatEventConfig.channelId(cfg) == null) {
                continue;
            }
            String gid = g.getId();
            long next = nextFire.computeIfAbsent(gid, k -> now + randomIntervalMs(cfg));
            if (now < next || active.containsKey(gid)) {
                continue;
            }
            if (now - lastActivity.getOrDefault(gid, 0L) > ACTIVITY_WINDOW_MS) {
                nextFire.put(gid, now + RETRY_MS);
                continue;
            }
            fire(g, cfg);
            nextFire.put(gid, now + randomIntervalMs(cfg));
        }
    }

    private long randomIntervalMs(GuildConfig cfg) {
        long min = ChatEventConfig.minMinutes(cfg);
        long max = ChatEventConfig.maxMinutes(cfg);
        long minutes = max <= min ? min : min + ThreadLocalRandom.current().nextLong(max - min + 1);
        return minutes * 60_000L;
    }

    // --- fire ------------------------------------------------------------------

    private void fire(Guild g, GuildConfig cfg) {
        TextChannel channel = g.getTextChannelById(ChatEventConfig.channelId(cfg));
        if (channel == null) {
            return;
        }
        ChatEvent draft = build(g.getId(), channel.getId());
        int accent = EmbedColor.resolve(cfg);
        channel.sendMessageComponents(ChatEventView.panel(accent, draft)).useComponentsV2().queue(sent -> {
            ChatEvent ev = draft.withMessageId(sent.getId());
            active.put(g.getId(), ev);
            ctx.scheduler().once(() -> expire(g.getId(), ev), TIMEOUT_S, TimeUnit.SECONDS);
        }, err -> { });
    }

    private ChatEvent build(String guildId, String channelId) {
        long expires = System.currentTimeMillis() + TIMEOUT_S * 1000L;
        return switch (ChatEventType.random()) {
            case QUIZ -> {
                QuizBank.Question q = QuizBank.random();
                yield new ChatEvent(ChatEventType.QUIZ, guildId, channelId, "", q.text(), null, q.correct(),
                        q.options(), expires);
            }
            case TYPING -> {
                String w = WordBank.random();
                yield new ChatEvent(ChatEventType.TYPING, guildId, channelId, "", w, w, -1, List.of(), expires);
            }
            case MATH -> {
                MathEvent.Problem p = MathEvent.generate(new Random());
                yield new ChatEvent(ChatEventType.MATH, guildId, channelId, "", p.prompt(), p.answer(), -1,
                        List.of(), expires);
            }
            case GRAB -> new ChatEvent(ChatEventType.GRAB, guildId, channelId, "", null, "", -1, List.of(), expires);
        };
    }

    private void expire(String guildId, ChatEvent ev) {
        if (active.remove(guildId, ev)) {
            TextChannel ch = ctx.jda().getTextChannelById(ev.channelId());
            if (ch != null) {
                ch.editMessageComponentsById(ev.messageId(),
                                ChatEventView.expired(EmbedColor.resolve(
                                        ctx.database().guildConfig().findOrEmpty(guildId))))
                        .useComponentsV2().queue(ok -> { }, err -> { });
            }
        }
    }

    // --- resolution ------------------------------------------------------------

    /** Mensagens no canal do evento: marca atividade e resolve TYPING/MATH. */
    public void onGuildMessage(MessageReceivedEvent event) {
        if (!event.isFromGuild() || event.getAuthor().isBot() || event.getMember() == null) {
            return;
        }
        String gid = event.getGuild().getId();
        String chId = ChatEventConfig.channelId(cfg(event.getGuild()));
        if (chId == null || !chId.equals(event.getChannel().getId())) {
            return;
        }
        lastActivity.put(gid, System.currentTimeMillis());
        ChatEvent ev = active.get(gid);
        if (ev == null || (ev.type() != ChatEventType.TYPING && ev.type() != ChatEventType.MATH)) {
            return;
        }
        if (ChatEventAnswer.matches(event.getMessage().getContentRaw(), ev.answer()) && active.remove(gid, ev)) {
            // mantém o canal limpo: apaga a mensagem vencedora (best-effort)
            event.getMessage().delete().queue(s -> { }, e -> { });
            reward(event.getGuild(), event.getMember(), ev);
        }
    }

    /** Botões de QUIZ/GRAB. */
    public void resolveButton(ButtonInteractionEvent event, ComponentId id) {
        if (event.getGuild() == null || event.getMember() == null) {
            return;
        }
        String gid = event.getGuild().getId();
        ChatEvent ev = active.get(gid);
        if (ev == null) {
            Replies.ephemeral(event, ctx, "Esse evento já encerrou.");
            return;
        }
        if ("ans".equals(id.action())) {
            if (parse(id.arg(0)) != ev.correctIndex()) {
                Replies.ephemeral(event, ctx, "Resposta errada!");
                return;
            }
        } else if (!"grab".equals(id.action())) {
            return;
        }
        if (!active.remove(gid, ev)) {
            Replies.ephemeral(event, ctx, "Alguém já ganhou!");
            return;
        }
        reward(event.getGuild(), event.getMember(), ev);
        event.editComponents(ChatEventView.resolved(EmbedColor.resolve(cfg(event.getGuild())),
                        event.getMember().getAsMention(), rewardText(event.getGuild(), event.getMember())))
                .useComponentsV2().queue(ok -> { }, err -> { });
    }

    // --- reward ----------------------------------------------------------------

    private void reward(Guild g, Member m, ChatEvent ev) {
        GuildConfig cfg = cfg(g);
        if (EconomyConfig.enabled(cfg)) {
            int level = LevelFormula.levelForXp(leveling.users().xp(g.getId(), m.getId()));
            economy.wallets().addCash(g.getId(), m.getId(), LevelBonus.scale(EVENT_COIN_BASE, level));
        }
        TextChannel channel = g.getTextChannelById(ev.channelId());
        if (LevelingConfig.enabled(cfg) && channel != null) {
            leveling.award(g, m, EVENT_XP, channel);
        }
        if ((ev.type() == ChatEventType.TYPING || ev.type() == ChatEventType.MATH) && channel != null) {
            channel.editMessageComponentsById(ev.messageId(),
                            ChatEventView.resolved(EmbedColor.resolve(cfg), m.getAsMention(), rewardText(g, m)))
                    .useComponentsV2().queue(ok -> { }, err -> { });
        }
    }

    private String rewardText(Guild g, Member m) {
        GuildConfig cfg = cfg(g);
        StringBuilder sb = new StringBuilder();
        if (EconomyConfig.enabled(cfg)) {
            int level = LevelFormula.levelForXp(leveling.users().xp(g.getId(), m.getId()));
            sb.append("**+").append(EconomyFormat.formatNamed(LevelBonus.scale(EVENT_COIN_BASE, level), cfg)).append("**");
        }
        if (LevelingConfig.enabled(cfg)) {
            sb.append(sb.length() > 0 ? " e " : "").append("**+").append(EVENT_XP).append(" XP**");
        }
        return sb.length() == 0 ? "a recompensa" : sb.toString();
    }

    private static int parse(String s) {
        try { return Integer.parseInt(s); } catch (Exception e) { return -1; }
    }
}
