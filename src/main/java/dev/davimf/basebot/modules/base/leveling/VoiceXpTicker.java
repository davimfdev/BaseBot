package dev.davimf.basebot.modules.base.leveling;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.modules.base.vip.VipBonus;
import dev.davimf.basebot.modules.base.vip.VipBonusSource;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;

import java.util.ArrayList;
import java.util.List;

/**
 * A cada 60s, credita XP e tempo em call das sessões abertas. Todas as escritas do ciclo vão numa
 * única transação ({@link VoiceXpBatch}).
 *
 * <p>A contagem de <b>tempo</b> não depende de {@code level:enabled} — desligar o XP não deve
 * zerar o ranking de call. Só o XP é condicionado ao toggle.
 */
public final class VoiceXpTicker {

    private static final long XP_PER_MIN = 10;

    private final BotContext ctx;
    private final LevelingService leveling;
    private final VoiceSessionRepository sessions;
    private final VoiceGate gate;
    private final VipBonusSource vip;

    public VoiceXpTicker(BotContext ctx, LevelingService leveling, VoiceGate gate, VipBonusSource vip) {
        this.ctx = ctx;
        this.leveling = leveling;
        this.sessions = new VoiceSessionRepository(ctx.database().sqlite());
        this.gate = gate;
        this.vip = vip;
    }

    public void tick() {
        if (ctx.jda() == null) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Guild guild : ctx.jda().getGuilds()) {
            if (!gate.isReconciled(guild.getId())) {
                // O reconciler ainda não rodou para esta guild (ou falhou): creditar agora
                // lançaria o período offline inteiro de uma vez para todo mundo em call. As
                // outras guilds, já reconciliadas, continuam sendo processadas normalmente.
                continue;
            }
            List<VoiceSessionRepository.Open> open = sessions.openSessions(guild.getId());
            if (open.isEmpty()) {
                continue;
            }
            GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(guild.getId());
            boolean xpOn = LevelingConfig.enabled(cfg);

            List<VoiceXpBatch.Credit> credits = new ArrayList<>();
            for (VoiceSessionRepository.Open s : open) {
                Member member = guild.getMemberById(s.userId());
                AudioChannel channel = guild.getChannelById(AudioChannel.class, s.channelId());

                long xpDelta = 0;
                long timeTo = s.timeCreditedUntil(); // janela vazia = sem crédito
                if (member != null && channel != null) {
                    VoiceStateSnapshot snap = VoiceSnapshots.of(member, channel, cfg, 0);
                    if (xpOn && VoiceEligibility.xpEligible(snap)) {
                        xpDelta = (Math.max(0, now - s.xpCreditedUntil()) * XP_PER_MIN) / 60_000L;
                        xpDelta = VipBonus.scale(xpDelta, vip.bonusFor(guild.getId(), s.userId()).xpPct());
                    }
                    if (VoiceEligibility.timeEligible(snap)) {
                        timeTo = now;
                    }
                }
                credits.add(new VoiceXpBatch.Credit(s.id(), guild.getId(), s.userId(),
                        xpDelta, s.timeCreditedUntil(), timeTo, now));
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
