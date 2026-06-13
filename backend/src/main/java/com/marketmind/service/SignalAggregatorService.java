package com.marketmind.service;

import com.marketmind.model.CompetitorSnapshot;
import com.marketmind.model.Product;
import com.marketmind.model.SentimentEvent;
import com.marketmind.model.SignalContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Normalises a product's raw signals (competitor snapshots + sentiment +
 * inventory) into the single {@link SignalContext} the agent reasons over.
 */
@Service
public class SignalAggregatorService {

    private static final Logger log = LoggerFactory.getLogger(SignalAggregatorService.class);

    /** Stock-level severity, most-actionable first. Used to pick the "main" competitor signal. */
    private static final Map<String, Integer> STOCK_SEVERITY = Map.of(
            "OUT_OF_STOCK", 4,
            "CRITICALLY_LOW", 3,
            "LOW", 2,
            "NORMAL", 1);

    /**
     * Heuristic daily sell-through used to estimate stock runway. We have no real
     * sales history in the hackathon dataset, so we model it as a fraction of
     * current stock — enough to drive the mode-transition rules sensibly.
     */
    private static final double SELL_THROUGH_FRACTION = 0.05;

    public SignalContext aggregate(Product product,
                                   List<CompetitorSnapshot> snapshots,
                                   SentimentEvent sentiment) {

        List<CompetitorSnapshot> latestPerCompetitor = latestPerCompetitor(snapshots);

        String mainStockLevel = latestPerCompetitor.stream()
                .map(CompetitorSnapshot::getStockLevel)
                .filter(level -> level != null)
                .max((a, b) -> Integer.compare(
                        STOCK_SEVERITY.getOrDefault(a, 0),
                        STOCK_SEVERITY.getOrDefault(b, 0)))
                .orElse(null);

        Double competitorPriceDeltaPct = latestPerCompetitor.stream()
                .map(CompetitorSnapshot::getPrice)
                .filter(price -> price != null)
                .min(BigDecimal::compareTo) // cheapest competitor
                .map(cheapest -> cheapest.subtract(product.getYourPrice())
                        .divide(product.getYourPrice(), 4, RoundingMode.HALF_UP)
                        .doubleValue())
                .orElse(null);

        Set<String> visualFlags = new LinkedHashSet<>();
        for (CompetitorSnapshot s : latestPerCompetitor) {
            if (s.getVisualSignals() != null) {
                for (String flag : s.getVisualSignals()) {
                    if (flag != null && !flag.isBlank()) {
                        visualFlags.add(flag);
                    }
                }
            }
        }

        double sellThrough = Math.max(1.0, product.getStock() * SELL_THROUGH_FRACTION);
        int stockDaysRemaining = (int) Math.floor(product.getStock() / sellThrough);

        SignalContext ctx = SignalContext.builder()
                .product(product)
                .mainCompetitorStockLevel(mainStockLevel)
                .competitorPriceDeltaPct(competitorPriceDeltaPct)
                .competitorVisualFlags(new ArrayList<>(visualFlags))
                .sentimentScore24h(sentiment != null ? sentiment.getScore24h() : null)
                .sentimentTrend(sentiment != null ? sentiment.getTrend() : null)
                .viralityFlag(sentiment != null ? sentiment.getViralityFlag() : null)
                .viralitySource(sentiment != null ? sentiment.getViralitySource() : null)
                .sentimentTopSignal(sentiment != null ? sentiment.getTopSignal() : null)
                .stockDaysRemaining(stockDaysRemaining)
                .sellThroughRate7d(round2(sellThrough))
                .currentMode(product.getCurrentMode())
                .build();

        log.info("[SIGNAL] {} → competitorStock={} priceDelta={} flags={} sentiment={} daysRemaining={}",
                product.getId(), mainStockLevel, competitorPriceDeltaPct, visualFlags,
                ctx.getSentimentScore24h(), stockDaysRemaining);
        return ctx;
    }

    /** Keep only the most recent snapshot per competitor (snapshots arrive newest-first). */
    private List<CompetitorSnapshot> latestPerCompetitor(List<CompetitorSnapshot> snapshots) {
        if (snapshots == null) {
            return List.of();
        }
        return new ArrayList<>(snapshots.stream()
                .collect(Collectors.toMap(
                        CompetitorSnapshot::getCompetitorName,
                        s -> s,
                        (first, later) -> first, // input is newest-first, so keep the first seen
                        java.util.LinkedHashMap::new))
                .values());
    }

    private static double round2(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
