package dev.davimf.basebot.modules.base.welcome;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;

/** Substitui os placeholders de boas-vindas/despedida. Puro/testável. */
public final class WelcomeText {

    private WelcomeText() {}

    public static String render(String template, Member member, Guild guild) {
        if (template == null) {
            return "";
        }
        return template
                .replace("{user}", member.getEffectiveName())
                .replace("{mention}", member.getAsMention())
                .replace("{server}", guild.getName())
                .replace("{count}", String.valueOf(guild.getMemberCount()));
    }
}
