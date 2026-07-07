package dev.davimf.basebot.modules.base.fun;

/** Uma pergunta de quiz personalizada (migração 032). */
public record QuizQuestion(String id, String guildId, String question,
                           String correct, String wrong1, String wrong2, String wrong3) {}
