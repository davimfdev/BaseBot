package dev.davimf.basebot.modules.base.leveling;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.database.model.GuildConfig;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;

import java.util.List;

/**
 * Fecha a janela pendente de uma sessão de voz creditando-a com o estado que vigorou DURANTE a
 * janela, e avança as watermarks. É o que faz a contagem parar no instante exato do mute, em vez
 * de só no tick seguinte.
 */
public final class VoiceSettler {

    private static final long XP_PER_MIN = 10;

    private VoiceSettler() {}

    /** @param before estado de voz ANTERIOR ao evento que disparou o settle */
    public static void settle(BotContext ctx, LevelingService leveling, Guild guild, Member member,
                              VoiceStateSnapshot before, long now, VoiceGate gate) {
        if (!gate.isReconciled(guild.getId())) {
            // Boot ainda não reancorou as watermarks: creditar agora lançaria o período offline
            // inteiro no ranking. O caller ainda fecha/abre a sessão normalmente; só o crédito
            // é suprimido aqui.
            return;
        }
        VoiceSessionRepository sessions = new VoiceSessionRepository(ctx.database().sqlite());
        VoiceSessionRepository.Open s = sessions.openSession(guild.getId(), member.getId());
        if (s == null) {
            return;
        }
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(guild.getId());

        long xpDelta = 0;
        if (LevelingConfig.enabled(cfg) && VoiceEligibility.xpEligible(before)) {
            xpDelta = (Math.max(0, now - s.xpCreditedUntil()) * XP_PER_MIN) / 60_000L;
        }
        long timeTo = VoiceEligibility.timeEligible(before) ? now : s.timeCreditedUntil();

        List<VoiceXpBatch.Result> results = VoiceXpBatch.apply(ctx.database().sqlite(), List.of(
                new VoiceXpBatch.Credit(s.id(), guild.getId(), member.getId(),
                        xpDelta, s.timeCreditedUntil(), timeTo, now)));

        for (VoiceXpBatch.Result r : results) {
            leveling.applyVoiceLevelUp(guild, member, r.oldXp(), r.newXp());
        }
    }
}
