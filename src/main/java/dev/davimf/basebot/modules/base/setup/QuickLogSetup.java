package dev.davimf.basebot.modules.base.setup;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.modules.base.setup.SetupLogTypes.LogType;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Setup rápido das logs: cria as categorias {@code logs {modulo}} e os canais {@code 📂・{log}}
 *  para os módulos ativos, restringe a visibilidade ao @everyone e grava cada canal na sua
 *  chave. Idempotente: pula tipos já configurados com canal vivo e reusa categorias existentes.
 *
 *  <p>O nome do canal usa o emoji Unicode {@code 📂} de propósito: o Discord não aceita emojis
 *  custom (application emojis) em nomes de canal, então nada de {@code Emojis.of(...)} aqui. */
public final class QuickLogSetup {

    private static final Logger log = LoggerFactory.getLogger(QuickLogSetup.class);

    public record Summary(int created, int skipped, int categories) {}
    public record ChannelRef(String id, String name) {}
    public record Reconciliation(String logChannelId, boolean updateConfig,
                                 List<String> duplicateIds) {}

    private QuickLogSetup() {}

    public static String channelName(String logKey) {
        return "📂・" + logKey.replaceFirst("^log-", "");
    }

    public static String categoryName(String moduleLabel) {
        return "logs " + moduleLabel.toLowerCase(Locale.ROOT);
    }

    static boolean hasStandardName(String logKey, String channelName) {
        return channelName(logKey).equalsIgnoreCase(channelName);
    }

    static Reconciliation reconcile(String logKey, String configuredId,
                                    List<ChannelRef> channels) {
        ChannelRef configured = channels.stream()
                .filter(channel -> channel.id().equals(configuredId))
                .findFirst().orElse(null);
        List<ChannelRef> standard = channels.stream()
                .filter(channel -> hasStandardName(logKey, channel.name()))
                .toList();

        if (configured != null) {
            String standardKeeperId = hasStandardName(logKey, configured.name())
                    ? configured.id()
                    : standard.stream().findFirst().map(ChannelRef::id).orElse(null);
            List<String> duplicates = standard.stream()
                    .map(ChannelRef::id)
                    .filter(id -> !id.equals(standardKeeperId))
                    .toList();
            return new Reconciliation(configured.id(), false, duplicates);
        }
        if (!standard.isEmpty()) {
            ChannelRef keeper = standard.getFirst();
            return new Reconciliation(keeper.id(), true, standard.stream().skip(1)
                    .map(ChannelRef::id).toList());
        }
        return new Reconciliation(null, false, List.of());
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
        log.info("Quick log setup started for guild {} ({})", guild.getName(), guild.getId());
        int created = 0;
        int skipped = 0;
        Set<String> categoriesTouched = new HashSet<>();
        Set<String> duplicateIds = new LinkedHashSet<>();
        GuildConfig working = ctx.database().guildConfig().findOrEmpty(guild.getId());
        for (LogType type : typesForActive(ctx.activeModules())) {
            List<ChannelRef> liveChannels = guild.getTextChannels().stream()
                    .map(channel -> new ChannelRef(channel.getId(), channel.getName()))
                    .toList();
            Reconciliation reconciliation = reconcile(
                    type.key(), working.channel(type.key()), liveChannels);
            if (reconciliation.logChannelId() != null) {
                if (reconciliation.updateConfig()) {
                    log.info("Reusing channel {} for log {} in guild {}; queued for batch save",
                            reconciliation.logChannelId(), type.key(), guild.getId());
                    working = GuildConfigEdits.withChannel(
                            working, type.key(), reconciliation.logChannelId());
                } else {
                    log.info("Keeping configured channel {} for log {} in guild {}",
                            reconciliation.logChannelId(), type.key(), guild.getId());
                }
                duplicateIds.addAll(reconciliation.duplicateIds());
                skipped++;
                continue;
            }
            String catName = categoryName(type.module());
            Category category = guild.getCategoriesByName(catName, true).stream().findFirst()
                    .orElseGet(() -> guild.createCategory(catName)
                            .addPermissionOverride(guild.getPublicRole(), null,
                                    EnumSet.of(Permission.VIEW_CHANNEL))
                            .complete());
            categoriesTouched.add(category.getId());
            TextChannel channel = guild.createTextChannel(channelName(type.key()), category).complete();
            log.info("Created channel {} for log {} in guild {}; queued for batch save",
                    channel.getId(), type.key(), guild.getId());
            working = GuildConfigEdits.withChannel(working, type.key(), channel.getId());
            created++;
        }
        log.info("Persisting {} log channel mappings for guild {} in one batch",
                working.channels().size(), guild.getId());
        ctx.database().guildConfig().save(working);
        for (String duplicateId : duplicateIds) {
            TextChannel duplicate = guild.getTextChannelById(duplicateId);
            if (duplicate != null) {
                log.info("Deleting duplicate log channel {} after successful batch save in guild {}",
                        duplicateId, guild.getId());
                duplicate.delete().complete();
            }
        }
        Summary summary = new Summary(created, skipped, categoriesTouched.size());
        log.info("Quick log setup completed for guild {}: {} created, {} reused, {} categories touched",
                guild.getId(), created, skipped, categoriesTouched.size());
        return summary;
    }
}
