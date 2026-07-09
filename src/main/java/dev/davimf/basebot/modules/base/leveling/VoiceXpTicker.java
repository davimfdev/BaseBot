package dev.davimf.basebot.modules.base.leveling;

import dev.davimf.basebot.core.BotContext;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;

import java.util.ArrayList;
import java.util.List;

/** Credita XP por voz a cada 60s. Todas as escritas do ciclo vão numa única transação (VoiceXpBatch). */
public final class VoiceXpTicker {

    private static final long XP_PER_MIN = 10;

    private final BotContext ctx;
    private final LevelingService leveling;
    private final VoiceSessionRepository sessions;

    public VoiceXpTicker(BotContext ctx, LevelingService leveling) {
        this.ctx = ctx;
        this.leveling = leveling;
        this.sessions = new VoiceSessionRepository(ctx.database().sqlite());
    }

    public void tick() {
        if (ctx.jda() == null) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Guild guild : ctx.jda().getGuilds()) {
            if (!LevelingConfig.enabled(ctx.database().guildConfig().findOrEmpty(guild.getId()))) {
                continue;
            }
            List<VoiceSessionRepository.Open> open = sessions.openSessions(guild.getId());
            if (open.isEmpty()) {
                continue;
            }
            String afkId = guild.getAfkChannel() != null ? guild.getAfkChannel().getId() : null;
            List<VoiceXpBatch.Credit> credits = new ArrayList<>();
            for (VoiceSessionRepository.Open s : open) {
                Member member = guild.getMemberById(s.userId());
                AudioChannel channel = guild.getChannelById(AudioChannel.class, s.channelId());
                long delta = 0;
                if (member != null && channel != null) {
                    long humans = channel.getMembers().stream().filter(m -> !m.getUser().isBot()).count();
                    GuildVoiceState vs = member.getVoiceState();
                    boolean afk = afkId != null && afkId.equals(channel.getId());
                    VoiceStateSnapshot snap = new VoiceStateSnapshot(
                            member.getUser().isBot(), humans,
                            vs != null && vs.isSelfMuted(),
                            vs != null && vs.isSelfDeafened(),
                            vs != null && vs.isGuildDeafened(),
                            afk,
                            true); // escopo entra na Task 7; irrelevante para XP
                    if (VoiceEligibility.xpEligible(snap)) {
                        delta = (Math.max(0, now - s.xpCreditedUntil()) * XP_PER_MIN) / 60_000L;
                    }
                }
                credits.add(new VoiceXpBatch.Credit(s.id(), guild.getId(), s.userId(), delta,
                        s.timeCreditedUntil(), s.timeCreditedUntil(), now));
            }
            List<VoiceXpBatch.Result> results = VoiceXpBatch.apply(ctx.database().sqlite(), credits);
            // Efeitos de level-up FORA da transação (chamadas ao Discord).
            for (VoiceXpBatch.Result r : results) {
                Member member = guild.getMemberById(r.userId());
                if (member != null) {
                    leveling.applyVoiceLevelUp(guild, member, r.oldXp(), r.newXp());
                }
            }
        }
    }
}
