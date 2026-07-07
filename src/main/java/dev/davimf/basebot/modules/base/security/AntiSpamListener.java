package dev.davimf.basebot.modules.base.security;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.database.model.GuildConfig;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.List;

/** Canal-armadilha anti-spam: qualquer mensagem de membro não isento → kick + purga. */
public final class AntiSpamListener extends ListenerAdapter {

    private final BotContext ctx;

    public AntiSpamListener(BotContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (!event.isFromGuild() || event.isWebhookMessage()) {
            return;
        }
        var guild = event.getGuild();
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(guild.getId());
        if (!SecurityConfig.antispam(cfg)) {
            return;
        }
        String trap = cfg.channel(SecurityConfig.CHANNEL_ANTISPAM);
        if (trap == null || !trap.equals(event.getChannel().getId())) {
            return;
        }
        Member author = event.getMember();
        if (author == null) {
            return;
        }
        boolean bot = event.getAuthor().isBot();
        boolean owner = author.isOwner();
        boolean admin = author.hasPermission(Permission.ADMINISTRATOR);
        List<String> roleIds = author.getRoles().stream().map(ISnowflake::getId).toList();
        if (SecurityConfig.isSpamExempt(cfg, bot, owner, admin, roleIds)) {
            return;
        }
        AntiSpamService.handle(ctx, guild, author);
    }
}
