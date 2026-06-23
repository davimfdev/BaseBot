package dev.davimf.basebot.modules.base.moderation;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;

import java.util.List;

/** Adapts live JDA members to the pure {@link RoleHierarchy} rules (BOTSPECS Module 1). */
public final class Moderation {

    private Moderation() {}

    /** Highest role position among the given positions; 0 (== @everyone) when empty. */
    public static int topPosition(List<Integer> rolePositions) {
        return rolePositions.stream().mapToInt(Integer::intValue).max().orElse(0);
    }

    private static int topPosition(Member member) {
        return topPosition(member.getRoles().stream().map(Role::getPosition).toList());
    }

    /** True if {@code actor} and the bot ({@code self}) both outrank {@code target}. */
    public static boolean canModerate(Member actor, Member target, Member self) {
        return RoleHierarchy.canModerate(
                topPosition(actor), topPosition(target), actor.isOwner(), topPosition(self));
    }

    /** True if both the actor and the bot may assign/remove {@code role} (by position). */
    public static boolean canManageRole(Member actor, Role role, Member self) {
        int rolePos = role.getPosition();
        return RoleHierarchy.actorOutranks(topPosition(actor), rolePos, actor.isOwner())
                && self.canInteract(role);
    }
}
