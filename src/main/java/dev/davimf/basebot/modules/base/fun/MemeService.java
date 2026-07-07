package dev.davimf.basebot.modules.base.fun;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.util.EmbedColor;
import dev.davimf.basebot.util.ImageMedia;
import net.dv8tion.jda.api.components.mediagallery.MediaGallery;
import net.dv8tion.jda.api.components.mediagallery.MediaGalleryItem;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.utils.FileUpload;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Pipeline assíncrono dos memes: baixa avatar → compõe → MediaGallery → erase. */
public final class MemeService {

    private static final Logger log = LoggerFactory.getLogger(MemeService.class);
    private static final long MAX_OUT = 8L * 1024 * 1024;
    private final BotContext ctx;
    private final Map<String, BufferedImage> templateCache = new ConcurrentHashMap<>();

    public MemeService(BotContext ctx) {
        this.ctx = ctx;
    }

    private BufferedImage template(String resourcePath) {
        return templateCache.computeIfAbsent(resourcePath, p -> {
            try (InputStream in = MemeService.class.getResourceAsStream(p)) {
                if (in == null) {
                    throw new IllegalStateException("template ausente: " + p);
                }
                return ImageIO.read(in);
            } catch (Exception e) {
                throw new IllegalStateException("falha ao carregar " + p, e);
            }
        });
    }

    public void generate(SlashCommandInteractionEvent event, MemeTemplate spec, Member alvo,
                         Map<String, String> textos, String caption) {
        event.deferReply(false).queue(hook -> ctx.scheduler().executor().execute(() -> {
            ImageMedia.Image av = null;
            try {
                BufferedImage tpl = template(spec.resourcePath());
                String url = alvo.getEffectiveAvatarUrl().replace(".gif", ".png") + "?size=256";
                av = ImageMedia.fromUrl(url);
                BufferedImage avatar = ImageIO.read(new ByteArrayInputStream(av.bytes()));
                if (avatar == null) {
                    throw new IllegalStateException("avatar ilegível");
                }
                byte[] png = MemeRender.compose(tpl, avatar, spec, textos);
                if (png.length > MAX_OUT) {
                    hook.editOriginal("A imagem ficou grande demais. Tenta de novo.").queue();
                    return;
                }
                int accent = EmbedColor.resolve(ctx.database().guildConfig()
                        .findOrEmpty(event.getGuild() == null ? "0" : event.getGuild().getId()));
                String texto = caption + " " + alvo.getAsMention();
                hook.editOriginalComponents(Panels.container(accent, Panels.text(texto),
                                MediaGallery.of(MediaGalleryItem.fromFile(FileUpload.fromData(png, "meme.png")))))
                        .useComponentsV2().queue(ok -> { }, err -> {
                            log.warn("falha ao enviar o meme", err);
                            hook.editOriginal("Não consegui gerar a imagem 😕").queue(null, x -> { });
                        });
            } catch (Exception e) {
                hook.editOriginal("Não consegui gerar a imagem 😕").queue(null, x -> { });
            } finally {
                if (av != null) {
                    av.erase();
                }
            }
        }));
    }
}
