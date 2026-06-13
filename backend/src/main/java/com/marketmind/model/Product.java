package com.marketmind.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Product {

    @Id
    private String id;

    private String name;
    private String category;

    @Column(name = "your_price")
    private BigDecimal yourPrice;

    @Column(name = "price_floor")
    private BigDecimal priceFloor;

    @Column(name = "price_ceiling")
    private BigDecimal priceCeiling;

    private Integer stock;

    @Column(name = "stock_reserve_min")
    private Integer stockReserveMin;

    @Column(name = "max_daily_price_change_pct")
    private BigDecimal maxDailyPriceChangePct;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_mode")
    private StrategyMode currentMode;

    @Column(name = "ad_bid")
    private BigDecimal adBid;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
