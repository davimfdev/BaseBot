package dev.davimf.basebot.modules.base.economy;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.modules.base.economy.EquipmentCatalog.Equip;
import dev.davimf.basebot.modules.base.economy.EquipmentCatalog.Slot;
import dev.davimf.basebot.modules.base.economy.InventoryRepository.Row;
import dev.davimf.basebot.modules.base.economy.InventoryRepository.UseResultType;
import dev.davimf.basebot.modules.base.economy.InventoryRepository.DestroyResultType;
import dev.davimf.basebot.util.Emojis;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/** Crime organizado: lobby in-memory (1 por guild) + resolução em lote por-participante. */
public final class OrgCrimeService {

    public static final class Lobby {
        final String leaderId;
        final Set<String> participants = new LinkedHashSet<>();
        boolean started;
        Lobby(String leaderId) { this.leaderId = leaderId; participants.add(leaderId); }
        public String leaderId() { return leaderId; }
        /** Cópia imutável em ordem estável (o Set interno não vaza pra view/handler). */
        public List<String> participants() { return List.copyOf(participants); }
        public boolean started() { return started; }
    }

    /** Snapshot da arma válida de um participante, capturado na revalidação (evita byKey repetido). */
    private record WeaponSnapshot(Row row, Equip equip) {}

    private final BotContext ctx;
    private final JailService jail;
    private final InventoryRepository inv;
    private final WalletRepository wallets;
    private final CooldownRepository cooldowns;
    private final Map<String, Lobby> lobbies = new ConcurrentHashMap<>();

    public OrgCrimeService(BotContext ctx, JailService jail) {
        this.ctx = ctx;
        this.jail = jail;
        this.inv = new InventoryRepository(ctx.database().sqlite());
        this.wallets = new WalletRepository(ctx.database().sqlite());
        this.cooldowns = new CooldownRepository(ctx.database().sqlite());
    }

    public Lobby lobby(String guildId) { return lobbies.get(guildId); }
    public synchronized void cancel(String guildId) { lobbies.remove(guildId); }

    private boolean dailyCooldownActive(String g, String u) {
        return System.currentTimeMillis() < cooldowns.lastTs(g, u, "orgcrime")
                + EconomyDefaults.ORG_DAILY_COOLDOWN_S * 1000L;
    }

    /** Erro de entrada comum a líder e participante (null = pode entrar). */
    private String entryError(String guildId, String userId) {
        if (!EconomyConfig.enabled(ctx.database().guildConfig().findOrEmpty(guildId))) {
            return "A economia está desligada.";
        }
        if (jail.resolve(guildId, userId).kind() == JailService.Kind.PRESO) {
            return "Você está preso.";
        }
        if (inv.equipped(guildId, userId, Slot.WEAPON) == null) {
            return "Você precisa de uma arma equipada.";
        }
        if (dailyCooldownActive(guildId, userId)) {
            return "Você já participou de um crime organizado hoje.";
        }
        return null;
    }

    /** Abre o lobby; o líder passa pelas MESMAS validações de entrada. Devolve erro (null = aberto). */
    public synchronized String open(String guildId, String leaderId) {
        if (lobbies.containsKey(guildId)) {
            return "Já existe um crime organizado sendo montado aqui.";
        }
        String err = entryError(guildId, leaderId);
        if (err != null) {
            return err;
        }
        lobbies.put(guildId, new Lobby(leaderId));
        return null;
    }

    /** Tenta entrar (null = entrou). */
    public synchronized String join(String guildId, String userId) {
        Lobby l = lobbies.get(guildId);
        if (l == null || l.started) {
            return "Não há lobby aberto.";
        }
        if (l.participants.contains(userId)) {
            return "Você já está no grupo.";
        }
        if (l.participants.size() >= EconomyDefaults.ORG_MAX) {
            return "O grupo está cheio.";
        }
        String err = entryError(guildId, userId);
        if (err != null) {
            return err;
        }
        l.participants.add(userId);
        return null;
    }

    private static boolean validUse(UseResultType t) {
        return t == UseResultType.USED || t == UseResultType.USED_AND_BROKE; // PERMANENT/NOT_* = inválido
    }

    /** Resolve o crime (só o líder deve acionar — checado no handler). Devolve a mensagem pública. */
    public synchronized String start(String guildId) {
        Lobby l = lobbies.get(guildId);
        if (l == null) {
            return "Não há lobby.";
        }
        if (l.started) {
            return "Esse crime já foi iniciado.";
        }
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(guildId);
        if (!EconomyConfig.enabled(cfg)) {           // defesa: nunca resolver com economia off
            return "A economia está desligada.";
        }
        l.started = true;
        long now = System.currentTimeMillis();

        // 1) Revalida + captura a arma equipada VÁLIDA (Row + Equip do catálogo) de cada participante.
        Map<String, WeaponSnapshot> weapons = new LinkedHashMap<>();
        for (String uid : l.participants) {
            if (jail.resolve(guildId, uid).kind() == JailService.Kind.PRESO) {
                continue;
            }
            if (dailyCooldownActive(guildId, uid)) {
                continue;
            }
            Row w = inv.equipped(guildId, uid, Slot.WEAPON);
            if (w == null) {
                continue;
            }
            Equip e = EquipmentCatalog.byKey(w.itemKey());
            if (e == null || e.slot() != Slot.WEAPON) { // dado antigo/inconsistente
                continue;
            }
            weapons.put(uid, new WeaponSnapshot(w, e));
        }
        if (weapons.size() < EconomyDefaults.ORG_MIN) {
            lobbies.remove(guildId);
            return "Crime cancelado — participantes válidos insuficientes (mín. " + EconomyDefaults.ORG_MIN + ").";
        }

        // 2) Chance a partir do snapshot.
        List<String> ids = new ArrayList<>(weapons.keySet());
        int sumBonus = 0, dirty = 0;
        for (String uid : ids) {
            sumBonus += weapons.get(uid).equip().chanceBonus();
            if (jail.fichaSuja(guildId, uid)) {
                dirty++;
            }
        }
        int chance = OrgCrime.chance(EconomyDefaults.ORG_BASE_CHANCE, sumBonus / ids.size(), dirty);
        boolean success = ThreadLocalRandom.current().nextInt(100) < chance;
        lobbies.remove(guildId);

        // 3) Muta por-participante; age só sobre quem teve a arma efetivamente mutada.
        if (success) {
            List<String> winners = new ArrayList<>();
            List<Double> weights = new ArrayList<>();
            for (String uid : ids) {
                if (validUse(inv.useOnce(guildId, uid, weapons.get(uid).row().id()).type())) {
                    winners.add(uid);
                    weights.add(weapons.get(uid).equip().mult());
                }
            }
            if (winners.isEmpty()) {
                return "Crime cancelado — ninguém tinha a arma na hora.";
            }
            long pote = EconomyDefaults.ORG_POT_PER_PLAYER * winners.size();
            double[] w = new double[weights.size()];
            for (int i = 0; i < w.length; i++) {
                w[i] = weights.get(i);
            }
            long[] cuts = OrgCrime.split(pote, w);
            StringBuilder sb = new StringBuilder(Emojis.of(Emojis.MONEY, "💰")
                    + " **Crime bem-sucedido!** Pote de " + EconomyFormat.format(pote, cfg) + " dividido:\n");
            for (int i = 0; i < winners.size(); i++) {
                wallets.addCash(guildId, winners.get(i), cuts[i]);
                cooldowns.stamp(guildId, winners.get(i), "orgcrime", now);
                sb.append("<@").append(winners.get(i)).append("> +")
                        .append(EconomyFormat.format(cuts[i], cfg)).append("\n");
            }
            return sb.toString();
        }
        // Falha: destrói e prende só quem tinha a arma.
        int jailed = 0;
        for (String uid : ids) {
            if (inv.destroy(guildId, uid, weapons.get(uid).row().id()).type() == DestroyResultType.DESTROYED) {
                long jailMs = ThreadLocalRandom.current().nextLong(
                        EconomyDefaults.ORG_JAIL_MIN_S, EconomyDefaults.ORG_JAIL_MAX_S + 1) * 1000L;
                jail.jailFor(guildId, uid, jailMs);
                cooldowns.stamp(guildId, uid, "orgcrime", now);
                jailed++;
            }
        }
        if (jailed == 0) {
            return "Crime cancelado — ninguém tinha a arma na hora.";
        }
        return Emojis.of(Emojis.KICK, "🚔") + " **A polícia chegou!** Os participantes perderam a arma e "
                + "foram presos. Paguem `/economia fianca` ou cumpram a pena.";
    }
}
