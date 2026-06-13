package com.marketmind.service;

import com.marketmind.dto.AgentCycleResult;
import com.marketmind.model.ActionLog;
import com.marketmind.model.ActionType;
import com.marketmind.model.CompetitorSnapshot;
import com.marketmind.model.GuardrailBlock;
import com.marketmind.model.GuardrailResult;
import com.marketmind.model.Product;
import com.marketmind.model.SentimentEvent;
import com.marketmind.model.SignalContext;
import com.marketmind.model.StrategyDecision;
import com.marketmind.model.StrategyMode;
import com.marketmind.repository.ActionLogRepository;
import com.marketmind.repository.GuardrailBlockRepository;
import com.marketmind.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * The main orchestrator. Runs the full loop for one product: scrape → visual
 * signals → sentiment → aggregate → guardrail pre-check → AI reasoning →
 * guardrail post-check → execute-or-block → log. Built to run end-to-end on
 * mock data with no API keys.
 */
@Service
public class AgentCycleService {

    private static final Logger log = LoggerFactory.getLogger(AgentCycleService.class);

    private final BrightDataService brightDataService;
    private final SenseNovaService senseNovaService;
    private final SentimentService sentimentService;
    private final SignalAggregatorService signalAggregatorService;
    private final GuardrailEngine guardrailEngine;
    private final KimiReasoningService kimiReasoningService;
    private final ActionExecutorService actionExecutorService;
    private final ProductRepository productRepository;
    private final ActionLogRepository actionLogRepository;
    private final GuardrailBlockRepository guardrailBlockRepository;

    public AgentCycleService(BrightDataService brightDataService,
                             SenseNovaService senseNovaService,
                             SentimentService sentimentService,
                             SignalAggregatorService signalAggregatorService,
                             GuardrailEngine guardrailEngine,
                             KimiReasoningService kimiReasoningService,
                             ActionExecutorService actionExecutorService,
                             ProductRepository productRepository,
                             ActionLogRepository actionLogRepository,
                             GuardrailBlockRepository guardrailBlockRepository) {
        this.brightDataService = brightDataService;
        this.senseNovaService = senseNovaService;
        this.sentimentService = sentimentService;
        this.signalAggregatorService = signalAggregatorService;
        this.guardrailEngine = guardrailEngine;
        this.kimiReasoningService = kimiReasoningService;
        this.actionExecutorService = actionExecutorService;
        this.productRepository = productRepository;
        this.actionLogRepository = actionLogRepository;
        this.guardrailBlockRepository = guardrailBlockRepository;
    }

    public List<AgentCycleResult> runAll() {
        List<AgentCycleResult> results = new ArrayList<>();
        for (Product p : productRepository.findAll()) {
            results.add(runCycle(p.getId()));
        }
        return results;
    }

    @Transactional
    public AgentCycleResult runCycle(String productId) {
        // Row-level lock on the product prevents concurrent cycles from racing on
        // price/mode. Falls back to a plain lookup if the locking query is unavailable.
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown product: " + productId));

        log.info("[CYCLE] === Running agent cycle for {} ({}) ===", product.getId(), product.getName());

        // 1. Scrape competitors (live or mock fallback).
        List<CompetitorSnapshot> snapshots = brightDataService.scrapeCompetitors(product);

        // 2. Visual signals per snapshot. Only overwrite when SenseNova actually
        //    returns something, so seed/mock snapshots keep their existing flags.
        for (CompetitorSnapshot s : snapshots) {
            List<String> visual = senseNovaService.extractVisualSignals(s.getImageUrl());
            if (!visual.isEmpty()) {
                s.setVisualSignals(visual.toArray(new String[0]));
            }
        }

        // 3. Sentiment.
        SentimentEvent sentiment = sentimentService.getSentiment(product.getCategory());

        // 4. Aggregate into one context object.
        SignalContext context = signalAggregatorService.aggregate(product, snapshots, sentiment);

        // 5. Guardrail pre-check (deterministic — may force a mode).
        guardrailEngine.preCheck(context);

        // 6. AI reasoning → proposed decision.
        StrategyDecision decision = kimiReasoningService.reason(context);

        // 7. Guardrail post-check (deterministic — re-enforces forced mode, validates action).
        GuardrailResult guardrail = guardrailEngine.postCheck(decision, context);

        // 8. Execute or block.
        AgentCycleResult result;
        if (guardrail.isAllowed()) {
            result = applyAndLog(product, decision);
        } else {
            result = blockAndLog(product, decision, guardrail.getReason());
        }

        log.info("[CYCLE] === {} done: mode {} (allowed={}) ===",
                product.getId(), decision.getRecommendedMode(), guardrail.isAllowed());
        return result;
    }

    private AgentCycleResult applyAndLog(Product product, StrategyDecision decision) {
        String daytonaJobId = actionExecutorService.execute(decision);

        StrategyMode previousMode = product.getCurrentMode();
        BigDecimal previousPrice = product.getYourPrice();
        boolean loggedSomething = false;

        // Mode transition (logged whenever the recommended mode differs).
        if (decision.getRecommendedMode() != null && decision.getRecommendedMode() != previousMode) {
            saveAction(product, ActionType.MODE_TRANSITION,
                    String.valueOf(previousMode), String.valueOf(decision.getRecommendedMode()),
                    true, false, null, modeExplanation(decision), daytonaJobId);
            product.setCurrentMode(decision.getRecommendedMode());
            loggedSomething = true;
        }

        // Price update.
        if (decision.getActionType() == ActionType.PRICE_UPDATE
                && decision.getProposedPrice() != null
                && decision.getProposedPrice().compareTo(previousPrice) != 0) {
            saveAction(product, ActionType.PRICE_UPDATE,
                    fmt(previousPrice), fmt(decision.getProposedPrice()),
                    true, false, null, decision.getPlainEnglishExplanation(), daytonaJobId);
            product.setYourPrice(decision.getProposedPrice());
            loggedSomething = true;
        }

        // Ad-bid update.
        if (decision.getActionType() == ActionType.AD_BID_UPDATE
                && decision.getProposedAdBid() != null
                && decision.getProposedAdBid().compareTo(product.getAdBid()) != 0) {
            saveAction(product, ActionType.AD_BID_UPDATE,
                    fmt(product.getAdBid()), fmt(decision.getProposedAdBid()),
                    true, false, null, decision.getPlainEnglishExplanation(), daytonaJobId);
            product.setAdBid(decision.getProposedAdBid());
            loggedSomething = true;
        }

        // Nothing changed — record that the agent looked and chose to hold.
        if (!loggedSomething) {
            saveAction(product, ActionType.NO_ACTION, null, null,
                    true, false, null, decision.getPlainEnglishExplanation(), daytonaJobId);
        }

        productRepository.save(product);

        return new AgentCycleResult(
                product.getId(), previousMode, product.getCurrentMode(), decision.getActionType(),
                previousPrice, decision.getProposedPrice(), product.getYourPrice(),
                true, null, decision.getConfidence(),
                decision.getPlainEnglishExplanation(), decision.getSource(), daytonaJobId);
    }

    private AgentCycleResult blockAndLog(Product product, StrategyDecision decision, String reason) {
        // HARD block: do not mutate the product. Log to both tables.
        String proposedValue = decision.getProposedPrice() != null
                ? fmt(decision.getProposedPrice())
                : (decision.getProposedAdBid() != null ? fmt(decision.getProposedAdBid()) : null);

        GuardrailBlock block = new GuardrailBlock();
        block.setProductId(product.getId());
        block.setActionType(decision.getActionType());
        block.setProposedValue(proposedValue);
        block.setBlockReason(reason);
        guardrailBlockRepository.save(block);

        saveAction(product, decision.getActionType(),
                fmt(product.getYourPrice()), proposedValue,
                false, true, reason, null, null);

        return new AgentCycleResult(
                product.getId(), product.getCurrentMode(), product.getCurrentMode(), decision.getActionType(),
                product.getYourPrice(), decision.getProposedPrice(), product.getYourPrice(),
                false, reason, decision.getConfidence(),
                decision.getPlainEnglishExplanation(), decision.getSource(), null);
    }

    private void saveAction(Product product, ActionType type, String from, String to,
                            boolean executed, boolean blocked, String blockReason,
                            String explanation, String daytonaJobId) {
        ActionLog logEntry = new ActionLog();
        logEntry.setProductId(product.getId());
        logEntry.setActionType(type);
        logEntry.setFromValue(from);
        logEntry.setToValue(to);
        logEntry.setExecuted(executed);
        logEntry.setGuardrailBlocked(blocked);
        logEntry.setBlockReason(blockReason);
        logEntry.setKimiExplanation(explanation);
        logEntry.setDaytonaJobId(daytonaJobId);
        actionLogRepository.save(logEntry);
    }

    private String modeExplanation(StrategyDecision decision) {
        if (decision.getPlainEnglishExplanation() != null) {
            return decision.getPlainEnglishExplanation();
        }
        return decision.getModeReason();
    }

    private static String fmt(BigDecimal v) {
        return v == null ? null : v.toPlainString();
    }
}
