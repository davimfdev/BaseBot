// [OUTLINE START]
// Package: dev.davimf.basebot.modules.facs.punish
// 
// Class: PunishComponentHandler
// 
// Constructors:
//   - `Constructor` : `public PunishComponentHandler(PunishService service)`
// 
// Methods:
//   - `Method` : `public String namespace()`
// 
// Fields:
//   - `Field` : `private final PunishService service`
// [OUTLINE END]



package dev.davimf.basebot.modules.facs.punish;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.component.ComponentHandler;
import dev.davimf.basebot.core.component.ComponentId;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;

/** Routes the {@code /punições} revoke select (BOTSPECS Module 4). */
public final class PunishComponentHandler implements ComponentHandler {

    private final PunishService service;

    public PunishComponentHandler(PunishService service) {
        this.service = service;
    }

    @Override
    public String namespace() {
        return PunishService.NS;
    }

    @Override
    public void onStringSelect(StringSelectInteractionEvent event, ComponentId id, BotContext ctx) {
        if ("revoke".equals(id.action())) {
            service.revoke(event, id.arg(0));
        }
    }
}
