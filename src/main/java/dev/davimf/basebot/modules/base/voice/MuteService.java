package dev.davimf.basebot.modules.base.voice;

import dev.davimf.basebot.core.BotContext;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Lifts expired timed mutes (BOTSPECS Module 1). Run on a short schedule + on boot so a
 * mute lasts only its configured time, removing the role (TEXT) or the server voice mute
 * (VOICE) once it expires.
 */
public final class MuteService {

    private static final Logger log = LoggerFactory.getLogger(MuteService.class);

    private final BotContext ctx;

    public MuteService(BotContext ctx) {
        this.ctx = ctx;
    }

    public void sweepExpired() {
        if (ctx.jda() == null) {
            return;
        }
        List<MuteRepository.Entry> expired = ctx.database().mutes().listExpired(System.currentTimeMillis());
        for (MuteRepository.Entry e : expired) {
            ctx.database().mutes().remove(e.guildId(), e.userId(), e.type());
            Guild guild = ctx.jda().getGuildById(e.guildId());
            if (guild == null) {
                continue;
            }
            if (MuteRepository.VOICE.equals(e.type())) {
                liftVoice(guild, e.userId());
            } else {
                liftText(guild, e.userId());
            }
            ctx.database().actionLogs().log(e.guildId(), "system", e.userId(),
                    MuteRepository.VOICE.equals(e.type()) ? "VOICE_UNMUTE_AUTO" : "UNMUTE_AUTO", null);
        }
        if (!expired.isEmpty()) {
            log.info("Lifted {} expired mute(s).", expired.size());
        }
    }

    private void liftVoice(Guild guild, String userId) {
        guild.retrieveMemberById(userId).queue(member -> {
            GuildVoiceState vs = member.getVoiceState();
            if (vs != null && vs.inAudioChannel()) {
                guild.mute(member, false).reason("Mute de call expirado").queue(ok -> {}, err -> {});
            }
        }, err -> {});
    }

    private void liftText(Guild guild, String userId) {
        String roleId = ctx.database().guildConfig().findOrEmpty(guild.getId()).role("mutado");
        Role role = roleId == null ? null : guild.getRoleById(roleId);
        if (role == null) {
            return;
        }
        guild.retrieveMemberById(userId).queue(member ->
                guild.removeRoleFromMember(member, role).reason("Mute expirado").queue(ok -> {}, err -> {}),
                err -> {});
    }
}
