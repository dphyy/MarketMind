package com.marketmind.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "competitor_snapshots")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CompetitorSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id")
    private String productId;

    @Column(name = "competitor_name")
    private String competitorName;

    private BigDecimal price;

    @Column(name = "stock_indicator")
    private String stockIndicator;

    @Column(name = "stock_level")
    private String stockLevel;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "visual_signals", columnDefinition = "text[]")
    private String[] visualSignals;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "data_source")
    private String dataSource;

    @CreationTimestamp
    @Column(name = "scraped_at")
    private LocalDateTime scrapedAt;
}
