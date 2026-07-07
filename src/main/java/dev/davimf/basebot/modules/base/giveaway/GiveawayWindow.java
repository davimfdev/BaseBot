package dev.davimf.basebot.modules.base.giveaway;

/** Parse da janela de horário "HH-HH". Puro. */
public final class GiveawayWindow {
    private GiveawayWindow() {}

    public static int[] parse(String s) {
        if (s == null) {
            return null;
        }
        String[] parts = s.trim().split("-");
        if (parts.length != 2) {
            return null;
        }
        try {
            int a = Integer.parseInt(parts[0].trim());
            int b = Integer.parseInt(parts[1].trim());
            if (a < 0 || b > 24 || a >= b) {
                return null;
            }
            return new int[]{a, b};
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
