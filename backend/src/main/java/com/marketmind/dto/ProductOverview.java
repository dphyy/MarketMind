package com.marketmind.dto;

import com.marketmind.model.CompetitorSnapshot;
import com.marketmind.model.Product;
import com.marketmind.model.SentimentEvent;

import java.util.List;

public record ProductOverview(
        Product product,
        SentimentEvent sentiment,
        List<CompetitorSnapshot> competitors
) {
}
