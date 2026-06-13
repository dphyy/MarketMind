package com.marketmind.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Thin OpenAI-compatible chat client for Kimi K2.6. Used by both strategy
 * reasoning (JSON) and the morning brief (prose).
 *
 * <p>Routing (per CLAUDE.md rule 4):
 * <pre>
 *   TOKENROUTER_BASE_URL set  -> call Kimi via TokenRouter (drop-in base-url swap)
 *   else KIMI_API_KEY set     -> call Kimi directly
 *   else                      -> not configured; callers fall back to rule-based logic
 * </pre>
 * The base-url and model are fully parameterised — nothing is hardcoded.
 */
@Service
public class KimiClient {

    private static final Logger log = LoggerFactory.getLogger(KimiClient.class);

    private final HttpClient http;
    private final ObjectMapper mapper;

    @Value("${marketmind.kimi.api-key:}")
    private String kimiApiKey;
    @Value("${marketmind.kimi.base-url:https://api.moonshot.cn/v1}")
    private String kimiBaseUrl;
    @Value("${marketmind.kimi.model:kimi-k2.6}")
    private String model;
    @Value("${marketmind.tokenrouter.api-key:}")
    private String tokenRouterApiKey;
    @Value("${marketmind.tokenrouter.base-url:}")
    private String tokenRouterBaseUrl;

    public KimiClient(HttpClient http, ObjectMapper mapper) {
        this.http = http;
        this.mapper = mapper;
    }

    /** True if either TokenRouter or a direct Kimi key is configured. */
    public boolean isConfigured() {
        return StringUtils.hasText(tokenRouterApiKey) || StringUtils.hasText(kimiApiKey);
    }

    /** One ordered way to reach Kimi: a named route with its base-url + key. */
    private record Route(String name, String baseUrl, String apiKey) {
    }

    /**
     * Resolve the route order. Per CLAUDE.md rule 4 (as refined): if a TokenRouter
     * key is set, route through TokenRouter first (drop-in base-url swap), then
     * fall back to calling Kimi directly. Either succeeding logs {@code [LIVE]}.
     */
    private List<Route> routes() {
        List<Route> routes = new ArrayList<>();
        if (StringUtils.hasText(tokenRouterApiKey)) {
            routes.add(new Route("TokenRouter", tokenRouterBaseUrl, tokenRouterApiKey));
        }
        if (StringUtils.hasText(kimiApiKey)) {
            routes.add(new Route("Kimi-direct", kimiBaseUrl, kimiApiKey));
        }
        return routes;
    }

    /**
     * Run one chat completion, trying each configured route in order and
     * returning the first success.
     *
     * @throws Exception if not configured or every route fails — callers must
     *                   catch and fall back to deterministic logic, never crash.
     */
    public String chat(String systemPrompt, String userPrompt, double temperature) throws Exception {
        List<Route> routes = routes();
        if (routes.isEmpty()) {
            throw new IllegalStateException("Kimi is not configured (no TokenRouter key, no Kimi key)");
        }
        Exception last = null;
        for (Route route : routes) {
            try {
                String content = call(route, systemPrompt, userPrompt, temperature);
                log.info("[LIVE] Kimi chat via {} ({} -> model {})",
                        route.name(), trimTrailingSlash(route.baseUrl()), model);
                return content;
            } catch (Exception e) {
                log.warn("[LIVE] Kimi via {} failed: {}", route.name(), e.getMessage());
                last = e;
            }
        }
        throw last;
    }

    private String call(Route route, String systemPrompt, String userPrompt, double temperature) throws Exception {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", model);
        body.put("temperature", temperature);
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", systemPrompt);
        messages.addObject().put("role", "user").put("content", userPrompt);

        String url = trimTrailingSlash(route.baseUrl()) + "/chat/completions";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(45))
                .header("Authorization", "Bearer " + route.apiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new RuntimeException("HTTP " + response.statusCode() + ": " + truncate(response.body()));
        }

        JsonNode root = mapper.readTree(response.body());
        JsonNode content = root.path("choices").path(0).path("message").path("content");
        if (content.isMissingNode() || content.asText().isBlank()) {
            throw new RuntimeException("response had no message content: " + truncate(response.body()));
        }
        return content.asText();
    }

    private static String trimTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    private static String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 300 ? s.substring(0, 300) + "…" : s;
    }
}
