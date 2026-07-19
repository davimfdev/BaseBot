package dev.davimf.basebot.modules.base.vip;

import dev.davimf.basebot.core.BotContext;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Reveal-on-occupancy: quando alguém entra, sai ou é movido de uma call VIP, reavalia se ela deve
 *  ficar visível para {@code @everyone} (pelo menos 1 humano permitido dentro) ou voltar a ficar
 *  oculta (esvaziou de humanos permitidos). A decisão em si é pura ({@link VipReveal#decide}); este
 *  listener só resolve o estado atual (quem está na call, quantos são permitidos, se já está
 *  revelada) via {@link VipService#grantByCallId} e aplica a mutação no Discord. */
public final class VipVoiceListener extends ListenerAdapter {
    private static final Logger log = LoggerFactory.getLogger(VipVoiceListener.class);

    private final BotContext ctx;
    private final VipService vip;

    public VipVoiceListener(BotContext ctx, VipService vip) {
        this.ctx = ctx;
        this.vip = vip;
    }

    /** Reage ao(s) canal(is) afetado(s) pelo evento: entrar (só {@code channelJoined}), sair (só
     *  {@code channelLeft}) ou mover (ambos — cada lado é reavaliado independentemente). */
    @Override
    public void onGuildVoiceUpdate(GuildVoiceUpdateEvent event) {
        String guildId = event.getGuild().getId();
        reactTo(guildId, event.getChannelLeft());
        reactTo(guildId, event.getChannelJoined());
    }

    private void reactTo(String guildId, AudioChannel channel) {
        if (!(channel instanceof VoiceChannel call)) {
            return; // null (nenhum lado) ou não é um voice channel (ex.: stage) — não é call VIP.
        }
        VipGrant grant = vip.grantByCallId(guildId, call.getId());
        if (grant == null) {
            return; // não é a call de um grant VIP ativo.
        }
        // Contagem/leitura de membros e a mutação de permissão tocam o JDA — nunca na thread de
        // eventos do gateway.
        ctx.scheduler().executor().execute(() -> apply(call, grant));
    }

    private void apply(VoiceChannel call, VipGrant grant) {
        try {
            // Bots nunca contam como "humano permitido" na ocupação da call.
            int allowed = (int) call.getMembers().stream()
                    .filter(m -> !m.getUser().isBot())
                    .filter(m -> vip.isAllowedInCall(m, grant, call))
                    .count();
            boolean revealed = vip.isRevealedToEveryone(call);
            switch (VipReveal.decide(grant.revealOnOccupancy(), allowed, revealed)) {
                case REVEAL -> vip.setEveryoneView(call, true);
                case HIDE -> vip.setEveryoneView(call, false);
                case NONE -> { }
            }
        } catch (RuntimeException e) {
            log.error("VIP: falha ao aplicar reveal-on-occupancy na call {} (guild {})",
                    call.getId(), call.getGuild().getId(), e);
        }
    }
}
