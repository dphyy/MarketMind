package com.marketmind.service;

import com.marketmind.model.ActionLog;
import com.marketmind.model.ActionType;
import com.marketmind.model.GuardrailResult;
import com.marketmind.model.Product;
import com.marketmind.model.SignalContext;
import com.marketmind.model.StrategyDecision;
import com.marketmind.model.StrategyMode;
import com.marketmind.repository.ActionLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * The deterministic guardrail layer — pure Java, ZERO AI. This is the core
 * integrity pitch: whatever the AI proposes, these rules always win.
 *
 * <ul>
 *   <li><b>Pre-check</b>: if stock is at/below the reserve minimum, force BUNKER
 *       mode regardless of anything else.</li>
 *   <li><b>Post-check</b>: validate the AI's proposed action against price floor,
 *       price ceiling, the daily velocity cap, and the stock-reserve ad rule —
 *       and re-enforce any mode forced by the pre-check.</li>
 * </ul>
 */
@Service
public class GuardrailEngine {

    private static final Logger log = LoggerFactory.getLogger(GuardrailEngine.class);

    /** Float tolerance so a change landing exactly on the cap is allowed, not blocked. */
    private static final double EPS = 1e-9;

    private final ActionLogRepository actionLogRepository;

    public GuardrailEngine(ActionLogRepository actionLogRepository) {
        this.actionLogRepository = actionLogRepository;
    }

    /**
     * Pre-check: validate current state before the AI reasons. If our own stock
     * has reached the reserve minimum, force BUNKER — the AI does not get a vote.
     */
    public void preCheck(SignalContext ctx) {
        Product p = ctx.getProduct();
        if (p.getStock() != null && p.getStockReserveMin() != null
                && p.getStock() <= p.getStockReserveMin()) {
            ctx.forceMode(StrategyMode.BUNKER);
            log.info("[GUARDRAIL] Pre-check forced BUNKER for {} — stock {} <= reserve min {}",
                    p.getId(), p.getStock(), p.getStockReserveMin());
        }
    }

    /**
     * Post-check: validate the AI's proposed action. Mutates {@code decision} to
     * re-enforce a forced mode (the deterministic layer wins), then returns a
     * hard allow/block result for the proposed price/ad changes.
     */
    public GuardrailResult postCheck(StrategyDecision decision, SignalContext ctx) {
        Product p = ctx.getProduct();

        // Re-enforce a forced mode on the AI's output. We do NOT merely hope the
        // prompt was respected — we overwrite the recommendation here.
        if (ctx.getForcedMode() != null && decision.getRecommendedMode() != ctx.getForcedMode()) {
            log.info("[GUARDRAIL] Re-enforcing forced mode {} over AI recommendation {} for {}",
                    ctx.getForcedMode(), decision.getRecommendedMode(), p.getId());
            decision.setRecommendedMode(ctx.getForcedMode());
            decision.setModeReason("Mode forced to " + ctx.getForcedMode()
                    + " by the guardrail engine (stock at/below reserve minimum). AI recommendation overridden.");
        }

        BigDecimal proposed = decision.getProposedPrice();
        if (proposed != null) {
            // Rule 1: price floor
            if (proposed.compareTo(p.getPriceFloor()) < 0) {
                return blocked(p, String.format(
                        "Proposed price $%.2f is below the margin floor of $%.2f. Action blocked.",
                        proposed, p.getPriceFloor()));
            }

            // Rule 2: price ceiling
            if (proposed.compareTo(p.getPriceCeiling()) > 0) {
                return blocked(p, String.format(
                        "Proposed price $%.2f exceeds the ceiling of $%.2f. Action blocked.",
                        proposed, p.getPriceCeiling()));
            }

            // Rule 3: daily velocity cap — cumulative change from the day-open price.
            BigDecimal dayOpen = dayOpenPrice(p);
            double cumulativePct =
                    proposed.subtract(dayOpen).doubleValue() / dayOpen.doubleValue();
            double cap = p.getMaxDailyPriceChangePct().doubleValue();
            if (Math.abs(cumulativePct) > cap + EPS) {
                return blocked(p, String.format(
                        "Proposed cumulative daily change of %+.1f%% (from day-open $%.2f) "
                                + "exceeds the %.0f%% velocity cap. Action blocked.",
                        cumulativePct * 100, dayOpen, cap * 100));
            }
        }

        // Rule 4: stock reserve vs ad spend. The brief's pseudocode names
        // INCREASE_AD_SPEND; the shipped enum is AD_BID_UPDATE, so we gate on an
        // AD_BID_UPDATE that *raises* the bid while stock approaches the reserve.
        if (decision.getActionType() == ActionType.AD_BID_UPDATE
                && decision.getProposedAdBid() != null
                && p.getAdBid() != null
                && decision.getProposedAdBid().compareTo(p.getAdBid()) > 0
                && p.getStock() != null && p.getStockReserveMin() != null
                && p.getStock() <= Math.ceil(p.getStockReserveMin() * 1.2)) {
            return blocked(p, String.format(
                    "Cannot increase ad spend — stock (%d) is approaching the reserve minimum of %d. Action blocked.",
                    p.getStock(), p.getStockReserveMin()));
        }

        return GuardrailResult.allowed();
    }

    private GuardrailResult blocked(Product p, String reason) {
        log.info("[GUARDRAIL] BLOCK {} — {}", p.getId(), reason);
        return GuardrailResult.blocked(reason);
    }

    /**
     * The day-open reference price: the {@code from_value} of the earliest
     * executed PRICE_UPDATE logged today, or {@code your_price} if there have
     * been no price changes yet today. This is the single reference the engine,
     * seed data, and demo all agree on.
     */
    BigDecimal dayOpenPrice(Product p) {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        List<ActionLog> todays = actionLogRepository
                .findByProductIdAndActionTypeAndExecutedTrueAndCreatedAtAfter(
                        p.getId(), ActionType.PRICE_UPDATE, startOfDay);
        return todays.stream()
                .filter(a -> a.getFromValue() != null && a.getCreatedAt() != null)
                .min(Comparator.comparing(ActionLog::getCreatedAt))
                .map(a -> new BigDecimal(a.getFromValue()))
                .orElse(p.getYourPrice());
    }
}
