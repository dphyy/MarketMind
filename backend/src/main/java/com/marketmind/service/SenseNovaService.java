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
 * Extracts visual commercial signals (clearance banners, flash-sale badges,
 * etc.) from a competitor listing image via SenseNova U1's multimodal endpoint.
 * If unconfigured, given no image, or on any failure, returns an empty list and
 * logs the outcome — it never crashes the agent cycle.
 */
@Service
public class SenseNovaService {

    private static final Logger log = LoggerFactory.getLogger(SenseNovaService.class);

    private static final String PROMPT = """
            Analyse this product listing image. Identify any of the following visual signals if present:
            - clearance_banner (text or sticker indicating clearance sale)
            - flash_sale_banner (flash sale or limited time offer indicator)
            - low_stock_badge (badge or sticker indicating low stock)
            - sale_percentage (any percentage discount prominently displayed)
            - bundle_offer (bundle deal text or imagery)

            Respond ONLY with a JSON array of detected signal strings.
            Example: ["clearance_banner", "low_stock_badge"]
            If none detected, respond: []
            """;

    private final HttpClient http;
    private final ObjectMapper mapper;

    @Value("${marketmind.sensenova.api-key:}")
    private String apiKey;
    @Value("${marketmind.sensenova.base-url:https://api.sensenova.cn/v1}")
    private String baseUrl;
    @Value("${marketmind.sensenova.model:SenseNova-U1}")
    private String model;
    // Endpoint path. OpenAI-compatible gateways (e.g. velaalpha) use
    // /chat/completions; SenseNova's native API uses /llm/chat-completions.
    @Value("${marketmind.sensenova.chat-path:/chat/completions}")
    private String chatPath;

    public SenseNovaService(HttpClient http, ObjectMapper mapper) {
        this.http = http;
        this.mapper = mapper;
    }

    /**
     * @return detected visual signals, or an empty list if unconfigured / no
     *         image / failure. An empty list means "no live signal" — callers
     *         should keep any signals already on the snapshot.
     */
    public List<String> extractVisualSignals(String imageUrl) {
        if (!StringUtils.hasText(apiKey) || !StringUtils.hasText(imageUrl)) {
            log.info("[MOCK] SenseNova skipped (configured={}, hasImage={})",
                    StringUtils.hasText(apiKey), StringUtils.hasText(imageUrl));
            return List.of();
        }
        try {
            List<String> signals = callSenseNova(imageUrl);
            log.info("[LIVE] SenseNova detected {} on {}", signals, imageUrl);
            return signals;
        } catch (Exception e) {
            log.warn("[LIVE] SenseNova failed ({}) — returning no visual signals", e.getMessage());
            return List.of();
        }
    }

    private List<String> callSenseNova(String imageUrl) throws Exception {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", model);
        ArrayNode messages = body.putArray("messages");
        ObjectNode userMsg = messages.addObject();
        userMsg.put("role", "user");
        ArrayNode content = userMsg.putArray("content");
        content.addObject().put("type", "text").put("text", PROMPT);
        // Standard OpenAI-compatible image part. The image URL must be directly
        // fetchable (no redirects) — the gateway fetches it server-side.
        ObjectNode imagePart = content.addObject();
        imagePart.put("type", "image_url");
        imagePart.putObject("image_url").put("url", imageUrl);

        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String path = chatPath.startsWith("/") ? chatPath : "/" + chatPath;
        String url = base + path;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(45))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            String errBody = response.body();
            throw new RuntimeException("SenseNova HTTP " + response.statusCode() + " at " + url + ": "
                    + (errBody != null && errBody.length() > 200 ? errBody.substring(0, 200) + "…" : errBody));
        }
        JsonNode root = mapper.readTree(response.body());
        String text = root.path("choices").path(0).path("message").path("content").asText("[]");
        return parseSignalArray(text);
    }

    private List<String> parseSignalArray(String text) throws Exception {
        String trimmed = text.trim();
        int start = trimmed.indexOf('[');
        int end = trimmed.lastIndexOf(']');
        if (start < 0 || end < start) {
            return List.of();
        }
        JsonNode arr = mapper.readTree(trimmed.substring(start, end + 1));
        List<String> signals = new ArrayList<>();
        if (arr.isArray()) {
            arr.forEach(n -> signals.add(n.asText()));
        }
        return signals;
    }
}
