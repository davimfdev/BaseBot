package dev.davimf.basebot.modules.base.fun;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public final class ForcaListener extends ListenerAdapter {
    private final ForcaService service;

    public ForcaListener(ForcaService service) { this.service = service; }

    @Override public void onMessageReceived(MessageReceivedEvent event) {
        service.onMessage(event);
    }
}
