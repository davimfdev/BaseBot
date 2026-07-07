package dev.davimf.basebot.modules.base.events;

import java.util.List;

/** Evento ativo (in-memory). Um por guild. */
public record ChatEvent(ChatEventType type, String guildId, String channelId, String messageId,
                        String prompt, String answer, int correctIndex, List<String> options, long expiresAt) {

    public ChatEvent withMessageId(String id) {
        return new ChatEvent(type, guildId, channelId, id, prompt, answer, correctIndex, options, expiresAt);
    }
}
