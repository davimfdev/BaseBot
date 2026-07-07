package dev.davimf.basebot.modules.base.fun;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Busca GIFs de ação no nekos.best (grátis, sem chave). Categorias: pat, hug, etc. */
public final class GifClient {

    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public CompletableFuture<Optional<String>> fetch(String category) {
        HttpRequest req = HttpRequest.newBuilder(URI.create("https://nekos.best/api/v2/" + category))
                .timeout(Duration.ofSeconds(5)).GET().build();
        return http.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> extractUrl(resp.body()))
                .exceptionally(e -> Optional.empty());
    }

    static Optional<String> extractUrl(String json) {
        try {
            JsonNode url = JSON.readTree(json).path("results").path(0).path("url");
            return url.isTextual() && !url.asText().isBlank() ? Optional.of(url.asText()) : Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
