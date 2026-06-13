package com.marketmind.service;

import com.marketmind.model.SentimentEvent;
import com.marketmind.repository.SentimentEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Provides the latest social-sentiment reading for a product category.
 *
 * <p>For the hackathon, sentiment is served from the pre-baked
 * {@code sentiment_events} seed data (the guaranteed fallback). In production a
 * batch of recent social posts would be sent to Kimi for classification (see
 * the brief, §8) and aggregated into the same {@link SentimentEvent} shape; the
 * downstream pipeline is identical either way. Every read logs its source.
 */
@Service
public class SentimentService {

    private static final Logger log = LoggerFactory.getLogger(SentimentService.class);

    private final SentimentEventRepository sentimentRepo;

    public SentimentService(SentimentEventRepository sentimentRepo) {
        this.sentimentRepo = sentimentRepo;
    }

    public SentimentEvent getSentiment(String category) {
        SentimentEvent event = sentimentRepo
                .findTopByCategoryOrderByRecordedAtDesc(category)
                .orElse(null);
        if (event == null) {
            log.info("[MOCK] No sentiment on record for category '{}' — treating as unavailable", category);
        } else {
            log.info("[MOCK] Sentiment for '{}': score={} trend={} virality={}",
                    category, event.getScore24h(), event.getTrend(), event.getViralityFlag());
        }
        return event;
    }
}
