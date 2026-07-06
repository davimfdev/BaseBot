package dev.davimf.basebot.modules.base.setup;

import dev.davimf.basebot.util.Emojis;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.modules.base.setup.SetupLogTypes.LogType;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Setup rápido das logs: cria as categorias {@code logs {modulo}} e os canais {@code " + Emojis.of(Emojis.FOLDER_OPEN, "📂") + "・{log}}
 *  para os módulos ativos, restringe a visibilidade ao @everyone e grava cada canal na sua
 *  chave. Idempotente: pula tipos já configurados com canal vivo e reusa categorias existentes. */
public final class QuickLogSetup {

    public record Summary(int created, int skipped, int categories) {}

    private QuickLogSetup() {}

    public static String channelName(String logKey) {
        return "" + Emojis.of(Emojis.FOLDER_OPEN, "📂") + "・" + logKey.replaceFirst("^log-", "");
    }

    public static String categoryName(String moduleLabel) {
        return "logs " + moduleLabel.toLowerCase(Locale.ROOT);
    }

    public static String logModuleFor(String botModuleName) {
        return "Sales".equals(botModuleName) ? "Vendas" : botModuleName;
    }

    public static List<LogType> typesForActive(Set<String> activeBotModuleNames) {
        Set<String> labels = activeBotModuleNames.stream()
                .map(QuickLogSetup::logModuleFor).collect(Collectors.toSet());
        return SetupLogTypes.ALL.stream()
                .filter(t -> labels.contains(t.module()))
                .collect(Collectors.toList());
    }

    /** BLOQUEANTE — chame fora da thread do JDA (ex.: ctx.scheduler().executor()). Usa
     *  {@code .complete()} para criar canais em sequência e gravar as chaves uma a uma. */
    public static Summary run(Guild guild, BotContext ctx) {
        int created = 0;
        int skipped = 0;
        Set<String> categoriesTouched = new HashSet<>();
        for (LogType type : typesForActive(ctx.activeModules())) {
            GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(guild.getId());
            String existing = cfg.channel(type.key());
            if (existing != null && guild.getTextChannelById(existing) != null) {
                skipped++;
                continue; // já configurado e vivo
            }
            String catName = categoryName(type.module());
            Category category = guild.getCategoriesByName(catName, true).stream().findFirst()
                    .orElseGet(() -> guild.createCategory(catName)
                            .addPermissionOverride(guild.getPublicRole(), null,
                                    EnumSet.of(Permission.VIEW_CHANNEL))
                            .complete());
            categoriesTouched.add(category.getId());
            TextChannel channel = guild.createTextChannel(channelName(type.key()), category).complete();
            GuildConfig updated = GuildConfigEdits.withChannel(cfg, type.key(), channel.getId());
            ctx.database().guildConfig().save(updated);
            created++;
        }
        return new Summary(created, skipped, categoriesTouched.size());
    }
}
