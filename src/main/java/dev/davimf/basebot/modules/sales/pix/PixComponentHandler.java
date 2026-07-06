// [OUTLINE START]
// Package: dev.davimf.basebot.modules.sales.pix
// 
// Class: PixComponentHandler
// 
// Methods:
//   - `Method` : `public String namespace()`
// [OUTLINE END]



package dev.davimf.basebot.modules.sales.pix;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.component.ComponentHandler;
import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.util.Replies;
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
            Replies.ephemeral(event, ctx, "Apenas o dono do Pix pode confirmar este pagamento.");
            return;
        }
        Replies.reply(event, ctx, "Pagamento confirmado pelo dono do Pix.");
    }
}
