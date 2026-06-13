package com.marketmind.service;

import com.marketmind.model.ActionLog;
import com.marketmind.model.ActionType;
import com.marketmind.model.GuardrailResult;
import com.marketmind.model.Product;
import com.marketmind.model.SignalContext;
import com.marketmind.model.StrategyDecision;
import com.marketmind.model.StrategyMode;
import com.marketmind.repository.ActionLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the deterministic guardrail layer. These cover the two
 * scenarios the demo depends on: the ALLOWED +10% Harvest price ($49.90 →
 * $54.90) and the BLOCKED +25.9% velocity-cap violation ($49.90 → $62.80).
 */
class GuardrailEngineTest {

    private ActionLogRepository actionLogRepo;
    private GuardrailEngine engine;

    @BeforeEach
    void setUp() {
        actionLogRepo = mock(ActionLogRepository.class);
        engine = new GuardrailEngine(actionLogRepo);
        // Default: no price changes logged today, so day-open falls back to your_price.
        when(actionLogRepo.findByProductIdAndActionTypeAndExecutedTrueAndCreatedAtAfter(
                any(), any(), any())).thenReturn(List.of());
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    /** SKU-001 from the seed data. */
    private static Product sku001() {
        Product p = new Product();
        p.setId("SKU-001");
        p.setName("ProSound Wireless Earbuds X3");
        p.setCategory("wireless_earbuds");
        p.setYourPrice(bd("49.90"));
        p.setPriceFloor(bd("38.00"));
        p.setPriceCeiling(bd("65.00"));
        p.setStock(120);
        p.setStockReserveMin(30);
        p.setMaxDailyPriceChangePct(bd("0.1500"));
        p.setCurrentMode(StrategyMode.HOLD);
        p.setAdBid(bd("1.20"));
        return p;
    }

    private static SignalContext ctxFor(Product p) {
        return SignalContext.builder().product(p).currentMode(p.getCurrentMode()).build();
    }

    private static StrategyDecision priceDecision(StrategyMode mode, String price) {
        return StrategyDecision.builder()
                .recommendedMode(mode)
                .actionType(ActionType.PRICE_UPDATE)
                .proposedPrice(bd(price))
                .build();
    }

    // ---------------------------------------------------------------- pre-check

    @Test
    void preCheck_forcesBunker_whenStockAtOrBelowReserve() {
        Product p = sku001();
        p.setStock(30); // == reserve min
        SignalContext ctx = ctxFor(p);

        engine.preCheck(ctx);

        assertThat(ctx.getForcedMode()).isEqualTo(StrategyMode.BUNKER);
    }

    @Test
    void preCheck_doesNotForce_whenStockComfortablyAboveReserve() {
        SignalContext ctx = ctxFor(sku001()); // stock 120, reserve 30
        engine.preCheck(ctx);
        assertThat(ctx.getForcedMode()).isNull();
    }

    // ----------------------------------------------------- the guaranteed demo

    @Test
    void postCheck_allows_demoHarvestPrice() {
        // $49.90 -> $54.90 == +10.0% cumulative, within the 15% cap, within bounds.
        StrategyDecision decision = priceDecision(StrategyMode.HARVEST, "54.90");

        GuardrailResult result = engine.postCheck(decision, ctxFor(sku001()));

        assertThat(result.isAllowed()).isTrue();
        assertThat(result.getReason()).isNull();
    }

    @Test
    void postCheck_blocks_velocityCapViolation() {
        // $49.90 -> $62.80 == +25.9% cumulative, exceeds the 15% cap. HARD block.
        StrategyDecision decision = priceDecision(StrategyMode.HARVEST, "62.80");

        GuardrailResult result = engine.postCheck(decision, ctxFor(sku001()));

        assertThat(result.isAllowed()).isFalse();
        assertThat(result.getReason()).contains("25.9%").contains("15%").contains("49.90");
    }

    // --------------------------------------------------------- price bounds

    @Test
    void postCheck_blocks_belowFloor() {
        StrategyDecision decision = priceDecision(StrategyMode.HOLD, "37.99");
        GuardrailResult result = engine.postCheck(decision, ctxFor(sku001()));
        assertThat(result.isAllowed()).isFalse();
        assertThat(result.getReason()).contains("floor");
    }

    @Test
    void postCheck_blocks_aboveCeiling() {
        StrategyDecision decision = priceDecision(StrategyMode.HARVEST, "65.01");
        GuardrailResult result = engine.postCheck(decision, ctxFor(sku001()));
        assertThat(result.isAllowed()).isFalse();
        assertThat(result.getReason()).contains("ceiling");
    }

    // ------------------------------------------------ velocity cap with history

    @Test
    void postCheck_usesDayOpenFromTodaysEarliestPriceUpdate() {
        // Earlier today the price already moved 49.90 -> 54.90. Day-open is 49.90,
        // so a further proposal to 57.90 is +16.0% cumulative => blocked.
        ActionLog earlier = new ActionLog();
        earlier.setProductId("SKU-001");
        earlier.setActionType(ActionType.PRICE_UPDATE);
        earlier.setFromValue("49.90");
        earlier.setToValue("54.90");
        earlier.setExecuted(true);
        earlier.setCreatedAt(LocalDateTime.now().minusHours(1));
        when(actionLogRepo.findByProductIdAndActionTypeAndExecutedTrueAndCreatedAtAfter(
                eq("SKU-001"), eq(ActionType.PRICE_UPDATE), any())).thenReturn(List.of(earlier));

        Product p = sku001();
        p.setYourPrice(bd("54.90")); // current price reflects the earlier move
        StrategyDecision decision = priceDecision(StrategyMode.HARVEST, "57.90");

        GuardrailResult result = engine.postCheck(decision, ctxFor(p));

        assertThat(result.isAllowed()).isFalse();
        assertThat(result.getReason()).contains("49.90"); // day-open, not current price
    }

    // -------------------------------------------------- stock reserve vs ads

    @Test
    void postCheck_blocks_adIncrease_whenStockApproachingReserve() {
        Product p = sku001();
        p.setStock(35);          // <= ceil(30 * 1.2) == 36
        p.setAdBid(bd("0.85"));
        StrategyDecision decision = StrategyDecision.builder()
                .recommendedMode(StrategyMode.HOLD)
                .actionType(ActionType.AD_BID_UPDATE)
                .proposedAdBid(bd("1.10"))
                .build();

        GuardrailResult result = engine.postCheck(decision, ctxFor(p));

        assertThat(result.isAllowed()).isFalse();
        assertThat(result.getReason()).contains("ad spend");
    }

    @Test
    void postCheck_allows_adIncrease_whenStockHealthy() {
        Product p = sku001(); // stock 120
        StrategyDecision decision = StrategyDecision.builder()
                .recommendedMode(StrategyMode.HARVEST)
                .actionType(ActionType.AD_BID_UPDATE)
                .proposedAdBid(bd("1.50"))
                .build();

        GuardrailResult result = engine.postCheck(decision, ctxFor(p));

        assertThat(result.isAllowed()).isTrue();
    }

    // ---------------------------------------------- forced-mode re-enforcement

    @Test
    void postCheck_reEnforcesForcedMode_overAiRecommendation() {
        Product p = sku001();
        p.setStock(15); // at reserve... force BUNKER in pre-check
        p.setStockReserveMin(15);
        SignalContext ctx = ctxFor(p);
        engine.preCheck(ctx);
        assertThat(ctx.getForcedMode()).isEqualTo(StrategyMode.BUNKER);

        // AI ignored the forced mode and wants HARVEST with a price still in bounds.
        StrategyDecision decision = priceDecision(StrategyMode.HARVEST, "50.90");

        engine.postCheck(decision, ctx);

        assertThat(decision.getRecommendedMode()).isEqualTo(StrategyMode.BUNKER);
        assertThat(decision.getModeReason()).contains("forced");
    }
}
