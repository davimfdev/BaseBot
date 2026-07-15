package dev.davimf.basebot.modules.base.economy;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.component.ComponentHandler;
import dev.davimf.basebot.core.component.ComponentId;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;

/** Runtime do botão de toggle de avisos no painel /economia (namespace "ecopanel"). */
public final class EconomiaPanelComponentHandler implements ComponentHandler {

    private final JailService jail;
    private final JobNotifyRepository notify;

    public EconomiaPanelComponentHandler(JailService jail, JobNotifyRepository notify) {
        this.jail = jail;
        this.notify = notify;
    }

    @Override public String namespace() { return "ecopanel"; }

    @Override
    public void onButton(ButtonInteractionEvent event, ComponentId id, BotContext ctx) {
        if (event.getGuild() == null || event.getMember() == null) {
            return;
        }
        if ("notify".equals(id.action())) {
            String g = event.getGuild().getId();
            String u = event.getMember().getId();
            notify.setEnabled(g, u, !notify.isEnabled(g, u)); // inverte
            event.editComponents(EconomiaPanelBuilder.build(ctx, event.getGuild(), event.getMember(), jail, notify))
                    .useComponentsV2().queue();
        }
    }
}
