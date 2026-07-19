package dev.davimf.basebot.modules.base.vip;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.util.EmbedColor;
import dev.davimf.basebot.util.Emojis;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu.SelectTarget;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;

/** Painel do membro VIP (/vip painel) — estado do grant + controles da call/cargo. */
public final class VipPanelView {

    /** Nível de boost mínimo do servidor para permitir emoji custom em cargo. */
    private static final int EMOJI_MIN_BOOST_TIER = 2;

    private VipPanelView() {}

    public static Container panel(BotContext ctx, Guild guild, VipGrant grant, VipPlan plan) {
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(guild.getId());
        int accent = EmbedColor.resolve(cfg);

        String planName = plan != null ? plan.name() : grant.planId();
        VoiceChannel call = grant.callChannelId() == null ? null : guild.getVoiceChannelById(grant.callChannelId());
        Role controlRole = grant.controlRoleId() == null ? null : guild.getRoleById(grant.controlRoleId());
        String callName = call != null ? call.getAsMention() : "não provisionada";
        String roleName = controlRole != null ? controlRole.getAsMention() : "não provisionado";
        String revealLabel = grant.revealOnOccupancy() ? "Ligado" : "Desligado";
        String expiry = grant.expiresAt() == null
                ? "permanente" : "<t:" + grant.expiresAt().getEpochSecond() + ":R>";

        StringBuilder body = new StringBuilder();
        body.append("## ").append(Emojis.of(Emojis.GEM, "💎")).append(" VIP · ").append(planName).append('\n');
        body.append('\n').append(Emojis.of(Emojis.CALL, "📞")).append(" Call · ").append(callName);
        body.append('\n').append(Emojis.of(Emojis.ROLES, "🎭")).append(" Cargo-controle · ").append(roleName);
        body.append('\n').append(Emojis.of(Emojis.STAR, "⭐")).append(" Revelar ocupação · ").append(revealLabel);
        body.append('\n').append(Emojis.of(Emojis.CLOCK, "🕒")).append(" Expira · ").append(expiry);

        Button rncall = Button.secondary(ComponentId.of("vippanel", "rncall", grant.id()), "Renomear call")
                .withEmoji(Emojis.button(Emojis.EDIT));
        Button rnrole = Button.secondary(ComponentId.of("vippanel", "rnrole", grant.id()), "Renomear cargo")
                .withEmoji(Emojis.button(Emojis.EDIT));
        String revealButtonLabel = grant.revealOnOccupancy() ? "Revelar: Ligado" : "Revelar: Desligado";
        Button reveal = (grant.revealOnOccupancy() ? Button.success(ComponentId.of("vippanel", "reveal", grant.id()), revealButtonLabel)
                : Button.secondary(ComponentId.of("vippanel", "reveal", grant.id()), revealButtonLabel))
                .withEmoji(Emojis.button(Emojis.STAR));
        Button emoji = Button.secondary(ComponentId.of("vippanel", "emoji", grant.id()), "Emoji do cargo")
                .withEmoji(Emojis.button(Emojis.PALETTE))
                .withDisabled(guild.getBoostTier().getKey() < EMOJI_MIN_BOOST_TIER);

        EntitySelectMenu access = EntitySelectMenu.create(ComponentId.of("vippanel", "access", grant.id()), SelectTarget.USER)
                .setPlaceholder("Conceder/remover acesso à call")
                .setRequiredRange(0, 25)
                .build();

        return Panels.container(accent,
                Panels.text(body.toString()),
                Panels.divider(),
                ActionRow.of(rncall, rnrole, reveal, emoji),
                ActionRow.of(access));
    }
}
