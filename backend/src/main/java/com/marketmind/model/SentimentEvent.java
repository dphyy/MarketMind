package com.marketmind.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "sentiment_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SentimentEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String category;

    @Column(name = "score_24h")
    private Double score24h;

    private String trend;

    @Column(name = "virality_flag")
    private Boolean viralityFlag;

    @Column(name = "virality_source")
    private String viralitySource;

    @Column(name = "top_signal")
    private String topSignal;

    @CreationTimestamp
    @Column(name = "recorded_at")
    private LocalDateTime recordedAt;
}
