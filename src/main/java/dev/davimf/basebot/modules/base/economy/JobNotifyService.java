package dev.davimf.basebot.modules.base.economy;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.modules.base.economy.EquipmentCatalog.Slot;
import dev.davimf.basebot.modules.base.economy.JobNotifyRepository.UserRef;
import dev.davimf.basebot.util.EmbedColor;
import dev.davimf.basebot.util.Emojis;
import net.dv8tion.jda.api.entities.Guild;

import java.util.List;

/** Varre usuários com aviso ligado e manda DM quando um trabalho fica disponível. À prova de restart. */
public final class JobNotifyService {

    /** Um trabalho notificável: cooldown (0 = daily via meia-noite), slot exigido (null = nenhum), isenção de cadeia. */
    private record Job(String action, String command, long cooldownS, Slot slot, boolean allowedWhileJailed) {}

    private static final List<Job> JOBS = List.of(
            new Job("daily", "economia daily", 0, null, true),
            new Job("work", "economia trabalhar", -1, null, false), // -1 = cooldown vem da config do guild
            new Job("minerar", "economia minerar", EconomyDefaults.MINE_COOLDOWN_S, Slot.MINING, false),
            new Job("cozinhar", "economia cozinhar", EconomyDefaults.COOK_COOLDOWN_S, Slot.COOKING, false),
            new Job("entregar", "economia entregar", EconomyDefaults.DELIVERY_COOLDOWN_S, Slot.DELIVERY, false),
            new Job("programar", "economia programar", EconomyDefaults.PROGRAM_COOLDOWN_S, Slot.TECH, false),
            new Job("plantar", "economia plantar", EconomyDefaults.PLANT_COOLDOWN_S, Slot.FARM, false),
            new Job("pescar", "economia pescar", EconomyDefaults.FISH_COOLDOWN_S, Slot.FISHING, false),
            new Job("explorar", "economia explorar", EconomyDefaults.EXPLORE_COOLDOWN_S, Slot.EXPEDITION, false),
            new Job("faturar", "economia faturar", EconomyDefaults.INVOICE_COOLDOWN_S, Slot.BUSINESS, false),
            new Job("crime", "economia crime", EconomyDefaults.CRIME_COOLDOWN_S, Slot.WEAPON, false),
            new Job("rob", "economia roubar", EconomyDefaults.ROB_COOLDOWN_S, Slot.WEAPON, false),
            new Job("orgcrime", "economia crimeorganizado", EconomyDefaults.ORG_DAILY_COOLDOWN_S, Slot.WEAPON, false));

    private final BotContext ctx;
    private final JailService jail;
    private final JobNotifyRepository prefs;
    private final CooldownRepository cooldowns;
    private final InventoryRepository inv;

    public JobNotifyService(BotContext ctx, JailService jail) {
        this.ctx = ctx;
        this.jail = jail;
        this.prefs = new JobNotifyRepository(ctx.database().sqlite());
        this.cooldowns = new CooldownRepository(ctx.database().sqlite());
        this.inv = new InventoryRepository(ctx.database().sqlite());
    }

    public JobNotifyRepository prefs() { return prefs; }

    public void sweep() {
        if (ctx.jda() == null) {
            return;
        }
        long now = System.currentTimeMillis();
        for (UserRef ref : prefs.enabledUsers()) {
            Guild guild = ctx.jda().getGuildById(ref.guildId());
            if (guild == null) {
                continue;
            }
            GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(ref.guildId());
            if (!EconomyConfig.enabled(cfg)) {
                continue;
            }
            boolean preso = jail.resolve(ref.guildId(), ref.userId()).kind() == JailService.Kind.PRESO;
            for (Job job : JOBS) {
                maybeNotify(now, ref, guild, cfg, preso, job);
            }
        }
    }

    private void maybeNotify(long now, UserRef ref, Guild guild, GuildConfig cfg, boolean preso, Job job) {
        long lastTs = cooldowns.lastTs(ref.guildId(), ref.userId(), job.action());
        long availableSince = availableSince(job, lastTs, now, cfg);
        boolean equipped = job.slot() == null || inv.equipped(ref.guildId(), ref.userId(), job.slot()) != null;
        long notifiedTs = prefs.notifiedTs(ref.guildId(), ref.userId(), job.action());
        if (!NotifyDecision.shouldNotify(now, availableSince, notifiedTs, equipped, preso, job.allowedWhileJailed())) {
            return;
        }
        prefs.stampNotified(ref.guildId(), ref.userId(), job.action(), now); // best-effort: marca antes do DM
        deliver(ref, guild, cfg, job);
    }

    private long availableSince(Job job, long lastTs, long now, GuildConfig cfg) {
        if (job.cooldownS() == 0) { // daily → meia-noite BRT
            return DailyReset.available(lastTs, now) ? DailyReset.todayMidnightMillis(now) : Long.MAX_VALUE;
        }
        long cd = job.cooldownS() == -1 ? EconomyConfig.workCooldownSeconds(cfg) : job.cooldownS();
        return lastTs + cd * 1000L;
    }

    private void deliver(UserRef ref, Guild guild, GuildConfig cfg, Job job) {
        int accent = EmbedColor.resolve(cfg);
        String text = Emojis.of(Emojis.BELL, "🔔") + " No servidor **" + guild.getName()
                + "**, seu `/" + job.command() + "` está disponível!";
        ctx.jda().openPrivateChannelById(ref.userId()).queue(
                pc -> pc.sendMessageComponents(Panels.container(accent, Panels.text(text))).useComponentsV2()
                        .queue(ok -> { }, err -> { }),
                err -> { });
    }
}
