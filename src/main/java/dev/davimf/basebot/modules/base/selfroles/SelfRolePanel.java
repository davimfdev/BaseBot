package dev.davimf.basebot.modules.base.selfroles;

import java.util.List;

/** Um painel de auto-atribuição e suas opções (cargo + rótulo + emoji). */
public record SelfRolePanel(String id, String guildId, String title, String description,
                            String style, boolean unique, String channelId, String messageId,
                            List<Option> options) {

    public static final String STYLE_BUTTONS = "buttons";
    public static final String STYLE_MENU = "menu";

    public record Option(String roleId, String label, String emoji, int position) {}

    public List<String> roleIds() {
        return options.stream().map(Option::roleId).toList();
    }
}
