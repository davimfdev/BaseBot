// [OUTLINE START]
// Package: dev.davimf.basebot.util
// 
// Class: ImageMedia
// 
// Constructors:
//   - `Constructor` : `private ImageMedia()`
// 
// Methods:
//   - `Method` : `private static final Set<String> ALLOWED_CONTENT_TYPES = Set. of(, , , ,)`
//   - `Method` : `private static final HttpClient HTTP = HttpClient. newBuilder()`
//   - `Method` : `public static Image fromBytes(byte[] bytes, String fileName)`
//   - `Method` : `public static boolean isAllowedContentType(String contentType)`
//   - `Method` : `public static String fileName(String sourceUrl, String contentType)`
//   - `Method` : `private static String extensionFor(String contentType)`
//   - `Method` : `public static Image fromAttachment(Message.Attachment attachment)`
//   - `Method` : `public static Image fromUrl(String url)`
//   - `Method` : `private static byte[] readCapped(InputStream in)`
// 
// Fields:
//   - `Field` : `public static final long MAX_BYTES`
// 
// Class: Image
// 
// Constructors:
//   - `Constructor` : `private Image(byte[] bytes, String fileName)`
// 
// Methods:
//   - `Method` : `public byte[] bytes()`
//   - `Method` : `public String fileName()`
// 
// Fields:
//   - `Field` : `private byte[] bytes`
//   - `Field` : `private final String fileName`
// [OUTLINE END]



package dev.davimf.basebot.util;

import net.dv8tion.jda.api.entities.Message;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

/**
 * Fetches images the bot needs from either a URL or a Discord attachment, holding the
 * bytes only transiently. Every image input must be <b>downloaded and re-uploaded</b> by
 * the bot (via {@code FileUpload}/{@code Icon}) so Discord re-hosts it permanently rather
 * than the bot echoing an external URL that could rot. Callers must {@link Image#erase()}
 * the bytes once the upload has been sent so images don't linger in memory.
 *
 * <p>The download methods block, so call them off the JDA event thread (e.g. on
 * {@code ctx.scheduler().executor()}) after deferring the reply.
 */
public final class ImageMedia {

    /** Hard cap on downloaded image size (Discord's own limits are lower, but this bounds memory). */
    public static final long MAX_BYTES = 8L * 1024 * 1024;

    private static final Set<String> ALLOWED_CONTENT_TYPES =
            Set.of("image/png", "image/jpeg", "image/jpg", "image/gif", "image/webp");

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private ImageMedia() {}

    /** Raw image bytes held transiently; {@link #erase()} zeroes and drops them after use. */
    public static final class Image {
        private byte[] bytes;
        private final String fileName;

        private Image(byte[] bytes, String fileName) {
            this.bytes = bytes;
            this.fileName = fileName;
        }

        public byte[] bytes() {
            return bytes;
        }

        public String fileName() {
            return fileName;
        }

        /** Zeroes the raw image bytes so the image does not linger in the bot's memory. */
        public void erase() {
            if (bytes != null) {
                Arrays.fill(bytes, (byte) 0);
                bytes = null;
            }
        }
    }

    /** Test/utility factory — wraps already-resolved bytes without any I/O. */
    public static Image fromBytes(byte[] bytes, String fileName) {
        return new Image(bytes, fileName);
    }

    public static boolean isAllowedContentType(String contentType) {
        if (contentType == null) {
            return false;
        }
        String ct = contentType.toLowerCase(Locale.ROOT);
        int semicolon = ct.indexOf(';');
        if (semicolon >= 0) {
            ct = ct.substring(0, semicolon);
        }
        return ALLOWED_CONTENT_TYPES.contains(ct.trim());
    }

    /** A safe, extension-correct upload filename derived from the content type. */
    public static String fileName(String sourceUrl, String contentType) {
        return "image" + extensionFor(contentType);
    }

    private static String extensionFor(String contentType) {
        String ct = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (ct.contains("png")) return ".png";
        if (ct.contains("gif")) return ".gif";
        if (ct.contains("webp")) return ".webp";
        if (ct.contains("jpeg") || ct.contains("jpg")) return ".jpg";
        return ".png";
    }

    /** Downloads a Discord attachment's bytes (must be an image). Blocks. */
    public static Image fromAttachment(Message.Attachment attachment) throws IOException {
        if (!attachment.isImage()) {
            throw new IOException("O anexo não é uma imagem.");
        }
        try (InputStream in = attachment.getProxy().download().join()) {
            byte[] bytes = readCapped(in);
            return new Image(bytes, fileName(attachment.getFileName(), attachment.getContentType()));
        }
    }

    /** Downloads an image from a URL, validating the content type. Blocks. */
    public static Image fromUrl(String url) throws IOException {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url.trim()))
                    .timeout(Duration.ofSeconds(20))
                    .GET()
                    .build();
            HttpResponse<InputStream> res = HTTP.send(req, HttpResponse.BodyHandlers.ofInputStream());
            if (res.statusCode() != 200) {
                res.body().close();
                throw new IOException("HTTP " + res.statusCode() + " ao baixar a imagem.");
            }
            String contentType = res.headers().firstValue("content-type").orElse(null);
            if (!isAllowedContentType(contentType)) {
                res.body().close();
                throw new IOException("A URL não aponta para uma imagem suportada (PNG/JPG/GIF/WEBP).");
            }
            try (InputStream in = res.body()) {
                byte[] bytes = readCapped(in);
                return new Image(bytes, fileName(url, contentType));
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Falha ao baixar a imagem: " + e.getMessage(), e);
        }
    }

    private static byte[] readCapped(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = in.read(buffer)) != -1) {
            total += read;
            if (total > MAX_BYTES) {
                throw new IOException("Imagem excede o tamanho máximo de 8 MB.");
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }
}
