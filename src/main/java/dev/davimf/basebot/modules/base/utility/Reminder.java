package dev.davimf.basebot.modules.base.utility;

/** Um lembrete agendado (migração 033). */
public record Reminder(String id, String guildId, String userId, String channelId, String message, long remindAt) {}
