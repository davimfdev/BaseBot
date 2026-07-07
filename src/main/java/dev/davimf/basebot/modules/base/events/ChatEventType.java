package dev.davimf.basebot.modules.base.events;

import java.util.concurrent.ThreadLocalRandom;

/** Tipos de evento de chat. */
public enum ChatEventType {
    QUIZ, TYPING, MATH, GRAB;

    public static ChatEventType random() {
        ChatEventType[] all = values();
        return all[ThreadLocalRandom.current().nextInt(all.length)];
    }
}
