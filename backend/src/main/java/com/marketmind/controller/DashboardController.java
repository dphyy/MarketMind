package com.marketmind.controller;

import com.marketmind.dto.ProductOverview;
import com.marketmind.model.ActionLog;
import com.marketmind.model.CompetitorSnapshot;
import com.marketmind.model.GuardrailBlock;
import com.marketmind.model.Product;
import com.marketmind.repository.ActionLogRepository;
import com.marketmind.repository.CompetitorSnapshotRepository;
import com.marketmind.repository.GuardrailBlockRepository;
import com.marketmind.repository.ProductRepository;
import com.marketmind.repository.SentimentEventRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class DashboardController {

    private final ProductRepository productRepo;
    private final CompetitorSnapshotRepository competitorRepo;
    private final SentimentEventRepository sentimentRepo;
    private final ActionLogRepository actionLogRepo;
    private final GuardrailBlockRepository guardrailRepo;

    public DashboardController(ProductRepository productRepo,
                              CompetitorSnapshotRepository competitorRepo,
                              SentimentEventRepository sentimentRepo,
                              ActionLogRepository actionLogRepo,
                              GuardrailBlockRepository guardrailRepo) {
        this.productRepo = productRepo;
        this.competitorRepo = competitorRepo;
        this.sentimentRepo = sentimentRepo;
        this.actionLogRepo = actionLogRepo;
        this.guardrailRepo = guardrailRepo;
    }

    @GetMapping("/dashboard/overview")
    public List<ProductOverview> overview() {
        return productRepo.findAll().stream()
                .map(p -> new ProductOverview(
                        p,
                        sentimentRepo.findTopByCategoryOrderByRecordedAtDesc(p.getCategory()).orElse(null),
                        competitorRepo.findByProductIdOrderByScrapedAtDesc(p.getId())))
                .toList();
    }

    @GetMapping("/dashboard/competitors/{productId}")
    public List<CompetitorSnapshot> competitors(@PathVariable String productId) {
        return competitorRepo.findByProductIdOrderByScrapedAtDesc(productId);
    }

    @GetMapping("/products")
    public List<Product> products() {
        return productRepo.findAll();
    }

    @GetMapping("/actions")
    public List<ActionLog> actions() {
        return actionLogRepo.findAllByOrderByCreatedAtDesc();
    }

    @GetMapping("/guardrails/blocks")
    public List<GuardrailBlock> guardrailBlocks() {
        return guardrailRepo.findAllByOrderByCreatedAtDesc();
    }
}
