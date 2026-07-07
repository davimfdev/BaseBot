package dev.davimf.basebot.modules.base.economy;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.component.ComponentHandler;
import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.util.EmbedColor;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;

/** Runtime do /mercado e /inventario (namespace "equip"). */
public final class EquipmentComponentHandler implements ComponentHandler {

    private final EquipmentService svc;

    public EquipmentComponentHandler(EquipmentService svc) { this.svc = svc; }

    @Override public String namespace() { return MercadoView.NS; } // "equip"

    @Override
    public void onStringSelect(StringSelectInteractionEvent event, ComponentId id, BotContext ctx) {
        if (event.getGuild() == null || event.getMember() == null) {
            return;
        }
        String g = event.getGuild().getId();
        String u = event.getMember().getId();
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(g);
        int accent = EmbedColor.resolve(cfg);
        switch (id.action()) {
            case "mslot" -> event.editComponents(MercadoView.slotList(accent,
                    EquipmentCatalog.Slot.valueOf(event.getValues().get(0)), cfg)).useComponentsV2().queue();
            case "buy" -> {
                String msg = svc.buy(g, u, event.getValues().get(0));
                event.editComponents(MercadoView.result(accent, msg)).useComponentsV2().queue();
            }
            case "equip" -> {
                String msg = svc.equip(g, u, parse(event.getValues().get(0)));
                event.editComponents(InventarioView.result(accent, msg)).useComponentsV2().queue();
            }
            default -> { }
        }
    }

    private static long parse(String s) { try { return Long.parseLong(s); } catch (Exception e) { return -1; } }
}
