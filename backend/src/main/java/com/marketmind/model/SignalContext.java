package com.marketmind.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * The single normalised context object the agent reasons over for one product
 * in one cycle. Built by {@code SignalAggregatorService} from the product, its
 * competitor snapshots, and the latest sentiment event.
 *
 * <p>This is a plain in-memory object — it is never persisted — so it is free to
 * carry a {@code forcedMode} set by the deterministic guardrail pre-check. The
 * deterministic layer (not the AI) owns that field.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SignalContext {

    private Product product;

    // ---- Competitor signals ----
    /** CRITICALLY_LOW, LOW, NORMAL, OUT_OF_STOCK — worst (lowest) level seen across competitors. */
    private String mainCompetitorStockLevel;
    /** Cheapest competitor price vs your price, as a fraction. Negative = competitor is cheaper. */
    private Double competitorPriceDeltaPct;
    /** Distinct visual flags detected across competitor listings (e.g. clearance_banner). */
    private List<String> competitorVisualFlags;

    // ---- Sentiment signals ----
    private Double sentimentScore24h;   // -1.0 .. 1.0, or null if unavailable
    private String sentimentTrend;      // ACCELERATING_POSITIVE, NEUTRAL, ...
    private Boolean viralityFlag;
    private String viralitySource;
    private String sentimentTopSignal;

    // ---- Inventory signals ----
    private Integer stockDaysRemaining; // estimated at current sell-through
    private Double sellThroughRate7d;   // units/day over last 7 days

    // ---- Current state ----
    private StrategyMode currentMode;
    private LocalDateTime lastModeChange;

    /**
     * Mode forced by the deterministic guardrail pre-check. {@code null} unless a
     * hard state (e.g. stock at/below reserve) requires overriding the AI. When
     * set, the post-check re-enforces it on the AI's proposed decision.
     */
    private StrategyMode forcedMode;

    /** Called by the guardrail pre-check to hard-force a mode regardless of the AI. */
    public void forceMode(StrategyMode mode) {
        this.forcedMode = mode;
    }
}
