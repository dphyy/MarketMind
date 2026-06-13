package com.marketmind.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketmind.model.ActionType;
import com.marketmind.model.Product;
import com.marketmind.model.SignalContext;
import com.marketmind.model.StrategyDecision;
import com.marketmind.model.StrategyMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Strategy reasoning. Calls Kimi K2.6 (via {@link KimiClient}) for a JSON
 * decision when configured; otherwise falls back to deterministic rule-based
 * logic. A live failure never crashes the cycle — it degrades to the fallback.
 *
 * <p>Note: this proposes a strategy. The deterministic {@code GuardrailEngine}
 * still has final say in the post-check.
 */
@Service
public class KimiReasoningService {

    private static final Logger log = LoggerFactory.getLogger(KimiReasoningService.class);

    private static final String SYSTEM_PROMPT = """
            You are MarketMind's strategy engine for an e-commerce seller.
            You receive a market signal context and must respond with ONLY valid JSON — no markdown, no preamble.

            Response schema:
            {
              "recommended_mode": "HARVEST|HOLD|BUNKER|GHOST",
              "mode_reason": "one sentence explaining why",
              "action_type": "PRICE_UPDATE|AD_BID_UPDATE|MODE_TRANSITION|NO_ACTION",
              "proposed_price": null or number,
              "proposed_ad_bid": null or number,
              "confidence": 0.0-1.0,
              "plain_english_explanation": "2-3 sentences written for a non-technical seller explaining what you observed and what you are doing"
            }

            Hard rules you must respect:
            - Never propose a price below the price_floor in the context
            - Never propose a price above the price_ceiling in the context
            - Be conservative: if signals are conflicting, recommend GHOST mode
            - The plain_english_explanation must be warm and reassuring, written as if from a trusted business advisor
            """;

    /** HARVEST raises price by this fraction toward the ceiling. */
    private static final BigDecimal HARVEST_PRICE_LIFT = new BigDecimal("0.10");
    /** BUNKER raises price by this fraction to slow sell-through. */
    private static final BigDecimal BUNKER_PRICE_LIFT = new BigDecimal("0.08");

    private final KimiClient kimi;
    private final ObjectMapper mapper;

    public KimiReasoningService(KimiClient kimi, ObjectMapper mapper) {
        this.kimi = kimi;
        this.mapper = mapper;
    }

    public StrategyDecision reason(SignalContext ctx) {
        if (kimi.isConfigured()) {
            try {
                String content = kimi.chat(SYSTEM_PROMPT, mapper.writeValueAsString(ctx), 0.2);
                StrategyDecision decision = parse(content);
                decision.setSource("LIVE");
                log.info("[LIVE] Kimi reasoned {} → {} ({})",
                        ctx.getProduct().getId(), decision.getRecommendedMode(), decision.getActionType());
                return decision;
            } catch (Exception e) {
                log.warn("[LIVE] Kimi reasoning failed for {} ({}). Falling back to rule-based logic.",
                        ctx.getProduct().getId(), e.getMessage());
            }
        }
        StrategyDecision decision = ruleBased(ctx);
        decision.setSource("MOCK");
        log.info("[MOCK] Rule-based reasoning {} → {} ({})",
                ctx.getProduct().getId(), decision.getRecommendedMode(), decision.getActionType());
        return decision;
    }

    private StrategyDecision parse(String content) throws Exception {
        String json = stripCodeFences(content);
        return mapper.readValue(json, StrategyDecision.class);
    }

    /** Tolerate models that wrap JSON in ```json ... ``` fences. */
    private static String stripCodeFences(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline > 0) {
                trimmed = trimmed.substring(firstNewline + 1);
            }
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3);
            }
        }
        return trimmed.trim();
    }

    // ----------------------------------------------------- rule-based fallback

    private StrategyDecision ruleBased(SignalContext ctx) {
        Product p = ctx.getProduct();
        boolean competitorStockedOut = isStockedOut(ctx.getMainCompetitorStockLevel());
        boolean positiveSentiment = ctx.getSentimentScore24h() != null && ctx.getSentimentScore24h() > 0.5;
        boolean viral = Boolean.TRUE.equals(ctx.getViralityFlag());
        boolean healthyRunway = ctx.getStockDaysRemaining() != null && ctx.getStockDaysRemaining() > 7;
        boolean reserveNear = p.getStock() != null && p.getStockReserveMin() != null
                && p.getStock() <= p.getStockReserveMin() * 1.2;
        boolean lowRunway = ctx.getStockDaysRemaining() != null && ctx.getStockDaysRemaining() <= 5;

        // GHOST: data missing / conflicting — freeze and observe.
        if (ctx.getSentimentScore24h() == null) {
            return StrategyDecision.builder()
                    .recommendedMode(StrategyMode.GHOST)
                    .modeReason("Sentiment data unavailable — conflicting/incomplete signals.")
                    .actionType(ActionType.NO_ACTION)
                    .confidence(0.4)
                    .plainEnglishExplanation("I couldn't get a clear read on social sentiment this cycle, so "
                            + "I'm holding everything steady and just watching. No changes were made to your "
                            + "pricing or ads while the picture is unclear.")
                    .build();
        }

        // BUNKER: protect your own reserve.
        if (reserveNear || lowRunway) {
            BigDecimal price = roundToTenCents(p.getYourPrice().multiply(BigDecimal.ONE.add(BUNKER_PRICE_LIFT)));
            price = clampToBounds(price, p);
            return StrategyDecision.builder()
                    .recommendedMode(StrategyMode.BUNKER)
                    .modeReason("Your stock is approaching the reserve minimum; protecting inventory.")
                    .actionType(ActionType.PRICE_UPDATE)
                    .proposedPrice(price)
                    .proposedAdBid(roundToTenCents(p.getAdBid().multiply(new BigDecimal("0.7"))))
                    .confidence(0.8)
                    .plainEnglishExplanation(String.format(
                            "Your stock (%d units) is getting close to the reserve you set. To avoid selling out "
                                    + "too fast, I'm nudging the price up to $%.2f and easing back on ad spend. This "
                                    + "protects the inventory you want to keep.",
                            p.getStock(), price))
                    .build();
        }

        // HARVEST: competitor stocked out + demand is hot + we have runway.
        if (competitorStockedOut && (positiveSentiment || viral) && healthyRunway) {
            BigDecimal price = roundToTenCents(p.getYourPrice().multiply(BigDecimal.ONE.add(HARVEST_PRICE_LIFT)));
            price = clampToBounds(price, p);
            return StrategyDecision.builder()
                    .recommendedMode(StrategyMode.HARVEST)
                    .modeReason("Competitor critically low on stock and sentiment is strongly positive.")
                    .actionType(ActionType.PRICE_UPDATE)
                    .proposedPrice(price)
                    .confidence(0.88)
                    .plainEnglishExplanation(String.format(
                            "Your main competitor just hit critically low stock and social buzz is spiking "
                                    + "(sentiment %+.2f). That's a window to capture demand, so I'm raising your price "
                                    + "to $%.2f — still comfortably within the limits you set.",
                            ctx.getSentimentScore24h(), price))
                    .build();
        }

        // HOLD: the default.
        return StrategyDecision.builder()
                .recommendedMode(StrategyMode.HOLD)
                .modeReason("No decisive signal — maintaining current position.")
                .actionType(ActionType.NO_ACTION)
                .confidence(0.6)
                .plainEnglishExplanation("The market looks steady right now — no competitor stockouts and sentiment "
                        + "is calm. I'm holding your price and ad spend where they are and will keep watching.")
                .build();
    }

    private static boolean isStockedOut(String level) {
        return "CRITICALLY_LOW".equals(level) || "OUT_OF_STOCK".equals(level);
    }

    /** Psychological pricing: round to the nearest $0.10 (e.g. 54.89 → 54.90). */
    private static BigDecimal roundToTenCents(BigDecimal price) {
        return price.multiply(BigDecimal.TEN)
                .setScale(0, RoundingMode.HALF_UP)
                .divide(BigDecimal.TEN)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal clampToBounds(BigDecimal price, Product p) {
        if (price.compareTo(p.getPriceCeiling()) > 0) {
            return p.getPriceCeiling();
        }
        if (price.compareTo(p.getPriceFloor()) < 0) {
            return p.getPriceFloor();
        }
        return price;
    }
}
