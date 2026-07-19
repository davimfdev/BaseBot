package dev.davimf.basebot.modules.base.vip;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.component.ComponentHandler;
import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.interactions.callbacks.IDeferrableCallback;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.modals.Modal;

import java.util.List;

/**
 * Ações do painel VIP ({@code /painel}): renomear call/cargo, alternar reveal-on-occupancy,
 * emoji do cargo-controle e conceder/revogar acesso via select de membros.
 *
 * <p>Guarda de dono em toda ação: o {@code grantId} embutido no custom-id só pode ser operado
 * pelo próprio dono do grant ({@code grant.userId() == event.getUser().getId()}) — nunca por
 * quem apenas conhece o customId de outro painel.
 */
public final class VipPanelComponentHandler implements ComponentHandler {

    /** Nível de boost mínimo do servidor para permitir emoji custom em cargo (mesmo limite do painel). */
    private static final int EMOJI_MIN_BOOST_TIER = 2;

    private final VipService vip;

    public VipPanelComponentHandler(VipService vip) {
        this.vip = vip;
    }

    @Override
    public String namespace() {
        return "vippanel";
    }

    @Override
    public void onButton(ButtonInteractionEvent event, ComponentId id, BotContext ctx) {
        VipGrant grant = ownedGrant(event, id);
        if (grant == null) {
            return;
        }
        Guild guild = event.getGuild();
        if (guild == null) {
            return;
        }
        switch (id.action()) {
            case "rncall" -> {
                VoiceChannel call = grant.callChannelId() == null ? null : guild.getVoiceChannelById(grant.callChannelId());
                TextInput nome = TextInput.create("nome", TextInputStyle.SHORT)
                        .setPlaceholder("Novo nome da call").setRequired(true).setMaxLength(100)
                        .setValue(call != null ? call.getName() : null).build();
                event.replyModal(Modal.create(ComponentId.of("vippanel", "rncall", grant.id()), "Renomear call")
                        .addComponents(Label.of("Nome", nome)).build()).queue();
            }
            case "rnrole" -> {
                Role role = grant.controlRoleId() == null ? null : guild.getRoleById(grant.controlRoleId());
                TextInput nome = TextInput.create("nome", TextInputStyle.SHORT)
                        .setPlaceholder("Novo nome do cargo").setRequired(true).setMaxLength(100)
                        .setValue(role != null ? role.getName() : null).build();
                event.replyModal(Modal.create(ComponentId.of("vippanel", "rnrole", grant.id()), "Renomear cargo")
                        .addComponents(Label.of("Nome", nome)).build()).queue();
            }
            case "reveal" -> {
                vip.toggleReveal(grant);
                Container updated = render(ctx, guild, grant.id());
                if (updated != null) {
                    event.editComponents(updated).useComponentsV2().queue();
                }
            }
            case "emoji" -> {
                if (guild.getBoostTier().getKey() < EMOJI_MIN_BOOST_TIER) {
                    Replies.ephemeral(event, ctx, "Este servidor não tem boost suficiente (nível 2+) para emoji de cargo.");
                    return;
                }
                TextInput emoji = TextInput.create("emoji", TextInputStyle.SHORT)
                        .setPlaceholder("Emoji unicode, ex: 💎").setRequired(true).setMinLength(1).setMaxLength(8).build();
                event.replyModal(Modal.create(ComponentId.of("vippanel", "emoji", grant.id()), "Emoji do cargo")
                        .addComponents(Label.of("Emoji", emoji)).build()).queue();
            }
            default -> { /* not ours */ }
        }
    }

    @Override
    public void onEntitySelect(EntitySelectInteractionEvent event, ComponentId id, BotContext ctx) {
        if (!"access".equals(id.action())) {
            return;
        }
        VipGrant grant = ownedGrant(event, id);
        if (grant == null) {
            return;
        }
        Guild guild = event.getGuild();
        if (guild == null) {
            return;
        }
        List<Member> members = event.getMentions().getMembers();
        event.deferEdit().queue();
        ctx.scheduler().executor().execute(() -> {
            for (Member member : members) {
                if (hasAccess(grant, guild, member)) {
                    vip.revokeAccess(grant, member);
                } else {
                    vip.grantAccess(grant, member);
                }
            }
            finishRender(event, ctx, guild, grant.id());
        });
    }

    @Override
    public void onModal(ModalInteractionEvent event, ComponentId id, BotContext ctx) {
        VipGrant grant = ownedGrant(event, id);
        if (grant == null) {
            return;
        }
        Guild guild = event.getGuild();
        if (guild == null) {
            return;
        }
        switch (id.action()) {
            case "rncall" -> {
                String nome = value(event, "nome");
                if (nome == null || nome.isBlank()) {
                    Replies.ephemeral(event, ctx, "Informe um nome.");
                    return;
                }
                String trimmed = nome.trim();
                event.deferEdit().queue();
                ctx.scheduler().executor().execute(() -> {
                    vip.renameCall(grant, trimmed);
                    finishRender(event, ctx, guild, grant.id());
                });
            }
            case "rnrole" -> {
                String nome = value(event, "nome");
                if (nome == null || nome.isBlank()) {
                    Replies.ephemeral(event, ctx, "Informe um nome.");
                    return;
                }
                String trimmed = nome.trim();
                event.deferEdit().queue();
                ctx.scheduler().executor().execute(() -> {
                    vip.renameControlRole(grant, trimmed);
                    finishRender(event, ctx, guild, grant.id());
                });
            }
            case "emoji" -> {
                String emoji = value(event, "emoji");
                if (emoji == null || emoji.isBlank()) {
                    Replies.ephemeral(event, ctx, "Informe um emoji.");
                    return;
                }
                if (guild.getBoostTier().getKey() < EMOJI_MIN_BOOST_TIER) {
                    Replies.ephemeral(event, ctx, "Este servidor não tem boost suficiente (nível 2+) para emoji de cargo.");
                    return;
                }
                String trimmed = emoji.trim();
                event.deferEdit().queue();
                ctx.scheduler().executor().execute(() -> {
                    vip.setRoleIcon(grant, trimmed);
                    finishRender(event, ctx, guild, grant.id());
                });
            }
            default -> { /* not ours */ }
        }
    }

    // --- helpers -----------------------------------------------------------------

    /** Guarda de dono: resolve o grant pelo arg(0) do customId e recusa se não pertencer ao autor
     *  da interação. Responde efêmero e retorna {@code null} quando recusa. */
    private <T extends IReplyCallback> VipGrant ownedGrant(T event, ComponentId id) {
        String grantId = id.arg(0);
        VipGrant grant = vip.grants().findById(grantId).orElse(null);
        if (grant == null || !grant.userId().equals(event.getUser().getId())) {
            event.reply("Este painel não é seu.").setEphemeral(true).queue();
            return null;
        }
        return grant;
    }

    /** Se o membro já tem acesso (cargo-controle ou override pessoal na call, conforme o recurso
     *  disponível no grant), para decidir entre {@link VipService#grantAccess} e {@link VipService#revokeAccess}. */
    private boolean hasAccess(VipGrant grant, Guild guild, Member member) {
        if (grant.controlRoleId() != null) {
            Role role = guild.getRoleById(grant.controlRoleId());
            return role != null && member.getRoles().contains(role);
        }
        if (grant.callChannelId() != null) {
            VoiceChannel call = guild.getVoiceChannelById(grant.callChannelId());
            return call != null && call.getPermissionOverride(member) != null;
        }
        return false;
    }

    /** Re-renderiza o painel a partir do estado atual do grant (recarregado do banco). */
    private Container render(BotContext ctx, Guild guild, String grantId) {
        VipGrant refreshed = vip.grants().findById(grantId).orElse(null);
        if (refreshed == null) {
            return null;
        }
        VipPlan plan = vip.plans().findById(refreshed.planId()).orElse(null);
        return VipPanelView.panel(ctx, guild, refreshed, plan);
    }

    /** Termina uma interação já deferida (deferEdit) editando a mensagem original via hook. */
    private void finishRender(IDeferrableCallback event, BotContext ctx, Guild guild, String grantId) {
        Container updated = render(ctx, guild, grantId);
        if (updated == null) {
            return;
        }
        event.getHook().editOriginalComponents(updated).useComponentsV2().queue();
    }

    private static String value(ModalInteractionEvent event, String key) {
        ModalMapping m = event.getValue(key);
        return m == null ? null : m.getAsString();
    }
}
