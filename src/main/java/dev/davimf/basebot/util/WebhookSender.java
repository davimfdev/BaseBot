package dev.davimf.basebot.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Sends and edits messages through a Discord webhook URL (BOTSPECS Module 1 — /embed,
 * /editembed). JDA core can't post as a webhook with a per-message username/avatar, so
 * this talks the webhook REST API directly. Editing omits {@code components}, so any
 * existing select menus on the target message are preserved.
 */
public final class WebhookSender {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    private WebhookSender() {}

    /** Builds an embed JSON object; null/blank title or description are omitted. */
    public static ObjectNode embed(String title, String description, int color) {
        ObjectNode embed = JSON.createObjectNode();
        if (title != null && !title.isBlank()) {
            embed.put("title", title);
        }
        if (description != null && !description.isBlank()) {
            embed.put("description", description);
        }
        embed.put("color", color & 0xFFFFFF);
        return embed;
    }

    /**
     * Posts an embed via the webhook, impersonating {@code username}/{@code avatarUrl}
     * when given. Returns the created message id (uses {@code ?wait=true}).
     */
    public static String send(String webhookUrl, String username, String avatarUrl, ObjectNode embed) {
        ObjectNode body = JSON.createObjectNode();
        if (username != null && !username.isBlank()) {
            body.put("username", username);
        }
        if (avatarUrl != null && !avatarUrl.isBlank()) {
            body.put("avatar_url", avatarUrl);
        }
        body.putArray("embeds").add(embed);
        try {
            HttpResponse<String> res = HTTP.send(
                    request(webhookUrl + "?wait=true").POST(json(body)).build(),
                    HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() / 100 != 2) {
                throw new WebhookException("webhook send HTTP " + res.statusCode() + ": " + res.body());
            }
            JsonNode node = JSON.readTree(res.body());
            return node.path("id").asText(null);
        } catch (WebhookException e) {
            throw e;
        } catch (Exception e) {
            throw new WebhookException("failed to send webhook message", e);
        }
    }

    /** Edits a webhook message's embed, leaving its components (select menus) untouched. */
    public static void edit(String webhookUrl, String messageId, ObjectNode embed) {
        ObjectNode body = JSON.createObjectNode();
        body.putArray("embeds").add(embed);
        try {
            HttpResponse<String> res = HTTP.send(
                    request(webhookUrl + "/messages/" + messageId)
                            .method("PATCH", json(body)).build(),
                    HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() / 100 != 2) {
                throw new WebhookException("webhook edit HTTP " + res.statusCode() + ": " + res.body());
            }
        } catch (WebhookException e) {
            throw e;
        } catch (Exception e) {
            throw new WebhookException("failed to edit webhook message", e);
        }
    }

    private static HttpRequest.Builder request(String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json");
    }

    private static HttpRequest.BodyPublisher json(ObjectNode body) {
        try {
            return HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body));
        } catch (Exception e) {
            throw new WebhookException("failed to serialize webhook body", e);
        }
    }

    /** Thrown when a webhook send/edit fails. */
    public static final class WebhookException extends RuntimeException {
        public WebhookException(String message) {
            super(message);
        }

        public WebhookException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
