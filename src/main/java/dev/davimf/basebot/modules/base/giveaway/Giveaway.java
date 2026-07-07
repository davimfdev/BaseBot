package dev.davimf.basebot.modules.base.giveaway;

/** Um sorteio persistido (migração 030). */
public record Giveaway(String id, String guildId, String channelId, String messageId, String prize,
                       long coinReward, int winners, long endsAt, boolean ended,
                       String reqRoleId, int reqMinDays, int reqMinVoiceHours,
                       int reqWindowStart, int reqWindowEnd) {

    public boolean hasWindow() { return reqWindowStart >= 0 && reqWindowEnd >= 0; }
}
