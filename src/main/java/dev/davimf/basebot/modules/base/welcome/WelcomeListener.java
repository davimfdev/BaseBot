package dev.davimf.basebot.modules.base.welcome;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.modules.base.listeners.AttachmentVault;
import dev.davimf.basebot.util.EmbedColor;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.mediagallery.MediaGallery;
import net.dv8tion.jda.api.components.mediagallery.MediaGalleryItem;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.ArrayList;
import java.util.List;

/** Boas-vindas (canal e/ou DM) + autorole no join; despedida opcional no leave. Requer GUILD_MEMBERS. */
public final class WelcomeListener extends ListenerAdapter {

    private final BotContext ctx;
    private final AttachmentVault vault;

    public WelcomeListener(BotContext ctx) {
        this.ctx = ctx;
        this.vault = new AttachmentVault(ctx, ctx.config().discord().vaultGuildId());
    }

    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(event.getGuild().getId());
        autorole(event.getMember(), cfg);
        if (!WelcomeConfig.enabled(cfg)) {
            return;
        }
        String text = WelcomeText.render(WelcomeConfig.message(cfg), event.getMember(), event.getGuild());
        int accent = EmbedColor.resolve(cfg);
        String[] img = WelcomeConfig.imageRef(cfg);
        if (img == null) {
            deliver(event.getMember(), cfg, container(accent, text, null));
        } else {
            vault.retrieveUrls(img[0], img[1], urls ->
                    deliver(event.getMember(), cfg, container(accent, text, urls.isEmpty() ? null : urls.get(0))));
        }
    }

    @Override
    public void onGuildMemberRemove(GuildMemberRemoveEvent event) {
        if (event.getMember() == null) {
            return; // sem cache do membro não há nome; pula
        }
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(event.getGuild().getId());
        if (!WelcomeConfig.farewellEnabled(cfg)) {
            return;
        }
        TextChannel ch = channel(event.getGuild(), cfg.channel(WelcomeConfig.KEY_FAREWELL_CHANNEL));
        if (ch == null) {
            return;
        }
        String text = WelcomeText.render(WelcomeConfig.farewellMessage(cfg), event.getMember(), event.getGuild());
        ch.sendMessageComponents(container(EmbedColor.resolve(cfg), text, null))
                .useComponentsV2().setAllowedMentions(List.of()).queue(ok -> {}, err -> {});
    }

    private void autorole(Member member, GuildConfig cfg) {
        String roleId = cfg.role(WelcomeConfig.KEY_AUTOROLE);
        if (roleId == null || roleId.isBlank()) {
            roleId = cfg.role(WelcomeConfig.FALLBACK_AUTOROLE_KEY); // facs em uso
        }
        if (roleId == null || roleId.isBlank()) {
            return;
        }
        Role role = member.getGuild().getRoleById(roleId);
        if (role != null && member.getGuild().getSelfMember().canInteract(role)) {
            member.getGuild().addRoleToMember(member, role).reason("Autorole").queue(ok -> {}, err -> {});
        }
    }

    private void deliver(Member member, GuildConfig cfg, Container panel) {
        TextChannel ch = channel(member.getGuild(), cfg.channel(WelcomeConfig.KEY_CHANNEL));
        if (ch != null) {
            ch.sendMessageComponents(panel).useComponentsV2()
                    .setAllowedMentions(List.of(Message.MentionType.USER)).queue(ok -> {}, err -> {});
        }
        if (WelcomeConfig.dm(cfg)) {
            member.getUser().openPrivateChannel().queue(
                    pc -> pc.sendMessageComponents(panel).useComponentsV2().queue(ok -> {}, err -> {}),
                    err -> { /* DM fechada (CANNOT_SEND_TO_USER): engole */ });
        }
    }

    private static Container container(int accent, String text, String imageUrl) {
        List<ContainerChildComponent> kids = new ArrayList<>();
        kids.add(Panels.text(text));
        if (imageUrl != null) {
            kids.add(MediaGallery.of(List.of(MediaGalleryItem.fromUrl(imageUrl))));
        }
        return Panels.container(accent, kids.toArray(new ContainerChildComponent[0]));
    }

    private static TextChannel channel(Guild guild, String id) {
        return id == null || id.isBlank() ? null : guild.getTextChannelById(id);
    }
}
