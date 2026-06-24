package dev.davimf.basebot.modules.facs.hierarchy;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.modules.base.setup.GuildConfigEdits;
import dev.davimf.basebot.util.EmbedColor;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Publishes and refreshes the auto-updating {@code /hierarquia} panel (BOTSPECS Module 4).
 * The panel's channel + message ids are stored in guild_config.settings so the
 * role-change listener can edit the existing message (debounced) instead of reposting.
 */
public final class HierarchyService {

    private static final Logger log = LoggerFactory.getLogger(HierarchyService.class);

    static final String CHANNEL_KEY = "hierarchy-channel-id";
    static final String MESSAGE_KEY = "hierarchy-message-id";

    private final BotContext ctx;

    public HierarchyService(BotContext ctx) {
        this.ctx = ctx;
    }

    /** (Re)publishes the panel in the command's channel and stores its location. */
    public void publish(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        if (guild == null || !(event.getChannel() instanceof TextChannel channel)) {
            event.reply("Use este comando em um canal de texto.").setEphemeral(true).queue();
            return;
        }
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(guild.getId());
        event.deferReply(true).queue();
        channel.sendMessageComponents(HierarchyView.panel(EmbedColor.resolve(cfg), guild, cfg))
                .useComponentsV2()
                .setAllowedMentions(List.of())
                .queue(msg -> {
                    GuildConfig updated = GuildConfigEdits.withSetting(
                            GuildConfigEdits.withSetting(cfg, CHANNEL_KEY, channel.getId()),
                            MESSAGE_KEY, msg.getId());
                    ctx.database().guildConfig().save(updated);
                    ctx.database().actionLogs().log(guild.getId(), event.getUser().getId(),
                            channel.getId(), "HIERARCHY_PUBLISH", msg.getId());
                    event.getHook().sendMessage("🏛️ Painel de hierarquia publicado neste canal.").queue();
                }, err -> event.getHook().sendMessage("Falha ao publicar: " + err.getMessage()).queue());
    }

    /** Rebuilds and edits the stored panel message. No-op if none was published. */
    public void refresh(String guildId) {
        if (ctx.jda() == null) {
            return;
        }
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(guildId);
        String channelId = cfg.setting(CHANNEL_KEY);
        String messageId = cfg.setting(MESSAGE_KEY);
        if (channelId == null || messageId == null) {
            return;
        }
        Guild guild = ctx.jda().getGuildById(guildId);
        if (guild == null) {
            return;
        }
        TextChannel channel = guild.getTextChannelById(channelId);
        if (channel == null) {
            return;
        }
        channel.retrieveMessageById(messageId)
                .flatMap(m -> m.editMessageComponents(HierarchyView.panel(EmbedColor.resolve(cfg), guild, cfg))
                        .useComponentsV2())
                .queue(ok -> {}, err -> log.debug("Hierarchy refresh failed for {}: {}",
                        guildId, err.getMessage()));
    }
}
