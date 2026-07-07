package dev.davimf.basebot.modules.base.listeners;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.util.AuditLookup;
import dev.davimf.basebot.util.ChannelLog;
import dev.davimf.basebot.util.Emojis;
import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Invite;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleRemoveEvent;
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateNicknameEvent;
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateTimeOutEvent;
import net.dv8tion.jda.api.events.user.update.UserUpdateGlobalNameEvent;
import net.dv8tion.jda.api.events.user.update.UserUpdateNameEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.utils.TimeFormat;

import java.util.stream.Collectors;

/** Logs de membro: entradas (com convite usado, contagem, avatar e banner), saídas,
 *  expulsão (kick via audit), apelido, nome/global-name, cargos, timeout. */
public final class MembershipLoggingListener extends ListenerAdapter {

    private final BotContext ctx;
    private final InviteTracker invites;

    public MembershipLoggingListener(BotContext ctx, InviteTracker invites) {
        this.ctx = ctx;
        this.invites = invites;
    }

    // --- join / leave ----------------------------------------------------------

    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        User u = event.getUser();
        Guild g = event.getGuild();
        int count = g.getMemberCount();
        invites.resolveUsedInvite(g, invite -> {
            String inviteLine;
            if (invite == null) {
                inviteLine = Emojis.of(Emojis.LINK, "🔗") + " **Convite** · `desconhecido`";
            } else {
                User by = invite.getInviter();
                String creator = by == null ? ""
                        : "\n-# └ criado por " + by.getAsMention() + " (`" + by.getName() + "`)";
                inviteLine = Emojis.of(Emojis.LINK, "🔗") + " **Convite** · `" + invite.getCode() + "`" + creator;
            }
            u.retrieveProfile().queue(
                    p -> postJoin(g, u, count, inviteLine, p.getBannerUrl()),
                    err -> postJoin(g, u, count, inviteLine, null));
        });
    }

    private void postJoin(Guild g, User u, int count, String inviteLine, String banner) {
        String body = "## " + Emojis.of(Emojis.JOIN, "📥") + " Membro entrou\n---\n"
                + Emojis.of(Emojis.MEMBER, "👤") + " **Membro** · " + u.getAsMention() + " (`" + u.getName() + "`)\n"
                + Emojis.of(Emojis.ID, "🆔") + " **ID** · `" + u.getId() + "`\n---\n"
                + Emojis.of(Emojis.CLOCK, "🕒") + " **Conta criada** · "
                + TimeFormat.DATE_TIME_SHORT.format(u.getTimeCreated()) + " • " + TimeFormat.RELATIVE.format(u.getTimeCreated()) + "\n---\n"
                + inviteLine + "\n"
                + Emojis.of(Emojis.MEMBERS, "👥") + " **Membros agora** · `" + count + "`";
        ChannelLog.post(ctx, g.getId(), "log-entradas", body, u.getEffectiveAvatarUrl(), banner);
    }

    @Override
    public void onGuildMemberRemove(GuildMemberRemoveEvent event) {
        User u = event.getUser();
        Guild g = event.getGuild();
        int count = g.getMemberCount();
        Member m = event.getMember(); // may be null if the member wasn't cached

        StringBuilder extra = new StringBuilder();
        if (m != null) {
            extra.append("\n---\n").append(Emojis.of(Emojis.CALENDAR, "📅")).append(" **Entrou no servidor** · ")
                    .append(TimeFormat.DATE_TIME_SHORT.format(m.getTimeJoined()))
                    .append(" • ").append(TimeFormat.RELATIVE.format(m.getTimeJoined()));
            String roles = m.getRoles().stream().map(Role::getAsMention).collect(Collectors.joining(", "));
            if (!roles.isBlank()) {
                extra.append("\n").append(Emojis.of(Emojis.ROLES, "🎭")).append(" **Cargos** · ").append(roles);
            }
        }
        String extraStr = extra.toString();
        u.retrieveProfile().queue(
                p -> postLeave(g, u, count, extraStr, p.getBannerUrl()),
                err -> postLeave(g, u, count, extraStr, null));

        // Distinguish a kick from a voluntary leave via the audit log (best-effort).
        AuditLookup.lookup(g, u.getId(), ActionType.KICK, actor ->
                ChannelLog.post(ctx, g.getId(), "log-kicks",
                        "## " + Emojis.of(Emojis.KICK, "👢") + " Expulso (kick)\n---\n"
                                + Emojis.of(Emojis.MEMBER, "👤") + " **Membro** · " + u.getAsTag() + " · `" + u.getId() + "`\n---\n"
                                + Emojis.of(Emojis.SHIELD, "🛡️") + " **Responsável** · " + actor.moderatorMention()
                                + "\n" + Emojis.of(Emojis.NOTE, "📝") + " **Motivo** · " + actor.reason(),
                        u.getEffectiveAvatarUrl(), (String) null));
    }

    private void postLeave(Guild g, User u, int count, String extra, String banner) {
        String body = "## " + Emojis.of(Emojis.LEAVE, "📤") + " Membro saiu\n---\n"
                + Emojis.of(Emojis.MEMBER, "👤") + " **Membro** · " + u.getAsMention() + " (`" + u.getName() + "`)\n"
                + Emojis.of(Emojis.ID, "🆔") + " **ID** · `" + u.getId() + "`\n---\n"
                + Emojis.of(Emojis.CLOCK, "🕒") + " **Conta criada** · "
                + TimeFormat.DATE_TIME_SHORT.format(u.getTimeCreated()) + " • " + TimeFormat.RELATIVE.format(u.getTimeCreated())
                + extra + "\n---\n"
                + Emojis.of(Emojis.MEMBERS, "👥") + " **Membros agora** · `" + count + "`";
        ChannelLog.post(ctx, g.getId(), "log-saidas", body, u.getEffectiveAvatarUrl(), banner);
    }

    // --- member updates --------------------------------------------------------

    @Override
    public void onGuildMemberUpdateNickname(GuildMemberUpdateNicknameEvent event) {
        String before = event.getOldNickname() == null ? "*nenhum*" : event.getOldNickname();
        String after = event.getNewNickname() == null ? "*nenhum*" : event.getNewNickname();
        ChannelLog.post(ctx, event.getGuild().getId(), "log-membros",
                "## " + Emojis.of(Emojis.NICKNAME, "🏷️") + " Apelido alterado\n---\n"
                        + Emojis.of(Emojis.MEMBER, "👤") + " **Membro** · " + event.getMember().getAsMention()
                        + "\n---\n**Antes** · " + before + "\n**Depois** · " + after);
    }

    @Override
    public void onGuildMemberRoleAdd(GuildMemberRoleAddEvent event) {
        String roles = event.getRoles().stream().map(Role::getAsMention).collect(Collectors.joining(", "));
        AuditLookup.lookup(event.getGuild(), event.getUser().getId(), ActionType.MEMBER_ROLE_UPDATE, actor ->
                ChannelLog.post(ctx, event.getGuild().getId(), "log-membros",
                        "## " + Emojis.of(Emojis.PLUS, "➕") + " Cargos adicionados\n---\n"
                                + Emojis.of(Emojis.MEMBER, "👤") + " **Membro** · " + event.getMember().getAsMention()
                                + "\n" + Emojis.of(Emojis.ROLES, "🎭") + " **Cargos** · " + roles
                                + "\n---\n" + Emojis.of(Emojis.SHIELD, "🛡️") + " **Responsável** · " + actor.moderatorMention()));
    }

    @Override
    public void onGuildMemberRoleRemove(GuildMemberRoleRemoveEvent event) {
        String roles = event.getRoles().stream().map(Role::getAsMention).collect(Collectors.joining(", "));
        AuditLookup.lookup(event.getGuild(), event.getUser().getId(), ActionType.MEMBER_ROLE_UPDATE, actor ->
                ChannelLog.post(ctx, event.getGuild().getId(), "log-membros",
                        "## " + Emojis.of(Emojis.MINUS, "➖") + " Cargos removidos\n---\n"
                                + Emojis.of(Emojis.MEMBER, "👤") + " **Membro** · " + event.getMember().getAsMention()
                                + "\n" + Emojis.of(Emojis.ROLES, "🎭") + " **Cargos** · " + roles
                                + "\n---\n" + Emojis.of(Emojis.SHIELD, "🛡️") + " **Responsável** · " + actor.moderatorMention()));
    }

    @Override
    public void onGuildMemberUpdateTimeOut(GuildMemberUpdateTimeOutEvent event) {
        boolean applied = event.getNewTimeOutEnd() != null;
        String body = applied
                ? "## " + Emojis.of(Emojis.HOURGLASS, "⏳") + " Timeout aplicado\n---\n"
                        + Emojis.of(Emojis.MEMBER, "👤") + " **Membro** · " + event.getMember().getAsMention()
                        + "\n**Até** · " + TimeFormat.RELATIVE.format(event.getNewTimeOutEnd())
                : "## " + Emojis.of(Emojis.CHECK_YES, "✅") + " Timeout removido\n---\n"
                        + Emojis.of(Emojis.MEMBER, "👤") + " **Membro** · " + event.getMember().getAsMention();
        AuditLookup.lookup(event.getGuild(), event.getUser().getId(), ActionType.MEMBER_UPDATE, actor ->
                ChannelLog.post(ctx, event.getGuild().getId(), "log-membros",
                        body + "\n---\n" + Emojis.of(Emojis.SHIELD, "🛡️") + " **Responsável** · " + actor.moderatorMention()
                                + "\n" + Emojis.of(Emojis.NOTE, "📝") + " **Motivo** · " + actor.reason()));
    }

    @Override
    public void onUserUpdateName(UserUpdateNameEvent event) {
        postUserChange(event.getUser(), "## " + Emojis.of(Emojis.MEMBER, "👤") + " Nome de usuário alterado",
                event.getOldName(), event.getNewName());
    }

    @Override
    public void onUserUpdateGlobalName(UserUpdateGlobalNameEvent event) {
        String before = event.getOldGlobalName() == null ? "*nenhum*" : event.getOldGlobalName();
        String after = event.getNewGlobalName() == null ? "*nenhum*" : event.getNewGlobalName();
        postUserChange(event.getUser(), "## " + Emojis.of(Emojis.IDCARD, "🪪") + " Nome de exibição alterado", before, after);
    }

    /** User events são globais; loga em log-membros de cada guilda mútua onde o membro está. */
    private void postUserChange(User user, String title, String before, String after) {
        for (Guild guild : user.getMutualGuilds()) {
            if (guild.getMember(user) == null) {
                continue;
            }
            ChannelLog.post(ctx, guild.getId(), "log-membros",
                    title + "\n" + Emojis.of(Emojis.MEMBER, "👤") + " **Membro** · " + user.getAsMention()
                            + "\n**Antes** · " + before + "\n**Depois** · " + after,
                    user.getEffectiveAvatarUrl(), (String) null);
        }
    }
}
