package com.marketmind.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketmind.dto.AgentCycleResult;
import com.marketmind.model.ActionLog;
import com.marketmind.model.ActionType;
import com.marketmind.model.CompetitorSnapshot;
import com.marketmind.model.GuardrailBlock;
import com.marketmind.model.Product;
import com.marketmind.model.SentimentEvent;
import com.marketmind.model.StrategyMode;
import com.marketmind.repository.ActionLogRepository;
import com.marketmind.repository.GuardrailBlockRepository;
import com.marketmind.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * End-to-end-on-mock test of the guaranteed demo: clicking "Run Agent Cycle"
 * for SKU-001 must flip HOLD → HARVEST and raise the price $49.90 → $54.90,
 * ALLOWED by the guardrail. Wires the real aggregator, guardrail engine,
 * rule-based reasoning, and executor; only the data sources and repositories
 * are mocked. No database or API keys required.
 */
class AgentCycleServiceDemoTest {

    private ProductRepository productRepo;
    private ActionLogRepository actionLogRepo;
    private GuardrailBlockRepository guardrailBlockRepo;
    private BrightDataService brightData;
    private SenseNovaService senseNova;
    private SentimentService sentiment;
    private AgentCycleService cycle;

    @BeforeEach
    void setUp() {
        productRepo = mock(ProductRepository.class);
        actionLogRepo = mock(ActionLogRepository.class);
        guardrailBlockRepo = mock(GuardrailBlockRepository.class);
        brightData = mock(BrightDataService.class);
        senseNova = mock(SenseNovaService.class);
        sentiment = mock(SentimentService.class);

        ObjectMapper mapper = new ObjectMapper();
        // Real collaborators — this is the logic under test.
        SignalAggregatorService aggregator = new SignalAggregatorService();
        GuardrailEngine guardrail = new GuardrailEngine(actionLogRepo);
        KimiReasoningService reasoning = new KimiReasoningService(new KimiClient(null, mapper), mapper);
        // No Daytona key in unit context → simulated path; HttpClient is unused.
        ActionExecutorService executor = new ActionExecutorService(java.net.http.HttpClient.newHttpClient(), mapper);

        cycle = new AgentCycleService(brightData, senseNova, sentiment, aggregator, guardrail,
                reasoning, executor, productRepo, actionLogRepo, guardrailBlockRepo);

        // No price changes today → day-open falls back to your_price.
        when(actionLogRepo.findByProductIdAndActionTypeAndExecutedTrueAndCreatedAtAfter(
                any(), any(), any())).thenReturn(List.of());
        when(senseNova.extractVisualSignals(any())).thenReturn(List.of());
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

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

    private static CompetitorSnapshot competitor(String name, String price, String level, String[] flags) {
        CompetitorSnapshot s = new CompetitorSnapshot();
        s.setProductId("SKU-001");
        s.setCompetitorName(name);
        s.setPrice(bd(price));
        s.setStockLevel(level);
        s.setVisualSignals(flags);
        return s;
    }

    private static SentimentEvent wirelessEarbudsSentiment() {
        SentimentEvent e = new SentimentEvent();
        e.setCategory("wireless_earbuds");
        e.setScore24h(0.78);
        e.setTrend("ACCELERATING_POSITIVE");
        e.setViralityFlag(true);
        e.setViralitySource("tiktok_hashtag_spike");
        return e;
    }

    @Test
    void runCycle_sku001_producesAllowedHarvestToFiftyFourNinety() {
        Product product = sku001();
        when(productRepo.findById("SKU-001")).thenReturn(Optional.of(product));
        when(brightData.scrapeCompetitors(any())).thenReturn(List.of(
                competitor("AudioZone SG", "47.50", "CRITICALLY_LOW", new String[]{"clearance_banner"}),
                competitor("TechDealsSG", "51.00", "NORMAL", new String[]{})));
        when(sentiment.getSentiment("wireless_earbuds")).thenReturn(wirelessEarbudsSentiment());

        AgentCycleResult result = cycle.runCycle("SKU-001");

        assertThat(result.allowed()).isTrue();
        assertThat(result.newMode()).isEqualTo(StrategyMode.HARVEST);
        assertThat(result.previousMode()).isEqualTo(StrategyMode.HOLD);
        assertThat(result.newPrice()).isEqualByComparingTo("54.90");
        assertThat(result.actionType()).isEqualTo(ActionType.PRICE_UPDATE);

        // The product was mutated and persisted.
        assertThat(product.getCurrentMode()).isEqualTo(StrategyMode.HARVEST);
        assertThat(product.getYourPrice()).isEqualByComparingTo("54.90");
        verify(productRepo).save(product);

        // A mode transition and a price update were logged; nothing was blocked.
        ArgumentCaptor<ActionLog> logs = ArgumentCaptor.forClass(ActionLog.class);
        verify(actionLogRepo, atLeastOnce()).save(logs.capture());
        List<ActionType> savedTypes = logs.getAllValues().stream().map(ActionLog::getActionType).toList();
        assertThat(savedTypes).contains(ActionType.MODE_TRANSITION, ActionType.PRICE_UPDATE);
        verify(guardrailBlockRepo, never()).save(any(GuardrailBlock.class));
    }

    @Test
    void runCycle_blocksAndLogs_whenProposedPriceExceedsVelocityCap() {
        // Current price already near the ceiling; a +10% HARVEST lift clamps to the
        // ceiling ($65.00), which from day-open $49.90 is +30% — over the 15% cap.
        Product product = sku001();
        product.setYourPrice(bd("60.00"));
        when(productRepo.findById("SKU-001")).thenReturn(Optional.of(product));
        when(brightData.scrapeCompetitors(any())).thenReturn(List.of(
                competitor("AudioZone SG", "59.00", "CRITICALLY_LOW", new String[]{"clearance_banner"})));
        when(sentiment.getSentiment("wireless_earbuds")).thenReturn(wirelessEarbudsSentiment());
        // Day-open today was $49.90 (an earlier executed price update).
        ActionLog earlier = new ActionLog();
        earlier.setActionType(ActionType.PRICE_UPDATE);
        earlier.setFromValue("49.90");
        earlier.setExecuted(true);
        earlier.setCreatedAt(java.time.LocalDateTime.now().minusHours(2));
        when(actionLogRepo.findByProductIdAndActionTypeAndExecutedTrueAndCreatedAtAfter(
                any(), any(), any())).thenReturn(List.of(earlier));

        AgentCycleResult result = cycle.runCycle("SKU-001");

        assertThat(result.allowed()).isFalse();
        assertThat(result.blockReason()).contains("velocity cap");
        // Price was NOT mutated on a hard block.
        assertThat(product.getYourPrice()).isEqualByComparingTo("60.00");
        verify(guardrailBlockRepo).save(any(GuardrailBlock.class));
    }
}
