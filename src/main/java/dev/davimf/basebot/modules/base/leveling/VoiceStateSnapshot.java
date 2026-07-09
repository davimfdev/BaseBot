package dev.davimf.basebot.modules.base.leveling;

/**
 * Estado de voz de um membro durante uma janela de tempo. Imutável e sem dependência da JDA,
 * para que a elegibilidade seja testável sem mocks.
 *
 * <p>As flags de deafen são separadas de propósito. Reconstruir o estado anterior a um evento
 * invertendo o agregado {@code deafened} está errado: se alguém já estava server-deafened e dá
 * self-deafen, o agregado é {@code true} antes e depois.
 *
 * <p>{@code guildMuted} não existe aqui: server-mute não afeta nenhum dos dois predicados.
 */
public record VoiceStateSnapshot(
        boolean bot,
        long humanCount,
        boolean selfMuted,
        boolean selfDeafened,
        boolean guildDeafened,
        boolean afkChannel,
        boolean inScope) {

    public boolean deafened() {
        return selfDeafened || guildDeafened;
    }

    public VoiceStateSnapshot withSelfMuted(boolean value) {
        return new VoiceStateSnapshot(bot, humanCount, value, selfDeafened, guildDeafened, afkChannel, inScope);
    }

    public VoiceStateSnapshot withSelfDeafened(boolean value) {
        return new VoiceStateSnapshot(bot, humanCount, selfMuted, value, guildDeafened, afkChannel, inScope);
    }

    public VoiceStateSnapshot withGuildDeafened(boolean value) {
        return new VoiceStateSnapshot(bot, humanCount, selfMuted, selfDeafened, value, afkChannel, inScope);
    }
}
