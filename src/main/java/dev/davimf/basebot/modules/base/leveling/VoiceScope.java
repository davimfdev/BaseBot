package dev.davimf.basebot.modules.base.leveling;

import dev.davimf.basebot.database.model.GuildConfig;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.attribute.ICategorizableChannel;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;

/**
 * Decide se um canal de voz conta tempo. Precedência: canal &gt; categoria &gt; padrão.
 * Dentro de cada nível a exclusão é avaliada primeiro, então um ID presente nas duas listas do
 * mesmo nível não conta — configuração contraditória nunca abre acesso.
 *
 * <p>O padrão é "aberto aos membros": {@code @everyone} <b>ou</b> o cargo membro
 * ({@code guild_config.roles["membro"]}) com {@code VIEW_CHANNEL} e {@code VOICE_CONNECT}
 * <b>efetivos</b> (herança de categoria + overrides), não o override local.
 *
 * <p>O cargo membro entra porque o lockdown de verificação nega {@code @everyone} nos canais e
 * concede ao cargo membro. Sem isto, um servidor com lockdown não contaria tempo em canal nenhum.
 */
public final class VoiceScope {

    /** Mesma chave usada por {@code SecurityComponentHandler} para o cargo de membro verificado. */
    private static final String ROLE_MEMBER = "membro";

    private VoiceScope() {}

    /** Núcleo puro, sem JDA — testável sem mocks. {@code categoryId} pode ser null. */
    public static boolean counts(String channelId, String categoryId, boolean openByDefault, GuildConfig cfg) {
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
        return openByDefault;
    }

    /** Adaptador: resolve categoria e "aberto aos membros" a partir da JDA e delega ao núcleo. */
    public static boolean inScope(AudioChannel channel, GuildConfig cfg) {
        Category category = channel instanceof ICategorizableChannel c ? c.getParentCategory() : null;
        return counts(channel.getId(), category == null ? null : category.getId(),
                openToMembers(channel, cfg), cfg);
    }

    /**
     * {@code @everyone} ou o cargo membro pode ver E entrar no canal.
     *
     * <p>{@code hasPermission(GuildChannel, ...)} de {@code IPermissionHolder} já é a permissão
     * EFETIVA. Não usar {@code getPermissionOverride} (só override local) nem {@code PermissionUtil}
     * (API interna na JDA 6.4.2).
     */
    private static boolean openToMembers(AudioChannel channel, GuildConfig cfg) {
        Guild guild = channel.getGuild();
        if (guild.getPublicRole().hasPermission(channel, Permission.VIEW_CHANNEL, Permission.VOICE_CONNECT)) {
            return true;
        }
        String memberRoleId = cfg.role(ROLE_MEMBER);
        if (memberRoleId == null || memberRoleId.isBlank()) {
            return false;
        }
        Role member = guild.getRoleById(memberRoleId);
        return member != null
                && member.hasPermission(channel, Permission.VIEW_CHANNEL, Permission.VOICE_CONNECT);
    }
}
