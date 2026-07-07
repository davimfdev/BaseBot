package dev.davimf.basebot.modules.base.moderation;

import dev.davimf.basebot.util.Emojis;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.component.ComponentHandler;
import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.util.EmbedColor;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;

import java.util.List;

/** Routes the infractions panels: history pagination, case revoke button, revoke picker. */
public final class InfractionComponentHandler implements ComponentHandler {

    private final ModerationService service;

    public InfractionComponentHandler(ModerationService service) {
        this.service = service;
    }

    @Override
    public String namespace() {
        return InfractionView.NS;
    }

    @Override
    public void onButton(ButtonInteractionEvent event, ComponentId id, BotContext ctx) {
        if (event.getGuild() == null) {
            return;
        }
        String guildId = event.getGuild().getId();
        int accent = EmbedColor.resolve(ctx.database().guildConfig().findOrEmpty(guildId));
        switch (id.action()) {
            case "histpage" -> {
                String userId = id.arg(0);
                List<Infraction> cases = service.repository().listByUser(guildId, userId);
                event.editComponents(InfractionView.history(accent, userId, cases, parseInt(id.arg(1))))
                        .useComponentsV2().queue();
            }
            case "revoke" -> {
                int caseNumber = parseInt(id.arg(0));
                service.revoke(guildId, caseNumber);
                service.repository().findByCase(guildId, caseNumber).ifPresent(inf ->
                        event.editComponents(InfractionView.caseDetail(accent, inf)).useComponentsV2().queue());
            }
            case "nuke" -> nuke(event, ctx, id.arg(0), accent);
            default -> { /* not ours */ }
        }
    }

    @Override
    public void onStringSelect(StringSelectInteractionEvent event, ComponentId id, BotContext ctx) {
        if (!"revokepick".equals(id.action()) || event.getGuild() == null) {
            return;
        }
        String guildId = event.getGuild().getId();
        service.revoke(guildId, parseInt(event.getValues().get(0)));
        String userId = id.arg(0);
        int accent = EmbedColor.resolve(ctx.database().guildConfig().findOrEmpty(guildId));
        event.editComponents(InfractionView.revokePicker(accent, userId,
                service.repository().listActiveByUser(guildId, userId))).useComponentsV2().queue();
    }

    private void nuke(ButtonInteractionEvent event, BotContext ctx, String channelId, int accent) {
        if (event.getMember() == null || !event.getMember().hasPermission(Permission.MANAGE_CHANNEL)) {
            Replies.ephemeral(event, ctx, "Você não tem permissão para limpar canais.");
            return;
        }
        TextChannel channel = event.getGuild().getTextChannelById(channelId);
        if (channel == null) {
            Replies.ephemeral(event, ctx, "Esse canal não existe mais.");
            return;
        }
        int position = channel.getPositionRaw();
        String modTag = event.getUser().getAsTag();
        event.editComponents(Panels.container(accent, Panels.text("## " + Emojis.of(Emojis.NUKE, "💥") + " Limpando o canal…")))
                .useComponentsV2().queue(ok -> {}, err -> {});
        channel.createCopy().reason(dev.davimf.basebot.util.ModReason.of(modTag, "Nuke")).queue(copy -> {
            copy.getManager().setPosition(position).queue(ok -> {}, err -> {});
            channel.delete().reason(dev.davimf.basebot.util.ModReason.of(modTag, "Nuke")).queue(ok -> {}, err -> {});
            copy.sendMessageComponents(Panels.container(accent,
                            Panels.text("## " + Emojis.of(Emojis.NUKE, "💥") + " Canal limpo"),
                            Panels.divider(),
                            Panels.text("-# por <@" + event.getUser().getId() + ">")))
                    .useComponentsV2().queue(ok -> {}, err -> {});
            ctx.database().actionLogs().log(event.getGuild().getId(), event.getUser().getId(),
                    copy.getId(), "NUKE", channelId);
        }, err -> Replies.ephemeral(event, ctx, "Falha ao limpar o canal: " + err.getMessage()));
    }

    private static int parseInt(String s) {
        try {
            return s == null ? 0 : Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
