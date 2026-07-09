package dev.davimf.basebot.modules.base.leveling;

import dev.davimf.basebot.database.model.GuildConfig;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.attribute.ICategorizableChannel;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;

/**
 * Decide se um canal de voz conta tempo. Precedência: canal &gt; categoria &gt; padrão.
 * Dentro de cada nível a exclusão é avaliada primeiro, então um ID presente nas duas listas do
 * mesmo nível não conta — configuração contraditória nunca abre acesso.
 *
 * <p>O padrão é "só canais públicos": {@code @everyone} com {@code VIEW_CHANNEL} e
 * {@code VOICE_CONNECT} <b>efetivos</b> (herança de categoria + overrides), não o override local.
 */
public final class VoiceScope {

    private VoiceScope() {}

    /** Núcleo puro, sem JDA — testável sem mocks. {@code categoryId} pode ser null. */
    public static boolean counts(String channelId, String categoryId, boolean publicChannel, GuildConfig cfg) {
        if (VoiceTimeConfig.excludeChannels(cfg).contains(channelId)) {
            return false;
        }
        if (VoiceTimeConfig.includeChannels(cfg).contains(channelId)) {
            return true;
        }
        if (categoryId != null) {
            if (VoiceTimeConfig.excludeCategories(cfg).contains(categoryId)) {
                return false;
            }
            if (VoiceTimeConfig.includeCategories(cfg).contains(categoryId)) {
                return true;
            }
        }
        return publicChannel;
    }

    /** Adaptador: resolve categoria e "público" a partir da JDA e delega ao núcleo. */
    public static boolean inScope(AudioChannel channel, GuildConfig cfg) {
        Category category = channel instanceof ICategorizableChannel c ? c.getParentCategory() : null;
        // hasPermission(GuildChannel, ...) em IPermissionHolder já é a permissão EFETIVA.
        // Não usar getPermissionOverride (só override local) nem PermissionUtil (API interna).
        boolean isPublic = channel.getGuild().getPublicRole()
                .hasPermission(channel, Permission.VIEW_CHANNEL, Permission.VOICE_CONNECT);
        return counts(channel.getId(), category == null ? null : category.getId(), isPublic, cfg);
    }
}
