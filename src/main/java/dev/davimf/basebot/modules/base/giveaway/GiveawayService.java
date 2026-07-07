package dev.davimf.basebot.modules.base.giveaway;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.modules.base.economy.EconomyConfig;
import dev.davimf.basebot.modules.base.economy.EconomyService;
import dev.davimf.basebot.modules.base.leveling.VoiceSessionRepository;
import dev.davimf.basebot.util.EmbedColor;
import dev.davimf.basebot.util.Emojis;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/** Coordenador dos sorteios: criar, participar, sortear, resortear e sweep de encerramento. */
public final class GiveawayService {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    private final BotContext ctx;
    private final EconomyService economy;
    private final GiveawayRepository repo;
    private final VoiceSessionRepository voice;

    public GiveawayService(BotContext ctx, EconomyService economy) {
        this.ctx = ctx;
        this.economy = economy;
        this.repo = new GiveawayRepository(ctx.database().sqlite());
        this.voice = new VoiceSessionRepository(ctx.database().sqlite());
    }

    private GuildConfig cfg(Guild g) { return ctx.database().guildConfig().findOrEmpty(g.getId()); }

    public String create(Guild guild, TextChannel channel, String prize, long durationMs, int winners,
                         long coins, String roleId, int minDays, int minHours, int[] window) {
        long endsAt = System.currentTimeMillis() + durationMs;
        Giveaway draft = new Giveaway(null, guild.getId(), channel.getId(), null, prize, Math.max(0, coins),
                Math.max(1, winners), endsAt, false, roleId, Math.max(0, minDays), Math.max(0, minHours),
                window == null ? -1 : window[0], window == null ? -1 : window[1]);
        String id = repo.create(draft);
        Giveaway g = repo.find(id).orElseThrow();
        int accent = EmbedColor.resolve(cfg(guild));
        channel.sendMessageComponents(GiveawayView.panel(accent, g, 0)).useComponentsV2()
                .queue(sent -> repo.setMessageId(id, sent.getId()), err -> { });
        return id;
    }

    public void enter(ButtonInteractionEvent event, String giveawayId) {
        if (event.getGuild() == null || event.getMember() == null) {
            return;
        }
        Optional<Giveaway> maybe = repo.find(giveawayId);
        if (maybe.isEmpty() || maybe.get().ended()) {
            Replies.ephemeral(event, ctx, "Esse sorteio já encerrou.");
            return;
        }
        Giveaway g = maybe.get();
        Guild guild = event.getGuild();
        Member m = event.getMember();
        boolean hasRole = g.reqRoleId() == null || g.reqRoleId().isBlank()
                || m.getRoles().stream().anyMatch(r -> r.getId().equals(g.reqRoleId()));
        long joined = m.getTimeJoined().toInstant().toEpochMilli();
        long totalVoice = g.reqMinVoiceHours() > 0 ? voice.totalVoiceMs(guild.getId(), m.getId()) : 0;
        boolean windowOk = !g.hasWindow() || windowOk(guild.getId(), m.getId(), g);
        String reason = GiveawayRequirements.firstUnmet(g, hasRole, joined, totalVoice, windowOk,
                System.currentTimeMillis());
        if (reason != null) {
            Replies.ephemeral(event, ctx, reason);
            return;
        }
        boolean added = repo.addEntry(giveawayId, m.getId());
        Replies.ephemeral(event, ctx, added ? "Inscrito no sorteio! Boa sorte." : "Você já está participando.");
        if (added) {
            refreshPanel(guild, g);
        }
    }

    private boolean windowOk(String guildId, String userId, Giveaway g) {
        for (long[] s : voice.sessionsOf(guildId, userId)) {
            if (VoiceWindow.overlapsDailyWindow(s[0], s[1], g.reqWindowStart(), g.reqWindowEnd(), ZONE)) {
                return true;
            }
        }
        return false;
    }

    private void refreshPanel(Guild guild, Giveaway g) {
        if (g.messageId() == null) {
            return;
        }
        TextChannel ch = guild.getTextChannelById(g.channelId());
        if (ch != null) {
            ch.editMessageComponentsById(g.messageId(),
                            GiveawayView.panel(EmbedColor.resolve(cfg(guild)), g, repo.entryCount(g.id())))
                    .useComponentsV2().queue(ok -> { }, err -> { });
        }
    }

    public void sweep() {
        if (ctx.jda() == null) {
            return;
        }
        for (Giveaway g : repo.dueActive(System.currentTimeMillis())) {
            draw(g, false);
        }
    }

    public String endNow(String id) {
        Optional<Giveaway> g = repo.find(id);
        if (g.isEmpty() || g.get().ended()) {
            return "Sorteio não encontrado ou já encerrado.";
        }
        draw(g.get(), false);
        return "Sorteio encerrado.";
    }

    public String reroll(String id) {
        Optional<Giveaway> g = repo.find(id);
        if (g.isEmpty() || !g.get().ended()) {
            return "Sorteio não encontrado ou ainda em andamento.";
        }
        draw(g.get(), true);
        return "Novo sorteio realizado.";
    }

    private void draw(Giveaway g, boolean reroll) {
        Guild guild = ctx.jda().getGuildById(g.guildId());
        if (guild == null) {
            repo.setEnded(g.id());
            return;
        }
        List<String> valid = new ArrayList<>();
        for (String uid : repo.entries(g.id())) {
            if (guild.getMemberById(uid) != null) {
                valid.add(uid);
            }
        }
        List<String> winners = GiveawayDraw.pick(valid, g.winners(), new Random());
        if (g.coinReward() > 0 && EconomyConfig.enabled(cfg(guild))) {
            for (String w : winners) {
                economy.wallets().addCash(guild.getId(), w, g.coinReward());
            }
        }
        if (!reroll) {
            repo.setEnded(g.id());
        }
        announce(guild, g, winners, reroll);
    }

    private void announce(Guild guild, Giveaway g, List<String> winners, boolean reroll) {
        TextChannel ch = guild.getTextChannelById(g.channelId());
        if (ch == null) {
            return;
        }
        int accent = EmbedColor.resolve(cfg(guild));
        List<String> mentions = winners.stream().map(w -> "<@" + w + ">").toList();
        if (!reroll && g.messageId() != null) {
            ch.editMessageComponentsById(g.messageId(), GiveawayView.ended(accent, g, mentions))
                    .useComponentsV2().queue(ok -> { }, err -> { });
        }
        boolean coins = g.coinReward() > 0 && EconomyConfig.enabled(cfg(guild));
        String head = reroll ? Emojis.of(Emojis.RECYCLE, "🔁") + " Novo ganhador sorteado! Prêmio entregue." : "🎉 Sorteio encerrado!";
        String body = mentions.isEmpty()
                ? "Não houve participantes válidos."
                : "Parabéns " + String.join(", ", mentions) + "! Vocês ganharam **" + g.prize() + "**"
                + (coins ? " + **" + g.coinReward() + "** moedas" : "") + ".";
        ch.sendMessageComponents(Panels.container(accent, Panels.text("## " + head), Panels.divider(), Panels.text(body)))
                .useComponentsV2()
                .setAllowedMentions(List.of(Message.MentionType.USER))
                .queue(ok -> { }, err -> { });
    }
}
