package dev.davimf.basebot.modules.facs.actions;

import dev.davimf.basebot.util.Emojis;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.component.ComponentHandler;
import dev.davimf.basebot.core.component.ComponentId;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;

/** Routes the {@code /painel-acoes} action panel interactions (BOTSPECS Module 4). */
public final class ActionComponentHandler implements ComponentHandler {

    private final ActionService service;

    public ActionComponentHandler(ActionService service) {
        this.service = service;
    }

    @Override
    public String namespace() {
        return ActionView.NS;
    }

    @Override
    public void onButton(ButtonInteractionEvent event, ComponentId id, BotContext ctx) {
        String arg = id.arg(0);
        switch (id.action()) {
            case "register" -> service.register(event);
            case "manage" -> service.manage(event);
            case "regfut" -> service.registerFuture(event, arg);
            case "regpast" -> service.registerPast(event, arg);
            case "join" -> service.join(event, arg);
            case "leave" -> service.leave(event, arg);
            case "align" -> service.align(event, arg);
            case "config" -> service.config(event, arg);
            case "togglentry" -> service.toggleEntries(event, arg);
            case "changetime" -> service.changeTime(event, arg);
            case "victory" -> service.result(event, arg, "VICTORY", Emojis.of(Emojis.TROPHY, "🏆") + " Vitória");
            case "defeat" -> service.result(event, arg, "DEFEAT", Emojis.of(Emojis.SKULL, "💀") + " Derrota");
            case "close" -> service.result(event, arg, "CLOSED", Emojis.of(Emojis.LOCK, "🔒") + " Encerrada");
            default -> { /* not ours */ }
        }
    }

    @Override
    public void onStringSelect(StringSelectInteractionEvent event, ComponentId id, BotContext ctx) {
        switch (id.action()) {
            case "regtype" -> service.registerTypePicked(event);
            case "managepick" -> service.managePick(event);
            default -> { /* not ours */ }
        }
    }

    @Override
    public void onEntitySelect(EntitySelectInteractionEvent event, ComponentId id, BotContext ctx) {
        switch (id.action()) {
            case "backfill" -> service.backfill(event, id.arg(0));
            case "removemember" -> service.removeMember(event, id.arg(0));
            default -> { /* not ours */ }
        }
    }

    @Override
    public void onModal(ModalInteractionEvent event, ComponentId id, BotContext ctx) {
        String arg = id.arg(0);
        switch (id.action()) {
            case "createfut" -> service.createFuture(event, arg);
            case "createpast" -> service.createPast(event, arg);
            case "settime" -> service.setTime(event, arg);
            default -> { /* not ours */ }
        }
    }
}
