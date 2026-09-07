package dev.davimf.basebot.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.davimf.basebot.config.BotConfig;
import dev.davimf.basebot.crypto.EncryptedBundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Posts encrypted ticket transcripts to the davimf.dev dashboard
 * ({@code POST /api/ticket-store}, auth header {@code x-ticket-secret}).
 *
 * <p>The JSON body mirrors the {@code REQUIRED} field list in
 * netlify/functions/ticket-store.ts exactly. The dashboard stores the row in the
 * Neon {@code tickets} table and serves it (encrypted) at {@code /ticket/{id}}.
 */
public final class TicketIngestClient {

    private static final Logger log = LoggerFactory.getLogger(TicketIngestClient.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final BotConfig.Tickets cfg;

    public TicketIngestClient(BotConfig.Tickets cfg) {
        this.cfg = cfg;
    }

    /**
     * Sends one transcript. Returns the public view URL on success.
     *
     * @param id          unique ticket id (also the URL slug)
     * @param guildName   human-readable guild name
     * @param channelName the ticket channel name (for display)
     * @param bundle      the encrypted payload from TicketCrypto
     */
    public String store(String id, String guildName, String channelName, EncryptedBundle bundle) {
        ObjectNode body = JSON.createObjectNode();
        body.put("id", id);
        body.put("source", cfg.source());
        body.put("guildName", guildName);
        body.put("channelName", channelName);
        body.put("saltKey", bundle.saltKey());
        body.put("saltHash", bundle.saltHash());
        body.put("iv", bundle.iv());
        body.put("ciphertext", bundle.ciphertext());
        body.put("passwordHash", bundle.passwordHash());
        body.put("iterations", bundle.iterations());

        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(cfg.ingestUrl()))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json")
                    .header("x-ticket-secret", cfg.ingestSecret())
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
                    .build();

            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            int code = res.statusCode();
            if (code == 200) {
                return cfg.viewUrl(id);
            }
            if (code == 409) {
                // Already stored (idempotent retry) — treat as success.
                log.warn("Ticket {} already stored on dashboard (409).", id);
                return cfg.viewUrl(id);
            }
            throw new IngestException("ticket-store returned HTTP " + code + ": " + res.body());
        } catch (IngestException e) {
            throw e;
        } catch (Exception e) {
            throw new IngestException("Failed to POST transcript for ticket " + id, e);
        }
    }

    /** Thrown when the dashboard rejects or fails to accept a transcript. */
    public static final class IngestException extends RuntimeException {
        public IngestException(String message) {
            super(message);
        }

        public IngestException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
