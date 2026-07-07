package dev.davimf.basebot.modules.base.events;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/** Encaminha mensagens do canal de eventos para o serviço (atividade + respostas de chat). */
public final class ChatEventListener extends ListenerAdapter {

    private final ChatEventService service;

    public ChatEventListener(ChatEventService service) {
        this.service = service;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        service.onGuildMessage(event);
    }
}
