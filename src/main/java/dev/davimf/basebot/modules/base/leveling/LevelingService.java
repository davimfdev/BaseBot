package dev.davimf.basebot.modules.base.leveling;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.modules.base.vip.VipBonus;
import dev.davimf.basebot.modules.base.vip.VipBonusSource;
import dev.davimf.basebot.util.EmbedColor;
import dev.davimf.basebot.util.Emojis;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/** Núcleo do leveling: concede XP, detecta level-up, aplica cargos (1 chamada) e notifica. */
public final class LevelingService {

    private final BotContext ctx;
    private final UserLevelRepository users;
    private final LevelRewardRepository rewards;
    private final VipBonusSource vip;

    public LevelingService(BotContext ctx, VipBonusSource vip) {
        this.ctx = ctx;
        this.users = new UserLevelRepository(ctx.database().sqlite());
        this.rewards = new LevelRewardRepository(ctx.database().postgres());
        this.vip = vip;
    }

    public UserLevelRepository users() { return users; }
    public LevelRewardRepository rewards() { return rewards; }

    /** Concede 15–25 XP de mensagem (+ bônus VIP), com o canal atual como contexto de notificação. */
    public void awardMessage(Guild guild, Member member, MessageChannel current) {
        long base = ThreadLocalRandom.current().nextInt(15, 26);
        long amount = VipBonus.scale(base, vip.bonusFor(guild.getId(), member.getId()).xpPct());
        award(guild, member, amount, current);
    }

    /** Soma XP e, se houve level-up, aplica cargos (1 chamada) e notifica. */
    public void award(Guild guild, Member member, long amount, MessageChannel current) {
        if (amount <= 0) {
            return;
        }
        long novo = users.addXp(guild.getId(), member.getId(), amount);
        int antes = LevelFormula.levelForXp(novo - amount);
        int agora = LevelFormula.levelForXp(novo);
        if (agora > antes) {
            onLevelUp(guild, member, antes, agora, current);
        }
    }

    /** Efeitos de level-up quando o XP já foi persistido (voz): cargos + notificação, sem re-somar XP. */
    public void applyVoiceLevelUp(Guild guild, Member member, long oldXp, long newXp) {
        int antes = LevelFormula.levelForXp(oldXp);
        int agora = LevelFormula.levelForXp(newXp);
        if (agora > antes) {
            onLevelUp(guild, member, antes, agora, null);
        }
    }

    public RankData rank(String guildId, String userId) {
        long xp = users.xp(guildId, userId);
        LevelFormula.Progress p = LevelFormula.progress(xp);
        return new RankData(p.level(), xp, p.into(), p.needed(), users.rank(guildId, userId));
    }

    private void onLevelUp(Guild guild, Member member, int antes, int agora, MessageChannel current) {
        Map<Integer, String> map = rewards.all(guild.getId());
        List<String> roleIds = LevelRewards.rolesForCrossedLevels(map, antes, agora);
        List<Role> toAdd = new ArrayList<>();
        for (String id : roleIds) {
            Role r = guild.getRoleById(id);
            if (r != null && guild.getSelfMember().canInteract(r) && !member.getRoles().contains(r)) {
                toAdd.add(r);
            }
        }
        if (!toAdd.isEmpty()) {
            guild.modifyMemberRoles(member, toAdd, List.of()).reason("Recompensa de nível " + agora)
                    .queue(ok -> {}, err -> {});
        }
        notify(guild, member, agora, toAdd, current);
    }

    private void notify(Guild guild, Member member, int level, List<Role> gained, MessageChannel current) {
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(guild.getId());
        String mode = LevelingConfig.notifyMode(cfg);
        if ("off".equals(mode)) {
            return;
        }
        int accent = EmbedColor.resolve(cfg);
        String text = Emojis.of(Emojis.TROPHY, "🎉") + " " + member.getAsMention()
                + " subiu para o **nível " + level + "**!"
                + (gained.isEmpty() ? "" : "\n" + Emojis.of(Emojis.ROLES, "🏷️") + " Novo cargo: "
                        + gained.stream().map(Role::getAsMention).reduce((a, b) -> a + " " + b).orElse(""));
        var panel = Panels.container(accent, Panels.text(text));
        switch (mode) {
            case "dm" -> member.getUser().openPrivateChannel().queue(
                    pc -> pc.sendMessageComponents(panel).useComponentsV2().queue(ok -> {}, err -> {}),
                    err -> { /* CANNOT_SEND_TO_USER: engole */ });
            case "channel" -> {
                String chId = LevelingConfig.notifyChannelId(cfg);
                var ch = chId == null ? null : guild.getTextChannelById(chId);
                if (ch != null) {
                    ch.sendMessageComponents(panel).useComponentsV2()
                            .setAllowedMentions(List.of(Message.MentionType.USER))
                            .queue(ok -> {}, err -> {});
                }
            }
            default -> { // "current"
                if (current != null) {
                    current.sendMessageComponents(panel).useComponentsV2()
                            .setAllowedMentions(List.of(Message.MentionType.USER))
                            .queue(ok -> {}, err -> {});
                } else { // level-up de voz sem canal atual → DM
                    member.getUser().openPrivateChannel().queue(
                            pc -> pc.sendMessageComponents(panel).useComponentsV2().queue(ok -> {}, err -> {}),
                            err -> { });
                }
            }
        }
    }
}
