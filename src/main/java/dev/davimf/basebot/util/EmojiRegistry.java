package dev.davimf.basebot.util;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Icon;
import net.dv8tion.jda.api.entities.emoji.ApplicationEmoji;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Syncs the bot's application emojis from bundled PNGs on startup, then fills {@link Emojis}.
 *
 * <p>Application emojis live on the <i>app</i> (up to 2000), work in every guild the bot is in,
 * and need no per-guild upload. The set is declared once in {@code resources/emojis/manifest.txt}
 * (one name per line); each name {@code X} is created from {@code resources/emojis/X.png} if it
 * isn't already on the app. Idempotent: existing emojis are skipped, missing PNGs are skipped
 * (so a partially-finished set works). Resolve emojis by name via {@link Emojis}, never by id.
 */
public final class EmojiRegistry {

    private static final Logger log = LoggerFactory.getLogger(EmojiRegistry.class);
    private static final String DIR = "/emojis/";

    private EmojiRegistry() {}

    /** Retrieves existing application emojis, creates any missing ones from bundled PNGs, and
     *  publishes the result into {@link Emojis}. Call once on ready; runs off the gateway thread
     *  via JDA's async queue. */
    public static void sync(JDA jda) {
        List<String> names = readManifest();
        jda.retrieveApplicationEmojis().queue(existing -> {
            Map<String, ApplicationEmoji> byName = new HashMap<>();
            for (ApplicationEmoji e : existing) {
                byName.put(e.getName(), e);
            }
            Emojis.load(byName);
            int missing = 0;
            for (String name : names) {
                if (byName.containsKey(name)) {
                    continue;
                }
                byte[] png = readResource(DIR + name + ".png");
                if (png == null) {
                    continue; // not drawn yet — skip, will be created on a later boot
                }
                missing++;
                jda.createApplicationEmoji(name, Icon.from(png, Icon.IconType.PNG)).queue(
                        created -> {
                            Emojis.put(name, created);
                            log.info("Created application emoji :{}:", name);
                        },
                        err -> log.warn("Failed to create application emoji :{}:", name, err));
            }
            log.info("Application emojis: {} present, {} being created.", byName.size(), missing);
        }, err -> log.warn("Failed to retrieve application emojis", err));
    }

    private static List<String> readManifest() {
        List<String> names = new ArrayList<>();
        byte[] raw = readResource(DIR + "manifest.txt");
        if (raw == null) {
            return names;
        }
        for (String line : new String(raw, StandardCharsets.UTF_8).split("\\R")) {
            String name = line.strip();
            if (!name.isEmpty() && !name.startsWith("#")) {
                names.add(name);
            }
        }
        return names;
    }

    private static byte[] readResource(String resource) {
        try (InputStream in = EmojiRegistry.class.getResourceAsStream(resource)) {
            return in == null ? null : in.readAllBytes();
        } catch (IOException e) {
            log.warn("Failed to read emoji resource {}", resource, e);
            return null;
        }
    }
}
