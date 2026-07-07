package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.modules.base.economy.ActionStatus;
import dev.davimf.basebot.modules.base.economy.CooldownRepository;
import dev.davimf.basebot.modules.base.economy.EconomyConfig;
import dev.davimf.basebot.modules.base.economy.EconomyCooldownKeys;
import dev.davimf.basebot.modules.base.economy.EconomyDefaults;
import dev.davimf.basebot.modules.base.economy.EconomiaPanelView;
import dev.davimf.basebot.modules.base.economy.EconomiaPanelView.ActionLine;
import dev.davimf.basebot.modules.base.economy.EquipmentCatalog;
import dev.davimf.basebot.modules.base.economy.EquipmentCatalog.Equip;
import dev.davimf.basebot.modules.base.economy.EquipmentCatalog.Slot;
import dev.davimf.basebot.modules.base.economy.InventoryRepository;
import dev.davimf.basebot.modules.base.economy.InventoryRepository.Row;
import dev.davimf.basebot.modules.base.economy.JailService;
import dev.davimf.basebot.modules.base.economy.WalletRepository;
import dev.davimf.basebot.util.EmbedColor;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** /economia — painel do que dá pra fazer + cooldowns + saldo + cadeia. */
public final class EconomiaCommand implements SlashCommand {

    private final JailService jail;

    public EconomiaCommand(JailService jail) { this.jail = jail; }

    @Override public String name() { return "economia"; }

    @Override public SlashCommandData data() {
        return Commands.slash("economia", "Mostra o que você pode fazer, seus cooldowns e saldo.");
    }

    @Override public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null || event.getMember() == null) {
            Replies.ephemeral(event, ctx, "Use este comando em um servidor.");
            return;
        }
        String g = event.getGuild().getId();
        String u = event.getMember().getId();
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(g);
        if (!EconomyConfig.enabled(cfg)) {
            Replies.ephemeral(event, ctx, "Economia desativada neste servidor.");
            return;
        }
        long now = System.currentTimeMillis();
        var wallets = new WalletRepository(ctx.database().sqlite());
        var cooldowns = new CooldownRepository(ctx.database().sqlite());
        var inv = new InventoryRepository(ctx.database().sqlite());

        WalletRepository.Wallet w = wallets.get(g, u);
        JailService.Status st = jail.resolve(g, u);            // pode aplicar release lazy + marcar ficha
        boolean preso = st.kind() == JailService.Kind.PRESO;
        boolean ficha = jail.fichaSuja(g, u);

        // Equipamento lido UMA vez por slot, reusado entre ações do mesmo slot.
        Map<Slot, Row> equipped = new EnumMap<>(Slot.class);
        for (Slot s : Slot.values()) {
            Row r = inv.equipped(g, u, s);
            if (r != null) {
                equipped.put(s, r);
            }
        }

        List<ActionLine> acoes = new ArrayList<>();
        acoes.add(line(g, u, now, cooldowns, preso, EconomyCooldownKeys.CD_DAILY, "daily",
                EconomyDefaults.DAILY_COOLDOWN_S, null, true, equipped, null));
        acoes.add(line(g, u, now, cooldowns, preso, EconomyCooldownKeys.CD_WORK, "trabalhar",
                EconomyConfig.workCooldownSeconds(cfg), null, false, equipped, null));
        acoes.add(line(g, u, now, cooldowns, preso, EconomyCooldownKeys.CD_MINE, "minerar",
                EconomyDefaults.MINE_COOLDOWN_S, Slot.MINING, false, equipped, "equipe uma picareta no `/mercado`"));
        acoes.add(line(g, u, now, cooldowns, preso, EconomyCooldownKeys.CD_COOK, "cozinhar",
                EconomyDefaults.COOK_COOLDOWN_S, Slot.COOKING, false, equipped, "equipe um utensílio no `/mercado`"));
        acoes.add(line(g, u, now, cooldowns, preso, EconomyCooldownKeys.CD_DELIVERY, "entregar",
                EconomyDefaults.DELIVERY_COOLDOWN_S, Slot.DELIVERY, false, equipped, "equipe uma moto no `/mercado`"));
        acoes.add(line(g, u, now, cooldowns, preso, EconomyCooldownKeys.CD_CRIME, "crime",
                EconomyDefaults.CRIME_COOLDOWN_S, Slot.WEAPON, false, equipped, "equipe uma arma no `/mercado`"));
        acoes.add(line(g, u, now, cooldowns, preso, EconomyCooldownKeys.CD_ROB, "roubar",
                EconomyDefaults.ROB_COOLDOWN_S, Slot.WEAPON, false, equipped, "equipe uma arma no `/mercado`"));
        acoes.add(line(g, u, now, cooldowns, preso, EconomyCooldownKeys.CD_ORG, "crimeorganizado",
                EconomyDefaults.ORG_DAILY_COOLDOWN_S, Slot.WEAPON, false, equipped, "equipe uma arma no `/mercado`"));

        event.replyComponents(EconomiaPanelView.panel(EmbedColor.resolve(cfg), event.getMember(), w, st, ficha, acoes, cfg))
                .useComponentsV2().setEphemeral(true).queue();
    }

    // preso já resolvido uma vez no execute() — passado aqui pra não repetir jail.resolve (nem leituras redundantes).
    private ActionLine line(String g, String u, long now, CooldownRepository cd, boolean preso, String key,
                            String command, long cooldownS, Slot slot, boolean exempt, Map<Slot, Row> equipped, String hint) {
        boolean unlocked = slot == null || equipped.containsKey(slot);
        ActionStatus status = ActionStatus.resolve(now, cd.lastTs(g, u, key), cooldownS, unlocked, preso, exempt);
        String eqLabel = null;
        if (slot != null && equipped.containsKey(slot)) {
            Row r = equipped.get(slot);
            Equip e = EquipmentCatalog.byKey(r.itemKey());
            eqLabel = (e == null ? "Item desconhecido" : e.name()) + " " + r.usosLeft() + "/"
                    + (e == null ? "?" : String.valueOf(e.maxUsos()));
        }
        return new ActionLine(command, status, hint, eqLabel);
    }
}
