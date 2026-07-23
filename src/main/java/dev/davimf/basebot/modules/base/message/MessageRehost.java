package dev.davimf.basebot.modules.base.message;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.davimf.basebot.util.ImageMedia;
import net.dv8tion.jda.api.utils.FileUpload;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Re-host de imagens do /mensagem: coleta as sources distintas do estado e as baixa via
 * {@link ImageMedia} para anexar ao envio normal (Discord re-hospeda permanentemente). O render
 * troca cada source pela ref {@code attachment://<nome>}; sources que falharem ficam de fora do
 * mapa e o render usa a URL crua como fallback.
 */
public final class MessageRehost {

    /** Sources baixadas e prontas para anexar. */
    public record Prepared(Map<String, String> refBySource, List<FileUpload> files,
                           List<ImageMedia.Image> images) {
        /** Zera os bytes de todas as imagens; chamar após o envio/edição resolver. */
        public void eraseAll() {
            images.forEach(ImageMedia.Image::erase);
        }
    }

    private MessageRehost() {}

    /** Sources de imagem distintas (ordem estável). Puro — sem I/O. */
    public static List<String> collectSources(ObjectNode state) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (MessageState.isContainer(state)) {
            for (JsonNode n : MessageState.blocks(state)) {
                ObjectNode b = (ObjectNode) n;
                String type = MessageState.str(b, "type");
                if (MessageState.BLOCK_IMAGE.equals(type)) {
                    addIf(out, MessageState.str(b, "src"));
                } else if (MessageState.BLOCK_TEXT.equals(type)) {
                    addIf(out, MessageState.str(b, "thumbnail"));
                }
            }
        } else {
            ObjectNode c = MessageState.classic(state);
            addIf(out, MessageState.str(c, "image"));
            addIf(out, MessageState.str(c, "thumbnail"));
        }
        return new ArrayList<>(out);
    }

    /** Baixa cada source (bloqueante). Nomes únicos por envio; falha → fora do mapa. */
    public static Prepared download(List<String> sources) {
        Map<String, String> refBySource = new LinkedHashMap<>();
        List<FileUpload> files = new ArrayList<>();
        List<ImageMedia.Image> images = new ArrayList<>();
        int i = 1;
        for (String src : sources) {
            try {
                ImageMedia.Image img = ImageMedia.fromUrl(src);
                String ext = extensionOf(img.fileName());
                String name = "image-" + i + ext;
                files.add(FileUpload.fromData(img.bytes(), name));
                images.add(img);
                refBySource.put(src, "attachment://" + name);
                i++;
            } catch (Exception ignored) {
                // fallback: sem ref -> render usa a URL crua
            }
        }
        return new Prepared(refBySource, files, images);
    }

    private static void addIf(LinkedHashSet<String> out, String s) {
        if (s != null && !s.isBlank()) {
            out.add(s);
        }
    }

    private static String extensionOf(String fileName) {
        int dot = fileName == null ? -1 : fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot) : ".png";
    }
}
