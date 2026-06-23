package dev.davimf.basebot.modules.sales.pix;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.component.ComponentHandler;
import dev.davimf.basebot.core.component.ComponentId;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;

/** Handles the Pix confirmation button, restricted to the Pix owner (BOTSPECS Module 3). */
public final class PixComponentHandler implements ComponentHandler {

    @Override
    public String namespace() {
        return "pix";
    }

    @Override
    public void onButton(ButtonInteractionEvent event, ComponentId id, BotContext ctx) {
        if (!"confirmar".equals(id.action())) {
            return;
        }
        String ownerId = id.arg(0);
        if (!PixOwnership.isOwner(event.getUser().getId(), ownerId)) {
            event.reply("Apenas o dono do Pix pode confirmar este pagamento.")
                    .setEphemeral(true).queue();
            return;
        }
        event.reply("Pagamento confirmado pelo dono do Pix.").queue();
    }
}
