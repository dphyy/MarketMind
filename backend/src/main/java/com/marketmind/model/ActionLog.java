package com.marketmind.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "action_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ActionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id")
    private String productId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type")
    private ActionType actionType;

    @Column(name = "from_value")
    private String fromValue;

    @Column(name = "to_value")
    private String toValue;

    private Boolean executed;

    @Column(name = "guardrail_blocked")
    private Boolean guardrailBlocked;

    @Column(name = "block_reason")
    private String blockReason;

    @Column(name = "kimi_explanation")
    private String kimiExplanation;

    @Column(name = "daytona_job_id")
    private String daytonaJobId;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
