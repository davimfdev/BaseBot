package dev.davimf.basebot.modules.base.moderation;

/**
 * Pure role-hierarchy rules for moderation (BOTSPECS Module 1). Positions are Discord
 * role positions (higher = more authority); the guild owner bypasses the position check
 * for the *actor* side, but the bot must always physically outrank the target to act.
 */
public final class RoleHierarchy {

    private RoleHierarchy() {}

    /** True if the actor may act on the target by position (owner bypasses position). */
    public static boolean actorOutranks(int actorTop, int targetTop, boolean actorIsOwner) {
        return actorIsOwner || actorTop > targetTop;
    }

    /** Full gate: the actor must outrank the target AND the bot must outrank the target. */
    public static boolean canModerate(int actorTop, int targetTop, boolean actorIsOwner, int botTop) {
        return actorOutranks(actorTop, targetTop, actorIsOwner) && botTop > targetTop;
    }
}
