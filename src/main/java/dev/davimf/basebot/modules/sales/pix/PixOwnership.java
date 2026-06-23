package dev.davimf.basebot.modules.sales.pix;

/**
 * The Pix confirmation button is restricted strictly to the Pix owner — the member who
 * generated the charge (BOTSPECS Module 3, "Validation").
 */
public final class PixOwnership {

    private PixOwnership() {}

    public static boolean isOwner(String clickerId, String ownerId) {
        return clickerId != null && clickerId.equals(ownerId);
    }
}
