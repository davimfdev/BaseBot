package dev.davimf.basebot.util;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.database.model.GuildConfig;

import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.mediagallery.MediaGallery;
import net.dv8tion.jda.api.components.mediagallery.MediaGalleryItem;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.thumbnail.Thumbnail;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Posts a Components V2 log entry to a per-type log channel configured in
 * {@code /setup → Logs} (BOTSPECS §General Logging). Mentions are suppressed so log
 * entries never ping; a missing/invalid channel is silently skipped.
 *
 * <p>Layout: a leading Markdown heading ({@code # …}) becomes the panel title. A line that is
 * exactly {@code ---} becomes a visible divider, splitting the body into "field"-like groups.
 * Every entry ends with a small <b>footer timestamp</b> ({@code -# <t:…:f>}). The rich overload
 * renders a {@code thumbnail} (top-right, attached to the first block) and one or more full-width
 * {@code images} (e.g. preserved attachments) as a {@code MediaGallery}.
 */
public final class ChannelLog {

    private ChannelLog() {}

    public static void post(BotContext ctx, String guildId, String logKey, String markdown) {
        post(ctx, guildId, logKey, markdown, null, List.of());
    }

    public static void post(BotContext ctx, String guildId, String logKey, String markdown,
                            String thumbnailUrl, String imageUrl) {
        post(ctx, guildId, logKey, markdown, thumbnailUrl,
                imageUrl == null || imageUrl.isBlank() ? List.of() : List.of(imageUrl));
    }

    public static void post(BotContext ctx, String guildId, String logKey, String markdown,
                            String thumbnailUrl, List<String> imageUrls) {
        if (ctx.jda() == null) {
            return;
        }
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(guildId);
        String channelId = cfg.channel(logKey);
        if (channelId == null) {
            return;
        }
        TextChannel channel = ctx.jda().getTextChannelById(channelId);
        if (channel == null) {
            return;
        }

        List<ContainerChildComponent> kids = new ArrayList<>();
        List<String> groups = splitGroups(markdown);

        String first = groups.get(0);
        String[] tb = splitHeading(first);
        String title = tb[0];
        String body0 = tb[1];

        if (thumbnailUrl != null && !thumbnailUrl.isBlank()) {
            Thumbnail thumb = Thumbnail.fromUrl(thumbnailUrl);
            if (title != null && !body0.isEmpty()) {
                kids.add(Section.of(thumb, Panels.text(title), Panels.text(body0)));
            } else {
                kids.add(Section.of(thumb, Panels.text(title != null ? title : first)));
            }
        } else if (title != null) {
            kids.add(Panels.text(title));
            if (!body0.isEmpty()) {
                kids.add(Panels.divider());
                kids.add(Panels.text(body0));
            }
        } else {
            kids.add(Panels.text(first));
        }

        for (int i = 1; i < groups.size(); i++) {
            kids.add(Panels.divider());
            kids.add(Panels.text(groups.get(i)));
        }

        if (imageUrls != null && !imageUrls.isEmpty()) {
            List<MediaGalleryItem> items = new ArrayList<>();
            for (String url : imageUrls) {
                if (url != null && !url.isBlank()) {
                    items.add(MediaGalleryItem.fromUrl(url));
                }
            }
            if (!items.isEmpty()) {
                kids.add(MediaGallery.of(items));
            }
        }

        // Footer: a separated timestamp on every log entry.
        kids.add(Panels.divider());
        kids.add(Panels.text("-# <t:" + Instant.now().getEpochSecond() + ":f>"));

        channel.sendMessageComponents(Panels.container(EmbedColor.resolve(cfg),
                        kids.toArray(new ContainerChildComponent[0])))
                .useComponentsV2()
                .setAllowedMentions(List.of())
                .queue(ok -> {}, err -> {});
    }

    /** Splits the markdown on lines that are exactly {@code ---} into "field"-like groups. */
    private static List<String> splitGroups(String markdown) {
        List<String> groups = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String line : markdown.split("\n", -1)) {
            if (line.strip().equals("---")) {
                groups.add(cur.toString().strip());
                cur.setLength(0);
            } else {
                cur.append(line).append('\n');
            }
        }
        groups.add(cur.toString().strip());
        groups.removeIf(String::isEmpty);
        return groups.isEmpty() ? List.of(markdown) : groups;
    }

    /** Returns {@code [title, body]}; {@code title} is null when there is no leading heading. */
    private static String[] splitHeading(String segment) {
        int nl = segment.indexOf('\n');
        if (segment.startsWith("#") && nl > 0) {
            return new String[]{segment.substring(0, nl).strip(), segment.substring(nl + 1).strip()};
        }
        if (segment.startsWith("#")) {
            return new String[]{segment.strip(), ""};
        }
        return new String[]{null, segment};
    }
}
