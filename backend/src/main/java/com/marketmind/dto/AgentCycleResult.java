package com.marketmind.dto;

import com.marketmind.model.ActionType;
import com.marketmind.model.StrategyMode;

import java.math.BigDecimal;

/**
 * Summary of one agent cycle, returned by the run endpoints so the UI can show
 * what just happened without a second round-trip.
 */
public record AgentCycleResult(
        String productId,
        StrategyMode previousMode,
        StrategyMode newMode,
        ActionType actionType,
        BigDecimal previousPrice,
        BigDecimal proposedPrice,
        BigDecimal newPrice,
        boolean allowed,
        String blockReason,
        Double confidence,
        String explanation,
        String reasoningSource,
        String daytonaJobId
) {
}
