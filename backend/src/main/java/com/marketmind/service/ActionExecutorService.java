package com.marketmind.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.marketmind.model.StrategyDecision;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Executes an approved action inside a Daytona sandbox. When a Daytona key is
 * configured it provisions a real isolated sandbox (and stores its real id);
 * otherwise — or if the live call fails — it falls back to a simulated job.
 * Either way it returns a {@code daytona_job_id} for the action log and never
 * throws into the agent cycle.
 */
@Service
public class ActionExecutorService {

    private static final Logger log = LoggerFactory.getLogger(ActionExecutorService.class);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final HttpClient http;
    private final ObjectMapper mapper;

    @Value("${marketmind.daytona.api-key:}")
    private String daytonaApiKey;
    @Value("${marketmind.daytona.base-url:https://app.daytona.io/api}")
    private String daytonaBaseUrl;
    @Value("${marketmind.daytona.region:us}")
    private String daytonaRegion;

    public ActionExecutorService(HttpClient http, ObjectMapper mapper) {
        this.http = http;
        this.mapper = mapper;
    }

    /**
     * Run the EXECUTE step as an isolated sandbox job and return its id.
     * Never throws into the agent cycle.
     */
    public String execute(StrategyDecision decision) {
        // In production: the seller's marketplace API credentials are injected here
        // via Terminal 3 TEE (Trusted Execution Environment). Credentials are
        // hardware-secured — never stored in plaintext in our DB. The agent is
        // cryptographically authorised to act on the seller's behalf.
        String marketplaceApiKey = "[TERMINAL_3_INJECTED_AT_RUNTIME]";

        if (StringUtils.hasText(daytonaApiKey)) {
            try {
                String jobId = runRealSandbox(decision);
                log.info("[LIVE] [DAYTONA] Sandbox {} provisioned for {}", jobId, decision.getActionType());
                simulateMarketplaceUpdate(decision, marketplaceApiKey);
                log.info("[LIVE] [DAYTONA] Job {} completed. Tearing down.", jobId);
                return jobId;
            } catch (Exception e) {
                log.warn("[LIVE] [DAYTONA] Real sandbox failed ({}) — using simulated job", e.getMessage());
            }
        }

        String jobId = simulatedJobId();
        log.info("[MOCK] [DAYTONA SANDBOX] Spinning up simulated job {} for {}", jobId, decision.getActionType());
        simulateMarketplaceUpdate(decision, marketplaceApiKey);
        log.info("[MOCK] [DAYTONA SANDBOX] Job {} completed successfully. Tearing down.", jobId);
        return jobId;
    }

    /**
     * Provision a real Daytona sandbox, returning its id. The sandbox represents
     * the isolated environment the marketplace write-back would run in. We tear
     * it down on a best-effort basis after capturing the id.
     */
    private String runRealSandbox(StrategyDecision decision) throws Exception {
        String base = trimTrailingSlash(daytonaBaseUrl);
        ObjectNode body = mapper.createObjectNode();
        body.put("language", "python");
        if (StringUtils.hasText(daytonaRegion)) {
            // The org has no default region, so specify it explicitly on creation.
            body.put("target", daytonaRegion);
        }
        body.putObject("labels")
                .put("app", "marketmind")
                .put("action", String.valueOf(decision.getActionType()));

        HttpRequest create = HttpRequest.newBuilder()
                .uri(URI.create(base + "/sandbox"))
                .timeout(Duration.ofSeconds(45))
                .header("Authorization", "Bearer " + daytonaApiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();

        HttpResponse<String> response = http.send(create, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new RuntimeException("create HTTP " + response.statusCode() + ": " + truncate(response.body()));
        }

        JsonNode root = mapper.readTree(response.body());
        String id = firstNonBlank(root, "id", "sandboxId", "sandbox_id");
        if (id == null) {
            throw new RuntimeException("no sandbox id in response: " + truncate(response.body()));
        }
        teardown(base, id);
        return id;
    }

    private void teardown(String base, String id) {
        try {
            HttpRequest delete = HttpRequest.newBuilder()
                    .uri(URI.create(base + "/sandbox/" + id))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + daytonaApiKey)
                    .DELETE()
                    .build();
            http.send(delete, HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            log.debug("[LIVE] [DAYTONA] Best-effort teardown of {} failed: {}", id, e.getMessage());
        }
    }

    private void simulateMarketplaceUpdate(StrategyDecision decision, String marketplaceApiKey) {
        // Stand-in for the real Shopee/Lazada write-back (out of scope for the
        // hackathon). The credential would authorise this call in production.
        log.debug("[DAYTONA SANDBOX] Simulated marketplace write: type={} price={} adBid={} auth={}",
                decision.getActionType(), decision.getProposedPrice(),
                decision.getProposedAdBid(), marketplaceApiKey);
    }

    private static String simulatedJobId() {
        return "daytona-sim-" + LocalDateTime.now().format(STAMP) + "-"
                + UUID.randomUUID().toString().substring(0, 8);
    }

    private static String firstNonBlank(JsonNode root, String... fields) {
        for (String f : fields) {
            JsonNode n = root.path(f);
            if (n.isTextual() && !n.asText().isBlank()) {
                return n.asText();
            }
        }
        return null;
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
