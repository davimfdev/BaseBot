// [OUTLINE START]
// Package: dev.davimf.basebot.modules.facs.punish
// 
// Class: PunishService
// 
// Constructors:
//   - `Constructor` : `public PunishService(BotContext ctx, PunishmentRepository repo)`
// 
// Methods:
//   - `Method` : `private static final Logger log = LoggerFactory. getLogger(PunishService.class)`
//   - `Method` : `private static final Duration ADV_TTL = Duration. ofDays(20)`
//   - `Method` : `public Container history(String guildId, String userId)`
//   - `Method` : `private static String labelOf(String type, int level)`
//   - `Method` : `private int accent(String guildId)`
//   - `Method` : `private static String trim(String s, int max)`
// 
// Fields:
//   - `Field` : `package-private static final String NS`
//   - `Field` : `private final BotContext ctx`
//   - `Field` : `private final PunishmentRepository repo`
// [OUTLINE END]



package dev.davimf.basebot.modules.facs.punish;

import dev.davimf.basebot.util.Emojis;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.database.model.Punishment;
import dev.davimf.basebot.modules.facs.FacsLog;
import dev.davimf.basebot.util.EmbedColor;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Punishment logic (BOTSPECS Module 4): apply Blacklist/Demotion/ADV, the {@code /punições}
 * history view with a revoke select, and the 20-day ADV auto-expiry sweep. ADV stacks —
 * a new ADV deactivates prior ones and increments the level.
 */
public final class PunishService {

    private static final Logger log = LoggerFactory.getLogger(PunishService.class);
    private static final Duration ADV_TTL = Duration.ofDays(20);
    static final String NS = "punir";

    private final BotContext ctx;
    private final PunishmentRepository repo;

    public PunishService(BotContext ctx, PunishmentRepository repo) {
        this.ctx = ctx;
        this.repo = repo;
    }

    public void apply(SlashCommandInteractionEvent event, Member target, String type, String reason) {
        String guildId = event.getGuild().getId();
        String userId = target.getId();
        int level = 0;
        String expiresAt = null;
        if (Punishment.ADV.equals(type)) {
            level = repo.highestActiveAdvLevel(guildId, userId) + 1;  // ADV 2 replaces ADV 1
            repo.deactivateAdvs(guildId, userId);
            expiresAt = Instant.now().plus(ADV_TTL).toString();
        }
        repo.create(guildId, userId, type, level, reason, event.getUser().getId(), expiresAt);

        String label = labelOf(type, level);
        ctx.database().actionLogs().log(guildId, event.getUser().getId(), userId, "PUNISH_" + type, label);
        String entry = "## " + Emojis.of(Emojis.SCALES, "⚖️") + " " + label + "\n---\n"
                + "" + Emojis.of(Emojis.MEMBER, "👤") + " **Membro** · " + target.getUser().getAsTag() + " · `" + userId + "`\n---\n"
                + "" + Emojis.of(Emojis.SHIELD, "🛡️") + " **Responsável** · <@" + event.getUser().getId() + ">\n"
                + "" + Emojis.of(Emojis.NOTE, "📝") + " **Motivo** · " + (reason == null || reason.isBlank() ? "*não informado*" : reason)
                + (Punishment.ADV.equals(type) ? "\n-# Expira automaticamente em 20 dias." : "");
        FacsLog.post(ctx, guildId, "log-punicoes", entry);

        Replies.reply(event, ctx, "" + Emojis.of(Emojis.SCALES, "⚖️") + " **" + label + "** aplicado em " + target.getAsMention() + ".");
    }

    public Container history(String guildId, String userId) {
        List<Punishment> all = repo.listByUser(guildId, userId);
        StringBuilder body = new StringBuilder();
        if (all.isEmpty()) {
            body.append("-# *Nenhuma punição registrada.*");
        } else {
            for (Punishment p : all) {
                if (body.length() > 0) {
                    body.append('\n');
                }
                body.append(p.active() ? "" + Emojis.of(Emojis.OFFLINE, "🔴") + " " : "" + Emojis.of(Emojis.DOT, "⚪") + " ").append("**").append(p.label()).append("** · ")
                        .append(p.reason() == null || p.reason().isBlank() ? "*sem motivo*" : p.reason())
                        .append("\n-# por <@").append(p.appliedBy()).append("> · ").append(p.createdAt());
            }
        }

        List<ContainerChildComponent> kids = new ArrayList<>();
        kids.add(Panels.text("## " + Emojis.of(Emojis.SCALES, "⚖️") + " Punições · <@" + userId + ">"));
        kids.add(Panels.divider());
        kids.add(Panels.text(body.toString()));
        List<Punishment> active = repo.listActiveByUser(guildId, userId);
        if (!active.isEmpty()) {
            StringSelectMenu.Builder menu = StringSelectMenu.create(ComponentId.of(NS, "revoke", userId))
                    .setPlaceholder("Revogar uma punição ativa");
            for (Punishment p : active) {
                menu.addOption(trim(p.label() + " — " + (p.reason() == null ? "" : p.reason()), 100), p.id());
            }
            kids.add(ActionRow.of(menu.build()));
        }
        return Panels.container(accent(guildId), kids.toArray(new ContainerChildComponent[0]));
    }

    public void revoke(StringSelectInteractionEvent event, String userId) {
        if (event.getGuild() == null) {
            return;
        }
        String guildId = event.getGuild().getId();
        String punishmentId = event.getValues().get(0);
        repo.find(punishmentId).ifPresent(p -> {
            repo.setActive(punishmentId, false);
            ctx.database().actionLogs().log(guildId, event.getUser().getId(), userId, "PUNISH_REVOKE", p.label());
            FacsLog.post(ctx, guildId, "log-punicoes", "## " + Emojis.of(Emojis.RECYCLE, "♻️") + " Punição revogada\n---\n"
                    + "**" + p.label() + "** de <@" + userId + "> revogada por <@"
                    + event.getUser().getId() + ">.");
        });
        event.editComponents(history(guildId, userId)).useComponentsV2().queue();
    }

    /** Auto-expires ADVs past 20 days (BOTSPECS §4; run on boot + on a schedule). */
    public void sweepExpiredAdvs() {
        List<Punishment> expired = repo.listExpiredAdvs(Instant.now().toString());
        for (Punishment p : expired) {
            repo.setActive(p.id(), false);
            ctx.database().actionLogs().log(p.guildId(), "system", p.userId(), "ADV_EXPIRED", p.label());
            FacsLog.post(ctx, p.guildId(), "log-punicoes",
                    "## " + Emojis.of(Emojis.HOURGLASS, "⌛") + " ADV expirado\n---\n**" + p.label() + "** de <@" + p.userId() + "> expirou (20 dias).");
        }
        if (!expired.isEmpty()) {
            log.info("Expired {} ADV(s).", expired.size());
        }
    }

    private static String labelOf(String type, int level) {
        return switch (type) {
            case Punishment.ADV -> "ADV " + level;
            case Punishment.BLACKLIST -> "Blacklist";
            case Punishment.DEMOTION -> "Rebaixamento";
            default -> type;
        };
    }

    private int accent(String guildId) {
        return EmbedColor.resolve(ctx.database().guildConfig().findOrEmpty(guildId));
    }

    private static String trim(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }
}
