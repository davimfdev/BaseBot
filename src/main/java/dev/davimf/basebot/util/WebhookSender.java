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
 * Posts messages through a Discord webhook URL (BOTSPECS Module 1 — /mensagem). JDA core
 * can't post as a webhook with a per-message username/avatar, so this talks the webhook
 * REST API directly. The caller builds the body (embeds or Components V2); this only adds
 * the impersonation fields and performs the request.
 */
public final class WebhookSender {

    /** Discord message flag: this message uses Components V2. */
    public static final int IS_COMPONENTS_V2 = 1 << 15;

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    private WebhookSender() {}

    /** Shared mapper so callers build component/embed trees with the same factory. */
    public static ObjectMapper mapper() {
        return JSON;
    }

    /**
     * POSTs a fully-built body to the webhook, adding {@code username}/{@code avatar_url}
     * when given. Returns the created message id ({@code ?wait=true}).
     */
    public static String post(String webhookUrl, String username, String avatarUrl, ObjectNode body) {
        if (username != null && !username.isBlank()) {
            body.put("username", username);
        }
        if (avatarUrl != null && !avatarUrl.isBlank()) {
            body.put("avatar_url", avatarUrl);
        }
        try {
            HttpResponse<String> res = HTTP.send(
                    request(webhookUrl + "?wait=true").POST(json(body)).build(),
                    HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() / 100 != 2) {
                throw new WebhookException("webhook HTTP " + res.statusCode() + ": " + res.body());
            }
            JsonNode node = JSON.readTree(res.body());
            return node.path("id").asText(null);
        } catch (WebhookException e) {
            throw e;
        } catch (Exception e) {
            throw new WebhookException("failed to post webhook message", e);
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

    /** Thrown when a webhook post fails. */
    public static final class WebhookException extends RuntimeException {
        public WebhookException(String message) {
            super(message);
        }

        public WebhookException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
